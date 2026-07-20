package com.betterpe.hud.elements;

import com.betterpe.hud.HudElement;

/**
 * The vanilla sidebar scoreboard, made movable. The draw call stays vanilla;
 * {@code InGameHudMixin} reads this element's saved offset and translates around
 * the vanilla render, so dragging the edit-screen box shifts the whole sidebar.
 *
 * isActive() is intentionally not wired to Scoreboard#getObjectiveForSlot: that
 * method's signature changed (int slot -> ScoreboardDisplaySlot enum) between
 * 1.20.1 and 1.20.3, so calling it from code compiled against 1.20.1 mappings would
 * risk a NoSuchMethodError on 1.20.4. The mixin only fires when vanilla itself draws.
 */
public class ScoreboardHudElement implements HudElement {
	public static final String ID = "scoreboard";
	public static final ScoreboardHudElement INSTANCE = new ScoreboardHudElement();

	private ScoreboardHudElement() {
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public String getDisplayName() {
		return "스코어보드";
	}

	@Override
	public int getWidth() {
		return 120;
	}

	@Override
	public int getHeight() {
		return 80;
	}

	@Override
	public int getAnchorX(int screenWidth) {
		return screenWidth - getWidth() - 2;
	}

	@Override
	public int getAnchorY(int screenHeight) {
		return screenHeight / 2 - getHeight() / 2;
	}
}
