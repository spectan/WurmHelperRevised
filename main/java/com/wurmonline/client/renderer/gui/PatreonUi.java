package com.wurmonline.client.renderer.gui;

/**
 * Stands in for SkloGraphics' own PatreonUi, whose class is left out of the unpacked jar (see main/pom.xml).
 * SkloGraphics injects calls to install and tick into the HUD; here they do nothing, so the supporter banner,
 * the startup popup and the thanks banner are never created. The "patreon" console command still works.
 */
public final class PatreonUi {
    private PatreonUi() {
    }

    public static void install(HeadsUpDisplay hud) {
    }

    public static void tick(HeadsUpDisplay hud) {
    }

    public static void showPopup() {
    }
}
