package com.betterpe.hud;

import com.betterpe.config.Config;
import com.betterpe.config.ConfigManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of {@link HudElement}s. Positions live in the shared {@link Config}
 * (config/betterpe.json) keyed by element id, so they persist with everything else.
 */
public final class HudManager {
	private static final Map<String, HudElement> ELEMENTS = new LinkedHashMap<>();

	private HudManager() {
	}

	public static void register(HudElement element) {
		ELEMENTS.put(element.getId(), element);
		// Ensure a config entry exists.
		configOf(element.getId());
	}

	public static Collection<HudElement> elements() {
		return ELEMENTS.values();
	}

	public static Config.HudElementConfig configOf(String id) {
		Map<String, Config.HudElementConfig> map = ConfigManager.get().hudElements;
		return map.computeIfAbsent(id, k -> new Config.HudElementConfig(0, 0));
	}

	public static void save() {
		ConfigManager.save();
	}
}
