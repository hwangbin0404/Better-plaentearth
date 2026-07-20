package com.betterpe.mixin;

import com.betterpe.config.Config;
import com.betterpe.config.ConfigManager;
import com.betterpe.hud.elements.ScoreboardHudElement;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the movable-scoreboard offset by translating the matrix around vanilla's
 * own sidebar rendering. Works across 1.20.1-1.20.4 because it only hooks the
 * private renderScoreboardSidebar(DrawContext, ScoreboardObjective) method, whose
 * signature is stable across those versions.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

	@Inject(method = "renderScoreboardSidebar", at = @At("HEAD"))
	private void betterpe$pushScoreboardOffset(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
		Config cfg = ConfigManager.get();
		Config.HudElementConfig ec = cfg.hudElements.get(ScoreboardHudElement.ID);
		context.getMatrices().push();
		if (cfg.modEnabled && ec != null) {
			context.getMatrices().translate(ec.x, ec.y, 0);
		}
	}

	@Inject(method = "renderScoreboardSidebar", at = @At("RETURN"))
	private void betterpe$popScoreboardOffset(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
		context.getMatrices().pop();
	}
}
