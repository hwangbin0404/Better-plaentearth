package com.betterpe.api;

import com.betterpe.BetterPlanetEarth;
import com.betterpe.api.model.Nation;
import com.betterpe.api.model.Resident;
import com.betterpe.api.model.Town;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rate-limit-aware client for the PlanetEarth REST API (https://api.planetearth.kr).
 *
 * The public API allows 70 requests / 15 min (bulk: 5 / 15 min) per IP. On a 429 we
 * stop sending for a full 15-minute cooldown, then resume automatically. Successful
 * lookups are cached per key for a TTL so repeated queries (e.g. crosshair tag on the
 * same player) don't burn quota.
 */
public final class PlanetEarthApi {
	private static final String BASE = "https://api.planetearth.kr";
	private static final Gson GSON = new Gson();
	private static final long COOLDOWN_MS = 15 * 60 * 1000L;
	private static final long DEFAULT_TTL_MS = 5 * 60 * 1000L;

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.build();
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "BetterPE-API");
		t.setDaemon(true);
		return t;
	});

	private static final Map<String, CacheEntry> CACHE = new HashMap<>();
	private static volatile long cooldownUntil = 0L;

	private PlanetEarthApi() {
	}

	public static boolean isRateLimited() {
		return System.currentTimeMillis() < cooldownUntil;
	}

	/** Remaining cooldown in seconds (0 if not limited). */
	public static long cooldownSecondsLeft() {
		long left = cooldownUntil - System.currentTimeMillis();
		return left > 0 ? left / 1000 : 0;
	}

	// ---- Typed lookups (async) ----

	public static CompletableFuture<Resident> resident(String name) {
		return firstOf("/resident?name=" + enc(name), Resident.class, "resident:" + name.toLowerCase());
	}

	public static CompletableFuture<Nation> nation(String name) {
		return firstOf("/nation?name=" + enc(name), Nation.class, "nation:" + name.toLowerCase());
	}

	public static CompletableFuture<Town> town(String name) {
		return firstOf("/town?name=" + enc(name), Town.class, "town:" + name.toLowerCase());
	}

	/**
	 * GET /town/bulk — the full town dataset in one request (bulk quota: 5 / 15 min).
	 * Cached for 15 minutes since it is heavy. Used by the ally-highlight feature to
	 * resolve every nation's residents locally without per-town calls.
	 */
	public static CompletableFuture<java.util.List<Town>> townBulk() {
		String cacheKey = "town:bulk";
		CacheEntry cached = CACHE.get(cacheKey);
		long now = System.currentTimeMillis();
		if (cached != null && now < cached.expiresAt) {
			@SuppressWarnings("unchecked")
			java.util.List<Town> value = (java.util.List<Town>) cached.value;
			return CompletableFuture.completedFuture(value);
		}
		if (isRateLimited()) {
			return CompletableFuture.completedFuture(java.util.Collections.emptyList());
		}
		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/town/bulk"))
						.timeout(Duration.ofSeconds(20))
						.header("User-Agent", "BetterPlanetEarth-Mod")
						.GET()
						.build();
				HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
				if (resp.statusCode() == 429) {
					cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS;
					BetterPlanetEarth.LOGGER.warn("PlanetEarth API rate limited (429); pausing for 15 minutes");
					return java.util.Collections.<Town>emptyList();
				}
				if (resp.statusCode() != 200) {
					return java.util.Collections.<Town>emptyList();
				}
				JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
				if (!"SUCCESS".equals(getString(root, "status"))) {
					return java.util.Collections.<Town>emptyList();
				}
				JsonArray data = root.getAsJsonArray("data");
				java.util.List<Town> out = new java.util.ArrayList<>();
				if (data != null) {
					for (int i = 0; i < data.size(); i++) {
						out.add(GSON.fromJson(data.get(i), Town.class));
					}
				}
				CACHE.put(cacheKey, new CacheEntry(out, System.currentTimeMillis() + COOLDOWN_MS));
				return out;
			} catch (Exception e) {
				BetterPlanetEarth.LOGGER.debug("Bulk town request failed", e);
				return java.util.Collections.<Town>emptyList();
			}
		}, EXECUTOR);
	}

	// ---- Core ----

	private static <T> CompletableFuture<T> firstOf(String path, Class<T> type, String cacheKey) {
		CacheEntry cached = CACHE.get(cacheKey);
		long now = System.currentTimeMillis();
		if (cached != null && now < cached.expiresAt) {
			@SuppressWarnings("unchecked")
			T value = (T) cached.value;
			return CompletableFuture.completedFuture(value);
		}
		if (isRateLimited()) {
			return CompletableFuture.completedFuture(null);
		}

		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
						.timeout(Duration.ofSeconds(10))
						.header("User-Agent", "BetterPlanetEarth-Mod")
						.GET()
						.build();
				HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

				if (resp.statusCode() == 429) {
					cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS;
					BetterPlanetEarth.LOGGER.warn("PlanetEarth API rate limited (429); pausing for 15 minutes");
					return null;
				}
				if (resp.statusCode() != 200) {
					return null;
				}

				JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
				if (!"SUCCESS".equals(getString(root, "status"))) {
					return null;
				}
				JsonArray data = root.getAsJsonArray("data");
				if (data == null || data.size() == 0) {
					return null;
				}
				T value = GSON.fromJson(data.get(0), type);
				CACHE.put(cacheKey, new CacheEntry(value, System.currentTimeMillis() + DEFAULT_TTL_MS));
				return value;
			} catch (Exception e) {
				BetterPlanetEarth.LOGGER.debug("API request failed: {}", path, e);
				return null;
			}
		}, EXECUTOR);
	}

	private static String getString(JsonObject o, String key) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static final class CacheEntry {
		final Object value;
		final long expiresAt;

		CacheEntry(Object value, long expiresAt) {
			this.value = value;
			this.expiresAt = expiresAt;
		}
	}
}
