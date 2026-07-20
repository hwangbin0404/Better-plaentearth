package com.betterpe.war;

import com.betterpe.chat.AllyManager;
import com.betterpe.config.Config;
import com.betterpe.config.ConfigManager;
import com.betterpe.hud.BossBarOverlay;
import net.minecraft.text.Text;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Feature 1: war notifier.
 *
 * Flow:
 *  1. Declaration message ("전쟁 선포 시간이 시작되었습니다...") marks today as a war day and
 *     starts a countdown boss bar to the configured start time (default 20:30).
 *  2. Start message ("전쟁이 시작되었습니다. 명예를 건 전투가 지금 시작됩니다!") flips to the ongoing phase.
 *  3. Flag-capture chat lines drive the boss bar color: defenders capturing -> blue,
 *     attackers on the flag -> red. We only keep the ongoing bar up when a nation we
 *     care about (our own or an ally) is involved; otherwise the bar is cleared.
 *
 * NOTE: the exact server strings below are matched with contains()/regex so small
 * wording changes still match, but they may need tuning against the live server.
 */
public final class WarManager {

	// --- Trigger substrings (tune against live server if wording differs) ---
	private static final String DECLARATION = "전쟁 선포 시간이 시작되었습니다";
	private static final String WAR_START = "전쟁이 시작되었습니다";
	private static final String DEFEND_CAPTURE = "방어측이 깃발 점령";
	private static final String ATTACK_ON_FLAG = "공격자들이 전쟁깃발에";
	// Capture lines start with "[국가명] 🛡 ..." / "[Asgard] 🗗 ..." — pull the nation.
	private static final Pattern NATION_PREFIX = Pattern.compile("^\\[([^\\]]+)]");

	private static final int COLOR_DEFEND = 0xFF3B82F6; // blue
	private static final int COLOR_ATTACK = 0xFFEF4444; // red
	private static final int COLOR_COUNTDOWN = 0xFFFFAA00; // amber

	public enum Phase {NONE, COUNTDOWN, ONGOING}

	private static Phase phase = Phase.NONE;
	private static long warStartEpochMs = 0L;
	private static int captureColor = COLOR_COUNTDOWN;
	private static String captureText = "";
	private static boolean involved = false;
	private static long lastCaptureMs = 0L;

	private WarManager() {
	}

	/** Called for every game chat line. */
	public static void onChat(String plain) {
		Config cfg = ConfigManager.get();
		if (!cfg.modEnabled || !cfg.warNotifierEnabled) {
			return;
		}

		if (plain.contains(DECLARATION)) {
			phase = Phase.COUNTDOWN;
			warStartEpochMs = computeStartEpochMs(cfg);
			involved = false;
			return;
		}
		if (plain.contains(WAR_START)) {
			phase = Phase.ONGOING;
			return;
		}
		if (plain.contains(DEFEND_CAPTURE)) {
			noteCapture(plain, COLOR_DEFEND, "방어측 점령중");
			return;
		}
		if (plain.contains(ATTACK_ON_FLAG)) {
			noteCapture(plain, COLOR_ATTACK, "공격측 점령중");
		}
	}

	private static void noteCapture(String plain, int color, String label) {
		phase = Phase.ONGOING;
		captureColor = color;
		captureText = label;
		lastCaptureMs = System.currentTimeMillis();
		// Involvement: the nation named in the message is ours or an ally.
		Matcher m = NATION_PREFIX.matcher(plain.trim());
		if (m.find()) {
			String nation = m.group(1).trim();
			if (AllyManager.isOwnOrAllyNation(nation)) {
				involved = true;
			}
		} else {
			// No nation prefix parsed — assume relevant so we don't miss it.
			involved = true;
		}
	}

	/** Whether the war feature currently wants to own the shared boss bar. */
	public static boolean wantsBossBar() {
		Config cfg = ConfigManager.get();
		if (!cfg.modEnabled || !cfg.warNotifierEnabled || !cfg.warBossBarEnabled) {
			return false;
		}
		return switch (phase) {
			case COUNTDOWN -> warStartEpochMs > System.currentTimeMillis();
			case ONGOING -> involved;
			default -> false;
		};
	}

	/** Called each client tick (only when this feature owns the bar) to refresh it. */
	public static void tick() {
		switch (phase) {
			case COUNTDOWN -> renderCountdown();
			case ONGOING -> renderOngoing();
			default -> {
			}
		}
	}

	private static void renderCountdown() {
		long now = System.currentTimeMillis();
		long remaining = warStartEpochMs - now;
		if (remaining <= 0) {
			phase = Phase.ONGOING;
			return;
		}
		long totalSec = remaining / 1000;
		long h = totalSec / 3600;
		long mm = (totalSec % 3600) / 60;
		long ss = totalSec % 60;
		String time = h > 0 ? String.format("%d:%02d:%02d", h, mm, ss) : String.format("%02d:%02d", mm, ss);
		// Progress across the last 6 hours before start, so the bar visibly fills.
		float window = 6 * 3600 * 1000f;
		float progress = 1f - Math.min(1f, remaining / window);
		BossBarOverlay.set("war", Text.literal("전쟁 시작까지 " + time), progress, COLOR_COUNTDOWN);
	}

	private static void renderOngoing() {
		String label = captureText.isEmpty() ? "전쟁 진행중" : captureText;
		int color = captureText.isEmpty() ? COLOR_COUNTDOWN : captureColor;
		BossBarOverlay.set("war", Text.literal(label), 1f, color);
	}

	private static long computeStartEpochMs(Config cfg) {
		ZoneId zone = ZoneId.systemDefault();
		LocalDate today = LocalDate.now(zone);
		LocalDateTime start = LocalDateTime.of(today, LocalTime.of(cfg.warStartHour, cfg.warStartMinute));
		return start.atZone(zone).toInstant().toEpochMilli();
	}

	public static void reset() {
		phase = Phase.NONE;
		involved = false;
		captureText = "";
		BossBarOverlay.clear("war");
	}
}
