package net.runelite.client.plugins.microbot.corruptedgauntlet;

import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared by the plugin, event handlers and overlay. */
@Singleton
@Slf4j
public class CorruptedGauntletScript extends StateMachineScript<CorruptedGauntletScript.State> {
    enum State { ENTER, PREP, FIGHT, LOOT }
    private final CorruptedGauntletPrep prep = new CorruptedGauntletPrep();
    private final GauntletCombat combat = new GauntletCombat();
    private final GauntletSession session = new GauntletSession();
    private final AtomicBoolean pending = new AtomicBoolean(true);
    private volatile View view = new View("Stopped", "Stopped", "", 0, 0, 0, 0, 0, 0, 0, 0, 0);
    private CorruptedGauntletConfig config;
    private GauntletLoadout loadout;
    private GauntletScene scene;
    private GauntletNavigation navigation;
    private boolean wasInside, collecting;
    private int chestTick, lastTick = -1, stableLootTicks;
    private long lastError;
    private Map<Integer, Integer> beforeChest = Map.of(), lastLoot = Map.of();
    private volatile boolean victory;
    private int latestProjectileCycle = -1;
    private int lastObjectCacheLogTick = Integer.MIN_VALUE;
    private String status = "Starting";

    @Override protected State initialState() { return State.ENTER; }
    @Override protected List<Transition<State>> defineTransitions() {
        List<Transition<State>> transitions = new ArrayList<>();
        for (State from : State.values()) {
            if (from != State.FIGHT) transitions.add(Transition.<State>from(from)
                    .when(() -> scene != null && scene.inside && scene.bossStarted, "serverBossStarted")
                    .because("Server confirms boss encounter").goTo(State.FIGHT));
            if (from != State.PREP) transitions.add(Transition.<State>from(from)
                    .when(() -> scene != null && scene.inside && !scene.bossStarted, "preparationInstance")
                    .because("Preparing configured loadout").goTo(State.PREP));
            if (from != State.LOOT) transitions.add(Transition.<State>from(from)
                    .when(() -> scene != null && !scene.inside && (scene.rewardAvailable || collecting), "rewardAvailable")
                    .because("Collecting arena reward").goTo(State.LOOT));
            if (from != State.ENTER) transitions.add(Transition.<State>from(from)
                    .when(() -> scene != null && !scene.inside && !scene.rewardAvailable && !collecting, "readyForNextRun")
                    .because("Ready for another attempt").goTo(State.ENTER));
        }
        return transitions;
    }

    @Override protected void onState(State state) {
        switch (state) {
            case ENTER: status = enter(); break;
            case PREP: status = prep.tick(scene, navigation, combat, config.prayerFlicking()); break;
            case FIGHT:
                GauntletScene.Mob boss = scene.boss();
                status = boss == null ? "Waiting for encounter result"
                        : combat.fight(scene, boss, navigation, loadout, config.prayerFlicking(), true);
                break;
            case LOOT: status = collectReward(); break;
        }
    }

    public boolean run(CorruptedGauntletConfig config) {
        if (mainScheduledFuture != null) mainScheduledFuture.cancel(true);
        this.config = config;
        session.reset(); wasInside = collecting = victory = false; lastTick = -1; scene = null;
        loadout = new GauntletLoadout(config); prep.reset(loadout); combat.reset();
        latestProjectileCycle = -1;
        if (getSnapshot() != null) forceState(State.ENTER, "New session");
        status = "Starting; waiting for client snapshot"; publish(); pending.set(true);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::tickSafely, 0, 50, TimeUnit.MILLISECONDS);
        return true;
    }

    private void tickSafely() {
        if (!pending.getAndSet(false)) return;
        try {
            GauntletScene latest = GauntletScene.read();
            if (latest == null) { status = "Waiting for client snapshot"; publish(); return; }
            scene = latest;
            if (!scene.loggedIn) {
                session.disconnected(); lastTick = -1; status = "Waiting for login"; publish(); return;
            }
            session.sample(scene.xp, System.currentTimeMillis());
            if (lastTick == scene.tick) { publish(); return; }
            lastTick = scene.tick;
            if (scene.inside && !wasInside) {
                loadout = new GauntletLoadout(config); prep.reset(loadout); combat.reset();
                session.attempts++; victory = false;
            }
            if (!scene.inside && wasInside) {
                combat.releasePrayer();
                if (victory) session.wins++;
                victory = false;
            }
            wasInside = scene.inside;
            String error = loadout.validationError();
            if (error != null) { status = error; publish(); return; }
            navigation = new GauntletNavigation(scene);
            if (lastObjectCacheLogTick == Integer.MIN_VALUE || scene.tick - lastObjectCacheLogTick >= 50) {
                logObjectCache();
                lastObjectCacheLogTick = scene.tick;
            }
            if (!step()) status = "Paused by Microbot input or script guard";
            Microbot.status = status;
            publish();
        } catch (Exception ex) {
            onError(getCurrentState(), ex); publish();
        } catch (LinkageError ex) {
            super.shutdown(); status = "Client API mismatch; update Microbot"; publish();
            log.warn("Corrupted Gauntlet requires a compatible client", ex);
        }
    }

    @Override protected State onError(State state, Exception ex) {
        status = "Action error: " + ex.getClass().getSimpleName();
        if (System.currentTimeMillis() - lastError > 10000) {
            log.warn("Corrupted Gauntlet action failed", ex); lastError = System.currentTimeMillis();
        }
        return null;
    }

    private String enter() {
        if (Rs2DepositBox.isOpen()) { Rs2DepositBox.closeDepositBox(); return "Closing deposit box"; }
        GauntletScene.Obj entrance = scene.objects.stream()
                .filter(o -> o.action("Enter-corrupted")
                        || (o.name.toLowerCase(Locale.ROOT).contains("gauntlet") && o.action("Enter"))
                        || (o.id == 36084 && o.action("Enter")))
                .min(Comparator.comparingInt(navigation::distance)).orElse(null);
        if (entrance != null) {
            String action = entrance.action("Enter-corrupted") ? "Enter-corrupted" : "Enter";
            if (GauntletActions.approach(navigation, entrance)) GauntletActions.object(entrance, action);
            return "Entering Corrupted Gauntlet";
        }
        GauntletScene.Obj portal = scene.objects.stream()
                .filter(o -> (o.id == 36081 || o.name.toLowerCase(Locale.ROOT).contains("portal"))
                        && o.action("Enter"))
                .min(Comparator.comparingInt(navigation::distance)).orElse(null);
        if (portal != null) {
            if (GauntletActions.approach(navigation, portal)) GauntletActions.object(portal, "Enter");
            return "Entering Gauntlet lobby";
        }
        return "No cached Gauntlet entry action found; see object-cache log";
    }

    private void logObjectCache() {
        if (scene == null || scene.inside) return;
        String entities = scene.objects.stream().sorted(Comparator.comparingInt(navigation::distance))
                .limit(60).map(o -> String.format(Locale.ROOT,
                        "id=%d resolved=%d baseName=%s name=%s impostors=%s actions=%s loc=%s view=%d",
                        o.id, o.resolvedId, o.baseName, o.name, o.impostorIds, o.actions, o.point, o.worldViewId))
                .reduce((a, b) -> a + " | " + b).orElse("<none near player>");
        log.info("[CorruptedGauntlet] Nearby cached tile objects player={} view={}: {}",
                scene.player, scene.worldViewId, entities);
    }

    private String collectReward() {
        if (!collecting && scene.slots > 20) {
            if (!Rs2DepositBox.isOpen()) Rs2DepositBox.openDepositBox(); else Rs2DepositBox.depositAll();
            return "Making room for chest loot";
        }
        if (Rs2DepositBox.isOpen()) { Rs2DepositBox.closeDepositBox(); return "Closing deposit box"; }
        if (!collecting) {
            GauntletScene.Obj chest = scene.object(36087);
            if (chest == null) return "Locating reward chest";
            if (!GauntletActions.approach(navigation, chest)) return "Walking to reward chest";
            beforeChest = new HashMap<>(scene.inventory);
            if (GauntletActions.object(chest, "Open")) {
                collecting = true; chestTick = scene.tick; stableLootTicks = 0; lastLoot = beforeChest;
            }
            return "Opening reward chest";
        }
        if (scene.rewardAvailable) {
            if (scene.tick - chestTick > 8) collecting = false;
            return "Waiting for reward collection";
        }
        stableLootTicks = scene.inventory.equals(lastLoot) ? stableLootTicks + 1 : 0;
        lastLoot = new HashMap<>(scene.inventory);
        if (stableLootTicks < 2 || scene.tick - chestTick < 3) return "Waiting for loot inventory update";
        for (Map.Entry<Integer, Integer> item : scene.inventory.entrySet()) {
            int gained = item.getValue() - beforeChest.getOrDefault(item.getKey(), 0);
            if (gained <= 0) continue;
            int price = item.getKey() == ItemID.COINS_995 ? 1 : item.getKey() == ItemID.CRYSTAL_SHARD
                    ? config.crystalShardValue() : Math.max(0, Microbot.getItemManager().getItemPrice(item.getKey()));
            session.profit += (long) gained * price;
        }
        collecting = false; return "Treasure collected; starting next run";
    }

    void onGameTick() { pending.set(true); }
    void onHunllefAnimation(int id, int animation) {
        if (id < 9035 || id > 9038) return;
        if (animation == 8754) combat.bossProtection = Rs2PrayerEnum.PROTECT_MAGIC;
        if (animation == 8755) combat.bossProtection = Rs2PrayerEnum.PROTECT_RANGE;
    }
    void onProjectile(int id, int startCycle) {
        if ((id != 1708 && id != 1712) || startCycle <= latestProjectileCycle) return;
        latestProjectileCycle = startCycle;
        if (id == 1708) combat.bossProtection = Rs2PrayerEnum.PROTECT_MAGIC;
        if (id == 1712) combat.bossProtection = Rs2PrayerEnum.PROTECT_RANGE;
    }
    void onBossDeath(int id) { if (id >= 9035 && id <= 9038) victory = true; }
    void onChatMessage(String message) {
        if (message != null && message.toLowerCase(Locale.ROOT).contains("completed the corrupted gauntlet")) victory = true;
    }
    private void publish() {
        long now = System.currentTimeMillis();
        String phase = scene == null || !scene.loggedIn ? "Idle" : scene.inside
                ? scene.bossStarted ? "HUNLLEF" : prep.phase() : collecting || scene.rewardAvailable ? "TREASURE" : "LOBBY";
        view = new View(status, phase, scene == null ? "" : scene.timer, scene == null ? 0 : scene.count(23874),
                scene == null ? 0 : scene.potionCount(), prep.roomsOpened(), session.attempts, session.wins,
                session.xpGained, session.hourly(session.xpGained, now), session.profit, session.hourly(session.profit, now));
    }
    @Override public void shutdown() {
        super.shutdown(); status = "Stopped"; publish();
        scheduledExecutorService.execute(combat::releasePrayer);
    }
    View view() { return view; }

    static final class View {
        final String status, phase, timer;
        final int food, potions, rooms, attempts, wins;
        final long xp, xpHour, profit, profitHour;
        View(String status, String phase, String timer, int food, int potions, int rooms, int attempts, int wins,
             long xp, long xpHour, long profit, long profitHour) {
            this.status = status; this.phase = phase; this.timer = timer; this.food = food; this.potions = potions;
            this.rooms = rooms; this.attempts = attempts; this.wins = wins; this.xp = xp; this.xpHour = xpHour;
            this.profit = profit; this.profitHour = profitHour;
        }
    }
}
