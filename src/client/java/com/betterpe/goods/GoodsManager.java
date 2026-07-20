package com.betterpe.goods;

import com.betterpe.config.Config;
import com.betterpe.config.ConfigManager;
import com.betterpe.dynmap.DynmapData;
import com.betterpe.dynmap.MapMarker;
import com.betterpe.dynmap.TownArea;
import com.betterpe.hud.BossBarOverlay;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Feature 4: parses /goods output, recommends goods reachable in the next port window,
 * and drives a boss-bar countdown for a selected good (with its nearest port).
 *
 * Also listens for two live server broadcasts (independent of /goods):
 *  - respawn confirmation: "! 특산품 OOO 이(가) 리스폰되었습니다." -> marks the good confirmed
 *    available right now (vs. our own countdown merely reaching zero).
 *  - acquisition: "[국가] 유저님이 특산품 OOO 을(를) 획득했습니다!" -> flashes a 3-second
 *    countdown boss bar and removes the good (it's consumed until the server says otherwise).
 *
 * Some goods only ever appear in chat by their bare name (no "(지역)" suffix) even though
 * the dynmap has several regional markers sharing that base name. When that happens we
 * resolve the specific region: for a respawn broadcast, by matching against whichever single
 * tracked entry shares the base name; for an acquisition, by finding which of the candidate
 * dynmap markers is closest to the acquiring nation's claims (claim boundary distance, not
 * town/nation center - see TownArea.distanceSqTo).
 */
public final class GoodsManager {

	// /goods reports every category at once under the generic word "특산품", e.g.
	// "현재 필드에 스폰된 특산품: ...". Per-category commands (/모래, /목재, ...) use the same
	// two line shapes but put their own category name in that spot instead - "현재 필드에
	// 스폰된 모래: ...", "리스폰 중인 목재: ..." - so the category has to be captured, not fixed.
	private static final String GENERIC_CATEGORY = "특산품";
	private static final Pattern SPAWNED_LINE = Pattern.compile("현재 필드에 스폰된\\s*([^:]+):(.*)");
	private static final Pattern RESPAWNING_LINE = Pattern.compile("리스폰 중인\\s*([^:]+):(.*)");

	// Trailing "(...분)" / "(...시간...)" time token in a respawning entry.
	private static final Pattern TIME_TAIL = Pattern.compile("\\(([^()]*(?:시간|분|초)[^()]*)\\)\\s*$");
	private static final Pattern HMS = Pattern.compile("(?:(\\d+)\\s*시간)?\\s*(?:(\\d+)\\s*분)?\\s*(?:(\\d+)\\s*초)?");

	// "! 특산품 목재(바이칼호) 이(가) 리스폰되었습니다." (trailing '.' or '!', leading '!' icon may vary).
	private static final Pattern RESPAWN_MSG =
			Pattern.compile("특산품\\s+(.+?)\\s+이\\(가\\)\\s*리스폰되었습니다");
	// "[Asgard] Alfheim님이 특산품 목재 을(를) 획득했습니다!"
	private static final Pattern ACQUIRE_MSG =
			Pattern.compile("^\\[([^\\]]+)]\\s*(\\S+)님이\\s+특산품\\s+(.+?)\\s+을\\(를\\)\\s*획득했습니다");

	private static final int COLOR_COUNTING_DOWN = 0xFFFFAA00; // amber: still on cooldown
	private static final int COLOR_WAITING = 0xFFEF4444;       // red: locally elapsed, not yet confirmed
	private static final int COLOR_CONFIRMED = 0xFF55FF55;     // green: server-confirmed spawned
	private static final int COLOR_ACQUIRED = 0xFF3B82F6;      // blue: acquisition announcement

	private static final long ACQUIRE_ANNOUNCE_MS = 3000L;
	// A fresh /goods invocation always starts with one of the two prefix lines; if the last
	// one we saw is older than this, treat the new line as the start of a brand new snapshot
	// and drop stale entries instead of merging into whatever we tracked before.
	private static final long SYNC_GAP_MS = 3000L;

	private static final Map<String, GoodsEntry> ENTRIES = new LinkedHashMap<>();
	private static volatile String selectedKey = null;
	private static long lastSyncLineMs = 0L;
	private static String lastSyncCategory = "";

	private static volatile String acquireText = null;
	private static volatile long acquireStartMs = 0L;

	private GoodsManager() {
	}

	// ---- Chat parsing ----

	public static void onChat(String plain) {
		Config cfg = ConfigManager.get();
		if (!cfg.modEnabled || !cfg.goodsEnabled) {
			return;
		}

		Matcher rm = RESPAWN_MSG.matcher(plain);
		if (rm.find()) {
			onRespawnConfirmed(rm.group(1).trim());
			return;
		}
		Matcher am = ACQUIRE_MSG.matcher(plain);
		if (am.find()) {
			onAcquired(am.group(1).trim(), am.group(2).trim(), am.group(3).trim());
			return;
		}

		Matcher rl = RESPAWNING_LINE.matcher(plain);
		if (rl.find()) {
			beginSyncWindow(rl.group(1).trim());
			parseRespawning(rl.group(2));
			return;
		}
		Matcher sl = SPAWNED_LINE.matcher(plain);
		if (sl.find()) {
			beginSyncWindow(sl.group(1).trim());
			parseSpawned(sl.group(2));
		}
	}

	/**
	 * Responses arrive as separate lines; wipe stale state once per fresh invocation so the
	 * tracker stays in sync with the server instead of accumulating old entries. /goods
	 * (category "특산품") reports the whole picture, so a fresh call there wipes everything.
	 * A per-category command only ever reports its own slice, so a fresh call there only
	 * wipes entries of that same category - otherwise checking /모래 would erase whatever
	 * /goods or /목재 had already told us about other goods.
	 */
	private static void beginSyncWindow(String category) {
		long now = System.currentTimeMillis();
		if (now - lastSyncLineMs > SYNC_GAP_MS || !category.equals(lastSyncCategory)) {
			if (GENERIC_CATEGORY.equals(category)) {
				ENTRIES.clear();
			} else {
				String categoryKey = GoodsEntry.normalize(category);
				ENTRIES.values().removeIf(e -> GoodsEntry.normalize(GoodsEntry.baseName(e.name)).equals(categoryKey));
			}
		}
		lastSyncLineMs = now;
		lastSyncCategory = category;
	}

	private static void parseRespawning(String body) {
		long now = System.currentTimeMillis();
		for (String raw : body.split(",")) {
			String item = raw.trim();
			if (item.isEmpty()) {
				continue;
			}
			Matcher m = TIME_TAIL.matcher(item);
			if (!m.find()) {
				continue;
			}
			String name = item.substring(0, m.start()).trim();
			long ms = parseDurationMs(m.group(1));
			if (!name.isEmpty()) {
				ENTRIES.put(GoodsEntry.normalize(name), new GoodsEntry(name, now + ms));
			}
		}
	}

	private static void parseSpawned(String body) {
		long now = System.currentTimeMillis();
		for (String raw : body.split(",")) {
			String name = raw.trim();
			if (!name.isEmpty()) {
				ENTRIES.put(GoodsEntry.normalize(name), new GoodsEntry(name, now, true));
			}
		}
	}

	private static long parseDurationMs(String token) {
		Matcher m = HMS.matcher(token.trim());
		if (!m.find()) {
			return 0;
		}
		long h = m.group(1) != null ? Long.parseLong(m.group(1)) : 0;
		long mm = m.group(2) != null ? Long.parseLong(m.group(2)) : 0;
		long s = m.group(3) != null ? Long.parseLong(m.group(3)) : 0;
		return ((h * 3600) + (mm * 60) + s) * 1000L;
	}

	// ---- Live respawn confirmation ----

	private static void onRespawnConfirmed(String rawName) {
		long now = System.currentTimeMillis();
		if (rawName.indexOf('(') >= 0) {
			GoodsEntry e = ENTRIES.computeIfAbsent(GoodsEntry.normalize(rawName),
					k -> new GoodsEntry(rawName, now));
			e.readyAtMs = now;
			e.confirmed = true;
			return;
		}

		// No region in the broadcast. If exactly one tracked entry shares this base name,
		// that's the one that just respawned; otherwise fall back to the bare name as-is.
		String baseKey = GoodsEntry.normalize(rawName);
		GoodsEntry unique = null;
		int matches = 0;
		for (GoodsEntry e : ENTRIES.values()) {
			if (GoodsEntry.normalize(GoodsEntry.baseName(e.name)).equals(baseKey)) {
				unique = e;
				matches++;
			}
		}
		if (matches == 1) {
			unique.readyAtMs = now;
			unique.confirmed = true;
			return;
		}
		GoodsEntry e = ENTRIES.computeIfAbsent(baseKey, k -> new GoodsEntry(rawName, now));
		e.readyAtMs = now;
		e.confirmed = true;
	}

	// ---- Live acquisition announcement ----

	private static void onAcquired(String nation, String user, String rawGoodName) {
		String fullName = rawGoodName.indexOf('(') >= 0
				? rawGoodName
				: resolveRegionalName(rawGoodName, nation);
		String key = GoodsEntry.normalize(fullName);

		// Always keep the tracker in sync (this good is consumed regardless of who took it),
		// but only flash the boss bar when it's the one *we* have tracked via /특품선택.
		boolean wasSelected = key.equals(selectedKey);
		ENTRIES.remove(key);
		if (!wasSelected) {
			return;
		}

		acquireText = "§b[" + nation + "]" + user + "님이 " + fullName + " 를 획득하셨습니다 특산품을 종료합니다.";
		acquireStartMs = System.currentTimeMillis();
	}

	/** Among dynmap goods markers sharing bareName's base name, picks the one whose claim
	 *  is nearest to the given nation's territory (claim boundary, not town/nation center). */
	private static String resolveRegionalName(String bareName, String nation) {
		String baseKey = GoodsEntry.normalize(bareName);
		List<MapMarker> candidates = new ArrayList<>();
		for (MapMarker m : DynmapData.goods()) {
			if (GoodsEntry.normalize(GoodsEntry.baseName(m.label)).equals(baseKey)) {
				candidates.add(m);
			}
		}
		if (candidates.isEmpty()) {
			return bareName;
		}
		if (candidates.size() == 1) {
			return candidates.get(0).label;
		}

		List<TownArea> nationTowns = DynmapData.townsOfNation(nation);
		if (nationTowns.isEmpty()) {
			return candidates.get(0).label;
		}
		MapMarker best = candidates.get(0);
		double bestD = Double.MAX_VALUE;
		for (MapMarker m : candidates) {
			for (TownArea t : nationTowns) {
				double d = t.distanceSqTo(m.x, m.z);
				if (d < bestD) {
					bestD = d;
					best = m;
				}
			}
		}
		return best.label;
	}

	/** Whether the acquisition flash wants the shared boss bar right now. */
	public static boolean hasAcquisitionAnnouncement() {
		return acquireText != null && System.currentTimeMillis() - acquireStartMs < ACQUIRE_ANNOUNCE_MS;
	}

	/** Renders the 3 -> 2 -> 1 acquisition countdown; called instead of tick() while active. */
	public static void tickAcquisition() {
		long elapsed = System.currentTimeMillis() - acquireStartMs;
		if (elapsed >= ACQUIRE_ANNOUNCE_MS) {
			acquireText = null;
			return;
		}
		long remainingSec = 3 - (elapsed / 1000);
		float progress = 1f - Math.min(1f, elapsed / (float) ACQUIRE_ANNOUNCE_MS);
		BossBarOverlay.set("acquisition", Text.literal(acquireText + " (" + remainingSec + ")"), progress, COLOR_ACQUIRED);
	}

	// ---- Recommendation (/특품추천) ----

	/** Goods that will have (re)spawned by the end of the next port window. */
	public static List<GoodsEntry> recommend() {
		Config cfg = ConfigManager.get();
		ShipSchedule.Window window = ShipSchedule.nextWindow(cfg, System.currentTimeMillis());
		long cutoff = window != null ? window.endMs : System.currentTimeMillis();

		List<GoodsEntry> out = new ArrayList<>();
		for (GoodsEntry e : ENTRIES.values()) {
			if (e.readyAtMs <= cutoff) {
				out.add(e);
			}
		}
		out.sort(Comparator.comparingLong(e -> e.readyAtMs));
		return out;
	}

	/** Builds the colored recommendation line, or null if there's nothing to show. */
	public static Text buildRecommendMessage() {
		List<GoodsEntry> recs = recommend();
		if (recs.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder("§b[특산품 추천]: ");
		boolean first = true;
		for (GoodsEntry e : recs) {
			if (!first) {
				sb.append("§7, ");
			}
			first = false;
			String port = portNameFor(e);
			sb.append("§a").append(e.name)
					.append("§f{").append(e.remainingText()).append("}");
			if (port != null) {
				sb.append("§7[").append(port).append("]");
			}
		}
		return Text.literal(sb.toString());
	}

	/** Nearest port OR train station to the good's map marker (whichever is closer). */
	private static String portNameFor(GoodsEntry e) {
		MapMarker goods = findGoodsMarker(e.key());
		if (goods == null) {
			return null;
		}
		MapMarker transport = DynmapData.nearestTransport(goods.x, goods.z);
		return transport != null ? transport.label : null;
	}

	private static MapMarker findGoodsMarker(String normalizedKey) {
		for (MapMarker g : DynmapData.goods()) {
			if (GoodsEntry.normalize(g.label).equals(normalizedKey)) {
				return g;
			}
		}
		return null;
	}

	// ---- Selection (/특품선택) ----

	/** Returns true if a matching good was found and selected. */
	public static boolean select(String query) {
		String key = GoodsEntry.normalize(query);
		GoodsEntry exact = ENTRIES.get(key);
		if (exact == null) {
			// Fall back to a contains-match on normalized names.
			for (GoodsEntry e : ENTRIES.values()) {
				if (e.key().contains(key)) {
					exact = e;
					break;
				}
			}
		}
		if (exact == null) {
			return false;
		}
		selectedKey = exact.key();
		return true;
	}

	public static void clearSelection() {
		selectedKey = null;
	}

	public static GoodsEntry selected() {
		return selectedKey != null ? ENTRIES.get(selectedKey) : null;
	}

	/** The dynmap marker for the currently selected good, or null if unselected/unresolved. */
	public static MapMarker selectedMarker() {
		return selectedKey != null ? findGoodsMarker(selectedKey) : null;
	}

	/** Drives the boss bar for the selected good; called each client tick. */
	public static void tick() {
		Config cfg = ConfigManager.get();
		if (!cfg.modEnabled || !cfg.goodsEnabled || selectedKey == null) {
			return;
		}
		GoodsEntry e = ENTRIES.get(selectedKey);
		if (e == null) {
			// The tracked good disappeared (e.g. resynced away, or just acquired) - stop tracking it.
			selectedKey = null;
			return;
		}
		String port = portNameFor(e);
		String portText = port != null ? " §7[" + port + "]" : "";

		long rem = e.remainingMs();
		int color;
		String timeColor;
		float progress;
		if (rem > 0) {
			color = COLOR_COUNTING_DOWN;
			timeColor = "§f";
			// Fill relative to a nominal 30-min max so it visibly ticks down.
			progress = 1f - Math.min(1f, rem / (30f * 60_000f));
		} else if (!e.confirmed) {
			color = COLOR_WAITING;
			timeColor = "§c";
			progress = 1f;
		} else {
			color = COLOR_CONFIRMED;
			timeColor = "§b";
			progress = 1f;
		}

		String label = "§a" + e.name + " " + timeColor + e.remainingText() + "§f" + portText;
		BossBarOverlay.set("goods", Text.literal(label), progress, color);
	}

	public static boolean hasSelection() {
		return selectedKey != null;
	}
}
