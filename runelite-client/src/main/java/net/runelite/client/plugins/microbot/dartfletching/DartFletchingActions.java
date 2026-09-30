package net.runelite.client.plugins.microbot.dartfletching;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

/** Live widgets and menu entries are read only on the client thread; gestures run on the script thread. */
public class DartFletchingActions {
    private final Client client;

    @Inject
    public DartFletchingActions(Client client) {
        this.client = client;
    }

    boolean loggedIn() {
        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> client.getGameState() == GameState.LOGGED_IN).orElse(false);
    }

    Inventory read(DartType type) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            // Rs2Inventory exposes the existing event-driven inventory snapshot.
            List<Rs2ItemModel> items = Rs2Inventory.all();
            Widget production = client.getWidget(InterfaceID.SKILLMULTI, 0);
            Widget makeX = client.getWidget(162, 43);
            return new Inventory(client.getGameState() == GameState.LOGGED_IN,
                    client.getBoostedSkillLevel(Skill.FLETCHING), count(items, type.getTipId()),
                    count(items, type.getMaterialId()), count(items, type.getProductId()),
                    28 - items.size(), selectedId(), production != null && !production.isHidden(),
                    makeX != null && !makeX.isHidden() && "Enter amount:".equalsIgnoreCase(makeX.getText()),
                    client.getLocalPlayer() != null && client.getLocalPlayer().getAnimation() != -1);
        }).orElse(null);
    }

    private static int count(List<Rs2ItemModel> items, int id) {
        return items.stream().filter(item -> item.getId() == id).mapToInt(Rs2ItemModel::getQuantity).sum();
    }

    private int selectedId() {
        assert client.isClientThread();
        if (!client.isWidgetSelected()) {
            return -1;
        }
        Widget selected = client.getSelectedWidget();
        return selected != null && selected.getItemId() > 0 ? selected.getItemId() : -2;
    }

    boolean clickItem(int itemId, int expectedSelectedId, boolean variation, BooleanSupplier allowed) {
        if (!allowed.getAsBoolean()) {
            return false;
        }
        if (!Rs2Tab.switchTo(InterfaceTab.INVENTORY) || !allowed.getAsBoolean()) {
            return false;
        }
        if (!Global.sleepUntil(() -> !allowed.getAsBoolean() || itemTarget(itemId) != null, 2000)
                || !allowed.getAsBoolean()) {
            return false;
        }
        Target target = itemTarget(itemId);
        if (target == null || !allowed.getAsBoolean()) {
            return false;
        }
        Point point = clickPoint(target.bounds, Math.random(), Math.random());
        moveTo(point);
        if (variation && Rs2Random.between(0, 99) < 20) {
            varyMouse(target.bounds, allowed);
            if (!allowed.getAsBoolean()) {
                return false;
            }
            moveTo(point);
        }
        MenuAction action = expectedSelectedId == -1 ? MenuAction.WIDGET_TARGET : MenuAction.WIDGET_TARGET_ON_WIDGET;
        if (!Global.sleepUntil(() -> !allowed.getAsBoolean()
                || useEntry(target, action, expectedSelectedId) != null, 1200) || !allowed.getAsBoolean()) {
            return false;
        }
        // Revalidate slot, bounds, selection, and the live Use menu after moving the mouse.
        Target current = itemTarget(itemId);
        NewMenuEntry entry = useEntry(target, action, expectedSelectedId);
        if (current == null || current.slot != target.slot || !current.bounds.equals(target.bounds)
                || entry == null || !allowed.getAsBoolean()) {
            return false;
        }
        Microbot.getMouse().click(point, entry);
        return true; // Issued only: the script separately checks selection/production.
    }

    private Target itemTarget(int id) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget inventory = client.getWidget(ComponentID.INVENTORY_CONTAINER);
            if (inventory == null || inventory.isHidden()) {
                return null;
            }
            Rs2ItemModel item = Rs2Inventory.get(id);
            Widget child = item == null ? null : inventory.getChild(item.getSlot());
            if (child == null || child.isHidden() || child.getItemId() != id) {
                return null;
            }
            Rectangle bounds = child.getBounds();
            Rectangle canvas = new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
            if (bounds.width < 8 || bounds.height < 8 || !canvas.contains(bounds)) {
                return null;
            }
            return new Target(id, item.getSlot(), new Rectangle(bounds));
        }).orElse(null);
    }

    private NewMenuEntry useEntry(Target target, MenuAction action, int expectedSelectedId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (client.isMenuOpen() || selectedId() != expectedSelectedId) {
                return null;
            }
            for (MenuEntry entry : client.getMenuEntries()) {
                if (entry.getType() == action && "Use".equalsIgnoreCase(entry.getOption())
                        && entry.getItemId() == target.id && entry.getParam0() == target.slot
                        && entry.getParam1() == ComponentID.INVENTORY_CONTAINER) {
                    return copy(entry);
                }
            }
            return null;
        }).orElse(null);
    }

    boolean clickProduction(DartType type, BooleanSupplier allowed) {
        Production target = productionTarget(type);
        if (target == null || !allowed.getAsBoolean()) {
            return false;
        }
        Point point = clickPoint(target.bounds, Math.random(), Math.random());
        moveTo(point);
        Production current = productionTarget(type);
        if (current == null || !current.bounds.equals(target.bounds)
                || current.entry.getParam1() != target.entry.getParam1() || !allowed.getAsBoolean()) {
            return false;
        }
        Microbot.getMouse().click(point, current.entry);
        return true;
    }

    boolean confirmAtlatlMakeX(BooleanSupplier allowed) {
        if (!allowed.getAsBoolean() || !atlatlMakeXMenuOpen()) {
            return false;
        }
        // The Atlatl Make-X flow can be exposed either as an amount prompt or
        // as the skill-multi production menu. Space confirms its default 10x10 choice.
        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
        return true;
    }

    private boolean atlatlMakeXMenuOpen() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget prompt = client.getWidget(162, 43);
            if (prompt != null && !prompt.isHidden() && "Enter amount:".equalsIgnoreCase(prompt.getText())) {
                return true;
            }
            Widget production = client.getWidget(InterfaceID.SKILLMULTI, 0);
            return production != null && !production.isHidden();
        }).orElse(false);
    }

    private Production productionTarget(DartType type) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget root = client.getWidget(InterfaceID.SKILLMULTI, 0);
            if (root == null || root.isHidden()) {
                return null;
            }
            ArrayDeque<Widget> queue = new ArrayDeque<>();
            Set<Widget> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            queue.add(root);
            while (!queue.isEmpty()) {
                Widget widget = queue.removeFirst();
                if (!seen.add(widget) || widget.isHidden()) {
                    continue;
                }
                String name = (widget.getName() + " " + widget.getText()).toLowerCase(java.util.Locale.ROOT);
                String[] options = widget.getActions();
                if (options != null && (widget.getItemId() == type.getProductId()
                        || name.contains(type.productName().toLowerCase(java.util.Locale.ROOT)))) {
                    for (int i = 0; i < options.length; i++) {
                        if (options[i] != null && (options[i].equalsIgnoreCase("Make")
                                || options[i].equalsIgnoreCase("Make-All") || options[i].equalsIgnoreCase("Fletch"))) {
                            Rectangle bounds = widget.getBounds();
                            if (bounds.width < 8 || bounds.height < 8) {
                                continue;
                            }
                            NewMenuEntry entry = new NewMenuEntry().option(options[i]).target(widget.getName())
                                    .type(i < 5 ? MenuAction.CC_OP : MenuAction.CC_OP_LOW_PRIORITY)
                                    .identifier(i + 1).param0(widget.getIndex()).param1(widget.getId())
                                    .itemId(widget.getItemId());
                            return new Production(new Rectangle(bounds), entry);
                        }
                    }
                }
                for (Widget[] children : Arrays.asList(widget.getDynamicChildren(), widget.getStaticChildren(),
                        widget.getNestedChildren())) {
                    if (children != null) {
                        for (Widget child : children) {
                            if (child != null) {
                                queue.add(child);
                            }
                        }
                    }
                }
            }
            return null;
        }).orElse(null);
    }

    private static NewMenuEntry copy(MenuEntry entry) {
        return new NewMenuEntry().option(entry.getOption()).target(entry.getTarget()).type(entry.getType())
                .identifier(entry.getIdentifier()).param0(entry.getParam0()).param1(entry.getParam1())
                .itemId(entry.getItemId());
    }

    private static void moveTo(Point point) {
        if (Microbot.naturalMouse != null) {
            Microbot.naturalMouse.moveTo(point.getX(), point.getY());
        } else {
            Microbot.getMouse().move(point);
        }
    }

    /** Elliptical distribution inset from the item's edges, with a fresh angle and radius per click. */
    static Point clickPoint(Rectangle bounds, double angleSample, double radiusSample) {
        double angle = angleSample * 2 * Math.PI;
        double radius = 0.25 + 0.75 * Math.sqrt(radiusSample);
        int x = (int) Math.round(bounds.getCenterX() + Math.cos(angle) * radius * Math.max(0, bounds.width / 2.0 - 4));
        int y = (int) Math.round(bounds.getCenterY() + Math.sin(angle) * radius * Math.max(0, bounds.height / 2.0 - 4));
        return new Point(x, y);
    }

    private static void varyMouse(Rectangle bounds, BooleanSupplier allowed) {
        int pattern = Rs2Random.betweenInclusive(0, 2);
        long hoverUntil = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Rs2Random.between(70, 180));
        if (pattern == 0) {
            Global.sleepUntil(() -> !allowed.getAsBoolean() || System.nanoTime() >= hoverUntil, 400);
            return;
        }
        double start = Math.random();
        int steps = pattern == 1 ? 8 : 16;
        int direction = Rs2Random.betweenInclusive(0, 1) == 0 ? -1 : 1;
        for (int i = 0; i <= steps && allowed.getAsBoolean(); i++) {
            Microbot.getMouse().move(clickPoint(bounds, start + direction * i / 16.0, 0.15));
            // Gesture pacing only; game-state waits use observed conditions in the state machine.
            Global.sleep(Rs2Random.between(15, 25));
        }
    }

    @RequiredArgsConstructor
    static class Inventory {
        final boolean loggedIn;
        final int level;
        final int tips;
        final int materials;
        final int products;
        final int freeSlots;
        final int selectedId;
        final boolean productionOpen;
        final boolean makeXPrompt;
        final boolean animating;
    }

    @RequiredArgsConstructor
    private static class Target {
        final int id;
        final int slot;
        final Rectangle bounds;
    }

    @RequiredArgsConstructor
    private static class Production {
        final Rectangle bounds;
        final NewMenuEntry entry;
    }

}
