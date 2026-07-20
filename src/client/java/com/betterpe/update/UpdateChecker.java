package com.betterpe.update;

import com.betterpe.BetterPlanetEarth;
import com.betterpe.config.Config;
import com.betterpe.config.ConfigManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Checks GitHub Releases for a newer version and, on request, downloads it and stages
 * a swap for the next launch.
 *
 * A running game holds its own mod jar open, so on Windows the file can't be deleted or
 * overwritten while playing - overwriting it in place fails with a sharing violation. So
 * "applying" an update here means: download the new jar into the mods folder, then hand
 * off to a small detached helper script (batch on Windows, shell elsewhere) that waits
 * for the old jar to become deletable and swaps it in. That only happens once this game
 * process fully exits, so the new version takes effect on the *next* launch, not instantly.
 */
public final class UpdateChecker {

	private static final String OWNER = "hwangbin0404";
	private static final String REPO = "Better-plaentearth";
	private static final String LATEST_RELEASE_API =
			"https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.build();
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "BetterPE-Update");
		t.setDaemon(true);
		return t;
	});

	private static volatile String latestVersion;
	private static volatile String releaseUrl;
	private static volatile String assetUrl;
	private static volatile String assetName;
	private static volatile boolean staged = false;

	private UpdateChecker() {
	}

	/** Called once on client start; silently does nothing if disabled or already current. */
	public static void checkAsync() {
		Config cfg = ConfigManager.get();
		if (!cfg.modEnabled || !cfg.updateCheckEnabled) {
			return;
		}
		CompletableFuture.runAsync(UpdateChecker::check, EXECUTOR);
	}

	private static void check() {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API))
					.timeout(Duration.ofSeconds(10))
					.header("Accept", "application/vnd.github+json")
					.header("User-Agent", "BetterPlanetEarth-Mod")
					.GET()
					.build();
			HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200) {
				return;
			}

			JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
			String tag = root.has("tag_name") ? root.get("tag_name").getAsString() : null;
			if (tag == null) {
				return;
			}
			String version = (tag.startsWith("v") || tag.startsWith("V")) ? tag.substring(1) : tag;
			if (version.equals(currentVersion())) {
				return;
			}

			String jarUrl = null;
			String jarName = null;
			JsonArray assets = root.getAsJsonArray("assets");
			if (assets != null) {
				for (int i = 0; i < assets.size(); i++) {
					JsonObject a = assets.get(i).getAsJsonObject();
					String name = a.get("name").getAsString();
					if (name.endsWith(".jar") && !name.endsWith("-sources.jar")) {
						jarUrl = a.get("browser_download_url").getAsString();
						jarName = name;
						break;
					}
				}
			}
			if (jarUrl == null) {
				// No downloadable jar attached to the release - nothing we can offer to apply.
				return;
			}

			latestVersion = version;
			releaseUrl = root.has("html_url") ? root.get("html_url").getAsString()
					: "https://github.com/" + OWNER + "/" + REPO + "/releases/latest";
			assetUrl = jarUrl;
			assetName = jarName;
			notifyIngame();
		} catch (Exception e) {
			BetterPlanetEarth.LOGGER.debug("Update check failed", e);
		}
	}

	private static void notifyIngame() {
		MinecraftClient mc = MinecraftClient.getInstance();
		mc.execute(() -> {
			if (mc.player == null) {
				return;
			}
			MutableText button = Text.literal("[클릭해서 업데이트]").setStyle(Style.EMPTY
					.withColor(Formatting.GREEN)
					.withBold(true)
					.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/업데이트"))
					.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
							Text.literal("새 버전을 내려받습니다. 게임을 재시작하면 적용됩니다."))));
			mc.player.sendMessage(Text.literal("§b[Better PlanetEarth] §f새 버전 §a" + latestVersion
					+ " §f이(가) 나왔습니다! ").append(button), false);
		});
	}

	private static String currentVersion() {
		Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(BetterPlanetEarth.MOD_ID);
		return container.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0.0.0");
	}

	// ---- Apply ----

	/**
	 * Downloads the new jar and stages the swap. Feedback is delivered via the given callback,
	 * always on the client thread (the download itself runs on a background thread, so callers
	 * don't need to worry about hopping threads themselves).
	 */
	public static void apply(Consumer<String> feedback) {
		Consumer<String> onClientThread = msg -> MinecraftClient.getInstance().execute(() -> feedback.accept(msg));
		if (assetUrl == null) {
			onClientThread.accept("§7새 업데이트가 없습니다.");
			return;
		}
		if (staged) {
			onClientThread.accept("§7이미 새 버전을 받아뒀습니다. 게임을 완전히 재시작하면 적용됩니다.");
			return;
		}
		onClientThread.accept("§7" + assetName + " 다운로드 중...");
		CompletableFuture.runAsync(() -> doApply(onClientThread), EXECUTOR);
	}

	private static void doApply(Consumer<String> feedback) {
		try {
			Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(BetterPlanetEarth.MOD_ID);
			List<Path> paths = container.isPresent() ? container.get().getOrigin().getPaths() : List.of();
			if (paths.isEmpty() || !paths.get(0).toString().endsWith(".jar")) {
				feedback.accept("§c개발 환경에서는 자동 교체가 불가능합니다. " + releaseUrl + " 에서 직접 받아주세요.");
				return;
			}
			Path currentJar = paths.get(0);
			Path modsDir = currentJar.getParent();
			Path finalJar = modsDir.resolve(assetName);
			Path stagedJar = modsDir.resolve(assetName + ".new");

			HttpRequest req = HttpRequest.newBuilder(URI.create(assetUrl))
					.timeout(Duration.ofMinutes(2))
					.header("User-Agent", "BetterPlanetEarth-Mod")
					.GET()
					.build();
			HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
			if (resp.statusCode() != 200) {
				feedback.accept("§c다운로드 실패 (HTTP " + resp.statusCode() + ")");
				return;
			}
			Files.write(stagedJar, resp.body());

			scheduleSwap(currentJar, stagedJar, finalJar);
			staged = true;
			feedback.accept("§a새 버전을 받았습니다! §f게임을 완전히 종료했다가 다시 실행하면 적용됩니다.");
		} catch (Exception e) {
			BetterPlanetEarth.LOGGER.error("Failed to download update", e);
			feedback.accept("§c업데이트 중 오류가 발생했습니다: " + e.getMessage());
		}
	}

	/**
	 * Spawns a detached helper script that polls until this jar is no longer locked (i.e.
	 * until the game process exits), deletes it, and moves the staged download into place
	 * under the new version's filename. Runs as an independent OS process so it outlives
	 * this JVM.
	 */
	private static void scheduleSwap(Path oldJar, Path stagedJar, Path finalJar) throws IOException {
		Path modsDir = oldJar.getParent();
		boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");

		if (windows) {
			Path script = modsDir.resolve("betterpe_update.bat");
			String content = "@echo off\r\n"
					+ ":wait\r\n"
					+ "del /f /q \"" + oldJar.toAbsolutePath() + "\" >nul 2>nul\r\n"
					+ "if exist \"" + oldJar.toAbsolutePath() + "\" (\r\n"
					+ "  timeout /t 2 /nobreak >nul\r\n"
					+ "  goto wait\r\n"
					+ ")\r\n"
					+ "move /y \"" + stagedJar.toAbsolutePath() + "\" \"" + finalJar.toAbsolutePath() + "\" >nul\r\n"
					+ "del \"%~f0\"\r\n";
			Files.writeString(script, content, StandardCharsets.UTF_8);
			new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "/min", script.toAbsolutePath().toString())
					.directory(modsDir.toFile())
					.start();
		} else {
			Path script = modsDir.resolve("betterpe_update.sh");
			String content = "#!/bin/sh\n"
					+ "while ! rm -f \"" + oldJar.toAbsolutePath() + "\" 2>/dev/null; do sleep 2; done\n"
					+ "mv -f \"" + stagedJar.toAbsolutePath() + "\" \"" + finalJar.toAbsolutePath() + "\"\n"
					+ "rm -- \"$0\"\n";
			Files.writeString(script, content, StandardCharsets.UTF_8);
			script.toFile().setExecutable(true);
			new ProcessBuilder("/bin/sh", script.toAbsolutePath().toString())
					.directory(modsDir.toFile())
					.start();
		}
	}
}
