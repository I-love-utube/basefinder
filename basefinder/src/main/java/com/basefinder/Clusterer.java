package com.basefinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Groups hits whose chain of neighbours is within {@code radius} blocks (single-linkage). No Minecraft dependencies. */
public final class Clusterer {
	private Clusterer() {
	}

	public static List<Cluster> run(List<Hit> hits, int radius, int minSize, String dimension) {
		int n = hits.size();
		if (n == 0 || n < minSize) {
			return List.of();
		}

		int cell = Math.max(1, radius);
		long r2 = (long) radius * radius;

		Map<Long, List<Integer>> grid = new HashMap<>();
		for (int i = 0; i < n; i++) {
			Hit h = hits.get(i);
			long k = key(Math.floorDiv(h.x(), cell), Math.floorDiv(h.y(), cell), Math.floorDiv(h.z(), cell));
			grid.computeIfAbsent(k, ignored -> new ArrayList<>()).add(i);
		}

		int[] parent = new int[n];
		for (int i = 0; i < n; i++) {
			parent[i] = i;
		}

		for (int i = 0; i < n; i++) {
			Hit a = hits.get(i);
			int cx = Math.floorDiv(a.x(), cell);
			int cy = Math.floorDiv(a.y(), cell);
			int cz = Math.floorDiv(a.z(), cell);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						List<Integer> bucket = grid.get(key(cx + dx, cy + dy, cz + dz));
						if (bucket == null) {
							continue;
						}
						for (int j : bucket) {
							if (j <= i) {
								continue;
							}
							Hit b = hits.get(j);
							long ddx = a.x() - b.x();
							long ddy = a.y() - b.y();
							long ddz = a.z() - b.z();
							if (ddx * ddx + ddy * ddy + ddz * ddz <= r2) {
								parent[find(parent, i)] = find(parent, j);
							}
						}
					}
				}
			}
		}

		Map<Integer, List<Hit>> groups = new HashMap<>();
		for (int i = 0; i < n; i++) {
			groups.computeIfAbsent(find(parent, i), ignored -> new ArrayList<>()).add(hits.get(i));
		}

		List<Cluster> out = new ArrayList<>();
		for (List<Hit> g : groups.values()) {
			if (g.size() < minSize) {
				continue;
			}
			Cluster c = new Cluster();
			c.dimension = dimension;
			c.minX = c.minY = c.minZ = Integer.MAX_VALUE;
			c.maxX = c.maxY = c.maxZ = Integer.MIN_VALUE;
			for (Hit h : g) {
				c.minX = Math.min(c.minX, h.x());
				c.minY = Math.min(c.minY, h.y());
				c.minZ = Math.min(c.minZ, h.z());
				c.maxX = Math.max(c.maxX, h.x());
				c.maxY = Math.max(c.maxY, h.y());
				c.maxZ = Math.max(c.maxZ, h.z());
				switch (h.category()) {
					case STORAGE -> c.storage++;
					case AUTOMATION -> c.automation++;
					case SPAWNER -> c.spawner++;
				}
			}
			c.size = g.size();
			c.id = dimension + "#" + c.minX + "," + c.minY + "," + c.minZ;
			out.add(c);
		}
		return out;
	}

	private static int find(int[] parent, int x) {
		int root = x;
		while (parent[root] != root) {
			root = parent[root];
		}
		while (parent[x] != root) {
			int next = parent[x];
			parent[x] = root;
			x = next;
		}
		return root;
	}

	private static long key(int cx, int cy, int cz) {
		return ((long) (cx & 0x1FFFFF) << 42) | ((long) (cy & 0x1FFFFF) << 21) | (long) (cz & 0x1FFFFF);
	}
}
