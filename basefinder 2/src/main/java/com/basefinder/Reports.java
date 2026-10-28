package com.basefinder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local false-positive log. Nothing is sent anywhere: each report is one JSON line appended to
 * config/basefinder-reports.jsonl, which the user can attach to an issue. Server address is not recorded.
 */
public final class Reports {
	private static final Logger LOG = LoggerFactory.getLogger("basefinder");
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("basefinder-reports.jsonl");

	private Reports() {
	}

	public static Path file() {
		return FILE;
	}

	public static boolean report(Cluster c) {
		JsonObject o = new JsonObject();
		o.addProperty("time", Instant.now().toString());
		o.addProperty("minecraft", "26.3");
		o.addProperty("mod", "1.1.0");
		o.addProperty("dimension", c.dimension);
		o.addProperty("type", c.typeName());
		o.addProperty("size", c.size);
		o.addProperty("storage", c.storage);
		o.addProperty("automation", c.automation);
		o.addProperty("spawner", c.spawner);
		o.addProperty("min", c.minX + "," + c.minY + "," + c.minZ);
		o.addProperty("max", c.maxX + "," + c.maxY + "," + c.maxZ);
		try {
			Files.writeString(FILE, o + "\n", StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			return true;
		} catch (IOException e) {
			LOG.warn("Could not write {}", FILE, e);
			return false;
		}
	}
}
