package com.betterpe.hud;

import com.betterpe.config.BetterPeConfigScreen;
import com.betterpe.config.Config;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Lunar-style "edit HUD" screen: every registered element renders as a draggable
 * labeled box; dragging updates its saved offset in the config directly.
 */
public class HudEditScreen extends Screen {
	private static final int BOX_COLOR = 0x55_2864FF;
	private static final int BOX_COLOR_HOVERED = 0x55_FFAA00;
	private static final int BOX_BORDER = 0xFF_FFFFFF;

	private HudElement dragging;
	private double dragOffsetX;
	private double dragOffsetY;

	public HudEditScreen() {
		super(Text.literal("HUD 편집"));
	}

	@Override
	protected void init() {
		this.addDrawableChild(ButtonWidget.builder(Text.literal("완료"), b -> this.close())
				.dimensions(this.width / 2 - 50, this.height - 28, 100, 20)
				.build());

		// Lunar-style: a settings button sits at the center of the edit screen so you can
		// jump straight to the mod's config without leaving HUD editing to hunt for it.
		this.addDrawableChild(ButtonWidget.builder(Text.literal("⚙ 설정"),
						b -> {
							if (this.client != null) {
								this.client.setScreen(BetterPeConfigScreen.create(this));
							}
						})
				.dimensions(this.width / 2 - 50, this.height / 2 - 10, 100, 20)
				.build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);

		for (HudElement element : HudManager.elements()) {
			Config.HudElementConfig config = HudManager.configOf(element.getId());
			int x = element.getAnchorX(this.width) + (int) config.x;
			int y = element.getAnchorY(this.height) + (int) config.y;
			int w = element.getWidth();
			int h = element.getHeight();

			boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
			context.fill(x, y, x + w, y + h, hovered ? BOX_COLOR_HOVERED : BOX_COLOR);
			context.drawBorder(x, y, w, h, BOX_BORDER);
			context.drawText(this.textRenderer, element.getDisplayName(), x + 4, y + 4, 0xFFFFFF, true);
		}

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (HudElement element : HudManager.elements()) {
				Config.HudElementConfig config = HudManager.configOf(element.getId());
				int x = element.getAnchorX(this.width) + (int) config.x;
				int y = element.getAnchorY(this.height) + (int) config.y;
				if (mouseX >= x && mouseX < x + element.getWidth()
						&& mouseY >= y && mouseY < y + element.getHeight()) {
					this.dragging = element;
					this.dragOffsetX = mouseX - x;
					this.dragOffsetY = mouseY - y;
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.dragging != null) {
			Config.HudElementConfig config = HudManager.configOf(this.dragging.getId());
			config.x = mouseX - this.dragOffsetX - this.dragging.getAnchorX(this.width);
			config.y = mouseY - this.dragOffsetY - this.dragging.getAnchorY(this.height);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && this.dragging != null) {
			this.dragging = null;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void close() {
		HudManager.save();
		if (this.client != null) {
			this.client.setScreen(null);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
