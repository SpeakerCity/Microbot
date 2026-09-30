package net.runelite.client.plugins.microbot.dartfletching;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

@Slf4j
public class DartFletchingScript extends StateMachineScript<DartFletchingScript.State> {
    enum State { SELECT_TIPS, USE_MATERIAL, WAIT_FOR_RESULT, MAKE }

    private static final int RESPONSE_TIMEOUT_MS = 8000;
    private static final int ATLATL_BATCH_TIMEOUT_MS = 120000;
    private static final int BATCH_IDLE_MS = 5000;
    private static final int ATLATL_BATCH_SIZE = 100;
    private final DartFletchingActions actions;
    private final LongSupplier clock;
    private DartFletchingConfig config;
    private DartType type;
    private DartFletchingActions.Inventory inventory;
    private DartFletchingActions.Inventory before;
    private boolean issued;
    private boolean combined;
    private boolean productionSubmitted;
    private boolean confirmed;
    private long deadline;
    private long nextClickAt;
    private long lastProgressAt;
    private int observedProducts;
    private int targetDartsThisBatch;
    private volatile boolean active;
    @Getter
    private volatile String status = "Ready";
    @Getter
    private volatile long dartsMade;

    @Inject
    public DartFletchingScript(DartFletchingActions actions) {
        this(actions, () -> System.nanoTime() / 1_000_000L);
    }

    DartFletchingScript(DartFletchingActions actions, LongSupplier clock) {
        this.actions = actions;
        this.clock = clock;
    }

    public boolean run(DartFletchingConfig settings) {
        if (isRunning()) {
            return true;
        }
        configure(settings);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!active) {
                    shutdown();
                    return;
                }
                readInventory();
                if (inventory == null || !inventory.loggedIn) {
                    if (issued || combined) {
                        stop("Logged out; restart to continue");
                    } else {
                        setStatus("Waiting for login");
                    }
                    return;
                }
                if (!step()) {
                    setStatus("Paused");
                }
            } catch (Exception ex) {
                log.warn("Dart fletching stopped after an unexpected error", ex);
                stop("Error; check the client log");
            }
        }, 100, 50, TimeUnit.MILLISECONDS);
        return true;
    }

    void configure(DartFletchingConfig settings) {
        config = settings;
        type = settings.dartType();
        active = true;
        setStatus("Starting " + type + " darts");
    }

    void readInventory() {
        inventory = actions.read(type);
    }

    @Override
    protected State initialState() {
        return State.SELECT_TIPS;
    }

    @Override
    protected List<Transition<State>> defineTransitions() {
        return List.of(
                Transition.from(State.SELECT_TIPS)
                        .when(() -> issued && inventory != null && inventory.selectedId == type.getTipId(), "tips selected")
                        .because("Use selection confirmed").goTo(State.USE_MATERIAL),
                Transition.from(State.USE_MATERIAL).when(() -> combined, "combine issued")
                        .because("Waiting for server response").goTo(State.WAIT_FOR_RESULT),
                Transition.from(State.WAIT_FOR_RESULT)
                        .when(() -> inventory != null && !productionSubmitted && !confirmed
                                && (inventory.productionOpen || (type == DartType.ATLATL && inventory.makeXPrompt)),
                                "crafting menu or Atlatl Make-X prompt appeared")
                        .because("Crafting choice requires confirmation").goTo(State.MAKE),
                Transition.from(State.MAKE).when(() -> productionSubmitted, "crafting submitted")
                        .because("Waiting for production").goTo(State.WAIT_FOR_RESULT),
                Transition.from(State.WAIT_FOR_RESULT)
                        .when(() -> confirmed && inventory != null && inventory.products == observedProducts
                                && clock.getAsLong() >= nextClickAt
                                && (!productionSubmitted || (inventory != null && !inventory.animating
                                && clock.getAsLong() - lastProgressAt >= BATCH_IDLE_MS)), "production confirmed and idle")
                        .because("Ready for the next batch").goTo(State.SELECT_TIPS)
        );
    }

    @Override
    protected void onTransition(State from, State to, String reason) {
        // The state snapshot retains transitions; don't log every pair of clicks.
        if (to == State.SELECT_TIPS) {
            issued = false;
            combined = false;
            confirmed = false;
            productionSubmitted = false;
            before = null;
        }
    }

    @Override
    protected void onState(State state) {
        if (!active || inventory == null) {
            return;
        }
        if (!validDelays(config.minDelay(), config.maxDelay())) {
            stop("Invalid delays: use 0 <= min <= max <= 10000");
            return;
        }
        long now = clock.getAsLong();
        switch (state) {
            case SELECT_TIPS:
                if (inventory.level < type.getLevel()) {
                    stop("Requires " + type.getLevel() + " Fletching");
                } else if (type == DartType.ATLATL
                        && (inventory.tips < ATLATL_BATCH_SIZE || inventory.materials < ATLATL_BATCH_SIZE)) {
                    stop("Atlatl batches need 100 dart tips and 100 headless Atlatl darts");
                } else if (inventory.tips == 0 || inventory.materials == 0) {
                    stop(type == DartType.ATLATL ? "Need tips and headless Atlatl darts" : "Need dart tips and feathers");
                } else if (inventory.products == 0 && inventory.freeSlots == 0) {
                    stop("Leave one inventory slot free");
                } else if (!issued && inventory.selectedId != -1) {
                    stop("Clear the selected item/spell, then restart");
                } else if (issued) {
                    if (now >= deadline) {
                        stop("Use was not confirmed; restart to retry");
                    }
                } else if (now >= nextClickAt) {
                    setStatus("Selecting " + type + " tips");
                    issued = actions.clickItem(type.getTipId(), -1, config.mouseVariation(), this::canAct);
                    if (!issued) {
                        stop("Could not find Use on the tips");
                        return;
                    }
                    deadline = clock.getAsLong() + RESPONSE_TIMEOUT_MS;
                    scheduleClick();
                }
                break;
            case USE_MATERIAL:
                if (now < nextClickAt) {
                    return;
                }
                if (inventory.selectedId != type.getTipId()) {
                    stop("Item selection changed; restart to retry");
                    return;
                }
                before = inventory;
                observedProducts = inventory.products;
                targetDartsThisBatch = type == DartType.ATLATL ? ATLATL_BATCH_SIZE : 1;
                setStatus("Using tips on " + (type == DartType.ATLATL ? "headless darts" : "feathers"));
                combined = actions.clickItem(type.getMaterialId(), type.getTipId(), config.mouseVariation(), this::canAct);
                if (!combined) {
                    stop("Could not use the selected tips");
                    return;
                }
                deadline = clock.getAsLong() + responseTimeout();
                scheduleClick();
                break;
            case MAKE:
                if (now < nextClickAt) {
                    return;
                }
                productionSubmitted = type == DartType.ATLATL
                        ? actions.confirmAtlatlMakeX(this::canAct)
                        : actions.clickProduction(type, this::canAct);
                if (!productionSubmitted) {
                    stop(type == DartType.ATLATL
                            ? "Could not confirm the Atlatl Make-X prompt"
                            : "Could not confirm the crafting menu");
                    return;
                }
                deadline = clock.getAsLong() + responseTimeout();
                scheduleClick();
                break;
            case WAIT_FOR_RESULT:
                if (madeDarts(before, inventory) && inventory.products > observedProducts) {
                    dartsMade += inventory.products - observedProducts;
                    observedProducts = inventory.products;
                    lastProgressAt = now;
                    if (type == DartType.ATLATL) {
                        confirmed = inventory.products - before.products >= targetDartsThisBatch;
                        deadline = now + ATLATL_BATCH_TIMEOUT_MS;
                        setStatus("Fletching Atlatl darts (" + (inventory.products - before.products)
                                + "/" + targetDartsThisBatch + ")");
                    } else {
                        confirmed = true;
                        setStatus("Fletching " + type + " darts");
                    }
                } else if (!confirmed && now >= deadline) {
                    stop(type == DartType.ATLATL
                            ? "Atlatl batch did not reach 100 darts; restart to retry"
                            : "No darts made; restart to retry");
                }
                break;
        }
    }

    static boolean validDelays(int min, int max) {
        return min >= 0 && max >= min && max <= 10000;
    }

    static boolean madeDarts(DartFletchingActions.Inventory before, DartFletchingActions.Inventory after) {
        return before != null && after != null && after.loggedIn
                && after.products > before.products && after.tips < before.tips
                && after.materials < before.materials;
    }

    private void scheduleClick() {
        nextClickAt = clock.getAsLong() + Rs2Random.betweenInclusive(config.minDelay(), config.maxDelay());
    }

    private int responseTimeout() {
        return type == DartType.ATLATL ? ATLATL_BATCH_TIMEOUT_MS : RESPONSE_TIMEOUT_MS;
    }

    private boolean canAct() {
        return active && !Thread.currentThread().isInterrupted() && !Microbot.pauseAllScripts.get()
                && !InputArbiter.isHuman() && actions.loggedIn();
    }

    private void setStatus(String message) {
        status = message;
        Microbot.status = message;
    }

    private void stop(String message) {
        active = false;
        setStatus(message);
    }

    @Override
    protected State onError(State state, Exception ex) {
        log.warn("Dart fletching stopped after an unexpected error", ex);
        stop("Error; check the client log");
        return null;
    }

    @Override
    public void shutdown() {
        active = false;
        super.shutdown();
        scheduledExecutorService.shutdownNow();
    }
}
