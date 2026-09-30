package net.runelite.client.plugins.microbot.corruptedgauntlet;

import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.WorldPoint;
import java.util.*;
import java.util.function.Predicate;

/** Cardinal routes use both sides of each collision edge. No global walker in the instance. */
final class GauntletNavigation {
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final int[] OUT = {8, 2, 128, 32};
    private static final int[] IN = {128, 32, 8, 2};
    final Map<WorldPoint, WorldPoint> parents = new HashMap<>();
    final Map<WorldPoint, Integer> distances = new HashMap<>();
    final GauntletScene scene;

    GauntletNavigation(GauntletScene scene) { this(scene, p -> true); }

    GauntletNavigation(GauntletScene scene, Predicate<WorldPoint> allowed) {
        this.scene = scene;
        if (scene.player == null || scene.collision == null) return;
        ArrayDeque<WorldPoint> queue = new ArrayDeque<>();
        queue.add(scene.player); distances.put(scene.player, 0);
        while (!queue.isEmpty()) {
            WorldPoint from = queue.removeFirst();
            int x = from.getX() - scene.baseX, y = from.getY() - scene.baseY;
            if (!inBounds(x, y)) continue;
            for (int i = 0; i < DIRECTIONS.length; i++) {
                int nx = x + DIRECTIONS[i][0], ny = y + DIRECTIONS[i][1];
                if (!inBounds(nx, ny)) continue;
                WorldPoint next = new WorldPoint(scene.baseX + nx, scene.baseY + ny, scene.plane);
                if (distances.containsKey(next) || !allowed.test(next)) continue;
                if (!edgeOpen(scene.collision[x][y], scene.collision[nx][ny], i)) continue;
                distances.put(next, distances.get(from) + 1);
                parents.put(next, from); queue.addLast(next);
            }
        }
    }

    static boolean edgeOpen(int from, int to, int direction) {
        return (from & OUT[direction]) == 0
                && (to & (IN[direction] | CollisionDataFlag.BLOCK_MOVEMENT_FULL | 0x1000000)) == 0;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < scene.collision.length && y >= 0 && y < scene.collision[x].length;
    }

    WorldPoint adjacent(GauntletScene.Obj object) {
        WorldPoint best = null;
        int shortest = Integer.MAX_VALUE;
        for (int dx = -1; dx <= object.width; dx++) {
            for (int dy = -1; dy <= object.height; dy++) {
                if (dx != -1 && dy != -1 && dx != object.width && dy != object.height) continue;
                WorldPoint candidate = new WorldPoint(object.point.getX() + dx, object.point.getY() + dy, scene.plane);
                int distance = distance(candidate);
                if (distance < shortest) { best = candidate; shortest = distance; }
            }
        }
        return best;
    }

    int distance(WorldPoint target) { return distances.getOrDefault(target, Integer.MAX_VALUE); }
    int distance(GauntletScene.Obj object) { return distance(adjacent(object)); }

    WorldPoint waypoint(WorldPoint target) {
        if (target == null || !distances.containsKey(target) || target.equals(scene.player)) return null;
        LinkedList<WorldPoint> path = new LinkedList<>();
        for (WorldPoint p = target; p != null && !p.equals(scene.player); p = parents.get(p)) path.addFirst(p);
        WorldPoint result = path.getFirst();
        int dx = result.getX() - scene.player.getX(), dy = result.getY() - scene.player.getY();
        // Stop at the first corner so the game's own click path cannot cut across hazards.
        for (int i = 1; i < Math.min(6, path.size()); i++) {
            WorldPoint next = path.get(i);
            if (next.getX() - result.getX() != dx || next.getY() - result.getY() != dy) break;
            result = next;
        }
        return result;
    }
}
