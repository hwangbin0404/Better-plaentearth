package com.betterpe;

import com.betterpe.boat.BoatAutoMove;
import com.betterpe.brewery.BreweryManager;
import com.betterpe.chat.AllyManager;
import com.betterpe.chat.ChatDispatcher;
import com.betterpe.command.BreweryCommands;
import com.betterpe.command.GoodsCommands;
import com.betterpe.command.UpdateCommand;
import com.betterpe.config.ConfigManager;
import com.betterpe.dynmap.DynmapData;
import com.betterpe.goods.GoodsManager;
import com.betterpe.headcount.HeadcountManager;
import com.betterpe.hud.BossBarOverlay;
import com.betterpe.hud.HudEditScreen;
import com.betterpe.hud.HudManager;
import com.betterpe.hud.HudRenderer;
import com.betterpe.hud.elements.GoodsBeaconHudElement;
import com.betterpe.hud.elements.LocationHudElement;
import com.betterpe.hud.elements.ScoreboardHudElement;
import com.betterpe.remote.RemoteStatusManager;
import com.betterpe.tag.PlayerTagRenderer;
import com.betterpe.update.UpdateChecker;
import com.betterpe.war.WarManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class BetterPlanetEarthClient implements ClientModInitializer {
	private static KeyBinding editHudKey;
	private static KeyBinding headcountKey;
	private static KeyBinding boatAutoMoveKey;

	@Override
	public void onInitializeClient() {
		ConfigManager.load();

		// HUD elements.
		HudManager.register(ScoreboardHudElement.INSTANCE);
		HudManager.register(LocationHudElement.INSTANCE);
		HudManager.register(BossBarOverlay.INSTANCE);
		HudManager.register(GoodsBeaconHudElement.INSTANCE);

		// Renderers / overlays.
		HudRenderer.init();
		PlayerTagRenderer.init();
		BreweryManager.init();

		// Chat + commands.
		ChatDispatcher.init();
		GoodsCommands.init();
		BreweryCommands.init();
		UpdateCommand.init();

		// Edit-HUD keybind (default: Right Shift).
		editHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.betterpe.edit_hud",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				"category.betterpe.general"
		));

		// Headcount keybind (default: Page Down) - hold to survey nearby players by nation.
		// Registered as a real KeyBinding so it shows up in vanilla Controls and is rebindable there.
		headcountKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.betterpe.headcount",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_PAGE_DOWN,
				"category.betterpe.general"
		));

		// Boat auto-move keybind (default: B) - toggle; only forces the forward key while
		// actually riding a boat. See BoatAutoMove for the rule-compliance rationale.
		boatAutoMoveKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.betterpe.boat_automove",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_B,
				"category.betterpe.general"
		));

		// On join: kick off dynmap + ally data, and check GitHub for a newer release.
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			DynmapData.refreshNow();
			UpdateChecker.checkAsync();
		});

		// Remote status page: track world join/leave so the status page knows when to
		// show "접속 확인 중" vs "오프라인" (queue/online state itself comes from chat lines).
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> RemoteStatusManager.onWorldJoin());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RemoteStatusManager.onWorldDisconnect());

		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		BetterPlanetEarth.LOGGER.info("Better PlanetEarth client initialized");
	}

	private void onClientTick(net.minecraft.client.MinecraftClient client) {
		// Open the HUD edit screen.
		while (editHudKey.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new HudEditScreen());
			}
		}

		// Runs regardless of world state so the status page reflects "offline" even at
		// the main menu, and so the socket is already authenticated once a world loads.
		RemoteStatusManager.tick();

		if (client.world == null || client.player == null) {
			return;
		}

		while (boatAutoMoveKey.wasPressed()) {
			BoatAutoMove.toggle();
		}

		// Periodic background refreshes.
		DynmapData.tickRefresh();
		AllyManager.refresh(false);

		// Forces the forward key while riding a boat with auto-move on; must run every tick
		// regardless of which feature currently owns the shared boss bar below.
		BoatAutoMove.clientTick();

		// Headcount: survey nearby players by nation while the key is held, report on release.
		if (headcountKey.isPressed()) {
			HeadcountManager.startScan();
			HeadcountManager.tick();
		} else if (HeadcountManager.isScanning()) {
			HeadcountManager.finishScan();
		}

		// Shared boss bar stack: every feature owns its own row and they all show at once
		// (war + brewery + a selected good + boat auto-move can all be visible together).
		// Its position is dragged/saved as one block via the Right-Shift edit screen (see
		// BossBarOverlay), which also fixes the on-screen stacking order.
		if (GoodsManager.hasAcquisitionAnnouncement()) {
			GoodsManager.tickAcquisition();
		} else {
			BossBarOverlay.clear("acquisition");
		}
		if (WarManager.wantsBossBar()) {
			WarManager.tick();
		} else {
			BossBarOverlay.clear("war");
		}
		if (BreweryManager.wantsBossBar()) {
			BreweryManager.tick();
		} else {
			BossBarOverlay.clear("brewery");
		}
		if (GoodsManager.hasSelection()) {
			GoodsManager.tick();
		} else {
			BossBarOverlay.clear("goods");
		}
		if (BoatAutoMove.wantsBossBar()) {
			BoatAutoMove.tick();
		} else {
			BossBarOverlay.clear("boat");
		}
	}
}
