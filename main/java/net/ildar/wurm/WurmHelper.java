package net.ildar.wurm;

import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.gui.BmlWindowComponent;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.WurmComponent;
import com.wurmonline.shared.constants.PlayerAction;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import net.ildar.wurm.bot.Bot;
import net.ildar.wurm.bot.BulkItemGetterBot;
import net.ildar.wurm.command.CommandRegistry;
import net.ildar.wurm.command.ConsoleCommandHandler;
import net.ildar.wurm.modules.ModuleManager;
import net.ildar.wurm.command.DevInfoCommandHandler;
import net.ildar.wurm.command.MovementCommandHandler;
import net.ildar.wurm.command.UtilityCommandHandler;
import net.ildar.wurm.command.VisualCommandHandler;

import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.vecmath.Color3f;

public class WurmHelper implements WurmClientMod, Initable, Configurable, PreInitable {
    public static final String BOT_COMMAND = "bot";
    private static final String MODULES_COMMAND = "modules";
    private final ModuleManager modules = new ModuleManager();
    private static final String INFO_COMMAND = "info";
    private final long BLESS_TIMEOUT = 1800000;

    public static HeadsUpDisplay hud;
    private static WurmHelper instance;
    public static boolean hideMount = false;
    public static boolean hideStructures = false;
    public static boolean showTileCoords = false;

    public volatile List<WurmComponent> components;
    private Logger logger;
    private CommandRegistry commandRegistry;
    private long lastBless = 0L;
    private boolean noBlessings = false;
    public static Color3f consoleColor = new Color3f(0.5f, 1, 1);
    // show bot state changes on screen as well as in the console
    public static boolean onscreenFeedback = true;
    // play a sound when a bot stops by itself
    public static boolean alarmOnStop = false;
    // put the last bot command back into the console input line, so it can be edited and repeated
    public static boolean prefillConsoleInput = true;

    public WurmHelper() {
        logger = Logger.getLogger("WurmHelper");
        commandRegistry = new CommandRegistry();
        MovementCommandHandler.register(commandRegistry);
        UtilityCommandHandler.register(commandRegistry);
        VisualCommandHandler.register(commandRegistry);
        DevInfoCommandHandler.register(commandRegistry);
        commandRegistry.register(BOT_COMMAND, new ConsoleCommandHandler() {
            @Override
            public void handle(String[] args) {
                BotController.getInstance().handleInput(args);
            }

            @Override
            public String getUsage() {
                return BotController.getInstance().getBotUsageArguments();
            }

            @Override
            public String getDescription() {
                return "Activates/configures the bot with provided abbreviation.";
            }
        });
        commandRegistry.register(INFO_COMMAND, new ConsoleCommandHandler() {
            @Override
            public void handle(String[] args) {
                handleInfoCommand(args);
            }

            @Override
            public String getUsage() {
                return "command|bots|abbreviation";
            }

            @Override
            public String getDescription() {
                return "Shows the description of a console command, lists all bots, or describes a bot by abbreviation.";
            }
        });
        commandRegistry.register(MODULES_COMMAND, new ConsoleCommandHandler() {
            @Override
            public void handle(String[] args) {
                Utils.consolePrint("Mods merged into WurmHelper (switch one off with module.<id>=false in WurmHelper.properties):");
                modules.describe().forEach(Utils::consolePrint);
            }

            @Override
            public String getUsage() {
                return "";
            }

            @Override
            public String getDescription() {
                return "Lists the mods merged into WurmHelper and whether each is on.";
            }
        });
        WurmHelper.instance = this;
    }

    public static WurmHelper getInstance() {
        return instance;
    }

    /**
     * Handle console commands
     */
    @SuppressWarnings("unused")
    public boolean handleInput(final String cmd, final String[] data) {
        ConsoleCommandHandler registryHandler = commandRegistry.get(cmd);
        if (registryHandler == null)
            return false;
        try {
            registryHandler.handle(Arrays.copyOfRange(data, 1, data.length));
            bless();
        } catch (Exception e) {
            Utils.consolePrint("Error on execution of command \"" + cmd + "\"");
            e.printStackTrace();
        }
        return true;
    }

    private void bless() {
        if (!noBlessings && hud != null && System.currentTimeMillis() - lastBless > BLESS_TIMEOUT) {
            hud.addOnscreenMessage("Ildar blesses you!", 1, 1, 1, (byte)1);
            lastBless = System.currentTimeMillis();
        }
    }

    private void printCommandUsage(String name, ConsoleCommandHandler handler) {
        Utils.consolePrint("Usage: " + name + " " + handler.getUsage());
    }

    private void handleInfoCommand(String [] input) {
        if (input.length != 1) {
            printCommandUsage(INFO_COMMAND, commandRegistry.get(INFO_COMMAND));
            printAvailableConsoleCommands();
            return;
        }

        if (input[0].equals("bots")) {
            BotController.getInstance().printBotList();
            return;
        }

        ConsoleCommandHandler registryHandler = commandRegistry.get(input[0]);
        if (registryHandler != null) {
            printCommandUsage(input[0], registryHandler);
            Utils.consolePrint(registryHandler.getDescription());
            return;
        }

        Class<? extends Bot> botClass = BotController.getInstance().getBotClass(input[0]);
        if (botClass != null) {
            BotController.getInstance().printBotDescription(botClass);
        } else {
            Utils.consolePrint("Unknown console command or bot abbreviation");
        }
    }

    private void printAvailableConsoleCommands() {
        Utils.consolePrint("Available custom commands:");
        List<String> names = new ArrayList<>(commandRegistry.getCommandNames());
        Collections.sort(names);
        for (String name : names)
            Utils.consolePrint("  " + name + " - " + commandRegistry.get(name).getDescription());
        Utils.consolePrint("Type \"info <command>\" for its usage, \"info bots\" for the list of bots");
    }

    public static void addCoordsText(int x, int y, int section, final PickData pickData) {
        String prefix;
        switch(section) {
            case 1:
                prefix = "North border of ";
                break;
            case 2:
                prefix = "West border of ";
                break;
            // cave walls
            case -1:
                prefix = "Floor of ";
                break;
            case -2:
                prefix = "Ceiling of ";
                break;
            case -3:
                prefix = "East face of ";
                break;
            case -4:
                prefix = "South face of ";
                break;
            case -5:
                prefix = "West face of ";
                break;
            case -6:
                prefix = "North face of ";
                break;
            // TODO: cave tile borders? their coords and `wallSide` are really funky though
            default:
                prefix = "";
        }
        pickData.addText(String.format("%s%d, %d", prefix, x, y));
    }

    @Override
    public void configure(Properties properties) {
        String noBlessings = properties.getProperty("NoBlessings", "false");
        this.noBlessings = noBlessings.equalsIgnoreCase("true");
        
        onscreenFeedback = properties.getProperty("OnscreenFeedback", "true").equalsIgnoreCase("true");
        alarmOnStop = properties.getProperty("AlarmOnStop", "false").equalsIgnoreCase("true");
        prefillConsoleInput = properties.getProperty("PrefillConsoleInput", "true").equalsIgnoreCase("true");

        String consoleMsgColor = properties.getProperty("ConsoleMsgColor", "0.5,1.0,1.0");
        try {
            String[] bits = consoleMsgColor.split(",");
            float r = Float.parseFloat(bits[0]);
            float g = Float.parseFloat(bits[1]);
            float b = Float.parseFloat(bits[2]);
            consoleColor = new Color3f(r, g, b);
        } catch(Exception err) {
            Utils.consolePrint(
                "%s: failed to parse ConsoleMsgColor property, using default",
                WurmHelper.class.getSimpleName()
            );
        }
        modules.configure(properties);
    }

    @Override
    public void preInit() {
        try {
            final ClassPool classPool = HookManager.getInstance().getClassPool();
            final CtClass ctWurmConsole = classPool.getCtClass("com.wurmonline.client.console.WurmConsole");
            ctWurmConsole.getMethod("handleDevInput", "(Ljava/lang/String;[Ljava/lang/String;)Z").insertBefore("if (net.ildar.wurm.WurmHelper.getInstance().handleInput($1,$2)) return true;");

            final CtClass ctSocketConnection = classPool.getCtClass("com.wurmonline.communication.SocketConnection");
            CtMethod tickWriting = ctSocketConnection.getMethod("tickWriting", "(J)Z");
            tickWriting.insertBefore("net.ildar.wurm.Utils.serverCallLock.lock();");
            tickWriting.insertAfter("net.ildar.wurm.Utils.serverCallLock.unlock();", true);

            CtMethod getBuffer = ctSocketConnection.getMethod("getBuffer", "()Ljava/nio/ByteBuffer;");
            getBuffer.insertBefore("net.ildar.wurm.Utils.serverCallLock.lock();");
            getBuffer.addCatch(
                "{ net.ildar.wurm.Utils.serverCallLock.unlock(); throw $e; }",
                classPool.get("java.lang.Throwable")
            );

            ctSocketConnection.getMethod("flush", "()V").insertAfter("net.ildar.wurm.Utils.serverCallLock.unlock();", true);

            final CtClass ctConsoleComponent = classPool.getCtClass("com.wurmonline.client.renderer.gui.ConsoleComponent");
            CtMethod consoleGameTickMethod = CtNewMethod.make(
                "public void gameTick() {" +
                "  javax.vecmath.Color3f c = net.ildar.wurm.WurmHelper.consoleColor;" +
                "  while(!net.ildar.wurm.Utils.consoleMessages.isEmpty()) {" +
                "    addLine((String)net.ildar.wurm.Utils.consoleMessages.poll(), c.x, c.y, c.z);" +
                "  }" +
                "  super.gameTick();" +
                "};",
                ctConsoleComponent
            );
            ctConsoleComponent.addMethod(consoleGameTickMethod);

            final CtClass ctWurmChat = classPool.getCtClass("com.wurmonline.client.renderer.gui.ChatPanelComponent");
            ctWurmChat.getMethod("addText", "(Ljava/lang/String;Ljava/util/List;Z)V").insertBefore("net.ildar.wurm.Chat.onMessage($1,$2,$3);");
            ctWurmChat.getMethod("addText", "(Ljava/lang/String;Ljava/lang/String;FFFZ)V").insertBefore("net.ildar.wurm.Chat.onMessage($1,$2,$6);");

            CtClass itemCellRenderableClass = classPool.getCtClass("com.wurmonline.client.renderer.cell.GroundItemCellRenderable");
            itemCellRenderableClass.defrost();
            CtMethod itemCellRenderableInitializeMethod = CtNewMethod.make("public void initialize() {\n" +
                    "                net.ildar.wurm.bot.Bot gigBot = net.ildar.wurm.BotController.getInstance().getActiveInstance(net.ildar.wurm.bot.GroundItemGetterBot.class);\n" +
                    "                if (gigBot != null) {\n" +
                    "                   ((net.ildar.wurm.bot.GroundItemGetterBot)gigBot).processNewItem(this);\n" +
                    "                }\n" +
                    "        super.initialize();\n" +
                    "    };", itemCellRenderableClass);
            itemCellRenderableClass.addMethod(itemCellRenderableInitializeMethod);
            
            CtClass structureDataClass = classPool.getCtClass("com.wurmonline.client.renderer.structures.StructureData");
            structureDataClass.getMethod("isVisible", "(Lcom/wurmonline/client/renderer/Frustum;)Z").insertBefore(
                "if(net.ildar.wurm.WurmHelper.hideStructures) return false;"
            );
            CtClass meshClass = classPool.getCtClass("com.wurmonline.client.renderer.mesh.Mesh");
            meshClass.getMethod("isVisible", "(Lcom/wurmonline/client/renderer/Frustum;)Z").insertBefore(
                "if(net.ildar.wurm.WurmHelper.hideStructures) return false;"
            );
            
            CtClass creatureRenderable = classPool.getCtClass("com.wurmonline.client.renderer.cell.CreatureCellRenderable");
            creatureRenderable.getMethod("isVisible", "(Lcom/wurmonline/client/renderer/Frustum;)Z").insertBefore(
                "if(net.ildar.wurm.WurmHelper.hideMount && " +
                "this == net.ildar.wurm.WurmHelper.hud.getWorld().getPlayer().getCarrierCreature())" +
                "return false;"
            );
            
            CtClass tilePicker = classPool.getCtClass("com.wurmonline.client.renderer.TilePicker");
            tilePicker.getMethod("getHoverDescription", "(Lcom/wurmonline/client/renderer/PickData;)V").insertAfter(
                "if(net.ildar.wurm.WurmHelper.showTileCoords)" +
                "  net.ildar.wurm.WurmHelper.addCoordsText(x, y, section, $1);"
            );
            CtClass cavePicker = classPool.getCtClass("com.wurmonline.client.renderer.cave.CaveWallPicker");
            cavePicker.getMethod("getHoverDescription", "(Lcom/wurmonline/client/renderer/PickData;)V").insertAfter(
                "final int tilex = (this.wallSide == 4) ? (this.x + 1) : ((this.wallSide == 2) ? (this.x - 1) : this.x);" +
                "final int tiley = (this.wallSide == 5) ? (this.y + 1) : ((this.wallSide == 3) ? (this.y - 1) : this.y);" +
                "if(net.ildar.wurm.WurmHelper.showTileCoords)" +
                "  net.ildar.wurm.WurmHelper.addCoordsText(tilex, tiley, -1 - wallSide, $1);"
            );
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading mod", e);
            logger.log(Level.SEVERE, e.toString());
            throw new RuntimeException(e);
        }
        // the merged mods hook the client after WurmHelper
        modules.preInit();
    }

    public void init() {
        modules.initEarly();
        try {
            HookManager.getInstance().registerHook("com.wurmonline.client.renderer.gui.HeadsUpDisplay", "init", "(II)V", () -> (proxy, method, args) -> {
                method.invoke(proxy, args);
                WurmHelper.hud = (HeadsUpDisplay)proxy;
                return null;
            });

            HookManager.getInstance().registerHook("com.wurmonline.client.renderer.gui.HeadsUpDisplay", "addComponent", "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)Z", () -> (proxy, method, args) -> {
                WurmComponent wc = (WurmComponent)args[0];
                boolean notadd = false;
                if (BulkItemGetterBot.closeBMLWindow && wc instanceof BmlWindowComponent) {
                    String title = Utils.getField(wc, "title");
                    if (title.equals("Removing items")) {
                        if(BulkItemGetterBot.currentMoveQuantity > 0)
                        {
                            Map<String, Object> inputs = Utils.getField(wc, "inputFields");
                            Object quantityField = inputs.values().iterator().next();
                            Utils.setField(
                                quantityField,
                                "input",
                                String.format("%d", BulkItemGetterBot.currentMoveQuantity)
                            );
                        }
                        
                        Method clickButton = ReflectionUtil.getMethod(wc.getClass(), "processButtonPressed");
                        clickButton.setAccessible(true);
                        clickButton.invoke(wc, "submit");
                        notadd = true;
                        BulkItemGetterBot.closeBMLWindow = false;
                    }
                }
                if (!notadd) {
                    Object o = method.invoke(proxy, args);
                    components = new ArrayList<>(Utils.getField(proxy, "components"));
                    return o;
                }
                return (Object)true;
            });
            HookManager.getInstance().registerHook("com.wurmonline.client.renderer.gui.HeadsUpDisplay", "setActiveWindow", "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)V", () -> (proxy, method, args) -> {
                method.invoke(proxy, args);
                components = new ArrayList<>(Utils.getField(proxy, "components"));
                return null;
            });

            Chat.registerMessageProcessor(":Event", message -> message.contains("You fail to relax"), () -> {
                try {
                    PickableUnit pickableUnit = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
                    if (pickableUnit != null)
                        WurmHelper.hud.sendAction(new PlayerAction("",(short) 384, PlayerAction.ANYTHING), pickableUnit.getId());
                } catch (Exception e) {
                    Utils.consolePrint("Got exception at the start of meditation " + e.getMessage());
                    Utils.consolePrint(e.toString());
                }
            });

            logger.info("Loaded");
        }
        catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading mod", e);
            logger.log(Level.SEVERE, e.toString());
        }
        modules.init();
    }
}
