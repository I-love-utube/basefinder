package com.basefinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class BaseFinderClient implements ClientModInitializer {
	public static final String MOD_ID = "basefinder";

	private static KeyMapping toggleKey;
	private static KeyMapping configKey;
	private static KeyMapping resultsKey;
	private static int saveCounter;

	@Override
	public void onInitializeClient() {
		Config.load();
		Store.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		// 26.3 replaced GLFW with SDL: key constants come from InputConstants and the constructor
		// takes (translation key, key, category).
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.basefinder.toggle", InputConstants.KEY_B, category));
		configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.basefinder.config", InputConstants.KEY_N, category));
		resultsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.basefinder.results", InputConstants.KEY_M, category));

		// Block entities are reported as the client loads and unloads chunks: no polling, no world scans.
		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> Tracker.onLoad(blockEntity));
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> Tracker.onUnload(blockEntity));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			Tracker.clear();
			ServerData server = client.getCurrentServer();
			Store.joinWorld(server != null ? server.ip : "singleplayer");
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Store.flush();
			Tracker.clear();
		});
		ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> Tracker.clear());

		ClientTickEvents.END_CLIENT_TICK.register(BaseFinderClient::tick);

		HighlightRenderer.register();
		Hud.register();

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			Store.flush();
			Config.save();
			HighlightRenderer.close();
		});
	}

	private static void tick(Minecraft mc) {
		while (toggleKey.consumeClick()) {
			Config cfg = Config.get();
			cfg.enabled = !cfg.enabled;
			Config.save();
			Config.changed();
			if (mc.player != null) {
				mc.player.sendSystemMessage(Component.literal("[BaseFinder] " + (cfg.enabled ? "scanning ON" : "scanning OFF")));
			}
		}
		while (configKey.consumeClick()) {
			ScreenUtil.open(new ConfigScreen(null));
		}
		while (resultsKey.consumeClick()) {
			ScreenUtil.open(new ResultsScreen(null));
		}

		Tracker.tick(mc);

		if (++saveCounter % 200 == 0) {
			Store.saveIfDirty();
		}
	}
}
