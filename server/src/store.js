'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const DATA_DIR = path.join(__dirname, '..', 'data');
const FILE = path.join(DATA_DIR, 'accounts.json');

let accounts = {};

function load() {
  try {
    accounts = JSON.parse(fs.readFileSync(FILE, 'utf8'));
  } catch {
    accounts = {};
  }
}

function persist() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  fs.writeFileSync(FILE, JSON.stringify(accounts, null, 2));
}

function hash(password, salt) {
  return crypto.scryptSync(password, salt, 64).toString('hex');
}

function isRegistered(nickname) {
  return Object.prototype.hasOwnProperty.call(accounts, nickname.toLowerCase());
}

/**
 * There is no separate registration step: the first password to ever
 * authenticate a nickname is stored as that nickname's password (this is how
 * the mod's "first time you turn this on, pick a password" flow becomes
 * durable server-side). Every call after that is a plain verify.
 */
function verifyOrRegister(nickname, password) {
  const key = nickname.toLowerCase();
  const existing = accounts[key];
  if (!existing) {
    const salt = crypto.randomBytes(16).toString('hex');
    accounts[key] = { salt, hash: hash(password, salt), displayName: nickname };
    persist();
    return true;
  }
  return existing.hash === hash(password, existing.salt);
}

load();

module.exports = { isRegistered, verifyOrRegister };
