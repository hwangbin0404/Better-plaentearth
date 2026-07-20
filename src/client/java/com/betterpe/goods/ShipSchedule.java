package com.betterpe.goods;

import com.betterpe.config.Config;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the next port-availability window from the PlanetEarth 정기선 schedule:
 *  - Every hour on the hour, usable for {@code shipHourlyWindowMinutes} (default 10).
 *  - Every day at 21:30, usable for {@code shipEveningWindowMinutes} (default 20).
 *  - Weekends at 22:30, usable for the same evening window.
 *  - Daily at 05:30 (post-reboot), usable for the hourly window length.
 *
 * The Saturday 21:00 ship being war-restricted is a server-side rule that doesn't
 * change when the ship arrives, so it's not modeled here.
 */
public final class ShipSchedule {

	public static final class Window {
		public final long startMs;
		public final long endMs;

		Window(long startMs, long endMs) {
			this.startMs = startMs;
			this.endMs = endMs;
		}

		public boolean contains(long t) {
			return t >= startMs && t <= endMs;
		}
	}

	private ShipSchedule() {
	}

	/**
	 * The window the player can next be at a port for. If a window is active right now,
	 * that one is returned (so its remaining time is what matters); otherwise the next
	 * upcoming window.
	 */
	public static Window nextWindow(Config cfg, long nowMs) {
		ZoneId zone = ZoneId.systemDefault();
		LocalDateTime now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone);

		List<Window> candidates = new ArrayList<>();
		// Look at today and tomorrow to cover wrap-around.
		for (int dayOffset = 0; dayOffset <= 1; dayOffset++) {
			LocalDateTime day = now.toLocalDate().plusDays(dayOffset).atStartOfDay();
			boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY
					|| day.getDayOfWeek() == DayOfWeek.SUNDAY;

			// Hourly ships.
			for (int h = 0; h < 24; h++) {
				addWindow(candidates, zone, day.withHour(h), cfg.shipHourlyWindowMinutes);
			}
			// Daily 21:30 evening ship.
			addWindow(candidates, zone, day.withHour(21).withMinute(30), cfg.shipEveningWindowMinutes);
			// Weekend 22:30 ship.
			if (weekend) {
				addWindow(candidates, zone, day.withHour(22).withMinute(30), cfg.shipEveningWindowMinutes);
			}
			// Daily 05:30 post-reboot ship.
			addWindow(candidates, zone, day.withHour(5).withMinute(30), cfg.shipHourlyWindowMinutes);
		}

		// Active window wins; else the earliest window that hasn't ended yet.
		Window best = null;
		for (Window w : candidates) {
			if (w.contains(nowMs)) {
				return w;
			}
			if (w.endMs >= nowMs && (best == null || w.startMs < best.startMs)) {
				best = w;
			}
		}
		return best;
	}

	private static void addWindow(List<Window> out, ZoneId zone, LocalDateTime start, int minutes) {
		long startMs = start.atZone(zone).toInstant().toEpochMilli();
		long endMs = startMs + minutes * 60_000L;
		out.add(new Window(startMs, endMs));
	}
}
