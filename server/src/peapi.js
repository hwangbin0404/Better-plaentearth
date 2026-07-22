'use strict';

// Read-only client for the public PlanetEarth API (documented at
// https://docs.planetearth.kr/api, served from https://api.planetearth.kr).
// Used to show each player's actual nation/town/API-reported online state on
// their status page, on top of the mod's own real-time push (which is the
// only thing that can see queue position, but has no idea about nation/town).

const BASE = 'https://api.planetearth.kr';
const CACHE_TTL_MS = 60 * 1000;
const cache = new Map(); // nickname(lower) -> { data, expires }

async function getResident(nickname) {
  const key = nickname.toLowerCase();
  const cached = cache.get(key);
  if (cached && cached.expires > Date.now()) {
    return cached.data;
  }
  try {
    const res = await fetch(`${BASE}/resident?name=${encodeURIComponent(nickname)}`, {
      headers: { 'User-Agent': 'BetterPlanetEarth-Status' },
      signal: AbortSignal.timeout(8000),
    });
    if (!res.ok) {
      return cached ? cached.data : null;
    }
    const json = await res.json();
    if (json.status !== 'SUCCESS' || !Array.isArray(json.data) || json.data.length === 0) {
      cache.set(key, { data: null, expires: Date.now() + CACHE_TTL_MS });
      return null;
    }
    const data = json.data[0];
    cache.set(key, { data, expires: Date.now() + CACHE_TTL_MS });
    return data;
  } catch {
    return cached ? cached.data : null;
  }
}

module.exports = { getResident };
