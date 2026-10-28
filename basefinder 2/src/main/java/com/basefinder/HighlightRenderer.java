package com.basefinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;

/**
 * Outlines clusters using Minecraft's built-in gizmo system with "always on top", so the outlines
 * show through walls. Gizmos are drawn by the game's own render pass, which means this mod never opens
 * a render pass or touches GPU buffers itself (26.3 keeps the level render pass open during mod events,
 * so doing that crashed with "Close the existing render pass before performing additional commands").
 */
public final class HighlightRenderer {
	private static final double PAD = 0.1;
	private static final int MAX_INDIVIDUAL_BLOCKS = 600;

	/** Immutable draw state: a world-space box and an ARGB color. */
	private record Box(AABB aabb, int argb) {
	}

	private static volatile List<Box> boxes = List.of();
	private static long lastKey = Long.MIN_VALUE;
	private static int frames;

	private HighlightRenderer() {
	}

	public static void register() {
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> draw());
	}

	/** Nothing to release any more; kept so the entrypoint does not need to change. */
	public static void close() {
	}

	private static void draw() {
		Minecraft mc = Minecraft.getInstance();
		Config cfg = Config.get();
		if (!cfg.enabled || mc.player == null || mc.level == null) {
			return;
		}

		rebuildIfNeeded(mc, cfg);

		for (Box b : boxes) {
			Gizmos.cuboid(b.aabb(), GizmoStyle.stroke(b.argb())).setAlwaysOnTop();
		}
	}

	/** Rebuilds the box list when data changed, and every 30 frames so "nearest N" follows the player. */
	private static void rebuildIfNeeded(Minecraft mc, Config cfg) {
		long key = Store.version * 31 + Tracker.VERSION.get() * 17 + Config.version;
		if (key == lastKey && ++frames % 30 != 0) {
			return;
		}
		lastKey = key;

		String dimension = mc.level.dimension().toString();
		double px = mc.player.getX();
		double py = mc.player.getY();
		double pz = mc.player.getZ();

		List<Cluster> visible = new ArrayList<>();
		for (Cluster c : Store.current()) {
			if (!c.ignored && c.dimension.equals(dimension) && cfg.isEnabled(c.dominant())) {
				visible.add(c);
			}
		}
		visible.sort(Comparator.comparingDouble(c -> c.distanceTo(px, py, pz)));
		if (visible.size() > cfg.maxRenderedClusters) {
			visible = new ArrayList<>(visible.subList(0, cfg.maxRenderedClusters));
		}

		List<Box> out = new ArrayList<>();
		for (Cluster c : visible) {
			out.add(new Box(new AABB(
					c.minX - PAD, c.minY - PAD, c.minZ - PAD,
					c.maxX + 1 + PAD, c.maxY + 1 + PAD, c.maxZ + 1 + PAD),
					argb(cfg.color(c.dominant()), 0xFF)));
		}

		if (cfg.markIndividualBlocks) {
			int budget = MAX_INDIVIDUAL_BLOCKS;
			for (Hit h : Tracker.HITS.values()) {
				if (budget <= 0) {
					break;
				}
				if (!cfg.isEnabled(h.category())) {
					continue;
				}
				for (Cluster c : visible) {
					if (contains(c, h)) {
						out.add(new Box(new AABB(h.x() - 0.02, h.y() - 0.02, h.z() - 0.02,
								h.x() + 1.02, h.y() + 1.02, h.z() + 1.02),
								argb(cfg.color(h.category()), 0xB0)));
						budget--;
						break;
					}
				}
			}
		}
		boxes = out;
	}

	private static boolean contains(Cluster c, Hit h) {
		return h.x() >= c.minX && h.x() <= c.maxX
				&& h.y() >= c.minY && h.y() <= c.maxY
				&& h.z() >= c.minZ && h.z() <= c.maxZ;
	}

	private static int argb(int rgb, int alpha) {
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}
}
