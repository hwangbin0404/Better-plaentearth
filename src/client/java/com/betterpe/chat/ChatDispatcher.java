package com.betterpe.chat;

import com.betterpe.goods.GoodsManager;
import com.betterpe.remote.RemoteStatusManager;
import com.betterpe.war.WarManager;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

/**
 * Single entry point for incoming game chat. Observes each line for the war and goods
 * features, then hands the message to the ally highlighter which may return a
 * recolored copy (feature 5) via MODIFY_GAME.
 */
public final class ChatDispatcher {

	private ChatDispatcher() {
	}

	public static void init() {
		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
			if (overlay) {
				return message;
			}
			String plain = message.getString();
			try {
				WarManager.onChat(plain);
				GoodsManager.onChat(plain);
				RemoteStatusManager.onChat(plain);
			} catch (Exception ignored) {
				// Never let a parser hiccup drop the chat line.
			}
			Text highlighted = message;
			try {
				highlighted = AllyHighlighter.highlight(message);
			} catch (Exception ignored) {
				// Same guarantee here: a highlighting bug must never hide the message.
			}
			return highlighted != null ? highlighted : message;
		});
	}
}
