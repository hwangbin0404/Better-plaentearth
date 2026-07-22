'use strict';

const http = require('http');
const crypto = require('crypto');
const express = require('express');
const cookieParser = require('cookie-parser');
const { WebSocketServer } = require('ws');

const store = require('./store');
const state = require('./state');
const peapi = require('./peapi');
const { renderHomePage, renderLoginPage, renderNotRegisteredPage, renderStatusPage } = require('./page');

const PORT = process.env.PORT || 8787;
const NICK_RE = /^[A-Za-z0-9_]{1,16}$/;
const SESSION_TTL_MS = 30 * 24 * 3600 * 1000; // 30 days

const app = express();
app.set('trust proxy', 1); // behind Caddy
app.use(cookieParser());
app.use(express.urlencoded({ extended: false }));

// nickname -> session token -> expiry. Tokens are per-nickname so the cookie only
// ever needs to prove "this browser knows this one nickname's password".
const sessions = new Map();

function cookieName(nickname) {
  return 'bpe_sess_' + nickname.toLowerCase();
}

function checkAuth(req, nickname) {
  const token = req.cookies[cookieName(nickname)];
  if (!token) return false;
  const entry = sessions.get(token);
  if (!entry || entry.nickname !== nickname.toLowerCase()) return false;
  if (entry.expires < Date.now()) {
    sessions.delete(token);
    return false;
  }
  return true;
}

app.get('/healthz', (req, res) => res.send('ok'));

app.get('/', (req, res) => res.send(renderHomePage()));

app.get('/:nickname', async (req, res) => {
  const nickname = req.params.nickname;
  if (!NICK_RE.test(nickname)) {
    return res.status(400).send('잘못된 닉네임입니다.');
  }
  if (!store.isRegistered(nickname)) {
    return res.status(404).send(renderNotRegisteredPage(nickname));
  }
  if (!checkAuth(req, nickname)) {
    return res.send(renderLoginPage(nickname, false));
  }
  const resident = await peapi.getResident(nickname);
  res.send(renderStatusPage(nickname, resident));
});

app.post('/:nickname/login', (req, res) => {
  const nickname = req.params.nickname;
  if (!NICK_RE.test(nickname)) {
    return res.status(400).send('잘못된 닉네임입니다.');
  }
  if (!store.isRegistered(nickname)) {
    return res.status(404).send(renderNotRegisteredPage(nickname));
  }
  const password = req.body.password || '';
  if (!store.verifyOrRegister(nickname, password)) {
    return res.send(renderLoginPage(nickname, true));
  }
  const token = crypto.randomBytes(24).toString('hex');
  sessions.set(token, { nickname: nickname.toLowerCase(), expires: Date.now() + SESSION_TTL_MS });
  res.cookie(cookieName(nickname), token, {
    httpOnly: true,
    sameSite: 'lax',
    secure: true,
    maxAge: SESSION_TTL_MS,
    path: '/' + nickname,
  });
  res.redirect('/' + encodeURIComponent(nickname));
});

app.get('/:nickname/events', (req, res) => {
  const nickname = req.params.nickname;
  if (!NICK_RE.test(nickname) || !checkAuth(req, nickname)) {
    return res.status(401).end();
  }
  res.set({
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
  });
  res.flushHeaders();

  const write = (payload) => res.write(`data: ${JSON.stringify(payload)}\n\n`);
  write({ kind: 'status', ...state.getStatus(nickname) });
  write({ kind: 'chat_history', lines: state.getChatHistory(nickname) });

  const unsubStatus = state.onUpdate(nickname, (status) => write({ kind: 'status', ...status }));
  const unsubChat = state.onChat(nickname, (entry) => write({ kind: 'chat', ...entry }));
  const heartbeat = setInterval(() => res.write(':ping\n\n'), 25000);

  req.on('close', () => {
    clearInterval(heartbeat);
    unsubStatus();
    unsubChat();
  });
});

app.get('/:nickname/resident', async (req, res) => {
  const nickname = req.params.nickname;
  if (!NICK_RE.test(nickname) || !checkAuth(req, nickname)) {
    return res.status(401).end();
  }
  res.json(await peapi.getResident(nickname));
});

app.post('/:nickname/disconnect', (req, res) => {
  const nickname = req.params.nickname;
  if (!NICK_RE.test(nickname) || !checkAuth(req, nickname)) {
    return res.status(401).end();
  }
  res.json({ ok: state.requestDisconnect(nickname) });
});

app.post('/:nickname/command', express.json(), (req, res) => {
  const nickname = req.params.nickname;
  if (!NICK_RE.test(nickname) || !checkAuth(req, nickname)) {
    return res.status(401).end();
  }
  const command = String(req.body.command || '').slice(0, 256).trim();
  if (!command) {
    return res.status(400).json({ ok: false });
  }
  res.json({ ok: state.requestCommand(nickname, command) });
});

const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

// ---- Mod protocol ----
// -> {"type":"auth","nickname":"...","password":"..."}     first message after connect
// <- {"type":"auth_ok"} | {"type":"auth_fail"}
// -> {"type":"status","state":"OFFLINE|CONNECTING|QUEUE|ONLINE","pos":N,"total":N}
// -> {"type":"chat","text":"..."}                           every game chat line, forwarded live
// <- {"type":"disconnect_request"}                          web UI clicked "접속 종료"
// <- {"type":"run_command","command":"..."}                 web UI submitted the command box
wss.on('connection', (ws) => {
  let authedNickname = null;

  ws.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      return;
    }

    if (msg.type === 'auth') {
      const nickname = String(msg.nickname || '');
      if (!NICK_RE.test(nickname)) {
        ws.close(4000, 'bad nickname');
        return;
      }
      if (!store.verifyOrRegister(nickname, String(msg.password || ''))) {
        ws.send(JSON.stringify({ type: 'auth_fail' }));
        ws.close(4001, 'bad password');
        return;
      }
      authedNickname = nickname;
      state.setModSocket(nickname, ws);
      ws.send(JSON.stringify({ type: 'auth_ok' }));
      return;
    }

    if (msg.type === 'status' && authedNickname) {
      state.setStatus(authedNickname, {
        state: msg.state,
        pos: typeof msg.pos === 'number' ? msg.pos : undefined,
        total: typeof msg.total === 'number' ? msg.total : undefined,
      });
    }

    if (msg.type === 'chat' && authedNickname && typeof msg.text === 'string') {
      state.pushChat(authedNickname, msg.text);
    }
  });

  ws.on('close', () => {
    if (authedNickname) {
      state.clearModSocket(authedNickname, ws);
      state.setStatus(authedNickname, { state: 'OFFLINE' });
    }
  });
});

server.listen(PORT, '127.0.0.1', () => {
  console.log('Better PlanetEarth status server listening on 127.0.0.1:' + PORT);
});
