'use strict';

const EventEmitter = require('events');

const emitter = new EventEmitter();
emitter.setMaxListeners(0);

const statuses = new Map(); // nickname(lower) -> { state, pos, total, updatedAt }
const modSockets = new Map(); // nickname(lower) -> live ws connection from the mod
const chatBuffers = new Map(); // nickname(lower) -> recent {text, ts}[], newest last
const CHAT_BUFFER_LIMIT = 200;

function setStatus(nickname, status) {
  const key = nickname.toLowerCase();
  const value = { state: 'OFFLINE', ...status, updatedAt: Date.now() };
  statuses.set(key, value);
  emitter.emit('update:' + key, value);
}

function getStatus(nickname) {
  return statuses.get(nickname.toLowerCase()) || { state: 'OFFLINE', updatedAt: 0 };
}

function onUpdate(nickname, cb) {
  const event = 'update:' + nickname.toLowerCase();
  emitter.on(event, cb);
  return () => emitter.off(event, cb);
}

function pushChat(nickname, text) {
  const key = nickname.toLowerCase();
  const entry = { text, ts: Date.now() };
  let buf = chatBuffers.get(key);
  if (!buf) {
    buf = [];
    chatBuffers.set(key, buf);
  }
  buf.push(entry);
  if (buf.length > CHAT_BUFFER_LIMIT) {
    buf.shift();
  }
  emitter.emit('chat:' + key, entry);
}

function getChatHistory(nickname) {
  return chatBuffers.get(nickname.toLowerCase()) || [];
}

function onChat(nickname, cb) {
  const event = 'chat:' + nickname.toLowerCase();
  emitter.on(event, cb);
  return () => emitter.off(event, cb);
}

function setModSocket(nickname, ws) {
  modSockets.set(nickname.toLowerCase(), ws);
}

function clearModSocket(nickname, ws) {
  const key = nickname.toLowerCase();
  if (modSockets.get(key) === ws) {
    modSockets.delete(key);
  }
}

/** Returns true if a live mod connection took the request, false if the player isn't connected. */
function requestDisconnect(nickname) {
  const ws = modSockets.get(nickname.toLowerCase());
  if (ws && ws.readyState === ws.OPEN) {
    ws.send(JSON.stringify({ type: 'disconnect_request' }));
    return true;
  }
  return false;
}

/** Returns true if a live mod connection took the command, false if the player isn't connected. */
function requestCommand(nickname, command) {
  const ws = modSockets.get(nickname.toLowerCase());
  if (ws && ws.readyState === ws.OPEN) {
    ws.send(JSON.stringify({ type: 'run_command', command }));
    return true;
  }
  return false;
}

module.exports = {
  setStatus, getStatus, onUpdate,
  pushChat, getChatHistory, onChat,
  setModSocket, clearModSocket,
  requestDisconnect, requestCommand,
};
