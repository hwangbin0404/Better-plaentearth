package com.betterpe.dynmap;

import com.betterpe.BetterPlanetEarth;
import com.betterpe.config.ConfigManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches and caches the dynmap marker file (marker_world.json) from
 * https://map.planetearth.kr. Holds town claim polygons, port markers, and goods
 * spawn markers. This is a static file served directly (no Cloudflare challenge),
 * so a plain GET works. Refreshed on an interval configured by the user.
 */
public final class DynmapData {
	private static final String URL = "https://map.planetearth.kr/tiles/_markers_/marker_world.json";
	private static final Pattern NATION_IN_DESC =
			Pattern.compile("font-size:120%\">\\s*([^<(]*?)\\s*\\(([^)]*)\\)");

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "BetterPE-Dynmap");
		t.setDaemon(true);
		return t;
	});

	private static volatile List<TownArea> towns = new ArrayList<>();
	private static volatile List<MapMarker> ports = new ArrayList<>();
	private static volatile List<MapMarker> trainStations = new ArrayList<>();
	private static volatile List<MapMarker> goods = new ArrayList<>();
	private static volatile long lastFetch = 0L;
	private static volatile boolean fetching = false;

	private DynmapData() {
	}

	public static List<TownArea> towns() {
		return towns;
	}

	public static List<MapMarker> ports() {
		return ports;
	}

	public static List<MapMarker> trainStations() {
		return trainStations;
	}

	public static List<MapMarker> goods() {
		return goods;
	}

	public static boolean isLoaded() {
		return lastFetch > 0;
	}

	/** Triggers a refresh if the cache is older than the configured interval. */
	public static void tickRefresh() {
		long intervalMs = Math.max(30, ConfigManager.get().dynmapRefreshSeconds) * 1000L;
		long now = System.currentTimeMillis();
		if (fetching || now - lastFetch < intervalMs) {
			return;
		}
		refreshNow();
	}

	public static void refreshNow() {
		if (fetching) {
			return;
		}
		fetching = true;
		CompletableFuture.runAsync(DynmapData::fetch, EXECUTOR)
				.whenComplete((v, t) -> fetching = false);
	}

	private static void fetch() {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
					.timeout(Duration.ofSeconds(20))
					.header("User-Agent", "BetterPlanetEarth-Mod")
					.GET()
					.build();
			HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200) {
				BetterPlanetEarth.LOGGER.warn("Dynmap markers returned HTTP {}", resp.statusCode());
				return;
			}
			parse(resp.body());
			lastFetch = System.currentTimeMillis();
		} catch (Exception e) {
			BetterPlanetEarth.LOGGER.debug("Dynmap fetch failed", e);
		}
	}

	private static void parse(String body) {
		JsonObject root = JsonParser.parseString(body).getAsJsonObject();
		JsonObject sets = root.getAsJsonObject("sets");
		if (sets == null) {
			return;
		}

		List<TownArea> newTowns = new ArrayList<>();
		JsonObject townSet = sets.getAsJsonObject("towny.markerset");
		if (townSet != null && townSet.has("areas")) {
			for (Map.Entry<String, JsonElement> e : townSet.getAsJsonObject("areas").entrySet()) {
				TownArea area = parseArea(e.getValue().getAsJsonObject());
				if (area != null) {
					newTowns.add(area);
				}
			}
		}

		List<MapMarker> newPorts = parseMarkers(sets.getAsJsonObject("항구"));
		List<MapMarker> newStations = parseMarkers(sets.getAsJsonObject("기차역"));
		List<MapMarker> newGoods = parseMarkers(sets.getAsJsonObject("goods"));

		towns = newTowns;
		ports = newPorts;
		trainStations = newStations;
		goods = newGoods;
		BetterPlanetEarth.LOGGER.info("Dynmap loaded: {} towns, {} ports, {} stations, {} goods",
				newTowns.size(), newPorts.size(), newStations.size(), newGoods.size());
	}

	private static TownArea parseArea(JsonObject o) {
		try {
			String label = o.has("label") ? o.get("label").getAsString() : "";
			String nation = "";
			if (o.has("desc")) {
				Matcher m = NATION_IN_DESC.matcher(o.get("desc").getAsString());
				if (m.find()) {
					nation = m.group(2).trim();
				}
			}
			double[] x = toDoubleArray(o.getAsJsonArray("x"));
			double[] z = toDoubleArray(o.getAsJsonArray("z"));
			if (x.length < 3 || x.length != z.length) {
				return null;
			}
			return new TownArea(label, nation, x, z);
		} catch (Exception e) {
			return null;
		}
	}

	private static List<MapMarker> parseMarkers(JsonObject set) {
		List<MapMarker> out = new ArrayList<>();
		if (set == null || !set.has("markers")) {
			return out;
		}
		for (Map.Entry<String, JsonElement> e : set.getAsJsonObject("markers").entrySet()) {
			JsonObject m = e.getValue().getAsJsonObject();
			try {
				String label = m.has("label") ? m.get("label").getAsString() : e.getKey();
				double x = m.get("x").getAsDouble();
				double z = m.get("z").getAsDouble();
				out.add(new MapMarker(label, x, z));
			} catch (Exception ignored) {
			}
		}
		return out;
	}

	private static double[] toDoubleArray(JsonArray arr) {
		double[] out = new double[arr.size()];
		for (int i = 0; i < arr.size(); i++) {
			out[i] = arr.get(i).getAsDouble();
		}
		return out;
	}

	// ---- Queries ----

	/** The town claim containing (px, pz), or null if in the wild. */
	public static TownArea townAt(double px, double pz) {
		for (TownArea t : towns) {
			if (t.contains(px, pz)) {
				return t;
			}
		}
		return null;
	}

	/** All towns whose claim boundary is within radius blocks of (px, pz), nearest edge first. */
	public static List<TownArea> townsWithin(double px, double pz, double radius) {
		double r2 = radius * radius;
		List<TownArea> out = new ArrayList<>();
		for (TownArea t : towns) {
			if (t.distanceSqTo(px, pz) <= r2) {
				out.add(t);
			}
		}
		out.sort((a, b) -> Double.compare(a.distanceSqTo(px, pz), b.distanceSqTo(px, pz)));
		return out;
	}

	/** All town claims belonging to the given nation (case-insensitive). */
	public static List<TownArea> townsOfNation(String nation) {
		List<TownArea> out = new ArrayList<>();
		if (nation == null || nation.isBlank()) {
			return out;
		}
		for (TownArea t : towns) {
			if (t.nation != null && t.nation.equalsIgnoreCase(nation)) {
				out.add(t);
			}
		}
		return out;
	}

	/** Nearest port marker to (px, pz), or null if none loaded. */
	public static MapMarker nearestPort(double px, double pz) {
		return nearestIn(ports, px, pz);
	}

	/** Nearest train station marker to (px, pz), or null if none loaded. */
	public static MapMarker nearestTrainStation(double px, double pz) {
		return nearestIn(trainStations, px, pz);
	}

	/** Nearest port OR train station to (px, pz), whichever is closer; null if none loaded. */
	public static MapMarker nearestTransport(double px, double pz) {
		MapMarker port = nearestPort(px, pz);
		MapMarker station = nearestTrainStation(px, pz);
		if (port == null) {
			return station;
		}
		if (station == null) {
			return port;
		}
		return port.distanceSqTo(px, pz) <= station.distanceSqTo(px, pz) ? port : station;
	}

	private static MapMarker nearestIn(List<MapMarker> markers, double px, double pz) {
		MapMarker best = null;
		double bestD = Double.MAX_VALUE;
		for (MapMarker m : markers) {
			double d = m.distanceSqTo(px, pz);
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}
}
