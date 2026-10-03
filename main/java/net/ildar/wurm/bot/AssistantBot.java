package net.ildar.wurm.bot;

import java.util.ArrayList;
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
    private volatile Enchant spellToCast = Enchant.DISPEL;
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
    private volatile long lockpickingTimeout = 610000;
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
    private volatile long trashCleaningTimeout = 5000;
    private long lastTrashCleaning;
    private long trashBinId;
    private volatile String trashBinName;
    private volatile boolean successfullStartTrashCleaning;

    private volatile boolean praying;
    private long altarId;
    private volatile String altarName;
    private long lastPrayer;
    private volatile long prayingTimeout = 1500000;
    private volatile int prayCount = 0;
    private volatile float prayStamina = 0.5f;
    private volatile boolean successfullStartOfPraying;

    private volatile boolean sacrificing;
    private long sacrificeAltarId;
    private volatile String sacrificeAltarName;
    private long lastSacrifice;
    private volatile long sacrificeTimeout = 1230000;
    private volatile boolean successfullStartOfSacrificing;
    
    private volatile boolean butchering;
    private long butcheringKnife = -10;
    
    private volatile boolean burying;
    private long shovel = -10;
    private long pickaxe = -10;
    private volatile boolean buryAll = true;
    private volatile long buryDelay = 2500;
    // modified by the console thread while the bot thread iterates them
    private final Map<Long, Long> corpseTimes = new ConcurrentHashMap<>();
    private final Set<String> blacklistedCorpseNames = ConcurrentHashMap.newKeySet();

    private volatile boolean kindlingBurning;
    private long forgeId;
    private volatile String forgeName;
    private long lastBurning;
    private volatile long kindlingBurningTimeout = 10000;
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
        // the corpses of rift creatures can't be buried, skip them unless the user clears the blacklist
        blacklistedCorpseNames.add("rift");
        registerInputHandler(AssistantBot.InputKey.w, input -> toggleDrinking());
        registerInputHandler(AssistantBot.InputKey.wid,
            input -> enableByTargetId(input, InputKey.wid, "water", this::enableDrinking));
        registerInputHandler(AssistantBot.InputKey.eat, input -> toggleEating());
        registerInputHandler(AssistantBot.InputKey.ls, input -> showSpellList());
        registerInputHandler(AssistantBot.InputKey.c, this::handleCasting);
        registerInputHandler(AssistantBot.InputKey.p, input -> togglePraying());
        registerInputHandler(AssistantBot.InputKey.pt,
            input -> handleTimeoutInput(input, InputKey.pt, this::changePrayerTimeout));
        registerInputHandler(AssistantBot.InputKey.pid,
            input -> enableByTargetId(input, InputKey.pid, "altar", this::enablePraying));
        registerInputHandler(AssistantBot.InputKey.pis, input -> prayOnSelectedInventoryItem());
        registerInputHandler(AssistantBot.InputKey.ps, this::setPrayerStamina);
        registerInputHandler(AssistantBot.InputKey.pc, this::setPrayerCount);
        registerInputHandler(AssistantBot.InputKey.s, input -> toggleSacrificing());
        registerInputHandler(AssistantBot.InputKey.st,
            input -> handleTimeoutInput(input, InputKey.st, this::changeSacrificeTimeout));
        registerInputHandler(AssistantBot.InputKey.sid,
            input -> enableByTargetId(input, InputKey.sid, "altar", this::enableSacrificing));
        registerInputHandler(AssistantBot.InputKey.kb, input -> toggleKindlingBurns());
        registerInputHandler(AssistantBot.InputKey.kbt,
            input -> handleTimeoutInput(input, InputKey.kbt, this::changeKindlingBurnsTimeout));
        registerInputHandler(AssistantBot.InputKey.kbid,
            input -> enableByTargetId(input, InputKey.kbid, "forge", this::enableKindlingBurns));
        registerInputHandler(AssistantBot.InputKey.cwov, input -> toggleWOVCasting());
        registerInputHandler(AssistantBot.InputKey.cleanup, input -> toggleTrashCleaning());
        registerInputHandler(AssistantBot.InputKey.cleanupt,
            input -> handleTimeoutInput(input, InputKey.cleanupt, this::changeTrashCleaningTimeout));
        registerInputHandler(AssistantBot.InputKey.cleanupid,
            input -> enableByTargetId(input, InputKey.cleanupid, "trash bin", this::enableTrashCleaning));
        registerInputHandler(AssistantBot.InputKey.l, input -> toggleLockpicking());
        registerInputHandler(AssistantBot.InputKey.lt,
            input -> handleTimeoutInput(input, InputKey.lt, this::changeLockpickingTimeout));
        registerInputHandler(AssistantBot.InputKey.lid,
            input -> enableByTargetId(input, InputKey.lid, "chest", this::enableLockpicking));
        registerInputHandler(AssistantBot.InputKey.b, input -> toggleButchering());
        registerInputHandler(AssistantBot.InputKey.bu, input -> toggleBurying());
        registerInputHandler(AssistantBot.InputKey.bua, input -> toggleBuryAll());
        registerInputHandler(AssistantBot.InputKey.bud, this::setBuryDelay);
        registerInputHandler(AssistantBot.InputKey.bub, this::addCorpseBlacklist);
        registerInputHandler(AssistantBot.InputKey.bubc, input -> clearCorpseBlacklist());
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
                    if (!successfullStartOfLockpicking && !noLock) {
                        lastLockpicking = System.currentTimeMillis();
                        continue;
                    }
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
                        // the console thread may clear corpseTimes at any time, so don't read it back
                        final Long firstSeen = corpseTimes.putIfAbsent(id, now);
                        
                        if (now - (firstSeen != null ? firstSeen : now) > buryDelay) {
                            serverConnection.sendAction(
                                needsPickaxeToBury(item) ? pickaxe : shovel,
                                new long[]{item.getId()},
                                buryAll ? PlayerAction.BURY_ALL : PlayerAction.BURY
                            );
                            if (++actions >= maxActions)
                                break;
                        }
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

                try {
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
                        InventoryMetaItem smelterRoot = Utils.getRootItem(heatingInventory);
                        if(playerInvRoot != null && smelterRoot != null) {
                            final long playerInvId = playerInvRoot.getId();
                            final long smelterId = smelterRoot.getId();

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
                        } else {
                            Utils.consolePrint("Couldn't find the player or smelter inventory root item, skipping lump heating");
                        }
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

                        lumpsToCombine.values().forEach(group -> {
                            if(group.size() < 2)
                                return;

                            // utter Java moment
                            // wtb slices, and generics that aren't lies to children
                            long[] lumpIds = group.stream().mapToLong(Long::longValue).toArray();
                            serverConnection.sendAction(lumpIds[0], lumpIds, PlayerAction.COMBINE);
                        });
                    }
                } catch (Exception e) {
                    Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error - " + e.getMessage());
                    Utils.consolePrint(e.toString());
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

    @Override
    void describeSettings(List<String> lines) {
        lines.add("Casting: " + onOff(casting) + " (spell: " + spellToCast.displayName() + ")");
        lines.add("Wisdom of Vynora casting: " + onOff(wovCasting));
        lines.add("Drinking: " + onOff(drinking) + (drinking ? " (target id: " + waterId + ")" : ""));
        lines.add("Eating: " + onOff(eating) + (eating ? " (target id: " + foodId + ")" : ""));
        lines.add("Praying: " + onOff(praying)
                + " (" + (praying ? "altar: " + describeTarget(altarName, altarId) + ", " : "")
                + "timeout: " + prayingTimeout + " ms, stamina: " + prayStamina
                + ", count: " + (prayCount == 0 ? "max" : String.valueOf(prayCount)) + ")");
        lines.add("Sacrificing: " + onOff(sacrificing)
                + " (" + (sacrificing ? "altar: " + describeTarget(sacrificeAltarName, sacrificeAltarId) + ", " : "")
                + "timeout: " + sacrificeTimeout + " ms)");
        lines.add("Kindling burning: " + onOff(kindlingBurning)
                + " (" + (kindlingBurning ? "forge: " + describeTarget(forgeName, forgeId) + ", " : "")
                + "timeout: " + kindlingBurningTimeout + " ms)");
        lines.add("Trash cleaning: " + onOff(trashCleaning)
                + " (" + (trashCleaning ? "trash bin: " + describeTarget(trashBinName, trashBinId) + ", " : "")
                + "timeout: " + trashCleaningTimeout + " ms)");
        lines.add("Lockpicking: " + onOff(lockpicking)
                + " (" + (lockpicking ? "target id: " + chestId + ", " : "")
                + "timeout: " + lockpickingTimeout + " ms)");
        lines.add("Butchering: " + onOff(butchering));
        lines.add("Burying: " + onOff(burying) + " (bury all: " + onOff(buryAll) + ", delay: " + buryDelay + " ms)");
        lines.add("Corpse blacklist: " + (blacklistedCorpseNames.isEmpty() ? "empty" : String.join(", ", blacklistedCorpseNames)));
        lines.add("Grooming: " + onOff(grooming));
        lines.add("No target: " + onOff(notarget));
        InventoryListComponent heatingInventory = lumpHeatingInventory;
        String heatingName = null;
        if (heatingInventory != null) {
            try {
                heatingName = Utils.getRootItem(heatingInventory).getDisplayName();
            } catch (Exception ignored) {
            }
        }
        lines.add("Lump heating: " + onOff(heatingInventory != null)
                + (heatingInventory != null ? " (container: " + (heatingName != null ? heatingName : "unknown") + ")" : ""));
        lines.add("Lump combining: " + onOff(lumpCombining));
        lines.add("Verbose: " + onOff(verbose));
    }

    private static String describeTarget(String name, long id) {
        return name != null ? name + " (id " + id + ")" : "id " + id;
    }

    /**
     * Turn a feature on for the target with the given id. Calling it again while the feature is on just changes the target
     */
    private void enableByTargetId(String[] input, InputKey key, String targetName, LongConsumer enable) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(key);
            return;
        }
        long id;
        try {
            id = Long.parseLong(input[0]);
        } catch (NumberFormatException e) {
            Utils.consolePrint("`" + input[0] + "` is not a valid " + targetName + " id");
            printInputKeyUsageString(key);
            return;
        }
        if (id <= 0) {
            Utils.consolePrint("`" + input[0] + "` is not a valid " + targetName + " id");
            printInputKeyUsageString(key);
            return;
        }
        enable.accept(id);
    }

    private void handleTimeoutInput(String[] input, InputKey key, IntConsumer changeTimeout) {
        Integer timeout = parseIntArg(input, key, 100, Integer.MAX_VALUE);
        if (timeout != null)
            changeTimeout.accept(timeout);
    }

    /**
     * @return the object selected in the select bar if its name contains the filter (null filter accepts any), otherwise null
     */
    private PickableUnit getSelectedUnit(String nameFilter) {
        try {
            PickableUnit pickableUnit = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
            if (pickableUnit == null)
                return null;
            if (nameFilter != null && !pickableUnit.getHoverName().toLowerCase().contains(nameFilter))
                return null;
            return pickableUnit;
        } catch (Exception e) {
            Utils.consolePrint("Can't get the selected object - " + e.getMessage());
            return null;
        }
    }

    /**
     * @return the id of the object under the mouse cursor, or 0 when there is none
     */
    private long getTargetUnderMouse() {
        int x = WurmHelper.hud.getWorld().getClient().getXMouse();
        int y = WurmHelper.hud.getWorld().getClient().getYMouse();
        long[] targets = WurmHelper.hud.getCommandTargetsFrom(x, y);
        if (targets != null && targets.length > 0)
            return targets[0];
        return 0;
    }

    private void prayOnSelectedInventoryItem() {
        List<InventoryMetaItem> selectedItems = Utils.getSelectedItems();
        if (selectedItems == null || selectedItems.isEmpty()) {
            Utils.consolePrint("Select an item in your inventory first!");
            return;
        }
        InventoryMetaItem item = selectedItems.get(0);
        this.altarId = item.getId();
        this.altarName = item.getDisplayName();
        lastPrayer = 0;
        praying = true;
        Utils.feedback(this.getClass().getSimpleName() + " praying on " + item.getDisplayName() + " is on!");
    }

    private void changeKindlingBurnsTimeout(int timeout) {
        kindlingBurningTimeout = timeout;
        Utils.consolePrint("Current kindling burn timeout is " + kindlingBurningTimeout + " milliseconds");
    }

    private void changePrayerTimeout(int timeout) {
        prayingTimeout = timeout;
        Utils.consolePrint("Current prayer timeout is " + prayingTimeout + " milliseconds");
    }

    private void setPrayerStamina(String[] input) {
        Float newStamina = parseFloatArg(input, AssistantBot.InputKey.ps, 0f, 1f);
        if (newStamina == null)
            return;
        prayStamina = newStamina;
        Utils.consolePrint("The bot will pray when stamina is at least " + prayStamina);
    }

    private void setPrayerCount(String[] input) {
        Integer newCount = parseIntArg(input, AssistantBot.InputKey.pc, 0, 1000);
        if (newCount == null)
            return;
        prayCount = newCount;
        if (prayCount == 0)
            Utils.consolePrint("The bot will queue as many prayers as possible");
        else
            Utils.consolePrint("The bot will queue " + prayCount + " prayer(s) at a time");
    }

    private void changeTrashCleaningTimeout(int timeout) {
        trashCleaningTimeout = timeout;
        Utils.consolePrint("Current trash cleaning timeout is " + trashCleaningTimeout + " milliseconds");
    }

    private void changeSacrificeTimeout(int timeout) {
        sacrificeTimeout = timeout;
        Utils.consolePrint("Current sacrifice timeout is " + sacrificeTimeout + " milliseconds");
    }

    private void changeLockpickingTimeout(int timeout) {
        lockpickingTimeout = timeout;
        Utils.consolePrint("Current lockpicking timeout is " + lockpickingTimeout + " milliseconds");
    }

    private void toggleTrashCleaning() {
        if (trashCleaning) {
            trashCleaning = false;
            Utils.feedback(this.getClass().getSimpleName() + " trash cleaning is off!");
        } else
            enableTrashCleaning(0);
    }

    /**
     * @param trashBinId the trash bin to clean, 0 to take the selected one
     */
    private void enableTrashCleaning(long trashBinId) {
        String name = null;
        if (trashBinId == 0) {
            PickableUnit pickableUnit = getSelectedUnit("trash heap");
            if (pickableUnit == null) {
                Utils.consolePrint("Select trash bin!");
                return;
            }
            trashBinId = pickableUnit.getId();
            name = pickableUnit.getHoverName();
        }
        this.trashBinId = trashBinId;
        this.trashBinName = name;
        lastTrashCleaning = 0;
        trashCleaning = true;
        Utils.feedback(this.getClass().getSimpleName() + " trash cleaning is on! Trash bin: " + describeTarget(name, trashBinId));
    }

    private void showSpellList() {
        Utils.consolePrint("Spell - abbreviation");
        for(Enchant enchant : Enchant.values())
            Utils.consolePrint(enchant.displayName() + " - " + enchant.abbreviation);
    }

    /**
     * Without a spell: toggle casting. With a spell: choose it and make sure casting is on
     */
    private void handleCasting(String[] input) {
        Enchant enchant = null;
        String spellName = joinArgs(input);
        if (spellName != null) {
            enchant = Enchant.find(spellName);
            if (enchant == null) {
                Utils.consolePrint("Unknown spell `" + spellName + "`, see " + InputKey.ls.name());
                return;
            }
        }
        if (enchant == null && casting) {
            casting = false;
            Utils.feedback("Spellcasts are off!");
            return;
        }
        if (enchant != null)
            spellToCast = enchant;
        if (casting) {
            Utils.feedback("The spell " + spellToCast.displayName() + " will be cast");
            return;
        }
        try {
            PaperDollInventory pdi = WurmHelper.hud.getPaperDollInventory();
            PaperDollSlot pds = Utils.getField(pdi, "bodyItem");
            bodyId = pds.getItemId();
            InventoryMetaItem statuette = Utils.locateToolItem("statuette of");
            if (statuette == null) {
                Utils.consolePrint("Can't find a statuette in your inventory");
                return;
            }
            statuetteId = statuette.getId();
            wovCasting = false;
            casting = true;
            Utils.feedback("Spellcasts are on! The spell " + spellToCast.displayName() + " will be cast");
        } catch (Exception e) {
            Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error - " + e.getMessage());
            Utils.consolePrint(e.toString());
            casting = false;
        }
    }

    private void togglePraying() {
        if (praying) {
            praying = false;
            Utils.feedback("Praying is off!");
        } else
            enablePraying(0);
    }

    /**
     * @param altarId the altar to pray at, 0 to take the selected one
     */
    private void enablePraying(long altarId) {
        String name = null;
        if (altarId == 0) {
            PickableUnit pickableUnit = getSelectedUnit("altar");
            if (pickableUnit == null) {
                Utils.consolePrint("Select an altar!");
                return;
            }
            altarId = pickableUnit.getId();
            name = pickableUnit.getHoverName();
        }
        this.altarId = altarId;
        this.altarName = name;
        lastPrayer = 0;
        praying = true;
        Utils.feedback(this.getClass().getSimpleName() + " praying is on! Altar: " + describeTarget(name, altarId));
    }

    private void toggleSacrificing() {
        if (sacrificing) {
            sacrificing = false;
            Utils.feedback("Sacrificing is off!");
        } else
            enableSacrificing(0);
    }

    /**
     * @param altarId the altar to sacrifice at, 0 to take the selected one
     */
    private void enableSacrificing(long altarId) {
        String name = null;
        if (altarId == 0) {
            PickableUnit pickableUnit = getSelectedUnit("altar");
            if (pickableUnit == null) {
                Utils.consolePrint("Select an altar!");
                return;
            }
            altarId = pickableUnit.getId();
            name = pickableUnit.getHoverName();
        }
        sacrificeAltarId = altarId;
        sacrificeAltarName = name;
        lastSacrifice = 0;
        sacrificing = true;
        Utils.feedback(this.getClass().getSimpleName() + " sacrificing is on! Altar: " + describeTarget(name, altarId));
    }

    private void toggleKindlingBurns() {
        if (kindlingBurning) {
            kindlingBurning = false;
            Utils.feedback("Kindling burning is off!");
        } else
            enableKindlingBurns(0);
    }

    /**
     * @param forgeId the forge to burn kindling in, 0 to take the selected one
     */
    private void enableKindlingBurns(long forgeId) {
        String name = null;
        if (forgeId == 0) {
            PickableUnit pickableUnit = getSelectedUnit(null);
            if (pickableUnit == null) {
                Utils.consolePrint("Select a forge first!");
                return;
            }
            forgeId = pickableUnit.getId();
            name = pickableUnit.getHoverName();
        }
        this.forgeId = forgeId;
        this.forgeName = name;
        lastBurning = 0;
        kindlingBurning = true;
        Utils.feedback(this.getClass().getSimpleName() + " kindling burning is on! Forge: " + describeTarget(name, forgeId));
    }

    private void toggleWOVCasting() {
        if (wovCasting) {
            wovCasting = false;
            Utils.feedback("Wisdom of Vynora casting is off!");
            return;
        }
        try {
            PaperDollInventory pdi = WurmHelper.hud.getPaperDollInventory();
            PaperDollSlot pds = Utils.getField(pdi, "bodyItem");
            bodyId = pds.getItemId();
            InventoryMetaItem statuette = Utils.locateToolItem("statuette of");
            if (statuette == null || bodyId == 0) {
                Utils.consolePrint("Couldn't find a statuette in your inventory. Wisdom of Vynora casting is off");
                return;
            }
            statuetteId = statuette.getId();
            boolean wasCasting = casting;
            casting = false;
            wovCasting = true;
            Utils.feedback("Wisdom of Vynora spellcasts are on!" + (wasCasting ? " Other spellcasts are off" : ""));
        } catch (Exception e) {
            Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error - " + e.getMessage());
            Utils.consolePrint(e.toString());
            wovCasting = false;
        }
    }

    private void toggleLockpicking() {
        if (lockpicking) {
            lockpicking = false;
            Utils.feedback("Lockpicking is off!");
        } else
            enableLockpicking(0);
    }

    /**
     * @param chestId the chest to pick, 0 to take the one under the mouse
     */
    private void enableLockpicking(long chestId) {
        if (chestId == 0) {
            chestId = getTargetUnderMouse();
            if (chestId == 0) {
                Utils.consolePrint("Can't find the target for lockpicking");
                return;
            }
        }
        this.chestId = chestId;
        lastLockpicking = 0;
        lockpicking = true;
        Utils.feedback("Lockpicking is on! Target id: " + chestId);
    }

    private void toggleDrinking() {
        if (drinking) {
            drinking = false;
            Utils.feedback("Drinking is off!");
        } else
            enableDrinking(0);
    }

    /**
     * @param targetId the liquid to drink, 0 to take the one under the mouse
     */
    private void enableDrinking(long targetId) {
        if (targetId == 0) {
            targetId = getTargetUnderMouse();
            if (targetId == 0) {
                Utils.consolePrint("Can't find the target water");
                return;
            }
        }
        waterId = targetId;
        drinking = true;
        Utils.feedback("Drinking is on! Target id: " + targetId);
    }

    private void toggleEating() {
        if (eating) {
            eating = false;
            Utils.feedback("Eating is off!");
            return;
        }
        long targetId = getTargetUnderMouse();
        if (targetId == 0) {
            Utils.consolePrint("Can't find the target food");
            return;
        }
        foodId = targetId;
        eating = true;
        Utils.feedback("Eating is on! Target id: " + targetId);
    }

    private void toggleButchering() {
        if (butchering) {
            butchering = false;
            butcheringKnife = -10;
            Utils.feedback("Bot will no longer butcher corpses");
            return;
        }
        InventoryMetaItem item = Utils.locateToolItem("butchering knife");
        if (item == null) {
            Utils.consolePrint("Couldn't find a butchering knife, butchering is disabled.");
            return;
        }
        butcheringKnife = item.getId();
        butchering = true;
        Utils.feedback("Bot will butcher corpses");
    }

    private void toggleBurying() {
        corpseTimes.clear();
        if (burying) {
            burying = false;
            shovel = -10;
            pickaxe = -10;
            Utils.feedback("Bot will no longer bury corpses");
            return;
        }
        InventoryMetaItem item = Utils.locateToolItem("shovel");
        if (item == null) {
            Utils.consolePrint("Couldn't find a shovel, burying is disabled.");
            return;
        }
        shovel = item.getId();

        item = Utils.locateToolItem("pickaxe");
        if (item == null) {
            pickaxe = -10;
            Utils.consolePrint("Couldn't find a pickaxe, bot will be unable to bury on rock");
        } else
            pickaxe = item.getId();
        burying = true;
        Utils.feedback("Bot will bury corpses using %s", buryAll ? "\"Bury all\" action" : "normal bury action");
    }

    private void toggleBuryAll() {
        buryAll ^= true;
        Utils.feedback(
            "Bot will use %s",
            buryAll ?
                "\"Bury all\" action" :
                "normal bury action (items will spill onto ground!)"
        );
    }

    private void setBuryDelay(String[] input) {
        Integer newBuryDelay = parseIntArg(input, AssistantBot.InputKey.bud, 0, Integer.MAX_VALUE);
        if (newBuryDelay == null)
            return;
        buryDelay = newBuryDelay;
        Utils.consolePrint("Bot will bury corpses after %d milliseconds", buryDelay);
    }

    private void addCorpseBlacklist(String[] input) {
        List<String> keywords = parseNameList(input);
        if (keywords.isEmpty()) {
            Utils.consolePrint("Must specify something to blacklist!");
            printInputKeyUsageString(InputKey.bub);
            return;
        }
        for (String keyword : keywords)
            blacklistedCorpseNames.add(keyword.toLowerCase());
        Utils.consolePrint(
            "Bot will not bury corpses with names containing: %s",
            String.join(", ", blacklistedCorpseNames)
        );
    }

    private void clearCorpseBlacklist() {
        blacklistedCorpseNames.clear();
        Utils.consolePrint("Corpse blacklist cleared, bot will bury all corpses");
    }

    private void toggleGrooming() {
        groomedCreatures.clear();
        if (grooming) {
            grooming = false;
            groomingBrush = -10;
            Utils.feedback("Bot will no longer groom creatures");
            return;
        }
        InventoryMetaItem item = Utils.locateToolItem("grooming brush");
        if (item == null) {
            Utils.consolePrint("Couldn't find a brush, grooming is disabled.");
            return;
        }
        groomingBrush = item.getId();
        grooming = true;
        Utils.feedback("Bot will groom creatures");
    }

    private void toggleNotarget() {
        notarget ^= true;
        Utils.feedback(
            "Bot will%s notarget far-away creatures",
            notarget ?
            "" :
            " no longer"
        );
    }

    private void toggleLumpHeating() {
        if(lumpHeatingInventory != null) {
            lumpHeatingInventory = null;
            Utils.feedback(
                "%s will no longer keep lumps heated",
                AssistantBot.class.getSimpleName()
            );
            return;
        }

        final int mouseX = WurmHelper.hud.getWorld().getClient().getXMouse();
        final int mouseY = WurmHelper.hud.getWorld().getClient().getYMouse();
        InventoryListComponent inventory = Utils.getInventoryAtPoint(mouseX, mouseY);
        if(inventory == null) {
            Utils.consolePrint("Couldn't find any inventory under cursor");
        } else {
            lumpHeatingInventory = inventory;
            Utils.feedback(
                "%s will use %s to keep lumps heated",
                AssistantBot.class.getSimpleName(),
                Utils.getRootItem(inventory).getDisplayName()
            );
        }
    }

    private void toggleLumpCombining() {
        lumpCombining ^= true;
        Utils.feedback(
            "%s will %scombine lumps",
            AssistantBot.class.getSimpleName(),
            lumpCombining ? "" : "no longer "
        );
    }

    private void toggleVerbosity() {
        verbose = !verbose;
        Utils.feedback("Verbose mode is " + onOff(verbose) + "!");
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
        w("Drinking", "Toggle automatic drinking of the liquid the user is pointing at", ""),
        wid("Drink By ID", "Turn on automatic drinking of the liquid with the provided id (changes the target if drinking is already on)", "<id>"),
        eat("Eating", "Toggle automatic eating of the food the user is pointing at", ""),
        ls("List Spells", "Show the list of available spells for autocasting", ""),
        c("Casting", "Toggle automatic casts of spells (if the player has enough favor). Needs a statuette in the inventory. " +
                "With a spell abbreviation the spell is chosen and casting is turned on (or kept on). The default spell is Dispel. " +
                "You can see the list of available spells with the \"" + ls.name() + "\" key", "[<spell>]"),
        p("Praying", "Toggle automatic praying at the selected altar. The timeout between prayers can be configured separately.", ""),
        pt("Prayer Timeout", "Change the timeout between prayers. Default is 1500000 (25 minutes)", "<milliseconds>"),
        pid("Pray By Altar ID", "Turn on automatic praying at the altar with the provided id (changes the altar if praying is already on)", "<id>"),
        pis("Pray On Selected Item", "Turn on automatic praying on the selected inventory item (e.g. prayer beads). Select the item in your inventory first.", ""),
        ps("Prayer Stamina", "Set the minimal stamina (0 to 1) needed for praying. Default is 0.5", "<stamina>"),
        pc("Prayer Count", "Set the number of prayers to be queued. If 0 (default) then as many as the action queue allows", "<count>"),
        s("Sacrificing", "Toggle automatic sacrificing at the selected altar. The timeout between sacrifices can be configured separately.", ""),
        st("Sacrifice Timeout", "Change the timeout between sacrifices. Default is 1230000 (20.5 minutes)", "<milliseconds>"),
        sid("Sacrifice By ID", "Turn on automatic sacrifices at the altar with the provided id (changes the altar if sacrificing is already on)", "<id>"),
        kb("Kindling Burning", "Toggle automatic burning of kindlings in player's inventory. " +
                AssistantBot.class.getSimpleName() + " will combine the kindlings and burn them using the selected forge. " +
                "The timeout of burns can be configured separately", ""),
        kbt("Kindling Timeout", "Change the timeout between kindling burns. Default is 10000", "<milliseconds>"),
        kbid("Kindling By Forge ID", "Turn on automatic kindling burns at the forge with the provided id (changes the forge if burning is already on)", "<id>"),
        cwov("Wisdom of Vynora", "Toggle automatic casts of Wisdom of Vynora spell. Turns other spellcasts off", ""),
        cleanup("Trash Cleaning", "Toggle automatic cleaning of the selected trash heap. The timeout between cleanings can be configured separately", ""),
        cleanupt("Trash Timeout", "Change the timeout between trash cleanings. Default is 5000", "<milliseconds>"),
        cleanupid("Trash By ID", "Turn on automatic cleaning of the trash bin with the provided id (changes the trash bin if cleaning is already on)", "<id>"),
        l("Lockpicking", "Toggle automatic lockpicking. The target chest should be beneath the user's mouse", ""),
        lt("Lockpick Timeout", "Change the timeout between lockpickings. Default is 610000", "<milliseconds>"),
        lid("Lockpick By ID", "Turn on automatic lockpicking of the target chest with the provided id (changes the chest if lockpicking is already on)", "<id>"),
        b("Butchering", "Toggle butchering of corpses on the ground", ""),
        bu("Burying", "Toggle burying of corpses on the ground", ""),
        bua("Bury All", "Toggle burying corpses with the \"Bury all\" action (default) vs the normal bury action", ""),
        bud("Bury Delay", "Set the delay before burying corpses (to allow other bots time to move items). Default is 2500", "<milliseconds>"),
        bub("Add Corpse Blacklist", "Add keywords (comma separated) to the corpse blacklist. Corpses with names containing them are not buried. " +
                "\"rift\" is in the blacklist by default", "<keyword>"),
        bubc("Clear Corpse Blacklist", "Clear the corpse blacklist", ""),
        groom("Grooming", "Toggle grooming of creatures", ""),
        v("Verbose", "Toggle verbose mode. In verbose mode the " + AssistantBot.class.getSimpleName() + " will output additional info to the console", ""),
        pave("Pave", "Pave the hovered tile with an unused item of the given name from the inventory", "<item name>"),
        pavec("Pave Corner", "Pave the hovered tile corner with an unused item of the given name from the inventory", "<item name>"),
        paveclear("Clear Paving", "Forget which items have already been used for paving", ""),
        notarget("No Target", "Toggle automatic clearing of the targeted creature if it is too far away", ""),
        lumpheating("Lump Heating", "Toggle automatic lump heating by swapping lumps into/out of the hovered container", ""),
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
        /**
         * @return the spell with the given abbreviation or name ignoring case, or null when there is none
         */
        static Enchant find(String text) {
            for(Enchant enchant : values())
                if (enchant.abbreviation.equalsIgnoreCase(text) || enchant.name().equalsIgnoreCase(text)
                        || normalizeName(enchant.name()).equals(normalizeName(text)))
                    return enchant;
            return null;
        }

        String displayName() {
            String lower = name().toLowerCase().replace('_', ' ');
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
    }
}
