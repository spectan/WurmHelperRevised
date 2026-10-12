package net.encode.wurmesp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.wurmonline.client.renderer.PickableUnit;

public class UnitTest {
    private static final String TROLL = "model.creature.humanoid.giant.troll";
    private static final String GLOVE = "model.item.glove";
    private static final float[] RAGING = {0.9f, 0.1f, 0.1f};
    private static final float[] AGGRO = {0.5f, 0.0f, 0.0f};
    private static final float[] MOB = {0.0f, 0.5f, 1.0f};
    private static final float[] SPOTTED = {1.0f, 0.4f, 0.0f};

    private boolean[] toggles;

    @Before
    public void setUp() {
        toggles = new boolean[]{WurmEspMod.players, WurmEspMod.mobs, WurmEspMod.animals, WurmEspMod.specials,
                WurmEspMod.items, WurmEspMod.uniques, WurmEspMod.conditioned};
        Unit.aggroMOBS = new String[]{"troll"};
        Unit.uniqueMOBS = new String[]{"unique"};
        Unit.specialITEMS = new String[]{"treasure"};
        Unit.spottedITEMS = new String[]{"mushroom"};
        Unit.conditionedMOBS = new String[]{"raging", "champion"};
        Unit.colorRaging = RAGING;
        Unit.colorMobsAggro = AGGRO;
        Unit.colorMobs = MOB;
        Unit.colorSpotted = SPOTTED;
        WurmEspMod.search = "defaultnosearch";
        WurmEspMod.searchType = WurmEspMod.SEARCHTYPE.NONE;
        WurmEspMod.players = false;
        WurmEspMod.mobs = true;
        WurmEspMod.animals = true;
        WurmEspMod.specials = true;
        WurmEspMod.items = true;
        WurmEspMod.uniques = true;
        WurmEspMod.conditioned = true;
        WurmEspMod.clearTracked();
        WurmEspMod.invalidate();
    }

    @After
    public void tearDown() {
        WurmEspMod.players = toggles[0];
        WurmEspMod.mobs = toggles[1];
        WurmEspMod.animals = toggles[2];
        WurmEspMod.specials = toggles[3];
        WurmEspMod.items = toggles[4];
        WurmEspMod.uniques = toggles[5];
        WurmEspMod.conditioned = toggles[6];
        WurmEspMod.clearTracked();
    }

    /** A pickable whose hover name can change after the unit was made (the server updates it live). */
    private static PickableUnit pickable(long id, AtomicReference<String> hover) {
        return (PickableUnit) Proxy.newProxyInstance(UnitTest.class.getClassLoader(), new Class<?>[]{PickableUnit.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getHoverName": return hover.get();
                        case "getId": return id;
                        default: return null;
                    }
                });
    }

    private static Unit unit(long id, String model, AtomicReference<String> hover) {
        return new Unit(null, id, pickable(id, hover), model, hover.get());
    }

    @Test
    public void conditionAppearingAfterSpawnIsPickedUp() {
        AtomicReference<String> hover = new AtomicReference<>("troll");
        Unit u = unit(1, TROLL, hover);
        assertFalse(u.isConditioned());
        assertArrayEquals(AGGRO, u.outlineColor(true), 0f);

        hover.set("raging troll");

        assertTrue("the unit must read the hover name as it is now", u.isConditioned());
        assertArrayEquals(RAGING, u.outlineColor(true), 0f);
        assertArrayEquals("the base colour stays when conditions are not shown", AGGRO, u.outlineColor(false), 0f);
    }

    @Test
    public void conditionGoingAwayIsDropped() {
        AtomicReference<String> hover = new AtomicReference<>("raging troll");
        Unit u = unit(1, TROLL, hover);
        assertTrue(u.isConditioned());

        hover.set("troll");

        assertFalse(u.isConditioned());
    }

    @Test
    public void colourReloadReachesUnitsThatAlreadyExist() {
        Unit u = unit(1, TROLL, new AtomicReference<>("troll"));
        assertArrayEquals(AGGRO, u.outlineColor(false), 0f);

        float[] reloaded = {0.2f, 0.3f, 0.4f};
        Unit.colorMobsAggro = reloaded;   // ConfigUtils.DoConfig replaces the array on "esp reload"
        WurmEspMod.invalidate();

        assertArrayEquals(reloaded, u.outlineColor(false), 0f);
    }

    @Test
    public void searchSetAfterSpawnFindsItemsThatAlreadyExist() {
        Unit glove = unit(7, GLOVE, new AtomicReference<>("leather glove"));
        WurmEspMod.track(glove);
        assertFalse("not interesting yet", WurmEspMod.shouldRender(glove));

        WurmEspMod.search = "glove";
        WurmEspMod.searchType = WurmEspMod.SEARCHTYPE.HOVER;
        WurmEspMod.invalidate();

        assertTrue("an item that was already there must light up", WurmEspMod.shouldRender(glove));
        assertTrue(WurmEspMod.trackedUnits().contains(glove));
    }

    @Test
    public void hoverSearchDoesNotMatchTheModelName() {
        Unit u = unit(8, GLOVE, new AtomicReference<>("mitten"));
        WurmEspMod.search = "glove";
        WurmEspMod.searchType = WurmEspMod.SEARCHTYPE.HOVER;
        WurmEspMod.invalidate();

        assertFalse(u.isSpotted());

        WurmEspMod.searchType = WurmEspMod.SEARCHTYPE.MODEL;
        WurmEspMod.invalidate();
        assertTrue(u.isSpotted());
    }

    @Test
    public void noSearchTypeMeansNoModelMatch() {
        Unit u = unit(9, GLOVE, new AtomicReference<>("mitten"));
        WurmEspMod.search = "glove";   // set, but the type is NONE
        WurmEspMod.invalidate();

        assertFalse(u.isSpotted());
    }

    @Test
    public void trackingTheSameIdTwiceKeepsOneUnit() {
        AtomicReference<String> hover = new AtomicReference<>("troll");
        WurmEspMod.track(unit(3, TROLL, hover));
        Unit again = unit(3, TROLL, hover);
        WurmEspMod.track(again);

        assertEquals(1, WurmEspMod.trackedUnits().size());
        assertTrue(WurmEspMod.trackedUnits().contains(again));

        WurmEspMod.untrack(3);
        assertTrue(WurmEspMod.trackedUnits().isEmpty());
    }

    @Test
    public void unitsComeAndGoWhileTheRendererIteratesThem() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < 20000; i++) {
                    WurmEspMod.track(unit(i % 500, TROLL, new AtomicReference<>("troll")));
                    WurmEspMod.untrack((i * 7) % 500);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        });
        writer.start();
        while (writer.isAlive()) {
            try {
                for (Unit u : WurmEspMod.trackedUnits()) WurmEspMod.shouldRender(u);
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
                break;
            }
        }
        writer.join();
        assertEquals(null, failure.get());
    }
}
