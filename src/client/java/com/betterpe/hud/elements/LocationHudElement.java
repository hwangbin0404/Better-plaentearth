package com.betterpe.hud.elements;

import com.betterpe.config.ConfigManager;
import com.betterpe.dynmap.DynmapData;
import com.betterpe.dynmap.TownArea;
import com.betterpe.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Feature 3: shows where you are, based on the dynmap town claim polygons.
 *  - Inside a claim:   마을명(국가명)
 *  - In the wild:      야생  +  nearby towns within the configured radius
 */
public class LocationHudElement implements HudElement {
	public static final String ID = "location";
	public static final LocationHudElement INSTANCE = new LocationHudElement();

	private static final int TITLE_COLOR = 0xFF55FFFF;
	private static final int WILD_COLOR = 0xFFAAAAAA;
	private static final int NEARBY_COLOR = 0xFFDDDDDD;

	private LocationHudElement() {
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public String getDisplayName() {
		return "위치 표시";
	}

	@Override
	public int getWidth() {
		return 140;
	}

	@Override
	public int getHeight() {
		return 40;
	}

	@Override
	public int getAnchorX(int screenWidth) {
		return 4;
	}

	@Override
	public int getAnchorY(int screenHeight) {
		return 4;
	}

	@Override
	public boolean isActive() {
		return ConfigManager.get().locationEnabled && DynmapData.isLoaded();
	}

	@Override
	public void render(DrawContext context, int x, int y, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return;
		}
		double px = mc.player.getX();
		double pz = mc.player.getZ();

		TownArea here = DynmapData.townAt(px, pz);
		if (here != null) {
			context.drawText(mc.textRenderer, here.display(), x, y, TITLE_COLOR, true);
			return;
		}

		// Wild: show "야생" and the nearest towns within radius.
		context.drawText(mc.textRenderer, "야생", x, y, WILD_COLOR, true);
		int radius = ConfigManager.get().locationNearbyRadius;
		List<TownArea> nearby = DynmapData.townsWithin(px, pz, radius);
		int line = y + 11;
		int shown = 0;
		for (TownArea t : nearby) {
			if (shown >= 3) {
				break;
			}
			context.drawText(mc.textRenderer, "근처: " + t.display(), x, line, NEARBY_COLOR, true);
			line += 10;
			shown++;
		}
	}
}
