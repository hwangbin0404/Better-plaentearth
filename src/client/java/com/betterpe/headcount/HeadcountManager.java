package com.betterpe.headcount;

import com.betterpe.api.model.Resident;
import com.betterpe.config.Config;
import com.betterpe.config.ConfigManager;
import com.betterpe.tag.PlayerTagRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Feature 6: hold the headcount key (default Page Down, rebindable in vanilla Controls)
 * to survey nearby players. While held, every tick scans players within headcountRadius
 * and resolves their nation (reusing {@link PlayerTagRenderer}'s /resident cache so this
 * doesn't burn extra API quota). On release, reports a per-nation headcount to local chat,
 * e.g. "[Asgard] 3명, [무소속] 1명".
 */
public final class HeadcountManager {

	private static final String UNAFFILIATED = "무소속";

	// nation (or "무소속") -> player names seen this survey, deduped case-insensitively.
	private static final Map<String, TreeSet<String>> SEEN =
			new LinkedHashMap<>();
	private static boolean scanning = false;

	private HeadcountManager() {
	}

	public static boolean isScanning() {
		return scanning;
	}

	public static void startScan() {
		if (scanning) {
			return;
		}
		scanning = true;
		SEEN.clear();
	}

	/** Called every client tick while the key is held. */
	public static void tick() {
		Config cfg = ConfigManager.get();
		if (!scanning || !cfg.modEnabled || !cfg.headcountEnabled) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) {
			return;
		}

		double r2 = cfg.headcountRadius * cfg.headcountRadius;
		for (PlayerEntity p : mc.world.getPlayers()) {
			if (p == mc.player || p.isSpectator()) {
				continue;
			}
			if (p.squaredDistanceTo(mc.player) > r2) {
				continue;
			}
			String name = p.getGameProfile().getName();
			Resident res = PlayerTagRenderer.ensureResident(name);
			if (res == null) {
				// Still resolving; a later tick while the key is still held will pick it up.
				continue;
			}
			if (!res.hasNation() && !cfg.headcountIncludeUnaffiliated) {
				continue;
			}
			String nation = res.hasNation() ? res.nation : UNAFFILIATED;
			SEEN.computeIfAbsent(nation, k -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)).add(name);
		}
	}

	/** Called on key release: prints the survey result and resets for next time. */
	public static void finishScan() {
		if (!scanning) {
			return;
		}
		scanning = false;
		Config cfg = ConfigManager.get();

		if (SEEN.isEmpty()) {
			report("§7[인원파악] 근처에 인원이 없습니다.");
			return;
		}

		List<Map.Entry<String, TreeSet<String>>> entries = new ArrayList<>(SEEN.entrySet());
		entries.sort((a, b) -> b.getValue().size() - a.getValue().size());

		StringBuilder sb = new StringBuilder("§b[인원파악] §f");
		boolean first = true;
		for (Map.Entry<String, TreeSet<String>> e : entries) {
			if (!first) {
				sb.append("§7, ");
			}
			first = false;
			sb.append("§a").append(e.getKey()).append("§f ").append(e.getValue().size()).append("명");
			if (cfg.headcountShowNames) {
				sb.append("§7(").append(String.join(", ", e.getValue())).append(")");
			}
		}
		report(sb.toString());
	}

	private static void report(String legacy) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.inGameHud != null) {
			mc.inGameHud.getChatHud().addMessage(Text.literal(legacy));
		}
	}
}
