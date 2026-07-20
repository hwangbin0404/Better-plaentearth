package com.betterpe.hud;

import com.betterpe.config.Config;
import com.betterpe.config.ConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Draws every registered freeform {@link HudElement} in-game at its saved position.
 * Vanilla-wrapping elements (scoreboard) render nothing here; they are handled by mixin.
 */
public final class HudRenderer {

	private HudRenderer() {
	}

	public static void init() {
		HudRenderCallback.EVENT.register(HudRenderer::onHudRender);
	}

	private static void onHudRender(DrawContext context, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		Config cfg = ConfigManager.get();
		if (!cfg.modEnabled) {
			return;
		}
		// Don't draw over the debug screen or while a screen (except chat) is open.
		if (client.options.hudHidden || (client.currentScreen != null && !(client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen))) {
			return;
		}

		int screenW = context.getScaledWindowWidth();
		int screenH = context.getScaledWindowHeight();

		for (HudElement element : HudManager.elements()) {
			Config.HudElementConfig ec = HudManager.configOf(element.getId());
			if (!ec.enabled || !element.isActive()) {
				continue;
			}
			int x = element.getAnchorX(screenW) + (int) ec.x;
			int y = element.getAnchorY(screenH) + (int) ec.y;

			context.getMatrices().push();
			context.getMatrices().translate(x, y, 0);
			if (ec.scale != 1.0) {
				context.getMatrices().scale((float) ec.scale, (float) ec.scale, 1f);
			}
			element.render(context, 0, 0, tickDelta);
			context.getMatrices().pop();
		}
	}
}
