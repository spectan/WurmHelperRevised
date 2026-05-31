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
    private final long BLESS_TIMEOUT = 1800000;

    public static HeadsUpDisplay hud;
    private static WurmHelper instance;
    public static boolean hideMount = false;
    public static boolean hideStructures = false;
    public static boolean showTileCoords = false;

    public List<WurmComponent> components;
    private Logger logger;
    private Map<ConsoleCommand, ConsoleCommandHandler> consoleCommandHandlers;
    private CommandRegistry commandRegistry;
    private long lastBless = 0L;
    private boolean noBlessings = false;
    public static Color3f consoleColor = new Color3f(0.5f, 1, 1);

    public WurmHelper() {
        logger = Logger.getLogger("WurmHelper");
        consoleCommandHandlers = new HashMap<>();
        commandRegistry = new CommandRegistry();
        MovementCommandHandler.register(commandRegistry);
        UtilityCommandHandler.register(commandRegistry);
        VisualCommandHandler.register(commandRegistry);
        DevInfoCommandHandler.register(commandRegistry);
        consoleCommandHandlers.put(ConsoleCommand.bot, this::handleBotCommand);
        consoleCommandHandlers.put(ConsoleCommand.info, this::handleInfoCommand);
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
        net.ildar.wurm.command.ConsoleCommandHandler registryHandler = commandRegistry.get(cmd);
        if (registryHandler != null) {
            try {
                registryHandler.handle(Arrays.copyOfRange(data, 1, data.length));
                if (!noBlessings && Math.abs(lastBless - System.currentTimeMillis()) > BLESS_TIMEOUT) {
                    hud.addOnscreenMessage("Ildar blesses you!", 1, 1, 1, (byte)1);
                    lastBless = System.currentTimeMillis();
                }
            } catch (Exception e) {
                Utils.consolePrint("Error on execution of command \"" + cmd + "\"");
                e.printStackTrace();
            }
            return true;
        }

        ConsoleCommand consoleCommand = ConsoleCommand.getByName(cmd);
        if (consoleCommand == ConsoleCommand.unknown)
            return false;
        ConsoleCommandHandler consoleCommandHandler = consoleCommandHandlers.get(consoleCommand);
        if (consoleCommandHandler == null)
            return false;
        try {
            consoleCommandHandler.handle(Arrays.copyOfRange(data, 1, data.length));
            if (!noBlessings && Math.abs(lastBless - System.currentTimeMillis()) > BLESS_TIMEOUT) {
                hud.addOnscreenMessage("Ildar blesses you!", 1, 1, 1, (byte)1);
                lastBless = System.currentTimeMillis();
            }
        } catch (Exception e) {
            Utils.consolePrint("Error on execution of command \"" + consoleCommand.name() + "\"");
            e.printStackTrace();
        }
        return true;
    }

    private void handleBotCommand(String[] input) {
        BotController.getInstance().handleInput(input);
    }

    private void printConsoleCommandUsage(ConsoleCommand consoleCommand) {
        if (consoleCommand == ConsoleCommand.bot) {
            Utils.consolePrint(BotController.getInstance().getBotUsageString());
            return;
        }
        Utils.consolePrint("Usage: " + consoleCommand.name() + " " + consoleCommand.getUsage());
    }

    private void handleInfoCommand(String [] input) {
        if (input.length != 1) {
            printConsoleCommandUsage(ConsoleCommand.info);
            printAvailableConsoleCommands();
            return;
        }

        if (input[0].equals("bots")) {
            BotController.getInstance().printBotList();
            return;
        }

        net.ildar.wurm.command.ConsoleCommandHandler registryHandler = commandRegistry.get(input[0]);
        if (registryHandler != null) {
            Utils.consolePrint("Usage: " + input[0] + " " + registryHandler.getUsage());
            Utils.consolePrint(registryHandler.getDescription());
            return;
        }

        ConsoleCommand command = ConsoleCommand.getByName(input[0]);
        if (command == ConsoleCommand.unknown) {
            Class<? extends Bot> botClass = BotController.getInstance().getBotClass(input[0]);
            if (botClass != null) {
                BotController.getInstance().printBotDescription(botClass);
            } else {
                Utils.consolePrint("Unknown console command or bot abbreviation");
            }
            return;
        }
        printConsoleCommandUsage(command);
        Utils.consolePrint(command.description);
    }

    private void printAvailableConsoleCommands() {
        StringBuilder commands = new StringBuilder();
        for (String name : commandRegistry.getCommandNames()) {
            commands.append(name).append(", ");
        }
        for (ConsoleCommand consoleCommand : consoleCommandHandlers.keySet()) {
            commands.append(consoleCommand.name()).append(", ");
        }
        if (commands.length() >= 2) {
            commands.setLength(commands.length() - 2);
        }
        Utils.consolePrint("Available custom commands - " + commands.toString());
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
                    "                if (net.ildar.wurm.BotController.getInstance().isInstantiated(net.ildar.wurm.bot.GroundItemGetterBot.class)) {\n" +
                    "                   net.ildar.wurm.bot.Bot gigBot = net.ildar.wurm.BotController.getInstance().getInstance(net.ildar.wurm.bot.GroundItemGetterBot.class);" +
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
    }

    public void init() {
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
                        if(BulkItemGetterBot.moveQuantity > 0)
                        {
                            Map<String, Object> inputs = Utils.getField(wc, "inputFields");
                            Object quantityField = inputs.values().iterator().next();
                            Utils.setField(
                                quantityField,
                                "input",
                                String.format("%d", BulkItemGetterBot.moveQuantity)
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
    }

    public enum ConsoleCommand{
        unknown("", ""),
        bot("abbreviation", "Activates/configures the bot with provided abbreviation."),
        info("command|bots|abbreviation", "Shows the description of a console command, lists all bots, or describes a bot by abbreviation.");

        private String usage;
        public String description;

        ConsoleCommand(String usage, String description) {
            this.usage = usage;
            this.description = description;
        }

        String getUsage() {
            return usage;
        }

        static ConsoleCommand getByName(String name) {
            try {
                return Enum.valueOf(ConsoleCommand.class, name);
            } catch(Exception e) {
                return ConsoleCommand.unknown;
            }
        }
    }

    interface ConsoleCommandHandler {
        void handle(String []input);
    }


}
