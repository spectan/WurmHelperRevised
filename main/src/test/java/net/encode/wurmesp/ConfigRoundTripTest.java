package net.encode.wurmesp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.encode.wurmesp.util.ConfigUtils;

/** What the ESP window and the esp command save must be what is loaded at the next start. */
public class ConfigRoundTripTest {
    private boolean players, flower, rideable, xray;

    @Before
    public void setUp() {
        players = WurmEspMod.players;
        flower = WurmEspMod.tilesFlower;
        rideable = WurmEspMod.tilesclosebynotrideable;
        xray = WurmEspMod.xray;
        // the lists and sounds the saver writes (set by a config load in the game)
        ConfigUtils.DoConfig(new Properties());
    }

    @After
    public void tearDown() {
        WurmEspMod.players = players;
        WurmEspMod.tilesFlower = flower;
        WurmEspMod.tilesclosebynotrideable = rideable;
        WurmEspMod.xray = xray;
    }

    private static void reloadFrom(Properties saved) {
        WurmEspMod.players = false;
        WurmEspMod.tilesFlower = false;
        WurmEspMod.tilesclosebynotrideable = false;
        WurmEspMod.xray = false;
        ConfigUtils.DoConfig(saved);
    }

    @Test
    public void flowerAndRideableTileTogglesAreSavedAndLoaded() {
        WurmEspMod.tilesFlower = true;
        WurmEspMod.tilesclosebynotrideable = true;
        Properties saved = ConfigUtils.toProperties();

        reloadFrom(saved);

        assertTrue("Flower Tiles is remembered", WurmEspMod.tilesFlower);
        assertTrue("Rideable Tiles is remembered", WurmEspMod.tilesclosebynotrideable);
    }

    @Test
    public void aToggleSwitchedOffStaysOff() {
        WurmEspMod.players = false;
        WurmEspMod.tilesFlower = false;
        Properties saved = ConfigUtils.toProperties();

        WurmEspMod.players = true;
        WurmEspMod.tilesFlower = true;
        ConfigUtils.DoConfig(saved);

        assertFalse(WurmEspMod.players);
        assertFalse(WurmEspMod.tilesFlower);
    }

    @Test
    public void everyToggleOfTheWindowIsInTheSavedFile() {
        Properties saved = ConfigUtils.toProperties();
        for (String key : new String[]{"players", "mobs", "animals", "specials", "items", "tilesflower", "uniques",
                "conditioned", "xray", "tilescloseby", "tilesclosebynotrideable", "deedsize"}) {
            assertTrue("missing " + key, saved.containsKey(key));
        }
        assertEquals("false", saved.getProperty("tilesflower"));
    }
}
