package net.encode.wurmesp;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.Color;
import com.wurmonline.client.renderer.PickRenderer;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.WorldRender;
import com.wurmonline.client.renderer.backend.Primitive;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.backend.RenderState;
import net.encode.wurmesp.util.ReUtils;

public class Unit {
    private static final String[] NONE = new String[0];
    private static final float[] BLACK = new float[]{0.0f, 0.0f, 0.0f};

    private World world;
    private long id;
    private String modelName;
    /** The hover name the unit was made with: used only while the live one cannot be read. */
    private String hoverName;
    private PickableUnit pickableUnit;
    public static float[] colorPlayers = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorPlayersEnemy = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorMobs = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorMobsAggro = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorSpecials = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorSpotted = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorUniques = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorAlert = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorAngry = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorChampion = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorDiseased = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorFierce = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorGreenish = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorHardened = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorLurking = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorRaging = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorScared = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorSlow = new float[]{0.0f, 0.0f, 0.0f};
    public static float[] colorSly = new float[]{0.0f, 0.0f, 0.0f};
    public static String[] aggroMOBS;
    public static String[] uniqueMOBS;
    public static String[] specialITEMS;
    public static String[] spottedITEMS;
    public static String[] conditionedMOBS;


    /** What the unit is, worked out from its names. Replaced as a whole when the hover name
     *  changes (a creature turns raging, a champion...) or the config / search changes
     *  ({@link WurmEspMod#invalidate()}), so it never goes stale and is cheap per frame. */
    private static final class Facts {
        final int generation;
        final String hover;
        boolean player, enemyPlayer, mob, aggro, champion, unique, special, spotted;
        String condition;

        Facts(int generation, String hover) {
            this.generation = generation;
            this.hover = hover;
        }
    }

    private volatile Facts facts;

    public Unit(World world, long id, PickableUnit pickableUnit, String modelName, String hoverName) {
        this.world = world;
        this.id = id;
        this.pickableUnit = pickableUnit;
        this.modelName = modelName == null ? "" : modelName;
        this.hoverName = hoverName == null ? "" : hoverName;
    }

    public long getId() {
        return this.id;
    }

    /** The hover name as it is now (the server changes it live), never null. */
    public String getHoverName() {
        PickableUnit p = this.pickableUnit;
        if (p != null) {
            try {
                String live = p.getHoverName();
                if (live != null) {
                    return live;
                }
            } catch (RuntimeException ignored) {
                // a unit being removed: the name it had is good enough
            }
        }
        return this.hoverName;
    }

    public String getModelName() {
        return this.modelName;
    }

    private Facts facts() {
        String hover = this.getHoverName();
        Facts f = this.facts;
        int gen = WurmEspMod.generation();
        if (f != null && f.generation == gen && f.hover.equals(hover)) {
            return f;
        }
        f = new Facts(gen, hover);
        String model = this.modelName;
        f.player = model.contains("model.creature.humanoid.human.player") && !model.contains("zombie");
        f.enemyPlayer = model.contains("ENEMY");
        f.mob = model.contains("model.creature") && !model.contains("humanoid.human");
        f.aggro = containsAny(hover, Unit.aggroMOBS);
        f.champion = hover.contains("champion");
        for (String condition : orEmpty(Unit.conditionedMOBS)) {
            if (hover.contains(condition)) {
                f.condition = condition;
                break;
            }
        }
        f.unique = containsAny(hover, Unit.uniqueMOBS);
        f.special = containsAny(hover, Unit.specialITEMS);
        f.spotted = containsAny(hover, Unit.spottedITEMS) || matchesSearch(hover, model);
        this.facts = f;
        return f;
    }

    private static boolean matchesSearch(String hover, String model) {
        String search = WurmEspMod.search;
        if (search == null || search.isEmpty()) {
            return false;
        }
        WurmEspMod.SEARCHTYPE type = WurmEspMod.searchType;
        if (type == WurmEspMod.SEARCHTYPE.HOVER || type == WurmEspMod.SEARCHTYPE.BOTH) {
            if (hover.contains(search)) {
                return true;
            }
        }
        if (type == WurmEspMod.SEARCHTYPE.MODEL || type == WurmEspMod.SEARCHTYPE.BOTH) {
            return model.contains(search);
        }
        return false;
    }

    private static String[] orEmpty(String[] list) {
        return list == null ? NONE : list;
    }

    private static boolean containsAny(String text, String[] list) {
        for (String entry : orEmpty(list)) {
            if (text.contains(entry)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPlayer() {
        return this.facts().player;
    }

    public boolean isEnemyPlayer() {
        return this.facts().enemyPlayer;
    }

    public boolean isMob() {
        return this.facts().mob;
    }

    public boolean isAggroMob() {
        return this.facts().aggro;
    }

    public boolean isChampion() {
        return this.facts().champion;
    }

    public boolean isConditioned() {
        return this.facts().condition != null;
    }

    public boolean isUnique() {
        return this.facts().unique;
    }

    public boolean isSpecial() {
        return this.facts().special;
    }

    public boolean isSpotted() {
        return this.facts().spotted;
    }

    /** Whether ESP can ever outline this: a player, a mob, or a special / spotted thing. */
    public boolean isRelevant() {
        Facts f = this.facts();
        return f.player || f.mob || f.special || f.spotted;
    }

    /** The outline colour right now: the condition's (when asked and the unit has one), else its kind's. */
    public float[] outlineColor(boolean showConditioned) {
        Facts f = this.facts();
        if (showConditioned && f.condition != null) {
            return conditionColor(f.condition);
        }
        if (f.player) {
            return f.enemyPlayer ? colorPlayersEnemy : colorPlayers;
        }
        if (f.unique) {
            return colorUniques;
        }
        if (f.aggro) {
            return colorMobsAggro;
        }
        if (f.mob) {
            return colorMobs;
        }
        if (f.special) {
            return colorSpecials;
        }
        if (f.spotted) {
            return colorSpotted;
        }
        return BLACK;
    }

    private static float[] conditionColor(String condition) {
        switch (condition) {
            case "alert": return colorAlert;
            case "angry": return colorAngry;
            case "champion": return colorChampion;
            case "diseased": return colorDiseased;
            case "fierce": return colorFierce;
            case "greenish": return colorGreenish;
            case "hardened": return colorHardened;
            case "lurking": return colorLurking;
            case "raging": return colorRaging;
            case "scared": return colorScared;
            case "slow": return colorSlow;
            case "sly": return colorSly;
            default: return BLACK;
        }
    }

    public void renderUnit(Queue queue, boolean showconditioned) {
        if (this.pickableUnit == null) {
            return;
        }
        float br = 3.5f;
        RenderState renderStateFill = new RenderState();
        RenderState renderStateFillDepth = new RenderState();
        RenderState renderStateOutline = new RenderState();
        Color color = new Color();
        float[] rgb = this.outlineColor(showconditioned);
        color.set(rgb[0], rgb[1], rgb[2]);
        renderStateFill.alphaval = color.a = br;
        color.a *= this.pickableUnit.getOutlineColor().a;
        
        WorldRender worldRenderer = (WorldRender)ReUtils.getField(this.world, "worldRenderer");
        PickRenderer.CustomPickFillRender customPickFill = (PickRenderer.CustomPickFillRender)ReUtils.getField(worldRenderer, "customPickFill");
        PickRenderer.CustomPickFillDepthRender customPickFillDepth = (PickRenderer.CustomPickFillDepthRender)ReUtils.getField(worldRenderer, "customPickFillDepth");
        PickRenderer.CustomPickOutlineRender customPickOutline = (PickRenderer.CustomPickOutlineRender)ReUtils.getField(worldRenderer, "customPickOutline");
        renderStateFill.twosided = false;
        renderStateFill.depthtest = Primitive.TestFunc.ALWAYS;
        renderStateFill.depthwrite = true;
        renderStateFill.customstate = customPickFill;
        this.pickableUnit.renderPicked(queue, renderStateFill, color);
        renderStateOutline.alphaval = color.a = br * 0.25f;
        color.a *= this.pickableUnit.getOutlineColor().a;
        renderStateOutline.twosided = false;
        renderStateOutline.depthtest = Primitive.TestFunc.LESS;
        renderStateOutline.depthwrite = false;
        renderStateOutline.blendmode = Primitive.BlendMode.ALPHABLEND;
        renderStateOutline.customstate = customPickOutline;
        this.pickableUnit.renderPicked(queue, renderStateOutline, color);
        renderStateFillDepth.customstate = customPickFillDepth;
        renderStateFillDepth.depthtest = Primitive.TestFunc.ALWAYS;
        this.pickableUnit.renderPicked(queue, renderStateFillDepth, color);
    }
}

