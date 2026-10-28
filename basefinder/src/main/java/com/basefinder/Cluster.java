package com.basefinder;

/**
 * A group of nearby hits. Plain public fields so Gson can persist it without extra setup.
 * Bounds are inclusive block coordinates.
 */
public final class Cluster {
	public String id;
	public String dimension;
	public int minX, minY, minZ, maxX, maxY, maxZ;
	public int size, storage, automation, spawner;
	public boolean ignored;

	public Category dominant() {
		if (spawner > 0) {
			return Category.SPAWNER;
		}
		return automation > storage ? Category.AUTOMATION : Category.STORAGE;
	}

	public String typeName() {
		if (spawner > 0) {
			return "Spawner base";
		}
		if (automation >= 3 && storage >= 3) {
			return "Base";
		}
		return automation > storage ? "Farm" : "Stash";
	}

	public int centerX() {
		return (minX + maxX) / 2;
	}

	public int centerY() {
		return (minY + maxY) / 2;
	}

	public int centerZ() {
		return (minZ + maxZ) / 2;
	}

	public double distanceTo(double x, double y, double z) {
		double dx = centerX() + 0.5 - x;
		double dy = centerY() + 0.5 - y;
		double dz = centerZ() + 0.5 - z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** True if the two boxes overlap after growing this one by pad blocks. */
	public boolean overlaps(Cluster o, int pad) {
		return minX - pad <= o.maxX && maxX + pad >= o.minX
				&& minY - pad <= o.maxY && maxY + pad >= o.minY
				&& minZ - pad <= o.maxZ && maxZ + pad >= o.minZ;
	}

	/** Grows this cluster to cover the other one (bounds union, counts keep the larger value). */
	public void merge(Cluster o) {
		minX = Math.min(minX, o.minX);
		minY = Math.min(minY, o.minY);
		minZ = Math.min(minZ, o.minZ);
		maxX = Math.max(maxX, o.maxX);
		maxY = Math.max(maxY, o.maxY);
		maxZ = Math.max(maxZ, o.maxZ);
		size = Math.max(size, o.size);
		storage = Math.max(storage, o.storage);
		automation = Math.max(automation, o.automation);
		spawner = Math.max(spawner, o.spawner);
		ignored |= o.ignored;
	}
}
