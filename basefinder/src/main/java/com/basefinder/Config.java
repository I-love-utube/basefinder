package com.basefinder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** User settings, stored as JSON in the Fabric config folder. */
public final class Config {
	private static final Logger LOG = LoggerFactory.getLogger("basefinder");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("basefinder.json");

	private static Config instance = new Config();

	/** Bumped on every settings change so the renderer knows to rebuild. */
	public static long version;

	public boolean enabled = true;
	public int scanRange = 128;
	public int minClusterSize = 4;
	public int clusterRadius = 8;
	public boolean markIndividualBlocks = false;
	public boolean announceNewFinds = true;
	public int maxRenderedClusters = 48;
	public Map<String, Boolean> categoryEnabled = new LinkedHashMap<>();
	public Map<String, Integer> categoryColor = new LinkedHashMap<>();

	public static Config get() {
		return instance;
	}

	public static void load() {
		if (Files.exists(FILE)) {
			try {
				Config read = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Config.class);
				if (read != null) {
					instance = read;
				}
			} catch (IOException | RuntimeException e) {
				LOG.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		instance.normalize();
		save();
	}

	public static void save() {
		try {
			Files.writeString(FILE, GSON.toJson(instance), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOG.warn("Could not write {}", FILE, e);
		}
	}

	/** Call after any setting changes. Marks the scanner and renderer dirty. */
	public static void changed() {
		version++;
		Tracker.markDirty();
	}

	private void normalize() {
		if (categoryEnabled == null) {
			categoryEnabled = new LinkedHashMap<>();
		}
		if (categoryColor == null) {
			categoryColor = new LinkedHashMap<>();
		}
		for (Category c : Category.values()) {
			categoryEnabled.putIfAbsent(c.name(), true);
			categoryColor.putIfAbsent(c.name(), c.defaultColor);
		}
		scanRange = Math.max(16, Math.min(1024, scanRange));
		minClusterSize = Math.max(2, Math.min(64, minClusterSize));
		clusterRadius = Math.max(2, Math.min(64, clusterRadius));
		maxRenderedClusters = Math.max(1, Math.min(200, maxRenderedClusters));
	}

	public boolean isEnabled(Category c) {
		return categoryEnabled.getOrDefault(c.name(), true);
	}

	public void setEnabled(Category c, boolean value) {
		categoryEnabled.put(c.name(), value);
	}

	public int color(Category c) {
		return categoryColor.getOrDefault(c.name(), c.defaultColor);
	}

	public void setColor(Category c, int rgb) {
		categoryColor.put(c.name(), rgb);
	}
}
