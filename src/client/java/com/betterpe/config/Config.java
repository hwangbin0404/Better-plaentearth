package com.betterpe.config;

import java.util.HashMap;
import java.util.Map;

/**
 * All user-tunable settings, serialized to config/betterpe.json.
 * Kept as a plain data holder so Gson round-trips it and Cloth Config can bind to it.
 */
public class Config {

	// ---- General ----
	public boolean modEnabled = true;

	// ---- Feature 1: War notifier ----
	public boolean warNotifierEnabled = true;
	/** Hour of day (0-23) the war starts. Default 20 (오후 8시). */
	public int warStartHour = 20;
	/** Minute of hour the war starts. Default 30. */
	public int warStartMinute = 30;
	public boolean warBossBarEnabled = true;

	// ---- Feature 2: Nation/Town crosshair tag ----
	public boolean tagEnabled = true;
	/** Max distance (blocks) to raycast for a targeted player. */
	public double tagMaxDistance = 48.0;
	/** Hide the tag entirely if the player has no nation and no town. */
	public boolean tagHideUnaffiliated = true;
	/** Vertical offset above the player's nametag. */
	public double tagYOffset = 0.4;
	/** true: only tag the player under the crosshair. false: tag everyone within tagNearbyRadius. */
	public boolean tagCrosshairOnly = true;
	/** Radius (blocks) used to find nearby players to tag when tagCrosshairOnly is false. */
	public double tagNearbyRadius = 15.0;

	// ---- Feature 3: Location HUD ----
	public boolean locationEnabled = true;
	/** Radius (blocks) within which nearby towns are listed while in the wild. */
	public int locationNearbyRadius = 500;
	/** How often (seconds) to refresh the dynmap marker cache. */
	public int dynmapRefreshSeconds = 180;

	// ---- Feature 4: Goods recommender ----
	public boolean goodsEnabled = true;
	/** Extra weekday ship at 21:30 lasts this many minutes. */
	public int shipEveningWindowMinutes = 20;
	/** Regular hourly ship stays for this many minutes after arrival. */
	public int shipHourlyWindowMinutes = 10;

	// ---- Feature 6: Headcount (hold key to survey nearby players by nation) ----
	public boolean headcountEnabled = true;
	/** Radius (blocks) scanned each tick the key is held. */
	public double headcountRadius = 100.0;
	/** Count/list players with no nation under a "무소속" group instead of skipping them. */
	public boolean headcountIncludeUnaffiliated = true;
	/** Also list each nation's player names, not just the headcount. */
	public boolean headcountShowNames = false;

	// ---- Feature 7: Brewery fermentation timer ----
	public boolean breweryEnabled = true;
	/** Delay (seconds, 1-20) between the clock-on-cauldron click and the countdown starting. */
	public int breweryStartDelaySeconds = 3;

	// ---- Feature 8: Boat auto-move (hold-W toggle while riding a boat; 규정 2조 2항 명시적 예외) ----
	public boolean boatAutoMoveEnabled = true;

	// ---- Feature 9: GitHub release update checker ----
	public boolean updateCheckEnabled = true;

	// ---- Feature 5: Ally/nation chat highlight ----
	public boolean chatHighlightEnabled = true;
	/** Color code for own-nation members (Minecraft formatting char without the &). */
	public String nationColor = "a";
	/** Color code for ally members. */
	public String allyColor = "b";
	/** Manual override for own MC name; blank = use the logged-in account name. */
	public String selfNameOverride = "";

	// ---- HUD element placements (id -> saved position) ----
	public Map<String, HudElementConfig> hudElements = new HashMap<>();

	/** Nested placement record for one HUD element. */
	public static class HudElementConfig {
		public double x;
		public double y;
		public double scale = 1.0;
		public boolean enabled = true;

		public HudElementConfig() {
		}

		public HudElementConfig(double x, double y) {
			this.x = x;
			this.y = y;
		}
	}
}
