package com.betterpe.hud;

import net.minecraft.client.gui.DrawContext;

/**
 * A draggable HUD element. Freeform elements (location, war info, goods timer)
 * draw their own text via {@link #render}; elements that wrap vanilla rendering
 * (the scoreboard) leave render() empty and are only shifted by their saved offset.
 */
public interface HudElement {

	String getId();

	String getDisplayName();

	/** Box width at scale 1.0, used for the edit-screen drag handle. */
	int getWidth();

	int getHeight();

	/** Whether this element currently has anything to show. */
	default boolean isActive() {
		return true;
	}

	/**
	 * Draw the element at (x, y) top-left, already translated/scaled by the caller.
	 * Wrappers around vanilla rendering can leave this empty.
	 */
	default void render(DrawContext context, int x, int y, float tickDelta) {
	}

	/**
	 * Fixed reference point the saved offset is added to. Freeform elements anchor at
	 * (0,0) so the offset is absolute; vanilla-wrapping elements anchor near vanilla's
	 * own default spot so the edit box overlaps the real element.
	 */
	default int getAnchorX(int screenWidth) {
		return 0;
	}

	default int getAnchorY(int screenHeight) {
		return 0;
	}
}
