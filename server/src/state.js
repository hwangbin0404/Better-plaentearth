'use strict';

const EventEmitter = require('events');

const emitter = new EventEmitter();
emitter.setMaxListeners(0);

const statuses = new Map(); // nickname(lower) -> { state, pos, total, updatedAt }
const modSockets = new Map(); // nickname(lower) -> live ws connection from the mod

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

module.exports = { setStatus, getStatus, onUpdate, setModSocket, clearModSocket, requestDisconnect };
