package com.basefinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Small always-on panel showing the nearest finds, so you do not have to open the results list. */
public final class Hud {
	private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
	private static final long NEW_MS = 10_000;
	private static final int MARGIN = 6;
	private static final int PAD = 3;
	private static final int LINE = 10;

	private Hud() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(BaseFinderClient.MOD_ID, "panel"),
				(graphics, delta) -> draw(graphics));
	}

	private static void draw(GuiGraphicsExtractor graphics) {
		Minecraft mc = Minecraft.getInstance();
		Config cfg = Config.get();
		if (!cfg.hudEnabled || !cfg.enabled || mc.player == null || mc.level == null) {
			return;
		}

		double px = mc.player.getX();
		double py = mc.player.getY();
		double pz = mc.player.getZ();
		String dimension = mc.level.dimension().toString();

		List<Cluster> near = new ArrayList<>();
		for (Cluster c : Store.current()) {
			if (!c.ignored && c.dimension.equals(dimension) && cfg.isEnabled(c.dominant())) {
				near.add(c);
			}
		}
		near.sort(Comparator.comparingDouble(c -> c.distanceTo(px, py, pz)));

		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		long now = System.currentTimeMillis();
		boolean flashOn = (now / 400) % 2 == 0;

		int shown = Math.min(cfg.hudRows, near.size());
		for (int i = 0; i < shown; i++) {
			Cluster c = near.get(i);
			boolean isNew = c.noticedAt != 0 && now - c.noticedAt < NEW_MS;
			lines.add(String.format("%s%s x%d  %dm %s", isNew ? "NEW! " : "", c.typeName(), c.size,
					(int) c.distanceTo(px, py, pz), direction(c, px, pz)));
			colors.add(isNew && flashOn ? 0xFFFFFF55 : 0xFF000000 | cfg.color(c.dominant()));
		}
		if (lines.isEmpty()) {
			lines.add("Base Finder: nothing nearby");
			colors.add(0xFFAAAAAA);
		} else if (near.size() > shown) {
			lines.add("+" + (near.size() - shown) + " more (press M)");
			colors.add(0xFFAAAAAA);
		}

		int width = 0;
		for (String s : lines) {
			width = Math.max(width, mc.font.width(s));
		}
		int boxW = width + PAD * 2;
		int boxH = lines.size() * LINE + PAD * 2 - 1;

		int screenW = mc.getWindow().getGuiScaledWidth();
		int screenH = mc.getWindow().getGuiScaledHeight();
		boolean right = cfg.hudCorner == 1 || cfg.hudCorner == 3;
		boolean bottom = cfg.hudCorner == 2 || cfg.hudCorner == 3;
		int x = right ? screenW - MARGIN - boxW : MARGIN;
		int y = bottom ? screenH - MARGIN - boxH - 40 : MARGIN;

		graphics.fill(x, y, x + boxW, y + boxH, 0x90000000);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(mc.font, lines.get(i), x + PAD, y + PAD + i * LINE, colors.get(i), true);
		}
	}

	/** Compass direction from the player to the cluster centre (N is negative Z, E is positive X). */
	private static String direction(Cluster c, double px, double pz) {
		double dx = c.centerX() + 0.5 - px;
		double dz = c.centerZ() + 0.5 - pz;
		double bearing = Math.toDegrees(Math.atan2(dx, -dz));
		int idx = (int) Math.round(bearing / 45.0);
		return COMPASS[Math.floorMod(idx, 8)];
	}
}
