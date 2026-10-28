package com.basefinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects relevant block entities as the client loads them (no polling, no world scans), and
 * clusters them on a background thread. Loading only does a map insert, so it cannot cause lag.
 */
public final class Tracker {
	private static final Logger LOG = LoggerFactory.getLogger("basefinder");

	/** Every relevant block entity currently loaded on the client, keyed by BlockPos.asLong(). */
	public static final Map<Long, Hit> HITS = new ConcurrentHashMap<>();
	public static final AtomicLong VERSION = new AtomicLong();

	private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "basefinder-cluster");
		t.setDaemon(true);
		return t;
	});
	private static final AtomicBoolean RUNNING = new AtomicBoolean();
	private static volatile List<Cluster> pending;
	private static volatile boolean dirty = true;

	private static int tickCounter;
	private static boolean haveLast;
	private static double lastX, lastY, lastZ;

	private Tracker() {
	}

	public static void onLoad(BlockEntity be) {
		String path = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath();
		Category category = Category.of(path);
		if (category == null) {
			return;
		}
		BlockPos p = be.getBlockPos();
		HITS.put(p.asLong(), new Hit(p.getX(), p.getY(), p.getZ(), category));
		VERSION.incrementAndGet();
		dirty = true;
	}

	public static void onUnload(BlockEntity be) {
		if (HITS.remove(be.getBlockPos().asLong()) != null) {
			VERSION.incrementAndGet();
			dirty = true;
		}
	}

	public static void clear() {
		HITS.clear();
		VERSION.incrementAndGet();
		pending = null;
		haveLast = false;
		dirty = true;
	}

	public static void markDirty() {
		dirty = true;
	}

	/** Called every client tick. */
	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}

		List<Cluster> done = pending;
		if (done != null) {
			pending = null;
			List<Cluster> fresh = Store.upsert(done);
			if (Config.get().announceNewFinds) {
				for (Cluster c : fresh) {
					if (!c.ignored) {
						mc.player.sendSystemMessage(Component.literal(String.format(
								"[BaseFinder] %s: %d blocks near %d, %d, %d",
								c.typeName(), c.size, c.centerX(), c.centerY(), c.centerZ())));
					}
				}
			}
		}

		Config cfg = Config.get();
		if (!cfg.enabled || ++tickCounter % 20 != 0 || RUNNING.get()) {
			return;
		}

		double px = mc.player.getX();
		double py = mc.player.getY();
		double pz = mc.player.getZ();
		boolean moved = !haveLast || sq(px - lastX) + sq(py - lastY) + sq(pz - lastZ) > 256;
		if (!dirty && !moved) {
			return;
		}

		double r2 = (double) cfg.scanRange * cfg.scanRange;
		List<Hit> snapshot = new ArrayList<>();
		for (Hit h : HITS.values()) {
			if (cfg.isEnabled(h.category())
					&& sq(h.x() + 0.5 - px) + sq(h.y() + 0.5 - py) + sq(h.z() + 0.5 - pz) <= r2) {
				snapshot.add(h);
			}
		}

		dirty = false;
		haveLast = true;
		lastX = px;
		lastY = py;
		lastZ = pz;

		String dimension = mc.level.dimension().toString();
		int radius = cfg.clusterRadius;
		int minSize = cfg.minClusterSize;
		RUNNING.set(true);
		POOL.execute(() -> {
			try {
				pending = Clusterer.run(snapshot, radius, minSize, dimension);
			} catch (Throwable t) {
				LOG.warn("Clustering failed", t);
			} finally {
				RUNNING.set(false);
			}
		});
	}

	private static double sq(double v) {
		return v * v;
	}
}
