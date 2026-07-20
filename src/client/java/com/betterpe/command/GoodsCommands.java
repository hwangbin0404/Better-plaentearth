package com.betterpe.command;

import com.betterpe.goods.GoodsManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Registers the client-side goods commands:
 *   /특품추천            -> prints recommended goods for the next port window
 *   /특품선택 <이름>     -> tracks a good on the boss bar with its nearest port
 *   /특품선택취소        -> clears the tracked good
 */
public final class GoodsCommands {

	private GoodsCommands() {
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
			dispatcher.register(literal("특품추천").executes(ctx -> {
				Text msg = GoodsManager.buildRecommendMessage();
				if (msg == null) {
					reply(ctx.getSource(), "§e추천할 특산품이 없습니다. 먼저 /goods 를 입력해 목록을 불러오세요.");
				} else {
					ctx.getSource().sendFeedback(msg);
				}
				return 1;
			}));

			dispatcher.register(literal("특품선택")
					.then(argument("이름", StringArgumentType.greedyString()).executes(ctx -> {
						String name = StringArgumentType.getString(ctx, "이름");
						if (GoodsManager.select(name)) {
							reply(ctx.getSource(), "§a[특산품 선택] §f" + name + " §7보스바에 표시합니다.");
						} else {
							reply(ctx.getSource(), "§c'" + name + "' 특산품을 목록에서 찾지 못했습니다.");
						}
						return 1;
					})));

			dispatcher.register(literal("특품선택취소").executes(ctx -> {
				GoodsManager.clearSelection();
				reply(ctx.getSource(), "§7특산품 추적을 해제했습니다.");
				return 1;
			}));
		});
	}

	private static void reply(FabricClientCommandSource source, String legacy) {
		source.sendFeedback(Text.literal(legacy));
	}
}
