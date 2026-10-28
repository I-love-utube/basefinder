package com.basefinder;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remembers discovered clusters per world/server so they survive chunk unloads and restarts.
 * Main-thread only. Singleplayer worlds all share the key "singleplayer".
 */
public final class Store {
	private static final Logger LOG = LoggerFactory.getLogger("basefinder");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("basefinder-discoveries.json");
	private static final Type TYPE = new TypeToken<Map<String, List<Cluster>>>() { }.getType();

	private static Map<String, List<Cluster>> all = new HashMap<>();
	private static String worldKey = "singleplayer";
	private static boolean dirty;

	/** Bumped whenever the visible list may have changed. */
	public static long version;

	private Store() {
	}

	public static void load() {
		if (!Files.exists(FILE)) {
			return;
		}
		try {
			Map<String, List<Cluster>> read = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), TYPE);
			if (read != null) {
				all = read;
			}
		} catch (IOException | RuntimeException e) {
			LOG.warn("Could not read {}", FILE, e);
		}
	}

	public static void joinWorld(String key) {
		worldKey = key;
		version++;
	}

	public static List<Cluster> current() {
		return all.computeIfAbsent(worldKey, k -> new ArrayList<>());
	}

	/** Merges freshly computed clusters into the saved list. Returns the ones that are new. */
	public static List<Cluster> upsert(List<Cluster> found) {
		List<Cluster> list = current();
		List<Cluster> fresh = new ArrayList<>();
		int pad = Config.get().clusterRadius;

		for (Cluster n : found) {
			Cluster match = null;
			Iterator<Cluster> it = list.iterator();
			while (it.hasNext()) {
				Cluster e = it.next();
				if (!e.dimension.equals(n.dimension) || !e.overlaps(n, pad)) {
					continue;
				}
				if (match == null) {
					match = e;
				} else {
					match.merge(e);
					it.remove();
				}
			}
			if (match == null) {
				list.add(n);
				fresh.add(n);
			} else {
				match.merge(n);
			}
		}
		if (!found.isEmpty()) {
			changed();
		}
		return fresh;
	}

	/** Removes every cluster that has not been ignored (ignored ones stay so they do not come back). */
	public static void clearUnignored() {
		current().removeIf(c -> !c.ignored);
		changed();
	}

	public static void changed() {
		dirty = true;
		version++;
	}

	public static void saveIfDirty() {
		if (dirty) {
			flush();
		}
	}

	public static void flush() {
		dirty = false;
		try {
			Files.writeString(FILE, GSON.toJson(all, TYPE), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.warn("Could not write {}", FILE, e);
		}
	}
}
