package com.betterpe;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterPlanetEarth implements ModInitializer {
	public static final String MOD_ID = "betterpe";
	public static final Logger LOGGER = LoggerFactory.getLogger("Better PlanetEarth");

	@Override
	public void onInitialize() {
		LOGGER.info("Better PlanetEarth (common) initialized");
	}
}
