package com.betterpe.remote;

import com.betterpe.BetterPlanetEarth;
import com.betterpe.config.Config;
import com.betterpe.config.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Feature 10: pushes live online/queue status to the player's own status page
 * (better-planetearth.ggm.kr/&lt;닉네임&gt;) over a websocket, and executes a
 * remote "접속 종료" command sent back from that page.
 *
 * Auth model: the backend has no separate registration step. Whichever
 * password first authenticates a given nickname becomes that nickname's
 * password (this is how "처음 활성화 시 비밀번호 설정" works) — every connection
 * after that must match it, and a mismatch just logs a warning rather than
 * trying to support a password-reset flow.
 */
public final class RemoteStatusManager {
	public enum State {OFFLINE, CONNECTING, QUEUE, ONLINE}

	// "현재 대기열: 5 / 14" — same pattern for both the initial join line and later updates.
	private static final Pattern QUEUE_PATTERN = Pattern.compile("현재\\s*대기열\\s*:\\s*(\\d+)\\s*/\\s*(\\d+)");
	// Printed once the queue clears and the proxy hands the client off to the real server.
	private static final String CONNECTING_SUCCESS = "PlanetEarth 서버에 접속하는 중입니다";

	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.build();
	private static final long RECONNECT_DELAY_MS = 5000;
	// The socket can go quiet without ever telling us it closed (a dropped connection
	// that never sent a TCP FIN/RST our way, e.g. right around the game's own
	// disconnect/reconnect cycle) -- ping it and force a reconnect if it stops answering,
	// instead of trusting onClose/onError to always fire.
	private static final long PING_INTERVAL_MS = 15000;
	private static final long STALE_TIMEOUT_MS = 45000;

	private static volatile WebSocket socket;
	private static volatile boolean authed = false;
	private static volatile long nextReconnectAttemptMs = 0L;
	private static volatile long lastAliveMs = 0L;
	private static volatile long lastPingSentMs = 0L;
	private static volatile State lastSentState = null;
	private static volatile int lastPos = -1;
	private static volatile int lastTotal = -1;
	private static final StringBuilder INBOUND_BUFFER = new StringBuilder();

	private RemoteStatusManager() {
	}

	// ---- Lifecycle (driven from the client tick / connection events) ----

	/** Called every client tick; connects/reconnects as needed and is a no-op otherwise. */
	public static void tick() {
		Config cfg = ConfigManager.get();
		if (!cfg.modEnabled || !cfg.remoteStatusEnabled || cfg.remoteStatusPassword.isBlank()) {
			closeIfOpen();
			return;
		}
		long now = System.currentTimeMillis();
		WebSocket ws = socket;
		if (ws != null) {
			if (now - lastAliveMs > STALE_TIMEOUT_MS) {
				BetterPlanetEarth.LOGGER.debug("Remote status socket looked dead, forcing reconnect");
				try {
					ws.abort();
				} catch (Exception ignored) {
				}
				socket = null;
				authed = false;
			} else if (authed && now - lastPingSentMs > PING_INTERVAL_MS) {
				lastPingSentMs = now;
				try {
					ws.sendPing(ByteBuffer.allocate(0));
				} catch (Exception ignored) {
				}
			}
		}
		if (socket == null && now >= nextReconnectAttemptMs) {
			connect(cfg);
		}
	}

	public static void onWorldJoin() {
		sendState(State.CONNECTING, -1, -1);
	}

	public static void onWorldDisconnect() {
		sendState(State.OFFLINE, -1, -1);
	}

	/** Called for every game chat line. */
	public static void onChat(String plain) {
		Config cfg = ConfigManager.get();
		if (!cfg.modEnabled || !cfg.remoteStatusEnabled) {
			return;
		}
		sendChatLine(plain);
		Matcher m = QUEUE_PATTERN.matcher(plain);
		if (m.find()) {
			sendState(State.QUEUE, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
			return;
		}
		if (plain.contains(CONNECTING_SUCCESS)) {
			sendState(State.ONLINE, -1, -1);
		}
	}

	// ---- Networking ----

	private static String nickname() {
		Config cfg = ConfigManager.get();
		if (!cfg.selfNameOverride.isBlank()) {
			return cfg.selfNameOverride;
		}
		return MinecraftClient.getInstance().getSession().getUsername();
	}

	private static void connect(Config cfg) {
		nextReconnectAttemptMs = System.currentTimeMillis() + RECONNECT_DELAY_MS;
		try {
			HTTP.newWebSocketBuilder()
					.connectTimeout(Duration.ofSeconds(8))
					.buildAsync(URI.create(cfg.remoteStatusServerUrl), new Listener())
					.exceptionally(ex -> {
						BetterPlanetEarth.LOGGER.debug("Remote status connect failed", ex);
						return null;
					});
		} catch (Exception e) {
			BetterPlanetEarth.LOGGER.debug("Remote status connect failed", e);
		}
	}

	private static void closeIfOpen() {
		WebSocket ws = socket;
		if (ws != null) {
			try {
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "");
			} catch (Exception ignored) {
			}
			socket = null;
			authed = false;
		}
	}

	private static void sendState(State state, int pos, int total) {
		lastSentState = state;
		lastPos = pos;
		lastTotal = total;
		WebSocket ws = socket;
		if (ws == null || !authed) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("type", "status");
		o.addProperty("state", state.name());
		if (pos >= 0) {
			o.addProperty("pos", pos);
		}
		if (total >= 0) {
			o.addProperty("total", total);
		}
		ws.sendText(GSON.toJson(o), true);
	}

	/** Forwards one game chat line to the status page's live chat log. */
	private static void sendChatLine(String plain) {
		WebSocket ws = socket;
		if (ws == null || !authed) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("type", "chat");
		o.addProperty("text", plain);
		ws.sendText(GSON.toJson(o), true);
	}

	/** Runs a command/message the web page's input box submitted, exactly as if typed in chat. */
	private static void runCommand(String command) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> {
			if (client.player == null || client.getNetworkHandler() == null) {
				return;
			}
			String trimmed = command.trim();
			if (trimmed.isEmpty()) {
				return;
			}
			if (trimmed.startsWith("/")) {
				client.getNetworkHandler().sendChatCommand(trimmed.substring(1));
			} else {
				client.getNetworkHandler().sendChatMessage(trimmed);
			}
		});
	}

	private static void requestRemoteDisconnect() {
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> {
			if (client.world == null) {
				return;
			}
			// Mirrors GameMenuScreen's own "Disconnect" button exactly: world.disconnect()
			// is what actually notifies the server/proxy and closes the network channel;
			// client.disconnect() only tears down local client-side state. Calling just the
			// latter (as an earlier version of this did) leaves the channel dangling, so the
			// server eventually times it out itself -- which is what was showing up as a
			// ReadTimeoutException and an unexpected kick a few seconds after this ran.
			client.world.disconnect();
			client.disconnect();
			client.setScreen(new DisconnectedScreen(
					new MultiplayerScreen(new TitleScreen()),
					Text.literal("접속 종료"),
					Text.literal("원격 상태 페이지에서 접속 종료를 요청했습니다.")));
		});
	}

	private static final class Listener implements WebSocket.Listener {
		@Override
		public void onOpen(WebSocket webSocket) {
			socket = webSocket;
			authed = false;
			lastAliveMs = System.currentTimeMillis();
			Config cfg = ConfigManager.get();
			JsonObject o = new JsonObject();
			o.addProperty("type", "auth");
			o.addProperty("nickname", nickname());
			o.addProperty("password", cfg.remoteStatusPassword);
			webSocket.sendText(GSON.toJson(o), true);
			WebSocket.Listener.super.onOpen(webSocket);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			lastAliveMs = System.currentTimeMillis();
			INBOUND_BUFFER.append(data);
			if (last) {
				String msg = INBOUND_BUFFER.toString();
				INBOUND_BUFFER.setLength(0);
				handleMessage(msg);
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
			lastAliveMs = System.currentTimeMillis();
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			socket = null;
			authed = false;
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			socket = null;
			authed = false;
			BetterPlanetEarth.LOGGER.debug("Remote status socket error", error);
		}

		private void handleMessage(String msg) {
			try {
				JsonObject o = JsonParser.parseString(msg).getAsJsonObject();
				String type = o.get("type").getAsString();
				switch (type) {
					case "auth_ok" -> {
						authed = true;
						State state = lastSentState != null ? lastSentState : State.OFFLINE;
						sendState(state, lastPos, lastTotal);
					}
					case "auth_fail" -> {
						authed = false;
						BetterPlanetEarth.LOGGER.warn(
								"원격 상태 페이지: 비밀번호가 서버에 이미 등록된 값과 다릅니다. 설정에서 비밀번호를 확인하세요.");
					}
					case "disconnect_request" -> requestRemoteDisconnect();
					case "run_command" -> {
						if (o.has("command")) {
							runCommand(o.get("command").getAsString());
						}
					}
					default -> {
					}
				}
			} catch (Exception ignored) {
			}
		}
	}
}
