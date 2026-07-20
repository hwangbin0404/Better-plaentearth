package com.betterpe.config;

import com.betterpe.BetterPlanetEarth;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads / saves the single {@link Config} instance to config/betterpe.json.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance()
			.getConfigDir()
			.resolve(BetterPlanetEarth.MOD_ID + ".json");

	private static Config config;

	private ConfigManager() {
	}

	public static Config get() {
		if (config == null) {
			load();
		}
		return config;
	}

	public static void load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, Config.class);
			} catch (IOException | RuntimeException e) {
				BetterPlanetEarth.LOGGER.error("Failed to read config, using defaults", e);
			}
		}
		if (config == null) {
			config = new Config();
		}
		if (config.hudElements == null) {
			config.hudElements = new java.util.HashMap<>();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			BetterPlanetEarth.LOGGER.error("Failed to save config", e);
		}
	}
}
