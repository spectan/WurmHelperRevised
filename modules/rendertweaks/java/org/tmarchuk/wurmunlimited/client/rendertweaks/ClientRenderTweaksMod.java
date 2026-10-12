package org.tmarchuk.wurmunlimited.client.rendertweaks;

/**
 * Created by Tyson Marchuk on 2016-03-29.
 * Hooks updated for the current client (WurmHelper): the render methods the original hooked are gone, grass is
 * hidden at DecorationChunk.render and trees at SurfaceCell.renderTrees.
 */

// From Wurm Unlimited Client
import com.wurmonline.client.console.WurmConsole;
import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.terrain.weather.WeatherControls;

// From Ago's modloader
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmMod;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

// Base Java
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientRenderTweaksMod implements WurmMod, Initable
{
    private static final Logger logger = Logger.getLogger(ClientRenderTweaksMod.class.getName());
    private volatile boolean renderGrass = true;
    private volatile boolean renderTrees = true;

    public static void logException(String msg, Throwable e)
    {
        if (logger != null)
            logger.log(Level.SEVERE, msg, e);
    }

    /** The first word of a console line, lowercase ("" for an empty line). */
    static String commandOf(String line)
    {
        if (line == null)
            return "";
        String t = line.trim();
        int space = t.indexOf(' ');
        return (space < 0 ? t : t.substring(0, space)).toLowerCase();
    }

    private static void say(String text)
    {
        try
        {
            net.ildar.wurm.Utils.consolePrint("%s", text);
        }
        catch (Throwable ignored)
        {
            // the console is a courtesy
        }
    }

    @Override
    public void init()
    {
        try
        {
            HookManager hooks = HookManager.getInstance();

            // The console commands: weather, toggleGrass, toggleTrees.
            hooks.registerHook("com.wurmonline.client.console.WurmConsole", "handleInput2", "(Ljava/lang/String;Z)V",
                    () -> (proxy, method, args) ->
                    {
                        synchronized (proxy)
                        {
                            String command = commandOf(String.valueOf(args[0]));
                            if (command.equals("weather"))
                            {
                                WurmConsole console = (WurmConsole) proxy;
                                World world = ReflectionUtil.getPrivateField(console, ReflectionUtil.getField(console.getClass(), "world"));
                                world.getWeather().setIgnoreWeather(true);
                                (new WeatherControls(world)).start();
                                return true;
                            }
                            else if (command.equals("togglegrass"))
                            {
                                renderGrass = !renderGrass;
                                say("Grass " + (renderGrass ? "shown" : "hidden"));
                                return true;
                            }
                            else if (command.equals("toggletrees"))
                            {
                                renderTrees = !renderTrees;
                                say("Trees " + (renderTrees ? "shown" : "hidden"));
                                return true;
                            }
                            return method.invoke(proxy, args);
                        }
                    });

            // Grass: one chunk of grass sprites is drawn by DecorationChunk.render(Queue).
            hooks.registerHook("com.wurmonline.client.renderer.terrain.decorator.DecorationChunk", "render",
                    "(Lcom/wurmonline/client/renderer/backend/Queue;)V",
                    () -> (proxy, method, args) -> renderGrass ? method.invoke(proxy, args) : null);

            // Trees and bushes: SurfaceCell.renderTrees(Queue, Frustum, boolean) adds a cell's trees to the queue.
            hooks.registerHook("com.wurmonline.client.renderer.cell.SurfaceCell", "renderTrees",
                    "(Lcom/wurmonline/client/renderer/backend/Queue;Lcom/wurmonline/client/renderer/Frustum;Z)V",
                    () -> (proxy, method, args) -> renderTrees ? method.invoke(proxy, args) : null);
        }
        catch (Throwable e)
        {
            logException("Failed to create hooks for " + ClientRenderTweaksMod.class.getName(), e);
            throw new RuntimeException(e);
        }
    }
}
