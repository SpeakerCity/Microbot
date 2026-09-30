package net.runelite.client.plugins.microbot.corruptedgauntlet;

import net.runelite.api.widgets.Widget;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import java.awt.Rectangle;
import java.util.*;

final class GauntletActions {
    private GauntletActions() { }

    static boolean object(GauntletScene.Obj target, String action) {
        if (target == null || (!action.isEmpty() && !target.action(action))) return false;
        if (Rs2Inventory.isItemSelected()) { Rs2Inventory.deselect(); return false; }
        var model = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileObjectCache().getStream()
                        .filter(o -> target.point.equals(o.getWorldLocation()))
                        .filter(o -> o.getWorldView() != null && o.getWorldView().getId() == target.worldViewId)
                        .filter(o -> {
                            var composition = o.getObjectComposition();
                            if (composition == null) return false;
                            String[] actions = composition.getActions();
                            return actions != null && Arrays.stream(actions).filter(Objects::nonNull)
                                    .anyMatch(a -> action.equalsIgnoreCase(a.replaceAll("<[^>]*>", "")));
                        }).findFirst().orElse(null)).orElse(null);
        return model != null && model.click(action);
    }

    static boolean attack(GauntletScene.Mob target) {
        if (Rs2Inventory.isItemSelected()) { Rs2Inventory.deselect(); return false; }
        var model = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2NpcCache().query().where(n -> n.getIndex() == target.index && !n.isDead()).first()).orElse(null);
        return model != null && model.click("Attack");
    }

    static boolean pickup(GauntletScene.Drop target) {
        var model = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getRs2TileItemCache().query().withId(target.id)
                        .where(i -> target.point.equals(i.getWorldLocation())).first()).orElse(null);
        return model != null && model.pickup();
    }

    static boolean walk(WorldPoint point) {
        if (point == null) return false;
        WalkClick click = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            var player = client.getLocalPlayer();
            if (player == null || player.getWorldView() == null) return null;
            var worldView = player.getWorldView();
            var local = net.runelite.api.coords.LocalPoint.fromWorld(worldView, point);
            if (local == null && worldView.isInstance())
                local = net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint.fromWorldInstance(point);
            if (local == null) return null;
            var canvas = net.runelite.api.Perspective.localToCanvas(client, local, worldView.getPlane());
            if (canvas == null || canvas.getX() < 1 || canvas.getY() < 1
                    || canvas.getX() >= client.getCanvasWidth() || canvas.getY() >= client.getCanvasHeight()) return null;
            NewMenuEntry entry = new NewMenuEntry().param0(canvas.getX()).param1(canvas.getY())
                    .type(MenuAction.WALK).identifier(0).itemId(0).option("Walk here");
            return new WalkClick(entry, new Rectangle(canvas.getX(), canvas.getY(), 1, 1));
        }).orElse(null);
        if (click == null) return false;
        Microbot.doInvoke(click.entry, click.bounds);
        return true;
    }

    private static final class WalkClick {
        final NewMenuEntry entry;
        final Rectangle bounds;
        WalkClick(NewMenuEntry entry, Rectangle bounds) { this.entry = entry; this.bounds = bounds; }
    }

    static void prayer(Rs2PrayerEnum prayer) {
        Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            Widget widget = Microbot.getClient().getWidget(prayer.getIndex());
            if (widget == null) return new Rectangle(Math.max(2, client.getCanvasWidth() / 2 - 4),
                    Math.max(2, client.getCanvasHeight() / 2 - 4), 8, 8);
            Rectangle rectangle = widget.getBounds();
            if (rectangle == null || rectangle.width <= 0 || rectangle.height <= 0
                    || rectangle.x < 1 || rectangle.y < 1
                    || rectangle.x + rectangle.width >= client.getCanvasWidth()
                    || rectangle.y + rectangle.height >= client.getCanvasHeight())
                return new Rectangle(Math.max(2, client.getCanvasWidth() / 2 - 4),
                        Math.max(2, client.getCanvasHeight() / 2 - 4), 8, 8);
            return new Rectangle(rectangle);
        }).orElse(null);
        if (bounds == null) return;
        NewMenuEntry entry = new NewMenuEntry().param0(-1).param1(prayer.getIndex())
                .opcode(MenuAction.CC_OP.getId()).identifier(1).itemId(-1)
                .option("Activate").target(prayer.getName());
        Microbot.doInvoke(entry, bounds);
    }

    static boolean inventoryAction(int id, String... choices) {
        String action = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            String[] actions = Microbot.getClient().getItemDefinition(id).getInventoryActions();
            if (actions != null) for (String choice : choices)
                for (String available : actions) if (choice.equalsIgnoreCase(available)) return choice;
            return null;
        }).orElse(null);
        return action != null && Rs2Inventory.interact(id, action);
    }

    static boolean approach(GauntletNavigation nav, GauntletScene.Obj target) {
        WorldPoint beside = nav.adjacent(target);
        if (beside == null) return false;
        if (nav.distance(beside) <= 1) return true;
        walk(nav.waypoint(beside));
        return false;
    }

    static void combine(GauntletScene s, int first, int second) {
        if (s.selectedItem != first) Rs2Inventory.use(first);
        else Rs2Inventory.use(second);
    }

    static boolean recipe(int id) {
        Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            for (Widget root : Microbot.getClient().getWidgetRoots()) {
                Rectangle match = findRecipe(root, id, new HashSet<Widget>());
                if (match != null) return match;
            }
            return null;
        }).orElse(null);
        if (bounds == null) return false;
        // Read widget geometry on the client thread, perform mouse input on the script thread.
        Microbot.getMouse().click(bounds);
        return true;
    }

    private static Rectangle findRecipe(Widget w, int id, Set<Widget> visited) {
        if (w == null || !visited.add(w) || w.isHidden()) return null;
        int group = w.getId() >>> 16;
        if (group == 149 || group == 387 || group == 15 || group == 161 || group == 640) return null;
        String[] actions = w.getActions();
        boolean craftAction = actions != null && Arrays.stream(actions).filter(Objects::nonNull)
                .anyMatch(a -> a.toLowerCase(Locale.ROOT).matches(".*(make|create|sing|craft).*"));
        if (w.getItemId() == id && (craftAction || group == 270)) {
            Rectangle b = w.getBounds();
            if (b != null && b.width > 0 && b.height > 0) return new Rectangle(b);
        }
        Widget[][] children = {w.getChildren(), w.getDynamicChildren(), w.getStaticChildren(), w.getNestedChildren()};
        for (Widget[] array : children) {
            if (array == null) continue;
            for (Widget child : array) {
                Rectangle found = findRecipe(child, id, visited);
                if (found != null) return found;
            }
        }
        return null;
    }
}
