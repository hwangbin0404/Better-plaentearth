package com.betterpe.command;

import com.betterpe.update.UpdateChecker;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * /업데이트 - triggered by clicking the update notification in chat (see UpdateChecker),
 * but also runnable directly. Downloads the pending release jar and stages it to replace
 * the current one the next time the game is launched.
 */
public final class UpdateCommand {

	private UpdateCommand() {
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
				dispatcher.register(literal("업데이트").executes(ctx -> {
					FabricClientCommandSource source = ctx.getSource();
					UpdateChecker.apply(msg -> source.sendFeedback(Text.literal(msg)));
					return 1;
				})));
	}
}
