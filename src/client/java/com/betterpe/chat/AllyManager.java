package com.betterpe.chat;

import com.betterpe.BetterPlanetEarth;
import com.betterpe.api.PlanetEarthApi;
import com.betterpe.api.model.Nation;
import com.betterpe.api.model.Resident;
import com.betterpe.api.model.Town;
import com.betterpe.config.ConfigManager;
import net.minecraft.client.MinecraftClient;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Feature 5 data: resolves the logged-in player's nation, its allies, and the full
 * member rosters, so chat lines can recolor a speaker's nickname.
 *
 * Strategy (quota-friendly):
 *   1. /resident?name=self       -> own nation
 *   2. /nation?name=ownNation    -> ally nation names
 *   3. /town/bulk (one call)     -> every town's nation + residents; filter locally
 *      into own-nation members (color &a) and ally-nation members (color &b).
 */
public final class AllyManager {
	private static volatile Set<String> nationMembers = new HashSet<>();
	private static volatile Set<String> allyMembers = new HashSet<>();
	private static volatile Set<String> ownAndAllyNations = new HashSet<>();
	private static volatile String ownNation = "";
	private static volatile boolean loading = false;
	private static volatile boolean loaded = false;

	private AllyManager() {
	}

	public static boolean isNationMember(String name) {
		return nationMembers.contains(name.toLowerCase(Locale.ROOT));
	}

	public static boolean isAllyMember(String name) {
		return allyMembers.contains(name.toLowerCase(Locale.ROOT));
	}

	public static boolean isOwnOrAllyNation(String nation) {
		return nation != null && ownAndAllyNations.contains(nation.toLowerCase(Locale.ROOT));
	}

	public static String ownNation() {
		return ownNation;
	}

	public static boolean isLoaded() {
		return loaded;
	}

	/** Resolve the logged-in player's name (config override wins). */
	public static String resolveSelfName() {
		String override = ConfigManager.get().selfNameOverride;
		if (override != null && !override.isBlank()) {
			return override.trim();
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getSession() != null) {
			return mc.getSession().getUsername();
		}
		return null;
	}

	/** Kick off the async resolve chain once per session (or when forced). */
	public static void refresh(boolean force) {
		if (loading || (loaded && !force)) {
			return;
		}
		String self = resolveSelfName();
		if (self == null || self.isBlank()) {
			return;
		}
		loading = true;

		PlanetEarthApi.resident(self).thenCompose(res -> {
			if (res == null || !res.hasNation()) {
				BetterPlanetEarth.LOGGER.info("Ally highlight: no nation for {}", self);
				return java.util.concurrent.CompletableFuture.completedFuture(null);
			}
			ownNation = res.nation;
			return PlanetEarthApi.nation(res.nation);
		}).thenCompose(nation -> {
			Set<String> nations = new HashSet<>();
			if (nation != null) {
				nations.add(nation.name.toLowerCase(Locale.ROOT));
				for (String ally : nation.allyList()) {
					nations.add(ally.toLowerCase(Locale.ROOT));
				}
			} else if (!ownNation.isBlank()) {
				nations.add(ownNation.toLowerCase(Locale.ROOT));
			}
			ownAndAllyNations = nations;
			return PlanetEarthApi.townBulk();
		}).thenAccept(towns -> {
			buildRosters(towns);
			loaded = true;
			loading = false;
			BetterPlanetEarth.LOGGER.info("Ally highlight ready: nation={}, {} nation members, {} ally members",
					ownNation, nationMembers.size(), allyMembers.size());
		}).exceptionally(t -> {
			loading = false;
			BetterPlanetEarth.LOGGER.debug("Ally refresh failed", t);
			return null;
		});
	}

	private static void buildRosters(List<Town> towns) {
		Set<String> nationSet = new HashSet<>();
		Set<String> allySet = new HashSet<>();
		if (towns != null) {
			String own = ownNation.toLowerCase(Locale.ROOT);
			for (Town t : towns) {
				if (t == null || t.nation == null || t.nation.isBlank()) {
					continue;
				}
				String tn = t.nation.toLowerCase(Locale.ROOT);
				if (!ownAndAllyNations.contains(tn)) {
					continue;
				}
				boolean isOwn = tn.equals(own);
				for (String member : t.residentList()) {
					if (isOwn) {
						nationSet.add(member.toLowerCase(Locale.ROOT));
					} else {
						allySet.add(member.toLowerCase(Locale.ROOT));
					}
				}
			}
		}
		nationMembers = nationSet;
		allyMembers = allySet;
	}
}
