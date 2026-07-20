package com.betterpe.command;

import com.betterpe.brewery.BreweryManager;
import com.betterpe.brewery.BreweryRecipe;
import com.betterpe.brewery.BreweryRecipes;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Registers the client-side brewery commands:
 *   /양조선택 <이름>     -> arms a recipe; the next clock-on-cauldron right-click starts its timer
 *   /양조선택취소        -> clears the armed/running timer
 */
public final class BreweryCommands {

	private static final SuggestionProvider<FabricClientCommandSource> RECIPE_SUGGESTIONS = (ctx, builder) -> {
		for (BreweryRecipe r : BreweryRecipes.all()) {
			builder.suggest(r.name);
		}
		return builder.buildFuture();
	};

	private BreweryCommands() {
	}

	public static void init() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
			dispatcher.register(literal("양조선택")
					.then(argument("이름", StringArgumentType.greedyString())
							.suggests(RECIPE_SUGGESTIONS)
							.executes(ctx -> {
								String name = StringArgumentType.getString(ctx, "이름");
								if (BreweryManager.select(name)) {
									BreweryRecipe r = BreweryManager.selected();
									reply(ctx.getSource(), "§a[양조 선택] §f" + r.name
											+ " §7(발효 " + r.fermentMinutes + "분"
											+ (r.agingYears > 0 ? ", 숙성 " + r.agingYears + "년/" + (r.agingYears * 20) + "분" : "")
											+ (r.distillCount > 0 ? ", 증류 " + r.distillCount + "회" : "")
											+ (!"a".equals(r.barrelType) && !"x".equals(r.barrelType) ? ", " + r.barrelType + "통" : "")
											+ ") 가마솥에 시계를 우클릭하면 측정을 시작합니다.");
								} else {
									reply(ctx.getSource(), "§c'" + name + "' 레시피를 찾지 못했습니다.");
								}
								return 1;
							})));

			dispatcher.register(literal("양조선택취소").executes(ctx -> {
				BreweryManager.clearSelection();
				reply(ctx.getSource(), "§7양조 타이머를 해제했습니다.");
				return 1;
			}));
		});
	}

	private static void reply(FabricClientCommandSource source, String legacy) {
		source.sendFeedback(Text.literal(legacy));
	}
}
