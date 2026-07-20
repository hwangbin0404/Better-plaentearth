package com.betterpe.hud.elements;

import com.betterpe.config.Config;
import com.betterpe.config.ConfigManager;
import com.betterpe.dynmap.MapMarker;
import com.betterpe.goods.GoodsEntry;
import com.betterpe.goods.GoodsManager;
import com.betterpe.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * A direction/distance beacon toward the currently selected good's dynmap marker (see
 * GoodsManager / /특품선택). Purely a navigation aid rendered by this mod itself - no
 * dependency on a minimap mod. Hides itself once within {@link #ARRIVAL_BLOCKS}, since at
 * that point you don't need directions anymore (the boss bar keeps tracking readiness).
 */
public class GoodsBeaconHudElement implements HudElement {
	public static final String ID = "goods_beacon";
	public static final GoodsBeaconHudElement INSTANCE = new GoodsBeaconHudElement();

	private static final int ARRIVAL_BLOCKS = 10;
	// 8-way compass rose relative to where the player is currently facing, in 45-degree steps.
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
	private static final int COLOR = 0xFFFFD54A;

	private GoodsBeaconHudElement() {
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public String getDisplayName() {
		return "특산품 방향 표시";
	}

	@Override
	public int getWidth() {
		return 130;
	}

	@Override
	public int getHeight() {
		return 12;
	}

	@Override
	public int getAnchorX(int screenWidth) {
		return (screenWidth - getWidth()) / 2;
	}

	@Override
	public int getAnchorY(int screenHeight) {
		return screenHeight - 60;
	}

	@Override
	public boolean isActive() {
		Config cfg = ConfigManager.get();
		if (!cfg.modEnabled || !cfg.goodsEnabled || !GoodsManager.hasSelection()) {
			return false;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null) {
			return false;
		}
		MapMarker marker = GoodsManager.selectedMarker();
		return marker != null && horizontalDistance(mc, marker) > ARRIVAL_BLOCKS;
	}

	@Override
	public void render(DrawContext context, int x, int y, float tickDelta) {
		MinecraftClient mc = MinecraftClient.getInstance();
		GoodsEntry entry = GoodsManager.selected();
		MapMarker marker = GoodsManager.selectedMarker();
		if (mc.player == null || entry == null || marker == null) {
			return;
		}

		double dist = horizontalDistance(mc, marker);
		String label = arrowFor(mc, marker) + " " + entry.name + " " + Math.round(dist) + "m";
		int textW = mc.textRenderer.getWidth(label);
		context.drawText(mc.textRenderer, label, x + (getWidth() - textW) / 2, y, COLOR, true);
	}

	private static double horizontalDistance(MinecraftClient mc, MapMarker marker) {
		double dx = marker.x - mc.player.getX();
		double dz = marker.z - mc.player.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Picks the compass-rose glyph for the marker's bearing relative to the player's yaw. */
	private static String arrowFor(MinecraftClient mc, MapMarker marker) {
		double dx = marker.x - mc.player.getX();
		double dz = marker.z - mc.player.getZ();
		// Minecraft yaw convention: forward = (-sin(yaw), cos(yaw)), yaw 0 = south (+Z).
		double bearing = Math.toDegrees(Math.atan2(-dx, dz));
		double relative = ((bearing - mc.player.getYaw()) % 360 + 360) % 360;
		int index = (int) Math.round(relative / 45.0) % 8;
		return ARROWS[index];
	}
}
