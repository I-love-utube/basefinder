package com.basefinder;

/** Block-entity families the finder cares about. */
public enum Category {
	STORAGE("Storage", 0xFFB000),
	AUTOMATION("Automation", 0x00C8FF),
	SPAWNER("Spawners", 0xFF3B3B);

	public final String shortName;
	public final int defaultColor;

	Category(String shortName, int defaultColor) {
		this.shortName = shortName;
		this.defaultColor = defaultColor;
	}

	/** Maps a block registry path (e.g. "trapped_chest") to a category, or null if irrelevant. */
	public static Category of(String path) {
		return switch (path) {
			case "chest", "trapped_chest", "barrel" -> STORAGE;
			case "hopper", "dropper", "dispenser", "crafter" -> AUTOMATION;
			case "spawner" -> SPAWNER;
			default -> path.endsWith("shulker_box") ? STORAGE : null;
		};
	}
}
