package com.betterpe.brewery;

import com.betterpe.config.Config;
import com.betterpe.config.ConfigManager;
import com.betterpe.hud.BossBarOverlay;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

/**
 * Feature 7: client-side fermentation timer for the Brewery plugin.
 *
 * Flow: pick a recipe with /양조선택, then right-click a cauldron with a clock in hand -
 * the same action the plugin itself uses to report fermentation progress. We can't read
 * the server's actual elapsed time, so instead we start our own countdown from that click,
 * using the recipe's known fermentation minutes. A short configurable buffer (1-20s) delays
 * the countdown's start to absorb the moment between the click and the plugin's own timer
 * actually beginning.
 */
public final class BreweryManager {

	private static volatile BreweryRecipe selected;
	// 0 = no active measurement (armed but not yet clicked, or nothing selected).
	private static volatile long pendingStartAtMs = 0L;
	private static volatile long fermentEndMs = 0L;

	private BreweryManager() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register(BreweryManager::onUseBlock);
	}

	private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
		Config cfg = ConfigManager.get();
		if (!cfg.modEnabled || !cfg.breweryEnabled || selected == null) {
			return ActionResult.PASS;
		}
		if (!player.getStackInHand(hand).isOf(Items.CLOCK)) {
			return ActionResult.PASS;
		}
		BlockState state = world.getBlockState(hitResult.getBlockPos());
		if (state.isOf(Blocks.CAULDRON) || state.isOf(Blocks.WATER_CAULDRON)) {
			startMeasuring(cfg);
		}
		return ActionResult.PASS;
	}

	private static void startMeasuring(Config cfg) {
		long now = System.currentTimeMillis();
		int delaySec = Math.max(1, Math.min(20, cfg.breweryStartDelaySeconds));
		pendingStartAtMs = now + delaySec * 1000L;
		fermentEndMs = pendingStartAtMs + selected.fermentMs();
	}

	/** Returns true and selects the recipe if found. */
	public static boolean select(String query) {
		BreweryRecipe r = BreweryRecipes.find(query);
		if (r == null) {
			return false;
		}
		selected = r;
		pendingStartAtMs = 0L;
		fermentEndMs = 0L;
		return true;
	}

	public static void clearSelection() {
		selected = null;
		pendingStartAtMs = 0L;
		fermentEndMs = 0L;
	}

	public static BreweryRecipe selected() {
		return selected;
	}

	public static boolean wantsBossBar() {
		Config cfg = ConfigManager.get();
		return cfg.modEnabled && cfg.breweryEnabled && selected != null && fermentEndMs > 0;
	}

	/** Called each client tick while wantsBossBar() is true. */
	public static void tick() {
		if (selected == null || fermentEndMs == 0) {
			return;
		}
		long now = System.currentTimeMillis();

		if (now < pendingStartAtMs) {
			long waitSec = (pendingStartAtMs - now) / 1000L + 1;
			String label = "§e" + selected.name + " §f측정 대기중… (" + waitSec + "초)";
			BossBarOverlay.set("brewery", Text.literal(label), 0f, 0xFFFFAA00);
			return;
		}

		long rem = fermentEndMs - now;
		if (rem <= 0) {
			BossBarOverlay.set("brewery", Text.literal("§a" + selected.name + " §b발효 완료!"), 1f, 0xFF55FF55);
			return;
		}

		long fermentTotalMs = selected.fermentMs();
		float progress = fermentTotalMs > 0 ? 1f - (rem / (float) fermentTotalMs) : 1f;
		String label = "§a" + selected.name + " §f발효중 " + formatRemaining(rem);
		BossBarOverlay.set("brewery", Text.literal(label), progress, 0xFFFFAA00);
	}

	private static String formatRemaining(long ms) {
		long sec = ms / 1000;
		long m = sec / 60;
		long s = sec % 60;
		return m > 0 ? m + "분 " + s + "초" : s + "초";
	}
}
