package com.betterpe.boat;

import com.betterpe.config.Config;
import com.betterpe.config.ConfigManager;
import com.betterpe.hud.BossBarOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.text.Text;

/**
 * Feature 8: boat auto-move.
 *
 * Server rule 2조 2항 bans automated macros in general, but explicitly carves out
 * "riding a boat with W held down to travel a long distance" as an allowed exception.
 * To stay squarely inside that exception, this does nothing beyond holding the vanilla
 * forward key down while the player is riding a boat - no steering, no pathing, no
 * obstacle handling, nothing the player themselves isn't allowed to do by physically
 * taping the key down. Toggle with the boat_automove keybind; it only actually forces
 * the key while you're in a boat, and releases it the instant you dismount or disable it.
 */
public final class BoatAutoMove {

	private static boolean enabled = false;
	private static boolean forcing = false;

	private BoatAutoMove() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void toggle() {
		enabled = !enabled;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			mc.player.sendMessage(Text.literal(enabled
					? "§b[보트 자동이동] §f켜짐 - 보트에 타면 W가 자동으로 눌린 상태가 됩니다."
					: "§b[보트 자동이동] §f꺼짐"), true);
		}
		if (!enabled) {
			release();
		}
	}

	/** Called every client tick regardless of who owns the shared boss bar. */
	public static void clientTick() {
		Config cfg = ConfigManager.get();
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!enabled || !cfg.modEnabled || !cfg.boatAutoMoveEnabled || mc.player == null) {
			if (forcing) {
				release();
			}
			return;
		}

		boolean inBoat = mc.player.getVehicle() instanceof BoatEntity;
		if (inBoat) {
			mc.options.forwardKey.setPressed(true);
			forcing = true;
		} else if (forcing) {
			release();
		}
	}

	public static boolean wantsBossBar() {
		return enabled;
	}

	/** Called each client tick while this feature owns the shared boss bar. */
	public static void tick() {
		String label = forcing ? "보트 자동이동 작동중" : "보트 자동이동 대기 (탑승 시 작동)";
		int color = forcing ? 0xFF22C55E : 0xFF6B7280;
		BossBarOverlay.set("boat", Text.literal(label), 1f, color);
	}

	private static void release() {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.options != null) {
			mc.options.forwardKey.setPressed(false);
		}
		forcing = false;
	}
}
