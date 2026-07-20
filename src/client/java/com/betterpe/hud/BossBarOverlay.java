package com.betterpe.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A stack of client-side boss bars, registered as a single {@link HudElement} so the whole
 * stack renders through {@link HudRenderer} and drags/persists as one block through the same
 * Right-Shift {@link HudEditScreen} as every other HUD element. Unlike a single shared bar,
 * each feature owns its own named slot (e.g. "war", "brewery"), so up to all of them can be
 * visible at once - a war, a brewery timer, and a selected good aren't mutually exclusive.
 * Slots render in a fixed order (see {@link #ORDER}) and only active ones take up a row, so
 * the stack grows/shrinks without leaving gaps.
 */
public final class BossBarOverlay implements HudElement {
	public static final String ID = "bossbar";
	public static final BossBarOverlay INSTANCE = new BossBarOverlay();

	private static final int BAR_WIDTH = 182;
	private static final int BAR_HEIGHT = 5;
	// Title text sits above the bar; total row height covers both.
	private static final int TEXT_HEIGHT = 11;
	private static final int ROW_HEIGHT = TEXT_HEIGHT + BAR_HEIGHT;
	private static final int ROW_GAP = 2;

	// Fixed stacking order, most time-sensitive first. Slot ids not listed here (there
	// shouldn't be any) simply never render.
	private static final List<String> ORDER = List.of("acquisition", "war", "brewery", "goods", "boat");

	private static final class Slot {
		volatile Text title = Text.empty();
		volatile float progress = 1f;
		volatile int color = 0xFF55FF55;
		volatile boolean active = false;
	}

	private static final Map<String, Slot> SLOTS = new ConcurrentHashMap<>();

	private BossBarOverlay() {
	}

	/** Sets/shows the named slot's bar. */
	public static void set(String slotId, Text title, float progress, int argbColor) {
		Slot s = SLOTS.computeIfAbsent(slotId, k -> new Slot());
		s.title = title;
		s.progress = Math.max(0f, Math.min(1f, progress));
		s.color = argbColor;
		s.active = true;
	}

	/** Hides the named slot's bar; other slots keep showing. */
	public static void clear(String slotId) {
		Slot s = SLOTS.get(slotId);
		if (s != null) {
			s.active = false;
		}
	}

	private static List<Slot> activeSlotsInOrder() {
		List<Slot> out = new ArrayList<>(ORDER.size());
		for (String id : ORDER) {
			Slot s = SLOTS.get(id);
			if (s != null && s.active) {
				out.add(s);
			}
		}
		return out;
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public String getDisplayName() {
		return "보스바";
	}

	@Override
	public int getWidth() {
		return BAR_WIDTH;
	}

	@Override
	public int getHeight() {
		int rows = Math.max(1, activeSlotsInOrder().size());
		return rows * ROW_HEIGHT + (rows - 1) * ROW_GAP;
	}

	@Override
	public boolean isActive() {
		return !activeSlotsInOrder().isEmpty();
	}

	/** Default position: centered near the top of the screen, matching vanilla's own bar. */
	@Override
	public int getAnchorX(int screenWidth) {
		return (screenWidth - BAR_WIDTH) / 2;
	}

	@Override
	public int getAnchorY(int screenHeight) {
		return 1;
	}

	@Override
	public void render(DrawContext context, int x, int y, float tickDelta) {
		List<Slot> slots = activeSlotsInOrder();
		if (slots.isEmpty()) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		TextRenderer tr = client.textRenderer;

		int rowY = y;
		for (Slot s : slots) {
			int barY = rowY + TEXT_HEIGHT;

			context.fill(x, barY, x + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF202020);
			int filled = (int) (BAR_WIDTH * s.progress);
			context.fill(x, barY, x + filled, barY + BAR_HEIGHT, s.color);

			int textW = tr.getWidth(s.title);
			context.drawText(tr, s.title, x + (BAR_WIDTH - textW) / 2, rowY, 0xFFFFFFFF, true);

			rowY += ROW_HEIGHT + ROW_GAP;
		}
	}
}
