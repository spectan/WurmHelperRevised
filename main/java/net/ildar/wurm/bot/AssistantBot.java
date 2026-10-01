package net.ildar.wurm.bot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.comm.SimpleServerConnectionClass;
import com.wurmonline.client.game.PlayerObj;
import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.client.renderer.gui.InventoryListComponent;
import com.wurmonline.client.renderer.gui.PaperDollInventory;
import com.wurmonline.client.renderer.gui.PaperDollSlot;
import com.wurmonline.client.renderer.gui.TargetWindow;
import com.wurmonline.mesh.Tiles.Tile;
import com.wurmonline.shared.constants.PlayerAction;

import net.ildar.wurm.Utils;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.annotations.BotInfo;

@BotInfo(name = "Assistant", description =
        "Assists player in various ways",
        abbreviation = "a")
public class AssistantBot extends Bot {
    private Enchant spellToCast = Enchant.DISPEL;
    private volatile boolean casting;
    private long statuetteId;
    private long bodyId;
    private volatile boolean wovCasting;
    private long lastWOV;
    private volatile boolean successfullCastStart;
    private volatile boolean successfullCasting;
    private volatile boolean needWaitWov;

    private volatile boolean lockpicking;
    private long chestId;
    private long lastLockpicking;
    private long lockpickingTimeout;
    private volatile boolean successfullStartOfLockpicking;
    private volatile int lockpickingResult;
    private volatile boolean successfullLocking;
    private volatile boolean noLock;

    private volatile boolean drinking;
    private long waterId;
    private volatile boolean successfullDrinkingStart;
    private volatile boolean successfullDrinking;

    private volatile boolean eating;
    private long foodId;
    private volatile boolean successfullEatingStart;
    private volatile boolean successfullEating;

    private volatile boolean trashCleaning;
    private long trashCleaningTimeout;
    private long lastTrashCleaning;
    private long trashBinId;
    private volatile boolean successfullStartTrashCleaning;

    private volatile boolean praying;
    private long altarId;
    private long lastPrayer;
    private long prayingTimeout;
    private int prayCount = 0;
    private float prayStamina = 0.5f;
    private volatile boolean successfullStartOfPraying;

    private volatile boolean sacrificing;
    private long sacrificeAltarId;
    private long lastSacrifice;
    private long sacrificeTimeout;
    private volatile boolean successfullStartOfSacrificing;
    
    private volatile boolean butchering;
    private long butcheringKnife = -10;
    
    private volatile boolean burying;
    private long shovel = -10;
    private long pickaxe = -10;
    private volatile boolean buryAll;
    private long buryDelay = 2500;
    // modified by the console thread while the bot thread iterates them
    private final Map<Long, Long> corpseTimes = new ConcurrentHashMap<>();
    private final Set<String> blacklistedCorpseNames = ConcurrentHashMap.newKeySet();

    private volatile boolean kindlingBurning;
    private long forgeId;
    private long lastBurning;
    private long kindlingBurningTimeout;
    private volatile boolean successfullStartOfBurning;
    
    private volatile boolean grooming;
    private long groomingBrush = -10;
    // creatures that can be ignored as they have been recently groomed
    private final Map<Long, Long> groomedCreatures = new ConcurrentHashMap<>();
    // creatures which were just queued to be groomed, cleared from groomedCreatures if groomingFailed
    private HashSet<Long> groomingQueued = new HashSet<>();
    private volatile boolean groomingFailed;
    private final long groomIgnoreDuration = 150_000; // 2.5 minutes

    private static final float notargetDistance = 4f * 10f; // 10 tiles
    private volatile boolean notarget = false;
    private long lastNotarget = 0;

    private volatile InventoryListComponent lumpHeatingInventory;

    private volatile boolean lumpCombining;

    private volatile boolean verbose = false;

    public AssistantBot() {
        registerInputHandler(AssistantBot.InputKey.w, input -> toggleDrinking(0));
        registerInputHandler(AssistantBot.InputKey.wid,
            input -> toggleByTargetId(input, InputKey.wid, "water", this::toggleDrinking));
        registerInputHandler(AssistantBot.InputKey.eat, input -> toggleEating());
        registerInputHandler(AssistantBot.InputKey.ls, input -> showSpellList());
        registerInputHandler(AssistantBot.InputKey.c, this::toggleAutocasting);
        registerInputHandler(AssistantBot.InputKey.p, input -> togglePraying(0));
        registerInputHandler(AssistantBot.InputKey.pt,
            input -> handleTimeoutInput(input, InputKey.pt, praying, "Automatic praying is off!", this::changePrayerTimeout));
        registerInputHandler(AssistantBot.InputKey.pid,
            input -> toggleByTargetId(input, InputKey.pid, "altar", this::togglePraying));
        registerInputHandler(AssistantBot.InputKey.pis, input -> togglePrayingOnInventoryItem());
        registerInputHandler(AssistantBot.InputKey.ps, this::setPrayerStamina);
        registerInputHandler(AssistantBot.InputKey.pc, this::setPrayerCount);
        registerInputHandler(AssistantBot.InputKey.s, input -> toggleSacrificing(0));
        registerInputHandler(AssistantBot.InputKey.st,
            input -> handleTimeoutInput(input, InputKey.st, sacrificing, "Automatic sacrificing is off!", this::changeSacrificeTimeout));
        registerInputHandler(AssistantBot.InputKey.sid,
            input -> toggleByTargetId(input, InputKey.sid, "altar", this::toggleSacrificing));
        registerInputHandler(AssistantBot.InputKey.kb, input -> toggleKindlingBurns(0));
        registerInputHandler(AssistantBot.InputKey.kbt,
            input -> handleTimeoutInput(input, InputKey.kbt, kindlingBurning, "Kindling burning is off!", this::changeKindlingBurnsTimeout));
        registerInputHandler(AssistantBot.InputKey.kbid,
            input -> toggleByTargetId(input, InputKey.kbid, "forge", this::toggleKindlingBurns));
        registerInputHandler(AssistantBot.InputKey.cwov, input -> toggleWOVCasting());
        registerInputHandler(AssistantBot.InputKey.cleanup, input -> toggleTrashCleaning(0));
        registerInputHandler(AssistantBot.InputKey.cleanupt,
            input -> handleTimeoutInput(input, InputKey.cleanupt, trashCleaning, "Trash cleaning is off!", this::changeTrashCleaningTimeout));
        registerInputHandler(AssistantBot.InputKey.cleanupid,
            input -> toggleByTargetId(input, InputKey.cleanupid, "trash bin", this::toggleTrashCleaning));
        registerInputHandler(AssistantBot.InputKey.l, input -> toggleLockpicking(0));
        registerInputHandler(AssistantBot.InputKey.lt,
            input -> handleTimeoutInput(input, InputKey.lt, lockpicking, "Automatic lockpicking is off!", this::changeLockpickingTimeout));
        registerInputHandler(AssistantBot.InputKey.lid,
            input -> toggleByTargetId(input, InputKey.lid, "chest", this::toggleLockpicking));
        registerInputHandler(AssistantBot.InputKey.b, input -> toggleButchering());
        registerInputHandler(AssistantBot.InputKey.bu, input -> toggleBurying());
        registerInputHandler(AssistantBot.InputKey.bua, input -> toggleBuryAll());
        registerInputHandler(AssistantBot.InputKey.bud, this::setBuryDelay);
        registerInputHandler(AssistantBot.InputKey.bub, input -> addCorpseBlacklist(
            Arrays
                .stream(input != null ? input : new String[]{})
                .collect(Collectors.joining(" "))
        ));
        registerInputHandler(AssistantBot.InputKey.bubc, input -> addCorpseBlacklist(null));
        registerInputHandler(AssistantBot.InputKey.groom, input -> toggleGrooming());
        registerInputHandler(AssistantBot.InputKey.v, input -> toggleVerbosity());
        registerInputHandler(AssistantBot.InputKey.pave,
            input -> pave(false, input == null ? "" : String.join(" ", input).toLowerCase())
        );
        registerInputHandler(AssistantBot.InputKey.pavec,
            input -> pave(true, input == null ? "" : String.join(" ", input).toLowerCase())
        );
        registerInputHandler(AssistantBot.InputKey.paveclear, input -> paveClear());
        registerInputHandler(AssistantBot.InputKey.notarget, input -> toggleNotarget());
        registerInputHandler(AssistantBot.InputKey.lumpheating, input -> toggleLumpHeating());
        registerInputHandler(AssistantBot.InputKey.lumpcombine, input -> toggleLumpCombining());
    }

    @Override
    public void work() throws Exception {
        registerEventProcessors();
        
        CreationWindow creationWindow = WurmHelper.hud.getCreationWindow();
        Object progressBar = Utils.getField(creationWindow, "progressBar");
        World world = WurmHelper.hud.getWorld();
        PlayerObj player = world.getPlayer();
        SimpleServerConnectionClass serverConnection = world.getServerConnection();
        final int maxActions = Utils.getMaxActionNumber();

        HashSet<String> lumpTypesInInventory = new HashSet<>();
        HashMap<Long, Long> lumpDropTimes = new HashMap<>();

        HashMap<String, ArrayList<Long>> lumpsToCombine = new HashMap<>();
        
        while (isActive()) {
            waitOnPause();
            final float progress = Utils.getField(progressBar, "progress");
            if (progress == 0f && creationWindow.getActionInUse() == 0){
                if (casting) {
                    float favor = player.getSkillSet().getSkillValue("favor");
                    if (favor > spellToCast.favorCap) {
                        successfullCasting = false;
                        successfullCastStart = false;
                        int counter = 0;
                        while (casting && !successfullCastStart && counter++ < 50 && favor > spellToCast.favorCap) {
                            if (verbose) Utils.consolePrint("successfullCastStart counter=" + counter);
                            serverConnection.sendAction(statuetteId, new long[]{bodyId}, spellToCast.playerAction);
                            favor = player.getSkillSet().getSkillValue("favor");
                            sleep(500);
                        }
                        counter = 0;
                        while (casting && !successfullCasting && counter++ < 100 && favor > spellToCast.favorCap) {
                            if (verbose) Utils.consolePrint("successfullCasting counter=" + counter);
                            sleep(2000);
                        }
                    }
                } else if (wovCasting && Math.abs(lastWOV - System.currentTimeMillis()) > 1810000) {
                    float favor = player.getSkillSet().getSkillValue("favor");
                    if (favor > 30) {
                        successfullCasting = false;
                        successfullCastStart = false;
                        needWaitWov = false;
                        int counter = 0;
                        while (wovCasting && !successfullCastStart && counter++ < 50 && !needWaitWov) {
                            if (verbose) Utils.consolePrint("successfullCastStart counter=" + counter);
                            serverConnection.sendAction(statuetteId, new long[]{bodyId}, PlayerAction.WISDOM_OF_VYNORA);
                            sleep(500);
                        }
                        counter = 0;
                        while (wovCasting && !successfullCasting && counter++ < 100 && !needWaitWov) {
                            if (verbose) Utils.consolePrint("successfullCasting counter=" + counter);
                            sleep(2000);
                        }
                        if (needWaitWov)
                            lastWOV = lastWOV + 20000;
                        else
                            lastWOV = System.currentTimeMillis();
                    }
                }
                if (drinking) {
                    float thirst = player.getThirst();
                    if (thirst > 0.1) {
                        successfullDrinking = false;
                        successfullDrinkingStart = false;
                        int counter = 0;
                        while (drinking && !successfullDrinkingStart && counter++ < 50) {
                            if (verbose) Utils.consolePrint("successfullDrinkingStart counter=" + counter);
                            WurmHelper.hud.sendAction(new PlayerAction("",(short) 183, PlayerAction.ANYTHING), waterId);
                            sleep(500);
                        }
                        counter = 0;
                        while (drinking && !successfullDrinking && counter++ < 100) {
                            if (verbose) Utils.consolePrint("successfullDrinking counter=" + counter);
                            sleep(2000);
                        }
                    }
                }
                if (eating) {
                    float hunger = player.getHunger();
                    if (hunger > 0.1) {
                        successfullEating = false;
                        successfullEatingStart = false;
                        int counter = 0;
                        while (eating && !successfullEatingStart && counter++ < 50) {
                            if (verbose) Utils.consolePrint("successfullEatingStart counter=" + counter);
                            WurmHelper.hud.sendAction(new PlayerAction("",(short) 182, PlayerAction.ANYTHING), foodId);
                            sleep(500);
                        }
                        counter = 0;
                        while (eating && !successfullEating && counter++ < 100) {
                            if (verbose) Utils.consolePrint("successfullEating counter=" + counter);
                            sleep(2000);
                        }
                    }
                }
                if (lockpicking && Math.abs(lastLockpicking - System.currentTimeMillis()) > lockpickingTimeout) {
                    long lockpickId = 0;
                    InventoryMetaItem lockpick = Utils.getInventoryItem("lock picks");
                    if (lockpick != null)
                        lockpickId = lockpick.getId();
                    if (lockpickId == 0) {
                        Utils.consolePrint("No lockpicks in inventory! Turning lockpicking off");
                        lockpicking = false;
                        continue;
                    }

                    successfullStartOfLockpicking = false;
                    lockpickingResult = -1;
                    int counter = 0;
                    while (lockpicking && !successfullStartOfLockpicking && counter++ < 50 && !noLock) {
                        if (verbose) Utils.consolePrint("successfullStartOfLockpicking counter=" + counter);
                        serverConnection.sendAction(lockpickId,
                                new long[]{chestId}, new PlayerAction("",(short) 101, PlayerAction.ANYTHING));
                        sleep(500);
                    }
                    if (!successfullStartOfLockpicking && !noLock) continue;
                    counter = 0;
                    while (lockpicking && lockpickingResult == -1 && counter++ < 100 && !noLock) {
                        if (verbose) Utils.consolePrint("lockpickingResult counter=" + counter);
                        sleep(2000);
                    }
                    if (noLock || lockpickingResult > 0) {
                        long padlockId = 0;
                        InventoryMetaItem padlock = Utils.getInventoryItem("padlock");
                        if (padlock != null)
                            padlockId = padlock.getId();
                        if (padlockId == 0) {
                            Utils.consolePrint("No padlocks in inventory! Can't lock the target");
                            noLock = false;
                            lastLockpicking = System.currentTimeMillis();
                            continue;
                        }
                        successfullLocking = false;
                        counter = 0;
                        while (lockpicking && !successfullLocking && counter++ < 50) {
                            if (verbose) Utils.consolePrint("successfullLocking lockingcounter=" + counter);
                            serverConnection.sendAction(padlockId,
                                    new long[]{chestId}, new PlayerAction("",(short) 161, PlayerAction.ANYTHING));
                            sleep(500);
                        }
                    }
                    if (noLock)
                        noLock = false;
                    else
                        lastLockpicking = System.currentTimeMillis();
                }
                if (trashCleaning) {
                    if (Math.abs(lastTrashCleaning - System.currentTimeMillis()) > trashCleaningTimeout) {
                        lastTrashCleaning = System.currentTimeMillis();
                        successfullStartTrashCleaning = false;
                        int counter = 0;
                        while (trashCleaning && !successfullStartTrashCleaning && counter++ < 30) {
                            if (verbose) Utils.consolePrint("successfullStartTrashCleaning counter=" + counter);
                            WurmHelper.hud.sendAction(new PlayerAction("",(short) 954, PlayerAction.ANYTHING), trashBinId);
                            sleep(1000);
                        }
                        successfullStartTrashCleaning = true;
                    }
                }

                if (praying) {
                    if (Math.abs(lastPrayer - System.currentTimeMillis()) > prayingTimeout && Utils.getPlayerStamina() >= prayStamina) {
                        lastPrayer = System.currentTimeMillis();
                        final int numPrayers =
                            prayCount == 0 ?
                                // leave a slot free so sacrificing isn't starved (i.e. "too busy" every iteration)
                                Math.max(1, maxActions - (sacrificing ? 1 : 0)) :
                                prayCount
                        ;
                        successfullStartOfPraying = false;
                        int counter = 0;
                        while (praying && !successfullStartOfPraying && counter++ < 50) {
                            if (verbose) Utils.consolePrint("successfullStartOfPraying counter=" + counter);
                            for (int x = 0; x < numPrayers; x++) {
                                WurmHelper.hud.sendAction(PlayerAction.PRAY, altarId);
                            }
                            sleep(1000);
                        }
                        successfullStartOfPraying = true;
                    }
                }

                if (sacrificing) {
                    if (Math.abs(lastSacrifice - System.currentTimeMillis()) > sacrificeTimeout) {
                        lastSacrifice = System.currentTimeMillis();
                        successfullStartOfSacrificing = false;
                        int counter = 0;
                        while (sacrificing && !successfullStartOfSacrificing && counter++ < 50) {
                            if (verbose) Utils.consolePrint("successfullStartOfSacrificing counter=" + counter);
                            WurmHelper.hud.sendAction(PlayerAction.SACRIFICE, sacrificeAltarId);
                            sleep(1000);
                        }
                        successfullStartOfSacrificing = true;
                    }
                }

                if (kindlingBurning) {
                    if (Math.abs(lastBurning - System.currentTimeMillis()) > kindlingBurningTimeout) {
                        lastBurning = System.currentTimeMillis();
                        List<InventoryMetaItem> kindlings = Utils.getInventoryItems("kindling")
                                .stream()
                                .filter(item -> item.getRarity() == 0)
                                .collect(Collectors.toList());
                        if (kindlings.size() > 1) {
                            kindlings.sort(Comparator.comparingDouble(InventoryMetaItem::getWeight));
                            InventoryMetaItem biggestKindling = kindlings.get(kindlings.size() - 1);
                            kindlings.remove(biggestKindling);
                            long[] targetIds = new long[Math.min(kindlings.size(), 64)];
                            for (int i = 0; i < targetIds.length; i++)
                                targetIds[i] = kindlings.get(i).getId();
                            serverConnection.sendAction(
                                    targetIds[0], targetIds, PlayerAction.COMBINE);
                            successfullStartOfBurning = false;
                            int counter = 0;
                            while (kindlingBurning && !successfullStartOfBurning && counter++ < 50) {
                                if (verbose) Utils.consolePrint("successfullStartOfBurning counter=" + counter);
                                serverConnection.sendAction(
                                        biggestKindling.getId(), new long[]{forgeId}, new PlayerAction("",(short) 117, PlayerAction.ANYTHING));
                                sleep(300);
                            }
                            successfullStartOfBurning = true;
                        }
                    }
                }
                
                if (butchering && butcheringKnife > 0) {
                    List<GroundItemCellRenderable> corpses = findCorpses(
                        (item, data) -> !data.getModelName().toString().toLowerCase().contains("butchered")
                    );
                    
                    int actions = 0;
                    for (GroundItemCellRenderable corpse: corpses) {
                        serverConnection.sendAction(
                            butcheringKnife,
                            new long[]{corpse.getId()},
                            PlayerAction.BUTCHER
                        );
                        
                        if (++actions >= maxActions)
                            break;
                    }
                } else if (butchering) {
                    Utils.consolePrint("Don't have a butchering knife to butcher with!");
                    butchering = false;
                }
                
                if (burying && shovel > 0) {
                    List<GroundItemCellRenderable> corpses = findCorpses(
                        (item, data) -> {
                            final String modelName = data.getModelName().toString().toLowerCase();
                            final String displayName = item.getHoverName().toLowerCase();
                            return
                                (!butchering || modelName.contains("butchered")) &&
                                (pickaxe > 0 || !needsPickaxeToBury(item)) &&
                                blacklistedCorpseNames
                                    .stream()
                                    .noneMatch(displayName::contains)
                            ;
                        }
                    );
                    // item lists seem to have consistent ordering between multiple clients,
                    // so this should help parallelize corpses across alts
                    Collections.shuffle(corpses);
                    
                    final long now = System.currentTimeMillis();
                    int actions = 0;
                    for (GroundItemCellRenderable item: corpses) {
                        final long id = item.getId();
                        corpseTimes.putIfAbsent(id, now);
                        
                        if (now - corpseTimes.get(id) > buryDelay) {
                            serverConnection.sendAction(
                                needsPickaxeToBury(item) ? pickaxe : shovel,
                                new long[]{item.getId()},
                                buryAll ? PlayerAction.BURY_ALL : PlayerAction.BURY
                            );
                        }
                        
                        if (++actions >= maxActions)
                            break;
                    }
                    
                    corpseTimes
                        .entrySet()
                        .stream()
                        .filter(e -> now - e.getValue() > 3 * buryDelay)
                        .map(e -> e.getKey())
                        .collect(Collectors.toList())
                        .forEach(id -> corpseTimes.remove(id))
                    ;
                } else if (burying) {
                    Utils.consolePrint("Don't have a shovel to bury with!");
                    burying = false;
                }
                
                if (grooming && groomingBrush > 0) {
                    final long now = System.currentTimeMillis();
                    groomedCreatures.entrySet().removeIf(
                        pair ->
                            now - pair.getValue() > groomIgnoreDuration ||
                            groomingFailed && groomingQueued.contains(pair.getKey())
                    );
                    groomingQueued.clear();
                    groomingFailed = false;
                    
                    List<CreatureCellRenderable> creatures = Utils.findCreatures(
                        (creature, data) ->
                            Utils.isGroomableCreature(creature) &&
                            Utils.isNearbyPlayer(creature) &&
                            !groomedCreatures.containsKey(creature.getId())
                    );
                    int actionsLeft = maxActions - creationWindow.getActionInUse();
                    for (CreatureCellRenderable creature: creatures) {
                        if(actionsLeft <= 0) break;
                        actionsLeft--;
                        final long id = creature.getId();
                        groomedCreatures.put(id, now);
                        groomingQueued.add(id);
                        serverConnection.sendAction(
                            groomingBrush,
                            new long[]{id},
                            PlayerAction.GROOM
                        );
                    }
                } else if(grooming) {
                    Utils.consolePrint("Don't have a brush to groom with!");
                    grooming = false;
                }

                if(notarget && System.currentTimeMillis() - lastNotarget > 2500) {
                    CreatureCellRenderable creature = null;
                    try
                    {
                        TargetWindow window = Utils.getField(WurmHelper.hud, "targetWindow");
                        creature = Utils.getField(window, "creature");
                    }
                    catch(Exception err)
                    {
                        Utils.consolePrint("Couldn't get target window or creature");
                        if(verbose)
                            Utils.consolePrint("=> %s", err);
                    }

                    if(creature != null && Utils.sqdistFromPlayer(creature) > notargetDistance * notargetDistance) {
                        lastNotarget = System.currentTimeMillis();
                        WurmHelper.hud.sendAction(PlayerAction.NO_TARGET, -10);
                    }
                }

                // read once: the console thread may clear it at any time
                final InventoryListComponent heatingInventory = lumpHeatingInventory;
                final boolean combining = lumpCombining;
                final List<InventoryMetaItem> lumps =
                    heatingInventory != null || combining ?
                        Utils.getInventoryItems("lump") :
                        Collections.emptyList()
                ;
                if(heatingInventory != null) {
                    InventoryListComponent playerInv = WurmHelper.hud.getInventoryWindow().getInventoryListComponent();
                    InventoryMetaItem playerInvRoot = Utils.getRootItem(playerInv);
                    final long playerInvId = playerInvRoot.getId();
                    final long smelterId = Utils.getRootItem(heatingInventory).getId();
                    
                    lumpTypesInInventory.clear();
                    for(InventoryMetaItem item: lumps) {
                        if(item.getTemperature() != 5) {
                            serverConnection.sendMoveSomeItems(smelterId, new long[]{item.getId()});
                            lumpDropTimes.put(item.getId(), System.currentTimeMillis());
                        } else {
                            lumpTypesInInventory.add(Utils.normalizeBaseName(item));
                        }
                    }

                    final long now = System.currentTimeMillis();
                    // entries older than the 60s cooldown no longer matter
                    lumpDropTimes.values().removeIf(dropTime -> now - dropTime >= 60_000);
                    long[] hotLumps = Utils.getItemIds(Utils.getInventoryItems(
                        heatingInventory,
                        item -> {
                            final String name = Utils.normalizeBaseName(item);
                            boolean shouldTake =
                                name.contains("lump") &&
                                (lumpCombining || !lumpTypesInInventory.contains(name)) &&
                                now - lumpDropTimes.getOrDefault(item.getId(), 0l) >= 60_000 &&
                                item.getTemperature() == 5
                            ;
                            if(shouldTake)
                                lumpTypesInInventory.add(name);
                            return shouldTake;
                        }
                    ));
                    if(hotLumps.length > 0)
                        serverConnection.sendMoveSomeItems(playerInvId, hotLumps);
                }

                if(combining) {
                    lumpsToCombine.values().forEach(a -> a.clear());
                    for(InventoryMetaItem lump: lumps) {
                        if(lump.getTemperature() != 5)
                            continue;

                        String lumpName = Utils.normalizeBaseName(lump);
                        ArrayList<Long> list = lumpsToCombine.get(lumpName);
                        if(list == null)
                            lumpsToCombine.put(lumpName, list = new ArrayList<>());
                        list.add(lump.getId());
                    }

                    lumpsToCombine.values().forEach(lumps -> {
                        if(lumps.size() < 2)
                            return;

                        // utter Java moment
                        // wtb slices, and generics that aren't lies to children
                        long[] lumpIds = lumps.stream().mapToLong(Long::longValue).toArray();
                        serverConnection.sendAction(lumpIds[0], lumpIds, PlayerAction.COMBINE);
                    });
                }
            }
            sleep(timeout);
        }
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("you will start dispelling")
                        || message.contains("You start to cast ")
                        || message.contains("you will start casting"),
                () -> successfullCastStart = true);
        registerEventProcessor(message -> message.contains("You cast ")
                        || message.contains("You fail to channel the ")
                        || message.contains("You must not move "),
                () -> successfullCasting = true);
        registerEventProcessor(message -> message.contains("until you can cast Wisdom of Vynora again."),
                () -> needWaitWov = true);
        registerEventProcessor(message -> message.contains("you will start drinking"),
                () -> successfullDrinkingStart = true);
        registerEventProcessor(message -> message.contains("The water is refreshing and it cools you down")
                        || message.contains("You are so bloated you cannot bring yourself to drink any thing"),
                () -> successfullDrinking = successfullDrinkingStart = true);
        registerEventProcessor(message -> message.contains("you will start eating"),
                () -> successfullEatingStart = true);
        registerEventProcessor(message -> message.contains("You are so full, you cannot possibly eat anything else")
                        || message.contains("You can't bring yourself to eat more right now"),
                () -> successfullEating = successfullEatingStart = true);
        registerEventProcessor(message -> message.contains("You start to pick the lock")
                        || message.contains("you will start picking lock"),
                () -> successfullStartOfLockpicking = true);
        registerEventProcessor(message -> message.contains("You fail to pick the lock"),
                () -> lockpickingResult = 0);
        registerEventProcessor(message -> message.contains("You pick the lock of"),
                () -> lockpickingResult = 1);
        registerEventProcessor(message -> message.contains("you will start attaching lock")
                        || message.contains("You lock the "),
                () -> successfullLocking = true);
        registerEventProcessor(message -> message.contains("is not locked."),
                () -> noLock = true);
        registerEventProcessor(message -> message.contains("you will start cleaning."),
                () -> successfullStartTrashCleaning = true);
        registerEventProcessor(message -> message.contains("You will start praying")
                        || message.contains("You start to pray")
                        || message.contains("you will start praying"),
                () -> successfullStartOfPraying = true);
        registerEventProcessor(message -> message.contains("you will start burning")
                        || message.contains("You fuel the"),
                () -> successfullStartOfBurning = true);
        registerEventProcessor(message -> message.contains("You start to sacrifice")
                        || message.contains("you will start sacrificing"),
                () -> successfullStartOfSacrificing = true);
        registerEventProcessor(
            message ->
                message.contains("shys away") ||
                message.contains("too far away to do that")
            ,
            () -> groomingFailed = true
        );
    }

    private void toggleByTargetId(String[] input, InputKey key, String targetName, LongConsumer toggle) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(key);
            return;
        }
        try {
            toggle.accept(Long.parseLong(input[0]));
        } catch (Exception e) {
            Utils.consolePrint("Can't get " + targetName + " id");
        }
    }

    private void handleTimeoutInput(String[] input, InputKey key, boolean enabled, String disabledMessage, IntConsumer changeTimeout) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(key);
            return;
        }
        if (enabled) {
            try {
                changeTimeout.accept(Integer.parseInt(input[0]));
            } catch (NumberFormatException e) {
                Utils.consolePrint("Wrong timeout value!");
            }
        } else {
            Utils.consolePrint(disabledMessage);
        }
    }

    private static int clampTimeout(int timeout) {
        if (timeout < 100) {
            Utils.consolePrint("Too small timeout!");
            return 100;
        }
        return timeout;
    }

    private void togglePrayingOnInventoryItem() {
        List<InventoryMetaItem> selectedItems = Utils.getSelectedItems();
        if (selectedItems == null || selectedItems.isEmpty()) {
            Utils.consolePrint("Select an item in your inventory first!");
            return;
        }
        InventoryMetaItem item = selectedItems.get(0);
        praying = true;
        this.altarId = item.getId();
        lastPrayer = 0;
        Utils.consolePrint(this.getClass().getSimpleName() + " praying on " + item.getDisplayName() + " is on!");
        changePrayerTimeout(1500000);
    }

    private void changeKindlingBurnsTimeout(int timeout) {
        kindlingBurningTimeout = clampTimeout(timeout);
        Utils.consolePrint("Current kindling burn timeout is " + kindlingBurningTimeout);
    }

    private void changePrayerTimeout(int timeout) {
        prayingTimeout = clampTimeout(timeout);
        Utils.consolePrint("Current prayer timeout is " + prayingTimeout);
    }
    
    private void setPrayerStamina(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(AssistantBot.InputKey.ps);
            return;
        }
        
        if (!praying) {
            Utils.consolePrint("Automatic praying is off!");
            return;
        }
        
        try {
            float newStamina = Float.parseFloat(input[0]);
            if (newStamina < 0f || newStamina > 1f) {
                Utils.consolePrint("Stamina must be a number between 0..1");
                return;
            }
            prayStamina = newStamina;
        } catch(NumberFormatException exception) {
            Utils.consolePrint("`%s` is not a real number", input[0]);
        }
    }
    
    private void setPrayerCount(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(AssistantBot.InputKey.pc);
            return;
        }
        
        if (!praying) {
            Utils.consolePrint("Automatic praying is off!");
            return;
        }
        
        try {
            int newCount = Integer.parseInt(input[0]);
            if (newCount < 0) {
                Utils.consolePrint("Prayer count must be a positive integer");
                return;
            }
            prayCount = newCount;
        } catch(NumberFormatException exception) {
            Utils.consolePrint("`%s` is not an integer", input[0]);
        }
    }

    private void changeTrashCleaningTimeout(int timeout) {
        trashCleaningTimeout = clampTimeout(timeout);
        Utils.consolePrint("Current trash cleaning timeout is " + trashCleaningTimeout);
    }

    private void changeSacrificeTimeout(int timeout) {
        sacrificeTimeout = clampTimeout(timeout);
        Utils.consolePrint("Current sacrifice timeout is " + sacrificeTimeout);
    }

    private void changeLockpickingTimeout(int timeout) {
        lockpickingTimeout = clampTimeout(timeout);
        Utils.consolePrint("Current lockpicking timeout is " + lockpickingTimeout);
    }

    private void toggleTrashCleaning(long trashBinId) {
        trashCleaning = !trashCleaning;
        if (trashCleaning) {
            if (trashBinId == 0) {
                try {
                    PickableUnit pickableUnit = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
                    if (pickableUnit == null || !pickableUnit.getHoverName().contains("trash heap")) {
                        Utils.consolePrint("Select trash bin!");
                        trashCleaning = false;
                        return;
                    }
                    trashBinId = pickableUnit.getId();
                } catch (Exception e) {
                    Utils.consolePrint("Can't turn trash cleaning on!");
                    trashCleaning = false;
                    return;
                }
            }
            this.trashBinId = trashBinId;
            lastTrashCleaning = 0;
            Utils.consolePrint(this.getClass().getSimpleName() + " trash cleaning is on!");
            changeTrashCleaningTimeout(5000);
        } else
            Utils.consolePrint(this.getClass().getSimpleName() + " trash cleaning is off!");

    }

    private void showSpellList() {
        Utils.consolePrint("Spell abbreviation");
        for(Enchant enchant : Enchant.values())
            Utils.consolePrint(enchant.name() + " " + enchant.abbreviation);
    }

    private void toggleAutocasting(String [] input) {
        casting = !casting;
        if (casting) {
            try {
                PaperDollInventory pdi = WurmHelper.hud.getPaperDollInventory();
                PaperDollSlot pds = Utils.getField(pdi, "bodyItem");
                bodyId = pds.getItemId();
                InventoryMetaItem statuette = Utils.locateToolItem("statuette of");
                if (statuette == null) {
                    Utils.consolePrint("Can't find a statuette in your inventory");
                    casting = false;
                    return;
                } else
                    Utils.consolePrint("Spellcasts are on!");
                statuetteId = statuette.getId();
                Enchant enchant = Enchant.DISPEL;
                if (input != null && input.length > 0) {
                    enchant = Enchant.getByAbbreviation(input[0]);
                }
                spellToCast = enchant;
                Utils.consolePrint("The spell " + spellToCast.name() + " will be casted");
            } catch (Exception e) {
                Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error - " + e.getMessage());
                Utils.consolePrint(e.toString());
                casting = false;
            }
        } else
            Utils.consolePrint("Spellcasts are off!");
    }

    private void togglePraying(long altarId) {
        praying = !praying;
        if (praying) {
            if (altarId == 0) {
                try {
                    PickableUnit pickableUnit = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
                    if (pickableUnit == null || !pickableUnit.getHoverName().toLowerCase().contains("altar")) {
                        Utils.consolePrint("Select an altar!");
                        praying = false;
                        return;
                    }
                    altarId = pickableUnit.getId();
                } catch (Exception e) {
                    Utils.consolePrint("Can't turn praying on!");
                    praying = false;
                    return;
                }
            }
            this.altarId = altarId;
            lastPrayer = 0;
            Utils.consolePrint(this.getClass().getSimpleName() + " praying is on!");
            changePrayerTimeout(1500000);
        } else
            Utils.consolePrint("Praying is off!");

    }

    private void toggleSacrificing(long altarId) {
        sacrificing = !sacrificing;
        if (sacrificing) {
            if (altarId == 0) {
                try {
                    PickableUnit pickableUnit = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
                    if (pickableUnit == null || !pickableUnit.getHoverName().toLowerCase().contains("altar")) {
                        Utils.consolePrint("Select an altar!");
                        sacrificing = false;
                        return;
                    }
                    altarId = pickableUnit.getId();
                } catch (Exception e) {
                    Utils.consolePrint("Can't turn sacrificing on!");
                    sacrificing = false;
                    return;
                }
            }
            sacrificeAltarId = altarId;
            lastSacrifice = 0;
            Utils.consolePrint(this.getClass().getSimpleName() + " sacrificing is on!");
            changeSacrificeTimeout(1230000);
        } else
            Utils.consolePrint("Sacrificing is off!");

    }

    private void toggleKindlingBurns(long forgeId) {
        kindlingBurning = !kindlingBurning;
        if (kindlingBurning) {
            if (forgeId == 0) {
                try {
                    PickableUnit pickableUnit = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
                    if (pickableUnit == null) {
                        Utils.consolePrint("Select a forge first!");
                        kindlingBurning = false;
                        return;
                    }
                    forgeId = pickableUnit.getId();
                } catch (Exception e) {
                    Utils.consolePrint("Can't turn kindling burning on!");
                    kindlingBurning = false;
                    return;
                }
            }
            this.forgeId = forgeId;
            lastBurning = 0;
            Utils.consolePrint(this.getClass().getSimpleName() + " kindling burning is on!");
            changeKindlingBurnsTimeout(10000);
        } else
            Utils.consolePrint("Kindling burning is off!");

    }

    private void toggleWOVCasting() {
        wovCasting = !wovCasting;
        if (wovCasting) {
            casting = false;
            try {
                PaperDollInventory pdi = WurmHelper.hud.getPaperDollInventory();
                PaperDollSlot pds = Utils.getField(pdi, "bodyItem");
                bodyId = pds.getItemId();
                InventoryMetaItem statuette = Utils.locateToolItem("statuette of");
                if (statuette == null || bodyId == 0) {
                    wovCasting = false;
                    Utils.consolePrint("Couldn't find a statuette in your inventory. casting is off");
                } else {
                    statuetteId = statuette.getId();
                    Utils.consolePrint("Wisdom of Vynora spellcasts are on!");
                }
            } catch (Exception e) {
                Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error - " + e.getMessage());
                Utils.consolePrint(e.toString());
                wovCasting = false;
            }
        } else
            Utils.consolePrint("Wisdom of Vynora casting is off!");
    }

    private void toggleLockpicking(long chestId) {
        lockpicking = !lockpicking;
        if (lockpicking) {
            if(chestId == 0) {
                int x = WurmHelper.hud.getWorld().getClient().getXMouse();
                int y = WurmHelper.hud.getWorld().getClient().getYMouse();
                long[] targets = WurmHelper.hud.getCommandTargetsFrom(x, y);
                if (targets != null && targets.length > 0) {
                    chestId = targets[0];
                } else {
                    lockpicking = false;
                    Utils.consolePrint("Can't find the target for lockpicking");
                    return;
                }
            }
            this.chestId = chestId;
            lastLockpicking = 0;
            Utils.consolePrint("Lockpicking is on!");
            changeLockpickingTimeout(610000);
        } else
            Utils.consolePrint("Lockpicking is off!");
    }

    private void toggleDrinking(long targetId) {
        drinking = !drinking;
        if (drinking) {
            if (targetId == 0) {
                int x = WurmHelper.hud.getWorld().getClient().getXMouse();
                int y = WurmHelper.hud.getWorld().getClient().getYMouse();
                long[] targets = WurmHelper.hud.getCommandTargetsFrom(x, y);
                if (targets != null && targets.length > 0) {
                    targetId = targets[0];
                } else {
                    drinking = false;
                    Utils.consolePrint("Can't find the target water");
                    return;
                }
            }
            waterId = targetId;
            Utils.consolePrint("Drinking is on!");
        } else
            Utils.consolePrint("Drinking is off!");
    }

    private void toggleEating() {
        eating = !eating;
        if (eating) {
            int x = WurmHelper.hud.getWorld().getClient().getXMouse();
            int y = WurmHelper.hud.getWorld().getClient().getYMouse();
            long[] targets = WurmHelper.hud.getCommandTargetsFrom(x, y);
            if (targets != null && targets.length > 0) {
                foodId = targets[0];
            } else {
                eating = false;
                Utils.consolePrint("Can't find the target food");
                return;
            }
            Utils.consolePrint("Eating is on!");
        } else
            Utils.consolePrint("Eating is off!");
    }
    
    private void toggleButchering() {
        butchering ^= true;
        Utils.consolePrint(
            "Bot will%s butcher corpses",
            butchering ?
                "" :
                " no longer"
        );
        
        if (butchering) {
            InventoryMetaItem item = Utils.locateToolItem("butchering knife");
            if(item == null)
            {
                Utils.consolePrint("Couldn't find a butchering knife, butchering is disabled.");
                butchering = false;
                return;
            }
            butcheringKnife = item.getId();
        } else
            butcheringKnife = -10;
    }
    
    private void toggleBurying() {
        burying ^= true;
        corpseTimes.clear();
        Utils.consolePrint(
            "Bot will%s bury corpses",
            burying ?
                "" :
                " no longer"
        );
        
        if (burying) {
            InventoryMetaItem item = Utils.locateToolItem("shovel");
            if(item == null)
            {
                Utils.consolePrint("Couldn't find a shovel, burying is disabled.");
                burying = false;
                return;
            }
            shovel = item.getId();
            
            item = Utils.locateToolItem("pickaxe");
            if(item == null)
                Utils.consolePrint("Couldn't find a pickaxe, bot will be unable to bury on rock");
            else
                pickaxe = item.getId();
            
            buryAll = false;
            toggleBuryAll(); // notify user that bury all is default
            addCorpseBlacklist("rift");
        } else {
            shovel = -10;
            pickaxe = -10;
        }
    }
    
    private void toggleBuryAll() {
        buryAll ^= true;
        Utils.consolePrint(
            "Bot will use %s",
            buryAll ?
                "\"Bury all\" action" :
                "normal bury action (items will spill onto ground!)"
        );
    }
    
    private void setBuryDelay(String[] input) {
        if (input == null || input.length == 0) {
            printInputKeyUsageString(AssistantBot.InputKey.bud);
            return;
        }
        
        try {
            long newBuryDelay = Long.parseLong(input[0]);
            buryDelay = Math.max(0, newBuryDelay);
            Utils.consolePrint("Bot will bury corpses after %d milliseconds", buryDelay);
        } catch (NumberFormatException e) {
            Utils.consolePrint("`%s` is not an integer", input[0]);
        }
    }
    
    private void addCorpseBlacklist(String keyword) {
        if (keyword != null) {
            if (keyword.equals(""))
            {
                Utils.consolePrint("Must specify something to blacklist!");
                return;
            }
            
            blacklistedCorpseNames.add(keyword.toLowerCase());
            Utils.consolePrint(
                "Bot will not bury corpses with names containing: %s",
                blacklistedCorpseNames
                    .stream()
                    .collect(Collectors.joining(", "))
            );
        } else {
            blacklistedCorpseNames.clear();
            Utils.consolePrint("Bot will bury all corpses");
        }
    }

    private void toggleGrooming() {
        grooming ^= true;
        groomedCreatures.clear();
        Utils.consolePrint(
            "Bot will%s groom creatures",
            grooming ?
                "" :
                " no longer"
        );
        
        if(grooming) {
            InventoryMetaItem item = Utils.locateToolItem("grooming brush");
            if(item == null) {
                Utils.consolePrint("Couldn't find a brush, grooming is disabled.");
                grooming = false;
                return;
            }
            groomingBrush = item.getId();
        } else {
            groomingBrush = -10;
        }
    }

    private void toggleNotarget() {
        notarget ^= true;
        Utils.consolePrint(
            "Bot will%s notarget far-away creatures",
            notarget ?
            "" :
            " no longer"
        );
    }

    private void toggleLumpHeating() {
        if(lumpHeatingInventory != null) {
            lumpHeatingInventory = null;
            Utils.consolePrint(
                "%s will no longer keep lumps heated",
                AssistantBot.class.getSimpleName()
            );
            return;
        }

        final int mouseX = WurmHelper.hud.getWorld().getClient().getXMouse();
        final int mouseY = WurmHelper.hud.getWorld().getClient().getYMouse();
        lumpHeatingInventory = Utils.getInventoryAtPoint(mouseX, mouseY);
        if(lumpHeatingInventory == null) {
            Utils.consolePrint("couldn't find any inventory under cursor");
        } else {
            Utils.consolePrint(
                "%s will use %s to keep lumps heated",
                AssistantBot.class.getSimpleName(),
                Utils.getRootItem(lumpHeatingInventory).getDisplayName()
            );
        }
    }

    private void toggleLumpCombining() {
        lumpCombining ^= true;
        Utils.consolePrint(
            "%s will %scombine lumps",
            AssistantBot.class.getSimpleName(),
            lumpCombining ? "" : "no longer "
        );
    }
    
    private void toggleVerbosity() {
        verbose = !verbose;
        if (verbose)
            Utils.consolePrint("Verbose mode is on!");
        else
            Utils.consolePrint("Verbose mode is off!");
    }
    
    private List<GroundItemCellRenderable> findCorpses(BiPredicate<GroundItemCellRenderable, GroundItemData> predicate) {
        predicate = ((BiPredicate<GroundItemCellRenderable, GroundItemData>)this::isCorpse)
            .and((item, data) -> Utils.isNearbyPlayer(item))
            .and(predicate)
        ;
        List<GroundItemCellRenderable> items = new ArrayList<>();
        try {
            ServerConnectionListenerClass sscc = WurmHelper.hud.getWorld().getServerConnection().getServerConnectionListener();
            Map<Long, GroundItemCellRenderable> groundItemsMap = Utils.getField(sscc, "groundItems");
            
            for(GroundItemCellRenderable item: groundItemsMap.values()) {
                GroundItemData data;
                try {
                    data = Utils.getField(item, "item");
                } catch (Exception e) {
                    Utils.consolePrint(e.toString());
                    continue;
                }
                
                if(predicate.test(item, data))
                    items.add(item);
            }
        } catch (Exception e) {
            Utils.consolePrint(e.toString());
        }
        return items;
    }
    
    private boolean isCorpse(GroundItemCellRenderable item, GroundItemData data) {
        return item.getHoverName().toLowerCase().startsWith("corpse of");
    }
    
    private boolean needsPickaxeToBury(GroundItemCellRenderable item) {
        if (item.getLayer() < 0)
            return true;
        final byte tileID = WurmHelper
            .hud
            .getWorld()
            .getNearTerrainBuffer()
            .getTileType(
                (int)(item.getXPos() / 4f),
                (int)(item.getYPos() / 4f)
            )
            .id
        ;
        return
            tileID == Tile.TILE_ROCK.id ||
            tileID == Tile.TILE_CLIFF.id
        ;
    }
    
    /**
     * How long a sent paver stays reserved even when no action shows as queued,
     * covering the round trip before the server reports the action
     */
    private static final long PAVER_PENDING_MS = 2000;
    /** item name -> (paver id -> time the pave action was sent) */
    private final HashMap<String, HashMap<Long, Long>> usedPavers = new HashMap<>();
    private void pave(boolean corner, String itemName)
    {
        if(itemName == null || itemName.length() == 0)
        {
            printInputKeyUsageString(InputKey.pave);
            return;
        }
        
        final InventoryListComponent plyInventory = WurmHelper.hud.getInventoryWindow().getInventoryListComponent();
        final List<InventoryMetaItem> items = Utils.getInventoryItems(plyInventory, itemName);
        final HashMap<Long, Long> used = usedPavers.computeIfAbsent(itemName, _k -> new HashMap<>());
        // A paver is reserved as soon as its action is sent, so repeated presses pick fresh items while
        // earlier ones are still queued. Consumed pavers leave the inventory and are forgotten. One that
        // is still here once nothing is queued was rejected by the server (e.g. a tile that can't be
        // paved) and becomes usable again.
        final HashSet<Long> inventoryIds = new HashSet<>();
        for (InventoryMetaItem item : items)
            inventoryIds.add(item.getId());
        final boolean busy = hasQueuedActions();
        final long now = System.currentTimeMillis();
        used.entrySet().removeIf(e -> !inventoryIds.contains(e.getKey())
            || (!busy && now - e.getValue() > PAVER_PENDING_MS));
        items.removeIf(item -> used.containsKey(item.getId()));
        if(items.isEmpty())
        {
            final String msg = String.format("Couldn't find any items named `%s`", itemName);
            WurmHelper.hud.addOnscreenMessage(msg, 1f, .5f, 0f, (byte)1);
            Utils.consolePrint(msg);
            return;
        }
        
        PickableUnit target = WurmHelper.hud.getWorld().getCurrentHoveredObject();
        if(target == null)
        {
            final String msg = "Not hovering over anything";
            WurmHelper.hud.addOnscreenMessage(msg, 1f, .5f, 0f, (byte)1);
            Utils.consolePrint(msg);
            return;
        }
        
        final PlayerAction action;
        if(itemName.equals("catseye"))
            action = PlayerAction.PLANT_SIGN;
        else
            action = corner ? PlayerAction.PAVE_CORNER : PlayerAction.PAVE;
        
        final long nextPaver = items.iterator().next().getId();
        used.put(nextPaver, now);
        WurmHelper.hud.getWorld().getServerConnection().sendAction(nextPaver, new long[]{target.getId()}, action);
    }
    
    private boolean hasQueuedActions()
    {
        try {
            return WurmHelper.hud.getCreationWindow().getActionInUse() > 0 || !isProgressZero();
        } catch (Exception e) {
            // can't tell, keep the reservations
            return true;
        }
    }
    
    private void paveClear()
    {
        usedPavers.clear();
        Utils.consolePrint("Paver usage history cleared");
    }
    
    private enum InputKey implements Bot.InputKey {
        w("Drinking", "Toggle automatic drinking of the liquid the user pointing at", ""),
        wid("Drink By ID", "Toggle automatic drinking of liquid with provided id", "id"),
        eat("Eating", "Toggle automatic eating of food the user is pointing at", ""),
        ls("List Spells", "Show the list of available spells for autocasting", ""),
        c("Clicks", "Toggle automatic casts of spells(if player has enough favor). Provide an optional spell abbreviation to change the default Dispel spell. " +
                "You can see the list of available spell with \"" + ls.name() + "\" key", "[spell_abbreviation]"),
        p("Praying", "Toggle automatic praying. The timeout between prayers can be configured separately.", ""),
        pt("Pray Timeout", "Change the timeout between prayers", "timeout(in milliseconds)"),
        pid("Pray By Altar ID", "Toggle automatic praying on altar with provided id", "id"),
        pis("Pray By Item Select", "Toggle automatic praying on selected inventory item (e.g. prayer beads). Select the item in your inventory first.", ""),
        ps("Prayer Stamina", "Set stamina threshold for praying.", "float"),
        pc("Prayer Count", "Set number of prayers to be queued. If 0 (default) then max per mind logic.", "integer"),
        s("Sacrificing", "Toggle automatic sacrificing. The timeout between sacrifices can be configured separately.", ""),
        st("Sacrifice Timeout", "Change the timeout between sacrifices", "timeout(in milliseconds)"),
        sid("Sacrifice By ID", "Toggle automatic sacrifices at altar with provided id", "id"),
        kb("Kindling Burn", "Toggle automatic burning of kindlings in player's inventory. " +
                AssistantBot.class.getSimpleName() + " will combine the kindlings and burn them using selected forge. " +
                "The timeout of burns can be configured separately", ""),
        kbt("Kindling Timeout", "Change the timeout between kindling burns", "timeout(in milliseconds)"),
        kbid("Kindling By Forge ID", "Toggle automatic kindling burns at forge with provided id", "id"),
        cwov("Wisdom of Vynora", "Toggle automatic casts of Wisdom of Vynora spell", ""),
        cleanup("Trash Cleaning", "Toggle automatic trash cleanings. The timeout between cleanings can be configured separately", ""),
        cleanupt("Trash Timeout", "Change the timeout between trash cleanings", "timeout(in milliseconds)"),
        cleanupid("Trash By ID", "Toggle automatic cleaning of items inside trash bin with provided id", "id"),
        l("Lockpicking", "Toggle automatic lockpicking. The target chest should be beneath the user's mouse", ""),
        lt("Lockpick Timeout", "Change the timeout between lockpickings", "timeout(in milliseconds)"),
        lid("Lockpick By ID", "Toggle automatic lockpicking of target chest with provided id", "id"),
        b("Butchering", "Toggle butchering of corpses on the ground", ""),
        bu("Burying", "Toggle burying of corpses on the ground", ""),
        bua("Bury All", "Toggle burying corpses with normal bury action vs bury all", ""),
        bud("Bury Delay", "Set delay before burying corpses (to allow other bots time to move items)", "msecs"),
        bub("Corpse Blacklist", "Add keyword to corpse blacklist -- e.g. to prevent bot burying Rift creatures which is set by default", "keyword"),
        bubc("Clear Blacklist", "Clear corpse blacklist", ""),
        groom("Grooming", "Toggle grooming of creatures", ""),
        v("Verbose", "Toggle verbose mode. In verbose mode the " + AssistantBot.class.getSimpleName() + " will output additional info to the console", ""),
        pave("Paving", "Paving helper that activates new materials", "item name"),
        pavec("Paving Corners", "Pave command for tile corners", "item name"),
        paveclear("Clear Paving", "Forget which items have already been used for paving", ""),
        notarget("No Target", "Automatically clear targeted creature if it is too far away", ""),
        lumpheating("Lump Heating", "Toggle automatic lump heating by swapping lumps into/out of hovered container", ""),
        lumpcombine("Lump Combining", "Toggle automatic combining of (hot) lumps", ""),
        ;

        private final KeyInfo keyInfo;

        InputKey(String fullName, String description, String usage) {
            keyInfo = new KeyInfo(fullName, description, usage);
        }

        @Override
        public KeyInfo keyInfo() {
            return keyInfo;
        }
    }

    enum Enchant{
        BLESS(10, PlayerAction.BLESS, "b"),
        MORNINGFOG(5, PlayerAction.MORNING_FOG, "mf"),
        DISPEL(10, PlayerAction.DISPEL, "d"),
        LIGHT_TOKEN(5, PlayerAction.LIGHT_TOKEN, "lt");

        int favorCap;
        PlayerAction playerAction;
        String abbreviation;
        Enchant(int favorCap, PlayerAction playerAction, String abbreviation) {
            this.favorCap = favorCap;
            this.playerAction = playerAction;
            this.abbreviation = abbreviation;
        }
        static Enchant getByAbbreviation(String abbreviation) {
            for(Enchant enchant : values())
                if (enchant.abbreviation.equals(abbreviation))
                    return enchant;
            return DISPEL;
        }
    }
}
