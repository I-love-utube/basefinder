package com.basefinder;

import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Settings screen. Every control is a plain button that cycles through presets. */
public class ConfigScreen extends Screen {
	private static final int[] PALETTE = {0xFFB000, 0x00C8FF, 0xFF3B3B, 0x55FF55, 0xFF55FF, 0xFFFFFF, 0xFFFF55, 0xAA66FF};
	private static final int[] RANGES = {32, 64, 96, 128, 192, 256, 384, 512};
	private static final int[] MIN_SIZES = {2, 3, 4, 5, 6, 8, 10, 15};
	private static final int[] RADII = {4, 6, 8, 12, 16, 24};

	private final Screen parent;

	public ConfigScreen(Screen parent) {
		super(Component.literal("Base Finder"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config cfg = Config.get();
		int left = this.width / 2 - 155;
		int right = this.width / 2 + 5;
		int w = 150;
		int y = 36;
		int step = 24;

		button(left, y, w, () -> text("Scanning: " + onOff(cfg.enabled)), () -> cfg.enabled = !cfg.enabled);
		button(right, y, w, () -> text("Scan range: " + cfg.scanRange), () -> cfg.scanRange = next(RANGES, cfg.scanRange));
		y += step;
		button(left, y, w, () -> text("Min blocks: " + cfg.minClusterSize),
				() -> cfg.minClusterSize = next(MIN_SIZES, cfg.minClusterSize));
		button(right, y, w, () -> text("Group radius: " + cfg.clusterRadius),
				() -> cfg.clusterRadius = next(RADII, cfg.clusterRadius));
		y += step;
		button(left, y, w, () -> text("Mark each block: " + onOff(cfg.markIndividualBlocks)),
				() -> cfg.markIndividualBlocks = !cfg.markIndividualBlocks);
		button(right, y, w, () -> text("Chat alerts: " + onOff(cfg.announceNewFinds)),
				() -> cfg.announceNewFinds = !cfg.announceNewFinds);
		y += step + 6;

		for (Category c : Category.values()) {
			button(left, y, w, () -> text(c.shortName + ": " + onOff(cfg.isEnabled(c))),
					() -> cfg.setEnabled(c, !cfg.isEnabled(c)));
			button(right, y, w, () -> Component.literal("Color ")
							.append(Component.literal("███").withStyle(Style.EMPTY.withColor(cfg.color(c)))),
					() -> cfg.setColor(c, next(PALETTE, cfg.color(c))));
			y += step;
		}

		this.addRenderableWidget(Button.builder(Component.literal("View results"),
				b -> ScreenUtil.open(new ResultsScreen(this))).bounds(left, this.height - 28, w, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"),
				b -> this.onClose()).bounds(right, this.height - 28, w, 20).build());
	}

	private void button(int x, int y, int w, Supplier<Component> label, Runnable action) {
		this.addRenderableWidget(Button.builder(label.get(), b -> {
			action.run();
			Config.changed();
			b.setMessage(label.get());
		}).bounds(x, y, w, 20).build());
	}

	private static Component text(String s) {
		return Component.literal(s);
	}

	private static String onOff(boolean v) {
		return v ? "ON" : "OFF";
	}

	/** Next value in the preset list after current (wraps around). */
	private static int next(int[] values, int current) {
		for (int v : values) {
			if (v > current) {
				return v;
			}
		}
		return values[0];
	}

	@Override
	public void onClose() {
		Config.save();
		ScreenUtil.open(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 14, 0xFFFFFFFF, true);
		String hint = "Only chunks your game has loaded are scanned. Highlights show through walls.";
		graphics.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, this.height - 44, 0xFFAAAAAA, false);
	}
}
