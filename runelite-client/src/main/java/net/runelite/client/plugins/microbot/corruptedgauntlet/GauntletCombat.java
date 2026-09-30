package net.runelite.client.plugins.microbot.corruptedgauntlet;

import net.runelite.api.HeadIcon;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import java.util.*;

final class GauntletCombat {
    volatile Rs2PrayerEnum bossProtection = Rs2PrayerEnum.PROTECT_RANGE;
    private Rs2PrayerEnum ownedPrayer;
    private final Map<Integer, WorldPoint> previousTornadoes = new HashMap<>();

    void reset() { bossProtection = Rs2PrayerEnum.PROTECT_RANGE; previousTornadoes.clear(); }

    String fight(GauntletScene s, GauntletScene.Mob enemy, GauntletNavigation nav,
                 GauntletLoadout loadout, boolean flick, boolean boss) {
        Rs2PrayerEnum prayer = boss ? bossProtection : enemy.id == 9047 ? Rs2PrayerEnum.PROTECT_MAGIC
                : enemy.id == 9048 ? Rs2PrayerEnum.PROTECT_RANGE : Rs2PrayerEnum.PROTECT_MELEE;
        protect(prayer, flick);
        boolean dodging = boss && dodge(s, enemy);
        if ((s.hp < 38 || s.maxHp - s.hp >= 20) && s.count(23874) > 0) {
            Rs2Inventory.interact(23874, "Eat"); return dodging ? "Dodging and eating" : "Eating paddlefish";
        }
        if (s.prayer < 15) for (int id = 23885; id >= 23882; id--) if (s.count(id) > 0) {
            Rs2Inventory.interact(id, "Drink"); return "Drinking Egniol potion";
        }
        int weapon = weapon(s, loadout, boss ? enemy.icon : null);
        if (weapon > 0 && !s.equipped.contains(weapon)) {
            Rs2Inventory.equip(weapon); return "Switching weapon";
        }
        if (dodging) return "Dodging tornadoes and dangerous floor";
        if (boss) nav = new GauntletNavigation(s, p -> s.objects.stream()
                .filter(o -> o.id == 36047 || o.id == 36048)
                .noneMatch(o -> contains(o, p)) && !contains(enemy, p));
        int range = weapon >= 23849 && weapon <= 23851 ? 1 : weapon > 0 ? 6 : 1;
        if (nav.distance(enemy) > range) {
            GauntletActions.walk(nav.waypoint(nav.adjacent(enemy))); return "Approaching combat target";
        }
        if (s.targetIndex != enemy.index) GauntletActions.attack(enemy);
        return boss ? "Fighting Hunllef" : enemy.id >= 9046 ? "Fighting demi-boss" : "Fighting monster for supplies";
    }

    private static boolean contains(GauntletScene.Obj o, WorldPoint p) {
        return p.getX() >= o.point.getX() && p.getX() < o.point.getX() + o.width
                && p.getY() >= o.point.getY() && p.getY() < o.point.getY() + o.height;
    }

    static int weapon(GauntletScene s, GauntletLoadout loadout, HeadIcon icon) {
        int best = -1, tier = 0;
        for (int base : loadout.weapons) {
            if ((base == 23855 && icon == HeadIcon.RANGED) || (base == 23852 && icon == HeadIcon.MAGIC)
                    || (base == 23849 && icon == HeadIcon.MELEE)) continue;
            int available = GauntletLoadout.tier(s, base);
            if (available > tier || (available == tier && available > 0 && s.equipped.contains(base + available - 1))) {
                tier = available; best = base + available - 1;
            }
        }
        return best;
    }

    private void protect(Rs2PrayerEnum prayer, boolean flick) {
        if (ownedPrayer != null && ownedPrayer != prayer && Rs2Prayer.isPrayerActive(ownedPrayer))
            GauntletActions.prayer(ownedPrayer);
        boolean active = Rs2Prayer.isPrayerActive(prayer);
        if (active && flick) GauntletActions.prayer(prayer);
        if (!active || flick) GauntletActions.prayer(prayer);
        ownedPrayer = prayer;
    }

    void releasePrayer() {
        if (ownedPrayer != null && Rs2Prayer.isPrayerActive(ownedPrayer)) GauntletActions.prayer(ownedPrayer);
        ownedPrayer = null;
    }

    private boolean dodge(GauntletScene s, GauntletScene.Mob boss) {
        Set<WorldPoint> floor = new HashSet<>(), unsafe = new HashSet<>();
        for (GauntletScene.Obj o : s.objects) if (o.id >= 36046 && o.id <= 36048) {
            for (int x = 0; x < o.width; x++) for (int y = 0; y < o.height; y++) {
                WorldPoint p = new WorldPoint(o.point.getX() + x, o.point.getY() + y, s.plane);
                floor.add(p); if (o.id != 36046) unsafe.add(p);
            }
        }
        for (int x = 0; x < boss.width; x++) for (int y = 0; y < boss.height; y++)
            unsafe.add(new WorldPoint(boss.point.getX() + x, boss.point.getY() + y, s.plane));
        List<WorldPoint> tornadoes = new ArrayList<>();
        Map<Integer, WorldPoint> current = new HashMap<>();
        for (GauntletScene.Mob n : s.npcs) if (n.id == 9039) {
            tornadoes.add(n.point); current.put(n.index, n.point);
            WorldPoint old = previousTornadoes.get(n.index);
            if (old != null) tornadoes.add(new WorldPoint(n.point.getX() + Integer.signum(n.point.getX() - old.getX()),
                    n.point.getY() + Integer.signum(n.point.getY() - old.getY()), s.plane));
        }
        previousTornadoes.clear(); previousTornadoes.putAll(current);
        if (tornadoes.isEmpty() && !unsafe.contains(s.player)) return false;
        GauntletNavigation safe = new GauntletNavigation(s, p -> !unsafe.contains(p)
                && (floor.isEmpty() ? p.distanceTo(boss.point) <= 12 : floor.contains(p))
                && tornadoes.stream().noneMatch(t -> t.distanceTo(p) <= 1));
        WorldPoint target = safe.distances.keySet().stream().filter(p -> safe.distance(p) > 0 && safe.distance(p) <= 10)
                .max(Comparator.comparingInt(p -> tornadoes.stream().mapToInt(t -> t.distanceTo(p)).min().orElse(10) * 10
                        + Math.min(6, safe.distance(p)))).orElse(null);
        if (target != null) GauntletActions.walk(safe.waypoint(target));
        return true;
    }
}
