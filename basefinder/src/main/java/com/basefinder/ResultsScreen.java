package com.basefinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Lists discovered clusters (nearest first) with Ignore and Report-false-positive buttons. */
public class ResultsScreen extends Screen {
	private static final int PAGE_SIZE = 6;
	private static final int ROW_H = 28;
	private static final int TOP = 40;

	private final Screen parent;
	private List<Cluster> rows = List.of();
	private int page;

	public ResultsScreen(Screen parent) {
		super(Component.literal("Base Finder - Results"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();
		String dimension = mc.level == null ? "" : mc.level.dimension().toString();
		double px = mc.player == null ? 0 : mc.player.getX();
		double py = mc.player == null ? 0 : mc.player.getY();
		double pz = mc.player == null ? 0 : mc.player.getZ();

		List<Cluster> list = new ArrayList<>();
		for (Cluster c : Store.current()) {
			if (c.dimension.equals(dimension)) {
				list.add(c);
			}
		}
		list.sort(Comparator.comparingDouble(c -> c.distanceTo(px, py, pz)));
		rows = list;

		int pages = pageCount();
		page = Math.max(0, Math.min(page, pages - 1));

		int x0 = this.width / 2 - 210;
		int start = page * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE && start + i < rows.size(); i++) {
			Cluster c = rows.get(start + i);
			int y = TOP + i * ROW_H;
			this.addRenderableWidget(Button.builder(Component.literal(c.ignored ? "Restore" : "Ignore"), b -> {
				c.ignored = !c.ignored;
				Store.changed();
				b.setMessage(Component.literal(c.ignored ? "Restore" : "Ignore"));
			}).bounds(x0 + 280, y, 60, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("Report FP"), b -> {
				boolean ok = Reports.report(c);
				c.ignored = true;
				Store.changed();
				if (mc.player != null) {
					mc.player.sendSystemMessage(Component.literal(ok
							? "[BaseFinder] Logged to " + Reports.file().getFileName() + " and ignored this cluster."
							: "[BaseFinder] Could not write the report file; cluster ignored."));
				}
				this.clearWidgets();
				this.init();
			}).bounds(x0 + 345, y, 75, 20).build());
		}

		int bottom = this.height - 28;
		this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			page--;
			this.clearWidgets();
			this.init();
		}).bounds(this.width / 2 - 60, bottom, 24, 20).build()).active = page > 0;
		this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			page++;
			this.clearWidgets();
			this.init();
		}).bounds(this.width / 2 + 36, bottom, 24, 20).build()).active = page < pages - 1;
		this.addRenderableWidget(Button.builder(Component.literal("Clear list"), b -> {
			Store.clearUnignored();
			page = 0;
			this.clearWidgets();
			this.init();
		}).bounds(this.width / 2 - 210, bottom, 90, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Back"),
				b -> this.onClose()).bounds(this.width / 2 + 120, bottom, 90, 20).build());
	}

	private int pageCount() {
		return Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	@Override
	public void onClose() {
		ScreenUtil.open(this.parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.text(this.font, this.title, this.width / 2 - this.font.width(this.title) / 2, 14, 0xFFFFFFFF, true);

		Minecraft mc = Minecraft.getInstance();
		double px = mc.player == null ? 0 : mc.player.getX();
		double py = mc.player == null ? 0 : mc.player.getY();
		double pz = mc.player == null ? 0 : mc.player.getZ();
		int x0 = this.width / 2 - 210;

		if (rows.isEmpty()) {
			String empty = "Nothing found yet. Explore so chunks with chests or hoppers load.";
			graphics.text(this.font, empty, this.width / 2 - this.font.width(empty) / 2, TOP + 20, 0xFFAAAAAA, false);
		}

		int start = page * PAGE_SIZE;
		for (int i = 0; i < PAGE_SIZE && start + i < rows.size(); i++) {
			Cluster c = rows.get(start + i);
			int color = c.ignored ? 0xFF808080 : 0xFF000000 | Config.get().color(c.dominant());
			String line = String.format("%s  %d blocks  %d, %d, %d  (%dm)", c.typeName(), c.size,
					c.centerX(), c.centerY(), c.centerZ(), (int) c.distanceTo(px, py, pz));
			graphics.text(this.font, line, x0, TOP + i * ROW_H + 6, color, true);
		}

		String pageLabel = (page + 1) + "/" + pageCount();
		graphics.text(this.font, pageLabel, this.width / 2 - this.font.width(pageLabel) / 2, this.height - 22, 0xFFFFFFFF, false);
	}
}
