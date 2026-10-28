package com.basefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Single place that opens screens, so a version change to this call is a one-line fix.
 * Fabric's 26.2 docs use minecraft.gui.setScreen(...); on 26.1 and earlier it was minecraft.setScreen(...).
 */
public final class ScreenUtil {
	private ScreenUtil() {
	}

	public static void open(Screen screen) {
		Minecraft.getInstance().gui.setScreen(screen);
	}
}
