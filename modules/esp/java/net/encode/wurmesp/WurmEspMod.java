package net.encode.wurmesp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import com.wurmonline.client.game.CaveDataBuffer;
import com.wurmonline.client.game.NearTerrainDataBuffer;
import com.wurmonline.client.renderer.PickRenderer;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;

import net.encode.wurmesp.feature.FeatureFlowerTiles;
import net.encode.wurmesp.feature.FeatureTilesCloseBy;
import net.encode.wurmesp.feature.FeatureTilesHighlight;
import net.encode.wurmesp.feature.FeatureTilesWalkable;
import net.encode.wurmesp.feature.FeatureXRay;
import net.encode.wurmesp.feature.hook.CmdShowDeedPlan;
import net.encode.wurmesp.feature.hook.GroundItemCellRenderableInit;
import net.encode.wurmesp.feature.hook.GroundItemCellRenderableRemove;
import net.encode.wurmesp.feature.hook.HandleDevInput;
import net.encode.wurmesp.feature.hook.HeadsUpDisplayInit;
import net.encode.wurmesp.feature.hook.MobileModelRenderableInit;
import net.encode.wurmesp.feature.hook.MobileModelRenderableRemove;
import net.encode.wurmesp.feature.hook.ProjectileCellRenderable;
import net.encode.wurmesp.feature.hook.RenderPickedItem;
import net.encode.wurmesp.util.ConfigUtils;
import net.encode.wurmesp.util.CronoManager;

public class WurmEspMod
implements WurmClientMod,
Initable,
Configurable {
    public static HeadsUpDisplay hud;
    public static Logger logger;
    public static Properties modProperties;
    /** Every creature and ground item the client has shown, by id. Filled and emptied by the
     *  renderable hooks (game threads) while the renderer iterates it, so it is concurrent;
     *  what is outlined is decided every frame ({@link #shouldRender}), not when a unit appears. */
    private static final ConcurrentHashMap<Long, Unit> trackedUnits = new ConcurrentHashMap<Long, Unit>();
    private static final long RELEVANT_RECHECK_MS = 1000L;
    /** Snapshot of the relevant tracked units, replaced whole ({@link #relevantUnits()}). */
    private static volatile List<Unit> relevantUnits = Collections.emptyList();
    private static volatile boolean relevantDirty = true;
    private static volatile long relevantBuiltAt;
    /** Bumped when the config or the search changes: units read their names again. */
    private static volatile int generation;
    public static CronoManager xrayCronoManager;
    public static CronoManager tilesFlowerCronoManager;
    public static CronoManager tilesCloseByCronoManager;
    public static CronoManager tilesHighlightCronoManager;
    public static CronoManager tilesCloseByWalkableCronoManager;
    public static FeatureXRay xrayManager;
    public static FeatureFlowerTiles tilesFlowerManager;
    public static FeatureTilesCloseBy tilesCloseByManager;
    public static FeatureTilesHighlight tilesHighlightManager;
    public static FeatureTilesWalkable tilesCloseByWalkableManager;
    public static CaveDataBuffer _caveBuffer;
    public static NearTerrainDataBuffer _terrainBuffer;
    public static NearTerrainDataBuffer _terrainBuffer2;
    public static volatile CopyOnWriteArrayList<float[]> _terrain;
    public static CopyOnWriteArrayList<float[]> _flowerTerrain;
    public static CopyOnWriteArrayList<float[]> _closeByTerrain;
    public static CopyOnWriteArrayList<float[]> _closeByWalkableTerrain;
    public static CopyOnWriteArrayList<int[]> _tilesHighlightBase;
    public static CopyOnWriteArrayList<float[]> _tilesHighlightTerrain;
    public static String search;
    public static SEARCHTYPE searchType;
    public static boolean players;
    public static boolean mobs;
    public static boolean animals;
    public static boolean specials;
    public static boolean items;
    public static boolean tilesFlower;
    public static boolean uniques;
    public static boolean conditioned;
    public static boolean tilescloseby;
    public static boolean deedsize;
    public static boolean tileshighlight;
    public static boolean tilesclosebynotrideable;
    public static boolean xray;
    public static boolean xraythread;
    public static boolean xrayrefreshthread;
    public static int xraydiameter;
    public static int xrayrefreshrate;
    public static int tilenotrideable;
    public static int flowerdiameter;
    public static boolean playsoundspecial;
    public static boolean playsounditem;
    public static boolean playsoundunique;
    public static String soundspecial;
    public static String sounditem;
    public static String soundunique;
    public static boolean conditionedcolorsallways;
    public static boolean championmcoloralways;
    public static String[] tilesFlowerSearch;
    public static PickRenderer _pickRenderer;

    public void init() {
        logger.log(Level.INFO, "[WurmEspMod] Initializing");
        xrayManager = new FeatureXRay();
        tilesFlowerManager = new FeatureFlowerTiles();
        tilesCloseByManager = new FeatureTilesCloseBy();
        tilesHighlightManager = new FeatureTilesHighlight();
        tilesCloseByWalkableManager = new FeatureTilesWalkable();
        xrayCronoManager = new CronoManager(xrayrefreshrate * 1000);
        tilesFlowerCronoManager = new CronoManager(1000L);
        tilesCloseByCronoManager = new CronoManager(1000L);
        tilesHighlightCronoManager = new CronoManager(1000L);
        tilesCloseByWalkableCronoManager = new CronoManager(1000L);
        new HandleDevInput();
        new ProjectileCellRenderable();
        new HeadsUpDisplayInit();
        new RenderPickedItem();
        new MobileModelRenderableInit();
        new MobileModelRenderableRemove();
        new GroundItemCellRenderableInit();
        new GroundItemCellRenderableRemove();
        new CmdShowDeedPlan();
    }

    public static int generation() {
        return generation;
    }

    /** The config, the lists or the search changed: every unit works out what it is again. */
    public static void invalidate() {
        generation++;
        relevantDirty = true;
    }

    public static void track(Unit unit) {
        trackedUnits.put(unit.getId(), unit);
        relevantDirty = true;
    }

    public static void untrack(long id) {
        trackedUnits.remove(id);
        relevantDirty = true;
    }

    public static Collection<Unit> trackedUnits() {
        return trackedUnits.values();
    }

    /** The tracked units ESP could outline at all ({@link Unit#isRelevant()}), so the renderer does
     *  not walk every ground item each frame. Rebuilt on the next call after a unit comes or goes or
     *  {@link #invalidate()}, and every second anyway for hover names that changed meanwhile. */
    public static List<Unit> relevantUnits() {
        long now = System.currentTimeMillis();
        if (relevantDirty || now - relevantBuiltAt >= RELEVANT_RECHECK_MS) {
            // cleared first: a unit tracked while this runs marks it again
            relevantDirty = false;
            relevantBuiltAt = now;
            ArrayList<Unit> relevant = new ArrayList<Unit>();
            for (Unit unit : trackedUnits.values()) {
                if (unit.isRelevant()) {
                    relevant.add(unit);
                }
            }
            relevantUnits = relevant;
        }
        return relevantUnits;
    }

    public static void clearTracked() {
        trackedUnits.clear();
        relevantDirty = true;
    }

    /** Whether any toggle that outlines units is on; when none is, the unit pass is skipped. */
    public static boolean anyUnitToggle() {
        return players || uniques || conditioned || animals || mobs || specials || items;
    }

    /** Whether the unit is outlined now, by the toggles and search as they are now. */
    public static boolean shouldRender(Unit unit) {
        return unit.shouldRender();
    }

    public void configure(Properties properties) {
        ConfigUtils.DoConfig(properties);
        logger.log(Level.INFO, "[WurmEspMod] Config loaded");
    }

    static {
        logger = Logger.getLogger("WurmEspMod");
        modProperties = new Properties();
        _caveBuffer = null;
        _terrainBuffer = null;
        _terrainBuffer2 = null;
        _terrain = new CopyOnWriteArrayList<float[]>();
        _flowerTerrain = new CopyOnWriteArrayList<float[]>();
        _closeByTerrain = new CopyOnWriteArrayList<float[]>();
        _closeByWalkableTerrain = new CopyOnWriteArrayList<float[]>();
        _tilesHighlightBase = new CopyOnWriteArrayList<int[]>();
        _tilesHighlightTerrain = new CopyOnWriteArrayList<float[]>();
        search = "defaultnosearch";
        searchType = SEARCHTYPE.NONE;
        players = true;
        mobs = false;
        animals = false;
        specials = true;
        items = true;
        tilesFlower = false;
        uniques = true;
        conditioned = true;
        tilescloseby = false;
        deedsize = false;
        tileshighlight = false;
        tilesclosebynotrideable = false;
        xray = false;
        xraythread = true;
        xrayrefreshthread = true;
        xraydiameter = 32;
        xrayrefreshrate = 5;
        tilenotrideable = 40;
        flowerdiameter = 64;
        playsoundspecial = true;
        playsounditem = true;
        playsoundunique = true;
        soundspecial = "sound.fx.conch";
        sounditem = "sound.fx.conch";
        soundunique = "sound.fx.conch";
        conditionedcolorsallways = false;
        championmcoloralways = false;
    }

    public static enum SEARCHTYPE {
        NONE,
        HOVER,
        MODEL,
        BOTH;

    }
}

