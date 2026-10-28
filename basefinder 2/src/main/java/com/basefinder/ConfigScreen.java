package com.basefinder;

import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Settings screen. Every control is a plain button that cycles through presets; hover for a tip. */
public class ConfigScreen extends Screen {
	private static final int[] PALETTE = {0xFFB000, 0x00C8FF, 0xFF3B3B, 0x55FF55, 0xFF55FF, 0xFFFFFF, 0xFFFF55, 0xAA66FF};
	private static final int[] RANGES = {32, 64, 96, 128, 192, 256, 384, 512};
	private static final int[] MIN_SIZES = {2, 3, 4, 5, 6, 8, 10, 15};
	private static final int[] RADII = {4, 6, 8, 12, 16, 24};
	private static final int[] HUD_ROWS = {1, 2, 3, 5, 8};
	private static final String[] CORNERS = {"Top-left", "Top-right", "Bottom-left", "Bottom-right"};

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
		int y = 32;
		int step = 23;

		button(left, y, w, () -> text("Scanning: " + onOff(cfg.enabled)),
				"Turns the finder on or off (same as the B key). When off, nothing is scanned, highlighted or shown on the HUD.",
				() -> cfg.enabled = !cfg.enabled);
		button(right, y, w, () -> text("Scan range: " + cfg.scanRange),
				"How far from you, in blocks, to look for bases. Your game only knows about chunks it has loaded, so going past your render distance will not find more.",
				() -> cfg.scanRange = next(RANGES, cfg.scanRange));
		y += step;
		button(left, y, w, () -> text("Min blocks: " + cfg.minClusterSize),
				"How many chests, hoppers or spawners must be close together to count as a base. Raise it to ignore small things like a lone dungeon chest.",
				() -> cfg.minClusterSize = next(MIN_SIZES, cfg.minClusterSize));
		button(right, y, w, () -> text("Group radius: " + cfg.clusterRadius),
				"Blocks closer together than this are grouped into one base. Raise it if one big base shows up as several boxes; lower it if separate bases get merged.",
				() -> cfg.clusterRadius = next(RADII, cfg.clusterRadius));
		y += step;
		button(left, y, w, () -> text("Mark each block: " + onOff(cfg.markIndividualBlocks)),
				"Also outline every single chest, hopper and spawner inside a base, not just the big box around the whole thing.",
				() -> cfg.markIndividualBlocks = !cfg.markIndividualBlocks);
		button(right, y, w, () -> text("Chat alerts: " + onOff(cfg.announceNewFinds)),
				"Print a message in chat the first time a new base is found.",
				() -> cfg.announceNewFinds = !cfg.announceNewFinds);
		y += step + 4;

		button(left, y, w, () -> text("HUD panel: " + onOff(cfg.hudEnabled)),
				"Shows a small panel on your screen with the nearest bases, their distance and compass direction. New finds flash yellow for 10 seconds.",
				() -> cfg.hudEnabled = !cfg.hudEnabled);
		button(right, y, w, () -> text("HUD: " + CORNERS[cfg.hudCorner]),
				"Which corner of the screen the HUD panel sits in. Pick a different one if it covers something you need.",
				() -> cfg.hudCorner = (cfg.hudCorner + 1) % CORNERS.length);
		y += step;
		button(left, y, w, () -> text("HUD rows: " + cfg.hudRows),
				"How many of the nearest bases the HUD panel lists at once.",
				() -> cfg.hudRows = next(HUD_ROWS, cfg.hudRows));
		y += step + 4;

		for (Category c : Category.values()) {
			button(left, y, w, () -> text(c.shortName + ": " + onOff(cfg.isEnabled(c))),
					categoryTip(c),
					() -> cfg.setEnabled(c, !cfg.isEnabled(c)));
			button(right, y, w, () -> Component.literal("Color ")
							.append(Component.literal("███").withStyle(Style.EMPTY.withColor(cfg.color(c)))),
					"Click to cycle the highlight color used for " + c.shortName.toLowerCase() + ".",
					() -> cfg.setColor(c, next(PALETTE, cfg.color(c))));
			y += step;
		}

		this.addRenderableWidget(Button.builder(Component.literal("View results"),
						b -> ScreenUtil.open(new ResultsScreen(this)))
				.bounds(left, this.height - 28, w, 20)
				.tooltip(tip("Open the list of every base found so far, nearest first, with Ignore and Report buttons."))
				.build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(right, this.height - 28, w, 20)
				.tooltip(tip("Save your settings and close this screen."))
				.build());
	}

	private static String categoryTip(Category c) {
		return switch (c) {
			case STORAGE -> "Chests, trapped chests, barrels and shulker boxes. Turn on to find stashes.";
			case AUTOMATION -> "Hoppers, droppers, dispensers and crafters. Turn on to find farms and machines.";
			case SPAWNER -> "Mob spawners. A base with a spawner is shown as a Spawner base.";
		};
	}

	private void button(int x, int y, int w, Supplier<Component> label, String tooltip, Runnable action) {
		this.addRenderableWidget(Button.builder(label.get(), b -> {
					action.run();
					Config.changed();
					b.setMessage(label.get());
				})
				.bounds(x, y, w, 20)
				.tooltip(tip(tooltip))
				.build());
	}

	private static Tooltip tip(String text) {
		return Tooltip.create(Component.literal(text));
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
		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 12, 0xFFFFFFFF, true);
		String hint = "Hover over any button for an explanation.";
		graphics.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, this.height - 42, 0xFFAAAAAA, false);
	}
}
