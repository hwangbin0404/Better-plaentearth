'use strict';

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

const BASE_STYLE = `
  :root { color-scheme: light dark; }
  * { box-sizing: border-box; }
  body {
    font-family: -apple-system, "Segoe UI", "Malgun Gothic", sans-serif;
    background: #0f1115; color: #e8e8e8;
    display: flex; align-items: center; justify-content: center;
    min-height: 100vh; margin: 0; padding: 16px;
  }
  .card {
    background: #171a21; border: 1px solid rgba(255,255,255,0.08); border-radius: 16px;
    padding: 32px; width: 100%; max-width: 340px; text-align: center;
  }
  img.head { border-radius: 10px; image-rendering: pixelated; }
  h1 { font-size: 20px; margin: 16px 0 4px; word-break: break-all; }
  .muted { color: #9aa0a6; font-size: 13px; }
  .badge {
    display: inline-flex; align-items: center; gap: 8px; margin-top: 18px;
    padding: 8px 16px; border-radius: 999px; font-weight: 600; font-size: 14px;
  }
  .dot { width: 9px; height: 9px; border-radius: 50%; }
  .badge.offline { background: rgba(120,120,120,0.15); color: #9aa0a6; }
  .badge.offline .dot { background: #9aa0a6; }
  .badge.connecting { background: rgba(245,158,11,0.15); color: #f59e0b; }
  .badge.connecting .dot { background: #f59e0b; }
  .badge.queue { background: rgba(59,130,246,0.15); color: #3b82f6; }
  .badge.queue .dot { background: #3b82f6; }
  .badge.online { background: rgba(34,197,94,0.15); color: #22c55e; }
  .badge.online .dot { background: #22c55e; }
  .updated { margin-top: 10px; font-size: 12px; }
  form { margin-top: 20px; display: flex; flex-direction: column; gap: 10px; }
  input[type=password] {
    background: rgba(255,255,255,0.06); color: inherit; border: 1px solid rgba(255,255,255,0.15);
    border-radius: 8px; padding: 10px 12px; font-size: 14px; text-align: center;
  }
  button {
    border: none; border-radius: 8px; padding: 10px 12px; font-size: 14px; font-weight: 600;
    cursor: pointer;
  }
  button.primary { background: #3b82f6; color: white; }
  button.danger { background: rgba(239,68,68,0.15); color: #ef4444; margin-top: 18px; width: 100%; }
  button.danger:disabled { opacity: 0.4; cursor: not-allowed; }
  .error { color: #ef4444; font-size: 13px; margin: 4px 0 0; }

  /* Must come last: same-specificity rules above would otherwise win by source
     order even when this media query matches, silently no-oping the override
     (e.g. dark card background + inherited light-mode text color = invisible text). */
  @media (prefers-color-scheme: light) {
    body { background: #f2f3f5; color: #1a1a1a; }
    .card { background: #ffffff; border-color: rgba(0,0,0,0.08); box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
    .muted { color: #6b7280; }
    input[type=password] { background: #f7f7f8; color: #1a1a1a; border-color: rgba(0,0,0,0.15); }
  }
`;

function shell(nickname, body) {
  return `<!doctype html><html lang="ko"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(nickname)} - Better PlanetEarth</title>
<style>${BASE_STYLE}</style>
</head><body>${body}</body></html>`;
}

function renderLoginPage(nickname, failed) {
  const body = `<div class="card">
    <img class="head" src="https://mc-heads.net/avatar/${encodeURIComponent(nickname)}/72" width="72" height="72" alt="">
    <h1>${escapeHtml(nickname)}</h1>
    <p class="muted">이 페이지는 비밀번호로 보호되어 있습니다.</p>
    ${failed ? '<p class="error">비밀번호가 올바르지 않습니다.</p>' : ''}
    <form method="post" action="/${encodeURIComponent(nickname)}/login">
      <input type="password" name="password" placeholder="비밀번호" autofocus required>
      <button class="primary" type="submit">확인</button>
    </form>
  </div>`;
  return shell(nickname, body);
}

function renderNotRegisteredPage(nickname) {
  const body = `<div class="card">
    <img class="head" src="https://mc-heads.net/avatar/${encodeURIComponent(nickname)}/72" width="72" height="72" alt="">
    <h1>${escapeHtml(nickname)}</h1>
    <p class="muted">이 닉네임은 아직 Better PlanetEarth 모드에서<br>원격 상태 페이지 기능을 켠 적이 없습니다.</p>
  </div>`;
  return shell(nickname, body);
}

const STATE_LABEL = {
  OFFLINE: '오프라인',
  CONNECTING: '접속 확인 중',
  QUEUE: '대기열',
  ONLINE: '온라인 (본서버)',
};
const STATE_CLASS = {
  OFFLINE: 'offline',
  CONNECTING: 'connecting',
  QUEUE: 'queue',
  ONLINE: 'online',
};

function renderStatusPage(nickname) {
  const nick = encodeURIComponent(nickname);
  const body = `<div class="card">
    <img class="head" id="head" src="https://mc-heads.net/avatar/${nick}/96" width="96" height="96" alt="">
    <h1>${escapeHtml(nickname)}</h1>
    <div class="badge offline" id="badge"><span class="dot"></span><span id="badge-text">확인 중...</span></div>
    <div class="muted updated" id="updated"></div>
    <button class="danger" id="disconnect-btn" disabled>접속 종료</button>
  </div>
  <script>
    const STATE_LABEL = ${JSON.stringify(STATE_LABEL)};
    const STATE_CLASS = ${JSON.stringify(STATE_CLASS)};
    const badge = document.getElementById('badge');
    const badgeText = document.getElementById('badge-text');
    const updated = document.getElementById('updated');
    const btn = document.getElementById('disconnect-btn');

    function render(s) {
      const cls = STATE_CLASS[s.state] || 'offline';
      badge.className = 'badge ' + cls;
      let text = STATE_LABEL[s.state] || s.state;
      if (s.state === 'QUEUE' && s.pos != null && s.total != null) {
        text += ' ' + s.pos + ' / ' + s.total;
      }
      badgeText.textContent = text;
      updated.textContent = s.updatedAt ? ('마지막 업데이트: ' + new Date(s.updatedAt).toLocaleTimeString('ko-KR')) : '';
      btn.disabled = (s.state === 'OFFLINE');
    }

    const es = new EventSource('/${nick}/events');
    es.onmessage = (e) => render(JSON.parse(e.data));

    btn.addEventListener('click', async () => {
      btn.disabled = true;
      btn.textContent = '요청 보내는 중...';
      try {
        const res = await fetch('/${nick}/disconnect', { method: 'POST' });
        const data = await res.json();
        btn.textContent = data.ok ? '접속 종료 요청 보냄' : '접속 중인 클라이언트 없음';
      } catch (e) {
        btn.textContent = '요청 실패';
      }
      setTimeout(() => { btn.textContent = '접속 종료'; }, 2000);
    });
  </script>`;
  return shell(nickname, body);
}

module.exports = { renderLoginPage, renderNotRegisteredPage, renderStatusPage, escapeHtml };
