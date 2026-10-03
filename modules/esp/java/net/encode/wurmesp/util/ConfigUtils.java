package net.encode.wurmesp.util;

import com.wurmonline.mesh.Tiles;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import net.encode.wurmesp.Unit;
import net.encode.wurmesp.WurmEspMod;

public class ConfigUtils {
    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void loadProperties(String name) {
        WurmEspMod.modProperties.clear();
        InputStream inputStream = null;
        Path path = Paths.get("mods", "WurmHelper"); // merged into WurmHelper: config lives in mods/WurmHelper
        path = path.resolve(name + ".properties");
        try {
            inputStream = Files.newInputStream(path, new OpenOption[0]);
            WurmEspMod.modProperties.load(inputStream);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void DoConfig(Properties properties) {
        WurmEspMod.players = Boolean.valueOf(properties.getProperty("players", Boolean.toString(WurmEspMod.players)));
        WurmEspMod.mobs = Boolean.valueOf(properties.getProperty("mobs", Boolean.toString(WurmEspMod.mobs)));
        WurmEspMod.animals = Boolean.valueOf(properties.getProperty("animals", Boolean.toString(WurmEspMod.animals)));
        WurmEspMod.specials = Boolean.valueOf(properties.getProperty("specials", Boolean.toString(WurmEspMod.specials)));
        WurmEspMod.items = Boolean.valueOf(properties.getProperty("items", Boolean.toString(WurmEspMod.items)));
        WurmEspMod.uniques = Boolean.valueOf(properties.getProperty("uniques", Boolean.toString(WurmEspMod.uniques)));
        WurmEspMod.conditioned = Boolean.valueOf(properties.getProperty("conditioned", Boolean.toString(WurmEspMod.conditioned)));
        WurmEspMod.tilescloseby = Boolean.valueOf(properties.getProperty("tilescloseby", Boolean.toString(WurmEspMod.tilescloseby)));
        WurmEspMod.deedsize = Boolean.valueOf(properties.getProperty("deedsize", Boolean.toString(WurmEspMod.deedsize)));
        WurmEspMod.xray = Boolean.valueOf(properties.getProperty("xray", Boolean.toString(WurmEspMod.xray)));
        WurmEspMod.xraythread = Boolean.valueOf(properties.getProperty("xraythread", Boolean.toString(WurmEspMod.xraythread)));
        WurmEspMod.xrayrefreshthread = Boolean.valueOf(properties.getProperty("xrayrefreshthread", Boolean.toString(WurmEspMod.xrayrefreshthread)));
        WurmEspMod.xraydiameter = Integer.parseInt(properties.getProperty("xraydiameter", Integer.toString(WurmEspMod.xraydiameter)));
        WurmEspMod.xrayrefreshrate = Integer.parseInt(properties.getProperty("xrayrefreshrate", Integer.toString(WurmEspMod.xrayrefreshrate)));
        WurmEspMod.tilenotrideable = Integer.parseInt(properties.getProperty("tilenotrideable", Integer.toString(WurmEspMod.tilenotrideable)));
        WurmEspMod.flowerdiameter = Integer.parseInt(properties.getProperty("flowerdiameter", Integer.toString(WurmEspMod.flowerdiameter)));
        WurmEspMod.playsoundspecial = Boolean.valueOf(properties.getProperty("playsoundspecial", Boolean.toString(WurmEspMod.playsoundspecial)));
        WurmEspMod.playsounditem = Boolean.valueOf(properties.getProperty("playsounditem", Boolean.toString(WurmEspMod.playsounditem)));
        WurmEspMod.playsoundunique = Boolean.valueOf(properties.getProperty("playsoundunique", Boolean.toString(WurmEspMod.playsoundunique)));
        WurmEspMod.soundspecial = properties.getProperty("soundspecial", WurmEspMod.soundspecial);
        WurmEspMod.sounditem = properties.getProperty("sounditem", WurmEspMod.sounditem);
        WurmEspMod.soundunique = properties.getProperty("soundunique", WurmEspMod.soundunique);
        WurmEspMod.conditionedcolorsallways = Boolean.valueOf(properties.getProperty("conditionedcolorsallways", Boolean.toString(WurmEspMod.conditionedcolorsallways)));
        WurmEspMod.championmcoloralways = Boolean.valueOf(properties.getProperty("championmcoloralways", Boolean.toString(WurmEspMod.championmcoloralways)));
        Unit.colorPlayers = ConfigUtils.colorStringToFloatA(properties.getProperty("colorPlayers", ConfigUtils.colorFloatAToString(Unit.colorPlayers)));
        Unit.colorPlayersEnemy = ConfigUtils.colorStringToFloatA(properties.getProperty("colorPlayersEnemy", ConfigUtils.colorFloatAToString(Unit.colorPlayersEnemy)));
        Unit.colorMobs = ConfigUtils.colorStringToFloatA(properties.getProperty("colorMobs", ConfigUtils.colorFloatAToString(Unit.colorMobs)));
        Unit.colorMobsAggro = ConfigUtils.colorStringToFloatA(properties.getProperty("colorMobsAggro", ConfigUtils.colorFloatAToString(Unit.colorMobsAggro)));
        Unit.colorSpecials = ConfigUtils.colorStringToFloatA(properties.getProperty("colorSpecials", ConfigUtils.colorFloatAToString(Unit.colorSpecials)));
        Unit.colorSpotted = ConfigUtils.colorStringToFloatA(properties.getProperty("colorSpotted", ConfigUtils.colorFloatAToString(Unit.colorSpotted)));
        Unit.colorUniques = ConfigUtils.colorStringToFloatA(properties.getProperty("colorUniques", ConfigUtils.colorFloatAToString(Unit.colorUniques)));
        Unit.colorAlert = ConfigUtils.colorStringToFloatA(properties.getProperty("colorAlert", ConfigUtils.colorFloatAToString(Unit.colorAlert)));
        Unit.colorAngry = ConfigUtils.colorStringToFloatA(properties.getProperty("colorAngry", ConfigUtils.colorFloatAToString(Unit.colorAngry)));
        Unit.colorChampion = ConfigUtils.colorStringToFloatA(properties.getProperty("colorChampion", ConfigUtils.colorFloatAToString(Unit.colorChampion)));
        Unit.colorDiseased = ConfigUtils.colorStringToFloatA(properties.getProperty("colorDiseased", ConfigUtils.colorFloatAToString(Unit.colorDiseased)));
        Unit.colorFierce = ConfigUtils.colorStringToFloatA(properties.getProperty("colorFierce", ConfigUtils.colorFloatAToString(Unit.colorFierce)));
        Unit.colorGreenish = ConfigUtils.colorStringToFloatA(properties.getProperty("colorGreenish", ConfigUtils.colorFloatAToString(Unit.colorGreenish)));
        Unit.colorHardened = ConfigUtils.colorStringToFloatA(properties.getProperty("colorHardened", ConfigUtils.colorFloatAToString(Unit.colorHardened)));
        Unit.colorLurking = ConfigUtils.colorStringToFloatA(properties.getProperty("colorLurking", ConfigUtils.colorFloatAToString(Unit.colorLurking)));
        Unit.colorRaging = ConfigUtils.colorStringToFloatA(properties.getProperty("colorRaging", ConfigUtils.colorFloatAToString(Unit.colorRaging)));
        Unit.colorScared = ConfigUtils.colorStringToFloatA(properties.getProperty("colorScared", ConfigUtils.colorFloatAToString(Unit.colorScared)));
        Unit.colorSlow = ConfigUtils.colorStringToFloatA(properties.getProperty("colorSlow", ConfigUtils.colorFloatAToString(Unit.colorSlow)));
        Unit.colorSly = ConfigUtils.colorStringToFloatA(properties.getProperty("colorSly", ConfigUtils.colorFloatAToString(Unit.colorSly)));
        String oreColorOreIron = properties.getProperty("oreColorOreIron", "default");
        if (!oreColorOreIron.equals("default")) {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_IRON, ConfigUtils.colorStringToFloatA(oreColorOreIron));
        } else {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_IRON, Color.RED.darker());
        }
        String oreColorOreCopper = properties.getProperty("oreColorOreCopper", "default");
        if (!oreColorOreCopper.equals("default")) {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_COPPER, ConfigUtils.colorStringToFloatA(oreColorOreCopper));
        } else {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_COPPER, Color.GREEN);
        }
        String oreColorOreTin = properties.getProperty("oreColorOreTin", "default");
        if (!oreColorOreTin.equals("default")) {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_TIN, ConfigUtils.colorStringToFloatA(oreColorOreTin));
        } else {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_TIN, Color.GRAY);
        }
        String oreColorOreGold = properties.getProperty("oreColorOreGold", "default");
        if (!oreColorOreGold.equals("default")) {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_GOLD, ConfigUtils.colorStringToFloatA(oreColorOreGold));
        } else {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_GOLD, Color.YELLOW.darker());
        }
        String oreColorOreAdamantine = properties.getProperty("oreColorOreAdamantine", "default");
        if (!oreColorOreAdamantine.equals("default")) {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_ADAMANTINE, ConfigUtils.colorStringToFloatA(oreColorOreAdamantine));
        } else {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_ADAMANTINE, Color.CYAN);
        }
        String oreColorOreGlimmersteel = properties.getProperty("oreColorOreGlimmersteel", "default");
        if (!oreColorOreGlimmersteel.equals("default")) {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_GLIMMERSTEEL, ConfigUtils.colorStringToFloatA(oreColorOreGlimmersteel));
        } else {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_GLIMMERSTEEL, Color.YELLOW.brighter());
        }
        String oreColorOreSilver = properties.getProperty("oreColorOreSilver", "default");
        if (!oreColorOreSilver.equals("default")) {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_SILVER, ConfigUtils.colorStringToFloatA(oreColorOreSilver));
        } else {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_SILVER, Color.LIGHT_GRAY);
        }
        String oreColorOreLead = properties.getProperty("oreColorOreLead", "default");
        if (!oreColorOreLead.equals("default")) {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_LEAD, ConfigUtils.colorStringToFloatA(oreColorOreLead));
        } else {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_LEAD, Color.PINK.darker().darker());
        }
        String oreColorOreZinc = properties.getProperty("oreColorOreZinc", "default");
        if (!oreColorOreZinc.equals("default")) {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_ZINC, ConfigUtils.colorStringToFloatA(oreColorOreZinc));
        } else {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ORE_ZINC, new Color(235, 235, 235));
        }
        String oreColorSlate = properties.getProperty("oreColorSlate", "default");
        if (!oreColorSlate.equals("default")) {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_SLATE, ConfigUtils.colorStringToFloatA(oreColorSlate));
        } else {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_SLATE, Color.BLACK);
        }
        String oreColorMarble = properties.getProperty("oreColorMarble", "default");
        if (!oreColorMarble.equals("default")) {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_MARBLE, ConfigUtils.colorStringToFloatA(oreColorMarble));
        } else {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_MARBLE, Color.WHITE);
        }
        String oreColorSandstone = properties.getProperty("oreColorSandstone", "default");
        if (!oreColorSandstone.equals("default")) {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_SANDSTONE, ConfigUtils.colorStringToFloatA(oreColorSandstone));
        } else {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_SANDSTONE, Color.YELLOW.darker().darker());
        }
        String oreColorRocksalt = properties.getProperty("oreColorRocksalt", "default");
        if (!oreColorRocksalt.equals("default")) {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ROCKSALT, ConfigUtils.colorStringToFloatA(oreColorRocksalt));
        } else {
            XrayColors.addMapping(Tiles.Tile.TILE_CAVE_WALL_ROCKSALT, Color.WHITE.darker());
        }
        Unit.aggroMOBS = splitList(properties.getProperty("aggroMOBS"),
                "anaconda;black bear;black wolf;brown bear;cave bug;crab;crocodile;fog Spider;goblin;hell hound;hell scorpious;huge spider;large rat;lava fiend;mountain lion;rabid hyena;scorpion;sea serpent;seal;shark;troll;wild cat;lava spider");
        Unit.uniqueMOBS = splitList(properties.getProperty("uniqueMOBS"),
                "forest giant;goblin leader;kyklops;troll king; dragon");
        Unit.specialITEMS = splitList(properties.getProperty("specialITEMS"), "treasure");
        Unit.spottedITEMS = splitList(properties.getProperty("spottedITEMS"),
                "source spring;source fountain;mushroom");
        Unit.conditionedMOBS = splitList(properties.getProperty("conditionedMOBS"),
                "alert;angry;champion;diseased;fierce;greenish;hardened;lurking;raging;scared;slow;sly");
        WurmEspMod.tilesFlowerSearch = splitList(properties.getProperty("tilesFlowerSearch"), "flowers");
    }

    public static void saveProperties(String name) {
        Properties p = new Properties();
        p.setProperty("players", Boolean.toString(WurmEspMod.players));
        p.setProperty("mobs", Boolean.toString(WurmEspMod.mobs));
        p.setProperty("animals", Boolean.toString(WurmEspMod.animals));
        p.setProperty("specials", Boolean.toString(WurmEspMod.specials));
        p.setProperty("items", Boolean.toString(WurmEspMod.items));
        p.setProperty("uniques", Boolean.toString(WurmEspMod.uniques));
        p.setProperty("conditioned", Boolean.toString(WurmEspMod.conditioned));
        p.setProperty("tilescloseby", Boolean.toString(WurmEspMod.tilescloseby));
        p.setProperty("deedsize", Boolean.toString(WurmEspMod.deedsize));
        p.setProperty("xray", Boolean.toString(WurmEspMod.xray));
        p.setProperty("xraythread", Boolean.toString(WurmEspMod.xraythread));
        p.setProperty("xrayrefreshthread", Boolean.toString(WurmEspMod.xrayrefreshthread));
        p.setProperty("xraydiameter", Integer.toString(WurmEspMod.xraydiameter));
        p.setProperty("xrayrefreshrate", Integer.toString(WurmEspMod.xrayrefreshrate));
        p.setProperty("tilenotrideable", Integer.toString(WurmEspMod.tilenotrideable));
        p.setProperty("flowerdiameter", Integer.toString(WurmEspMod.flowerdiameter));
        p.setProperty("playsoundspecial", Boolean.toString(WurmEspMod.playsoundspecial));
        p.setProperty("playsounditem", Boolean.toString(WurmEspMod.playsounditem));
        p.setProperty("playsoundunique", Boolean.toString(WurmEspMod.playsoundunique));
        p.setProperty("soundspecial", WurmEspMod.soundspecial);
        p.setProperty("sounditem", WurmEspMod.sounditem);
        p.setProperty("soundunique", WurmEspMod.soundunique);
        p.setProperty("conditionedcolorsallways", Boolean.toString(WurmEspMod.conditionedcolorsallways));
        p.setProperty("championmcoloralways", Boolean.toString(WurmEspMod.championmcoloralways));
        p.setProperty("colorPlayers", ConfigUtils.colorFloatAToString(Unit.colorPlayers));
        p.setProperty("colorPlayersEnemy", ConfigUtils.colorFloatAToString(Unit.colorPlayersEnemy));
        p.setProperty("colorMobs", ConfigUtils.colorFloatAToString(Unit.colorMobs));
        p.setProperty("colorMobsAggro", ConfigUtils.colorFloatAToString(Unit.colorMobsAggro));
        p.setProperty("colorSpecials", ConfigUtils.colorFloatAToString(Unit.colorSpecials));
        p.setProperty("colorSpotted", ConfigUtils.colorFloatAToString(Unit.colorSpotted));
        p.setProperty("colorUniques", ConfigUtils.colorFloatAToString(Unit.colorUniques));
        p.setProperty("colorAlert", ConfigUtils.colorFloatAToString(Unit.colorAlert));
        p.setProperty("colorAngry", ConfigUtils.colorFloatAToString(Unit.colorAngry));
        p.setProperty("colorChampion", ConfigUtils.colorFloatAToString(Unit.colorChampion));
        p.setProperty("colorDiseased", ConfigUtils.colorFloatAToString(Unit.colorDiseased));
        p.setProperty("colorFierce", ConfigUtils.colorFloatAToString(Unit.colorFierce));
        p.setProperty("colorGreenish", ConfigUtils.colorFloatAToString(Unit.colorGreenish));
        p.setProperty("colorHardened", ConfigUtils.colorFloatAToString(Unit.colorHardened));
        p.setProperty("colorLurking", ConfigUtils.colorFloatAToString(Unit.colorLurking));
        p.setProperty("colorRaging", ConfigUtils.colorFloatAToString(Unit.colorRaging));
        p.setProperty("colorScared", ConfigUtils.colorFloatAToString(Unit.colorScared));
        p.setProperty("colorSlow", ConfigUtils.colorFloatAToString(Unit.colorSlow));
        p.setProperty("colorSly", ConfigUtils.colorFloatAToString(Unit.colorSly));
        p.setProperty("oreColorOreIron", ConfigUtils.colorToString(XrayColors.getColorFor(Tiles.Tile.TILE_CAVE_WALL_ORE_IRON)));
        p.setProperty("oreColorOreCopper", ConfigUtils.colorToString(XrayColors.getColorFor(Tiles.Tile.TILE_CAVE_WALL_ORE_COPPER)));
        p.setProperty("oreColorOreTin", ConfigUtils.colorToString(XrayColors.getColorFor(Tiles.Tile.TILE_CAVE_WALL_ORE_TIN)));
        p.setProperty("oreColorOreGold", ConfigUtils.colorToString(XrayColors.getColorFor(Tiles.Tile.TILE_CAVE_WALL_ORE_GOLD)));
        p.setProperty("oreColorOreAdamantine", ConfigUtils.colorToString(XrayColors.getColorFor(Tiles.Tile.TILE_CAVE_WALL_ORE_ADAMANTINE)));
        p.setProperty("oreColorOreGlimmersteel", ConfigUtils.colorToString(XrayColors.getColorFor(Tiles.Tile.TILE_CAVE_WALL_ORE_GLIMMERSTEEL)));
        p.setProperty("oreColorOreSilver", ConfigUtils.colorToString(XrayColors.getColorFor(Tiles.Tile.TILE_CAVE_WALL_ORE_SILVER)));
        p.setProperty("oreColorOreLead", ConfigUtils.colorToString(XrayColors.getColorFor(Tiles.Tile.TILE_CAVE_WALL_ORE_LEAD)));
        p.setProperty("oreColorOreZinc", ConfigUtils.colorToString(XrayColors.getColorFor(Tiles.Tile.TILE_CAVE_WALL_ORE_ZINC)));
        p.setProperty("oreColorSlate", ConfigUtils.colorToString(XrayColors.getColorFor(Tiles.Tile.TILE_CAVE_WALL_SLATE)));
        p.setProperty("oreColorMarble", ConfigUtils.colorToString(XrayColors.getColorFor(Tiles.Tile.TILE_CAVE_WALL_MARBLE)));
        p.setProperty("oreColorSandstone", ConfigUtils.colorToString(XrayColors.getColorFor(Tiles.Tile.TILE_CAVE_WALL_SANDSTONE)));
        p.setProperty("oreColorRocksalt", ConfigUtils.colorToString(XrayColors.getColorFor(Tiles.Tile.TILE_CAVE_WALL_ROCKSALT)));
        p.setProperty("aggroMOBS", String.join(";", Unit.aggroMOBS));
        p.setProperty("uniqueMOBS", String.join(";", Unit.uniqueMOBS));
        p.setProperty("specialITEMS", String.join(";", Unit.specialITEMS));
        p.setProperty("spottedITEMS", String.join(";", Unit.spottedITEMS));
        p.setProperty("conditionedMOBS", String.join(";", Unit.conditionedMOBS));
        p.setProperty("tilesFlowerSearch", String.join(";", WurmEspMod.tilesFlowerSearch));
        Path path = Paths.get("mods", "WurmHelper").resolve(name + ".properties");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                p.store(out, "WurmEsp settings; rewritten when a setting is changed with the esp console command");
            }
        }
        catch (IOException e) {
            WurmEspMod.logger.log(Level.WARNING, "[WurmEspMod] Couldn't save " + path, e);
        }
    }

    private static String[] splitList(String value, String defaultValue) {
        return (value != null ? value : defaultValue).split(";");
    }

    private static float[] colorStringToFloatA(String color) {
        String[] colors = color.split(",");
        float[] colorf = new float[]{Float.valueOf(colors[0]).floatValue() / 255.0f, Float.valueOf(colors[1]).floatValue() / 255.0f, Float.valueOf(colors[2]).floatValue() / 255.0f};
        return colorf;
    }

    private static String colorFloatAToString(float[] color) {
        String colors = String.valueOf(Math.round(color[0] * 255.0f)) + "," + String.valueOf(Math.round(color[1] * 255.0f)) + "," + String.valueOf(Math.round(color[2] * 255.0f));
        return colors;
    }

    private static String colorToString(Color color) {
        return color.getRed() + "," + color.getGreen() + "," + color.getBlue();
    }
}

