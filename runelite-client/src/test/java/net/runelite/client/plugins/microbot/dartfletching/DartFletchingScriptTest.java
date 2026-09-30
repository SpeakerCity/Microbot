package net.runelite.client.plugins.microbot.dartfletching;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.Point;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.dartfletching.DartFletchingActions.Inventory;
import net.runelite.client.plugins.microbot.dartfletching.DartFletchingScript.State;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DartFletchingScriptTest {
    private DartFletchingActions actions;
    private DartFletchingScript script;
    private State state;
    private long now;

    @Before
    public void setup() {
        actions = mock(DartFletchingActions.class);
        when(actions.clickItem(anyInt(), anyInt(), anyBoolean(), any())).thenReturn(true);
        when(actions.clickProduction(any(), any())).thenReturn(true);
        when(actions.confirmAtlatlMakeX(any())).thenReturn(true);
        start(DartType.MITHRIL, 0, 0);
    }

    private void start(DartType type, int min, int max) {
        script = new DartFletchingScript(actions, () -> now);
        script.configure(new DartFletchingConfig() {
            @Override public DartType dartType() { return type; }
            @Override public int minDelay() { return min; }
            @Override public int maxDelay() { return max; }
        });
        state = script.initialState();
    }

    // Run the actual transition guards and handlers without a live client's Script.run() gate.
    private void tick(Inventory inventory) {
        when(actions.read(any())).thenReturn(inventory);
        script.readInventory();
        for (Transition<State> transition : script.defineTransitions()) {
            if (transition.from() == state && transition.condition().getAsBoolean()) {
                script.onTransition(state, transition.to(), transition.reason());
                state = transition.to();
                break;
            }
        }
        script.onState(state);
    }

    private Inventory inventory(int tips, int materials, int products, int selected, boolean menu, boolean animating) {
        return new Inventory(true, 99, tips, materials, products, 25, selected, menu, false, animating);
    }

    private void combine(DartType type) {
        tick(inventory(100, 100, 0, -1, false, false));
        tick(inventory(100, 100, 0, type.getTipId(), false, false));
        tick(inventory(100, 100, 0, -1, false, false));
    }

    @Test
    public void waitsForSelectionWithoutRepeatingClick() {
        for (int i = 0; i < 20; i++) {
            now += 100;
            tick(inventory(100, 100, 0, -1, false, false));
        }
        verify(actions, times(1)).clickItem(eq(ItemID.MITHRIL_DART_TIP), eq(-1), anyBoolean(), any());
        verify(actions, never()).clickItem(eq(ItemID.FEATHER), anyInt(), anyBoolean(), any());
        now = 9000;
        tick(inventory(100, 100, 0, -1, false, false));
        assertTrue(script.getStatus().contains("not confirmed"));
    }

    @Test
    public void missingServerResponseStopsWithoutSpamming() {
        combine(DartType.MITHRIL);
        for (int i = 0; i < 100; i++) {
            now += 100;
            tick(inventory(100, 100, 0, -1, false, false));
        }
        verify(actions, times(2)).clickItem(anyInt(), anyInt(), anyBoolean(), any());
        assertEquals(0, script.getDartsMade());
        assertTrue(script.getStatus().startsWith("No darts made"));
    }

    @Test
    public void confirmedProductionAllowsExactlyOneNewCycle() {
        combine(DartType.MITHRIL);
        tick(inventory(90, 90, 10, -1, false, false));
        assertEquals(10, script.getDartsMade());
        tick(inventory(90, 90, 10, -1, false, false));
        tick(inventory(90, 90, 10, -1, false, false));
        verify(actions, times(3)).clickItem(anyInt(), anyInt(), anyBoolean(), any());
        assertEquals(10, script.getDartsMade());
    }

    @Test
    public void addedDartsAloneAreNotConfirmation() {
        combine(DartType.MITHRIL);
        tick(inventory(100, 100, 10, -1, false, false));
        tick(inventory(100, 90, 10, -1, false, false));
        assertEquals(0, script.getDartsMade());
        verify(actions, times(2)).clickItem(anyInt(), anyInt(), anyBoolean(), any());
    }

    @Test
    public void makeXPromptIsConfirmedOnceAndScriptWaitsForAllHundredAtlatlDarts() {
        start(DartType.ATLATL, 0, 0);
        combine(DartType.ATLATL);
        tick(new Inventory(true, 99, 100, 100, 0, 25, -1, false, true, false));
        tick(new Inventory(true, 99, 100, 100, 0, 25, -1, false, true, false));
        verify(actions, times(1)).confirmAtlatlMakeX(any());
        for (int made = 10; made <= 90; made += 10) {
            now += 1000;
            tick(inventory(100 - made, 100 - made, made, -1, false, true));
            verify(actions, times(2)).clickItem(anyInt(), anyInt(), anyBoolean(), any());
        }
        now += 1000;
        tick(inventory(0, 0, 100, -1, false, false));
        assertTrue(script.getStatus().contains("100/100"));
        verify(actions, times(1)).confirmAtlatlMakeX(any());
        verify(actions, times(2)).clickItem(anyInt(), anyInt(), anyBoolean(), any());
        assertEquals(100, script.getDartsMade());
        now += 5000;
        tick(inventory(0, 0, 100, -1, false, false));
        verify(actions, times(3)).clickItem(anyInt(), anyInt(), anyBoolean(), any());
    }

    @Test
    public void atlatlUsesHeadlessDarts() {
        start(DartType.ATLATL, 0, 0);
        combine(DartType.ATLATL);
        verify(actions).clickItem(eq(ItemID.ATLATL_DART_TIPS), eq(-1), anyBoolean(), any());
        verify(actions).clickItem(eq(ItemID.HEADLESS_ATLATL_DART), eq(ItemID.ATLATL_DART_TIPS), anyBoolean(), any());
        verify(actions, never()).clickItem(eq(ItemID.FEATHER), anyInt(), anyBoolean(), any());
    }

    @Test
    public void configuredDelayAppliesBeforeSecondClick() {
        start(DartType.MITHRIL, 1000, 1000);
        tick(inventory(100, 100, 0, -1, false, false));
        now = 999;
        tick(inventory(100, 100, 0, ItemID.MITHRIL_DART_TIP, false, false));
        verify(actions, times(1)).clickItem(anyInt(), anyInt(), anyBoolean(), any());
        now = 1000;
        tick(inventory(100, 100, 0, ItemID.MITHRIL_DART_TIP, false, false));
        verify(actions, times(2)).clickItem(anyInt(), anyInt(), anyBoolean(), any());
    }

    @Test
    public void invalidDelayRangeStopsBeforeClicking() {
        start(DartType.MITHRIL, 500, 100);
        tick(inventory(100, 100, 0, -1, false, false));
        verify(actions, never()).clickItem(anyInt(), anyInt(), anyBoolean(), any());
        assertTrue(script.getStatus().startsWith("Invalid delays"));
        assertFalse(DartFletchingScript.validDelays(-1, 10));
        assertFalse(DartFletchingScript.validDelays(0, Integer.MAX_VALUE));
        assertTrue(DartFletchingScript.validDelays(0, 0));
    }

    @Test
    public void depletedSuppliesAndInsufficientLevelNeverClick() {
        tick(inventory(0, 100, 0, -1, false, false));
        assertTrue(script.getStatus().startsWith("Need"));
        start(DartType.DRAGON, 0, 0);
        tick(new Inventory(true, 94, 100, 100, 0, 25, -1, false, false, false));
        assertEquals("Requires 95 Fletching", script.getStatus());
        verify(actions, never()).clickItem(anyInt(), anyInt(), anyBoolean(), any());
    }

    @Test
    public void existingSelectionCannotBypassPreconditions() {
        tick(inventory(100, 100, 0, ItemID.MITHRIL_DART_TIP, false, false));
        assertTrue(script.getStatus().startsWith("Clear the selected"));
        verify(actions, never()).clickItem(anyInt(), anyInt(), anyBoolean(), any());
    }

    @Test
    public void clickDistributionStaysInsideItemWithMargin() {
        for (Rectangle bounds : new Rectangle[]{new Rectangle(600, 400, 32, 32), new Rectangle(10, 20, 8, 9)}) {
            Rectangle inset = new Rectangle(bounds);
            inset.grow(-2, -2);
            for (int angle = 0; angle <= 360; angle++) {
                for (int radius = 0; radius <= 10; radius++) {
                    Point point = DartFletchingActions.clickPoint(bounds, angle / 360.0, radius / 10.0);
                    assertTrue(inset.contains(point.getX(), point.getY()));
                }
            }
        }
    }

    @Test
    public void pluginCanBeConstructedByRuneliteLoader() throws Exception {
        assertNotNull(DartFletchingPlugin.class.getDeclaredConstructor().newInstance());
        assertEquals(ItemID.DRAGON_DART_TIP, DartType.DRAGON.getTipId());
        assertEquals(6, DartType.values().length);
    }

    @Test
    public void pluginInjectorProvidesSettingsAndFreshScripts() {
        DartFletchingPlugin plugin = new DartFletchingPlugin();
        DartFletchingConfig settings = new DartFletchingConfig() {};
        ConfigManager manager = mock(ConfigManager.class);
        when(manager.getConfig(DartFletchingConfig.class)).thenReturn(settings);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Client.class).toInstance(mock(Client.class));
                bind(ConfigManager.class).toInstance(manager);
                bind(OverlayManager.class).toInstance(mock(OverlayManager.class));
                bind(DartFletchingPlugin.class).toInstance(plugin);
                install(plugin);
            }
        });
        assertSame(settings, injector.getInstance(DartFletchingConfig.class));
        assertNotSame(injector.getInstance(DartFletchingScript.class), injector.getInstance(DartFletchingScript.class));
    }
}
