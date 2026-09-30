package net.runelite.client.plugins.microbot.corruptedgauntlet;

import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import java.util.*;

/** Preparation advances only after inventory or scene observations confirm an action. */
final class CorruptedGauntletPrep {
    enum Phase { INITIAL_SWEEP, INITIAL_CRAFT, EQUIPMENT_SWEEP, EQUIPMENT_CRAFT, FISHING, FINAL_PREP, ENTER_BOSS }
    private static final int[] ITEMS = {23837, 23838, 23836, 23835, 23872};
    private static final int[] NODES = {35967, 35969, 35975, 35973, 35971};
    private static final String[] ACTIONS = {"Mine", "Chop", "Pick", "Pick", "Fish"};
    private final Map<WorldPoint, Integer> attemptedDoors = new HashMap<>();
    private final Set<WorldPoint> openedDoors = new HashSet<>();
    private final Set<Integer> stashed = new HashSet<>();
    private WorldPoint home, pendingDoor, crossing;
    private int doorTick, targetNpc = -1, gatheringItem = -1, gatherTick;
    private boolean returningForFood;
    private GauntletLoadout loadout;
    private Phase phase;

    void reset(GauntletLoadout target) {
        loadout = target; phase = Phase.INITIAL_SWEEP; home = pendingDoor = crossing = null;
        attemptedDoors.clear(); openedDoors.clear(); stashed.clear(); targetNpc = gatheringItem = -1; returningForFood = false;
    }
    String phase() { return phase == null ? "Starting" : phase.toString().replace('_', ' '); }
    int roomsOpened() { return openedDoors.size(); }

    String tick(GauntletScene s, GauntletNavigation nav, GauntletCombat combat, boolean flick) {
        GauntletScene.Obj bowl = s.object(35966);
        if (bowl != null && nav.adjacent(bowl) != null) home = nav.adjacent(bowl);
        GauntletScene.Mob enemy = s.npcs.stream().filter(n -> n.index == targetNpc && !n.dead).findFirst().orElse(null);
        if (enemy != null) return combat.fight(s, enemy, nav, loadout, flick, false);
        targetNpc = -1;
        combat.releasePrayer();
        if (s.hp < Math.min(40, s.maxHp - 20) && s.count(ItemID.PADDLEFISH) > 0) {
            Rs2Inventory.interact(ItemID.PADDLEFISH, "Eat"); return "Eating during preparation";
        }
        if (s.count(ItemID.BURNT_FISH_23873) > 0) {
            Rs2Inventory.drop(ItemID.BURNT_FISH_23873); return "Discarding burnt fish";
        }
        if (phase == Phase.INITIAL_CRAFT || phase == Phase.EQUIPMENT_CRAFT || phase == Phase.FINAL_PREP)
            return atBowl(s, nav);
        if (phase == Phase.ENTER_BOSS) {
            if (!loadout.ready(s)) { phase = Phase.FINAL_PREP; return "Rechecking missing supplies"; }
            return enterBoss(s, nav);
        }
        String crossingStatus = finishDoor(s, nav);
        if (crossingStatus != null) return crossingStatus;
        if (s.slots < 28) {
            GauntletScene.Drop drop = s.drops.stream()
                    .filter(i -> loadout.needs(s, i.id, phase == Phase.FISHING)
                            || (i.id == ItemID.RAW_PADDLEFISH && s.foodCount() < 3))
                    .filter(i -> !stashed.contains(i.id) || home == null || i.point.distanceTo(home) > 12
                            || (s.slots < 24 && !loadout.needs(s, 23824, false) && !loadout.needs(s, 23834, false)
                            && !missingUpgrade(s)))
                    .filter(i -> nav.distance(i.point) != Integer.MAX_VALUE)
                    .min(Comparator.comparingInt(i -> nav.distance(i.point))).orElse(null);
            if (drop != null) {
                if (nav.distance(drop.point) > 1) GauntletActions.walk(nav.waypoint(drop.point));
                else GauntletActions.pickup(drop);
                return "Collecting monster supplies";
            }
        }
        if (phase == Phase.INITIAL_SWEEP) {
            int initialShards = (loadout.weaponTier >= 2 ? 80 : 20) + 10 * loadout.potions;
            if ((s.count(23834) > 0 || GauntletLoadout.tier(s, loadout.weapons[0]) > 0)
                    && s.count(23824) >= initialShards) {
                phase = Phase.INITIAL_CRAFT; return "Initial frame and shards ready; returning to bowl";
            }
            GauntletScene.Mob weak = selectMob(s, nav, true);
            if (weak != null) { targetNpc = weak.index; return combat.fight(s, weak, nav, loadout, flick, false); }
            String gathering = gatherNeeded(s, nav, true, false);
            if (gathering != null) return gathering;
            if (s.slots == 28) { phase = Phase.INITIAL_CRAFT; return "Inventory full; processing supplies"; }
            return explore(s, nav, false);
        }
        if (phase == Phase.FISHING) {
            if (s.foodCount() >= loadout.food || s.slots == 28) {
                phase = Phase.FINAL_PREP; return "Returning to cook and finish loadout";
            }
            String gathering = gatherNeeded(s, nav, false, true);
            return gathering != null ? gathering : explore(s, nav, false);
        }
        if (loadout.equipmentReady(s) && materialsComplete(s)) {
            phase = Phase.EQUIPMENT_CRAFT; return "Equipment complete; finishing potions";
        }
        if (s.slots >= 27 || materialsComplete(s)) {
            phase = Phase.EQUIPMENT_CRAFT; return "Returning to craft equipment";
        }
        if (s.hp < 45 && s.count(ItemID.PADDLEFISH) == 0) {
            if (s.count(ItemID.RAW_PADDLEFISH) > 0) {
                returningForFood = true; phase = Phase.EQUIPMENT_CRAFT; return "Returning to cook combat food";
            }
            String food = gather(s, nav, 4, 3);
            if (food != null) return food;
        }
        GauntletScene.Mob demi = s.npcs.stream().filter(n -> !n.dead && needsDemi(s, n.id) && s.hp >= 40)
                .filter(n -> nav.distance(n) != Integer.MAX_VALUE)
                .min(Comparator.comparingInt(nav::distance)).orElse(null);
        if (demi != null) { targetNpc = demi.index; return combat.fight(s, demi, nav, loadout, flick, false); }
        String gathering = gatherNeeded(s, nav, false, false);
        if (gathering != null) return gathering;
        if (loadout.needs(s, 23834, false) || loadout.needs(s, 23824, false)) {
            GauntletScene.Mob monster = selectMob(s, nav, false);
            if (monster != null) { targetNpc = monster.index; return combat.fight(s, monster, nav, loadout, flick, false); }
        }
        return explore(s, nav, missingUpgrade(s));
    }

    private boolean materialsComplete(GauntletScene s) {
        for (int id : new int[]{23837, 23838, 23836, 23824, 23834, 23831, 23832, 23833, 23835})
            if (loadout.needs(s, id, false)) return false;
        return true;
    }
    private GauntletScene.Mob selectMob(GauntletScene s, GauntletNavigation nav, boolean weakOnly) {
        return s.npcs.stream().filter(n -> !n.dead && n.id >= 9040 && n.id <= (weakOnly ? 9042 : 9045))
                .filter(n -> nav.distance(n) != Integer.MAX_VALUE)
                .min(Comparator.comparingInt(n -> nav.distance(n) + (n.id >= 9043 ? 8 : 0))).orElse(null);
    }
    private boolean needsDemi(GauntletScene s, int id) {
        for (int base : loadout.weapons) if (GauntletLoadout.demi(base) == id && loadout.weaponTier == 3
                && GauntletLoadout.tier(s, base) < 3 && s.count(GauntletLoadout.component(base)) == 0) return true;
        return false;
    }
    private boolean missingUpgrade(GauntletScene s) {
        for (int base : loadout.weapons) if (needsDemi(s, GauntletLoadout.demi(base))) return true;
        return false;
    }
    private String gatherNeeded(GauntletScene s, GauntletNavigation nav, boolean initial, boolean onlyFish) {
        int chosen = -1, best = Integer.MAX_VALUE;
        for (int i = 0; i < ITEMS.length; i++) {
            if (onlyFish != (i == 4)) continue;
            int target = i < 3 ? (initial ? Math.min(3, loadout.materialNeeded(s)) : loadout.materialNeeded(s))
                    : i == 3 ? Math.max(0, loadout.potions - s.potionCount() - s.count(23881)) : loadout.food;
            if ((i == 4 ? s.foodCount() : s.count(ITEMS[i])) >= target) continue;
            GauntletScene.Obj node = nearestObject(s, nav, NODES[i]);
            if (node != null && nav.distance(node) < best) { best = nav.distance(node); chosen = i; }
        }
        if (chosen < 0) return null;
        return gather(s, nav, chosen, chosen == 4 ? loadout.food : chosen == 3 ? loadout.potions
                : initial ? Math.min(3, loadout.materialNeeded(s)) : loadout.materialNeeded(s));
    }
    private String gather(GauntletScene s, GauntletNavigation nav, int index, int target) {
        if (s.slots == 28 || target <= 0) return null;
        GauntletScene.Obj node = nearestObject(s, nav, NODES[index]);
        if (node == null) return null;
        if (!(gatheringItem == ITEMS[index] && s.animation != -1 && s.tick - gatherTick < 12)
                && GauntletActions.approach(nav, node)) {
            GauntletActions.object(node, ACTIONS[index]); gatheringItem = ITEMS[index]; gatherTick = s.tick;
        }
        return ACTIONS[index] + " supplies: " + (index == 4 ? s.foodCount() : s.count(ITEMS[index])) + "/" + target;
    }
    private GauntletScene.Obj nearestObject(GauntletScene s, GauntletNavigation nav, int id) {
        return s.objects.stream().filter(o -> o.id == id && nav.distance(o) != Integer.MAX_VALUE)
                .min(Comparator.comparingInt(nav::distance)).orElse(null);
    }

    private String atBowl(GauntletScene s, GauntletNavigation nav) {
        GauntletScene.Obj bowl = nearestObject(s, nav, 35966);
        if (bowl == null || nav.distance(bowl) > 12) {
            if (s.count(23858) > 0 && (bowl == null || nav.distance(bowl) > 45)) {
                GauntletActions.inventoryAction(23858, "Teleport", "Activate"); return "Teleporting to starting room";
            }
            if (bowl != null) GauntletActions.approach(nav, bowl);
            else if (home != null && nav.distance(home) != Integer.MAX_VALUE) GauntletActions.walk(nav.waypoint(home));
            else return explore(s, nav, false);
            return "Returning to starting room";
        }
        int firstTier = GauntletLoadout.tier(s, loadout.weapons[0]);
        // Leave an item in inventory while its next upgrade is affordable.
        GauntletLoadout.Recipe recipe = loadout.remaining(s).stream().filter(r -> r.affordable(s))
                .filter(r -> phase != Phase.INITIAL_CRAFT || (r.output >= loadout.weapons[0]
                        && r.output <= loadout.weapons[0] + 1) || r.material > 0).findFirst().orElse(null);
        if (recipe != null) {
            if (recipe.previous > 0 && s.equipped.contains(recipe.previous)) {
                if (s.slots == 28) {
                    int spare = s.count(23872) > 0 ? 23872 : Arrays.stream(GauntletLoadout.MATERIALS)
                            .filter(id -> s.count(id) > recipe.material).boxed()
                            .max(Comparator.comparingInt(s::count)).orElse(-1);
                    if (spare > 0) { stashed.add(spare); Rs2Inventory.drop(spare); }
                    return "Freeing one slot for equipment upgrade";
                }
                Rs2Equipment.unEquip(recipe.previous); return "Removing item for upgrade";
            }
            if (!GauntletActions.recipe(recipe.output) && GauntletActions.approach(nav, bowl))
                GauntletActions.object(bowl, "Sing-crystal");
            return "Crafting equipment at singing bowl";
        }
        if (firstTier > 0 && !s.equipped.contains(loadout.weapons[0] + firstTier - 1)) {
            Rs2Inventory.equip(loadout.weapons[0] + firstTier - 1); return "Equipping primary weapon";
        }
        for (int base : GauntletLoadout.ARMOR) {
            int tier = GauntletLoadout.tier(s, base);
            if (tier > 0 && !s.equipped.contains(base + tier - 1)) {
                Rs2Inventory.equip(base + tier - 1); return "Equipping armour";
            }
        }
        if (s.count(23872) > 0 && (phase == Phase.FINAL_PREP || returningForFood || s.slots >= 26)) {
            if (s.animation == 896 || s.animation == 897) return "Cooking paddlefish";
            GauntletScene.Obj range = nearestObject(s, nav, 35980);
            if (!GauntletActions.recipe(23874) && range != null && GauntletActions.approach(nav, range))
                GauntletActions.object(range, "Cook");
            return "Cooking paddlefish";
        }
        returningForFood = false;
        String potion = potion(s, nav, bowl);
        if (potion != null) return potion;
        if (phase == Phase.INITIAL_CRAFT) {
            phase = firstTier >= Math.min(2, loadout.weaponTier) ? Phase.EQUIPMENT_SWEEP : Phase.INITIAL_SWEEP;
            return "Leaving bowl for remaining supplies";
        }
        if (!loadout.equipmentReady(s) || s.potionCount() < loadout.potions) {
            if (s.slots >= 27) {
                int drop = s.count(23872) > 0 ? 23872 :
                        Arrays.stream(GauntletLoadout.MATERIALS).filter(id -> s.count(id) > 0).boxed()
                                .max(Comparator.comparingInt(s::count)).orElse(-1);
                if (drop > 0) { stashed.add(drop); Rs2Inventory.drop(drop); return "Storing supplies beside bowl to free space"; }
            }
            phase = Phase.EQUIPMENT_SWEEP; return "Gathering missing equipment or potion materials";
        }
        for (int id : new int[]{23820, 23858, 23821, 23822, 23865, 23837, 23838, 23836, 23834, 23835, 23830, 23831, 23832, 23833, 23824}) {
            if (s.count(id) > 0) { Rs2Inventory.drop(id); return "Clearing finished prep materials"; }
        }
        if (s.foodCount() < loadout.food) { phase = Phase.FISHING; return "Gathering configured food target"; }
        if (s.count(23872) > 0) { phase = Phase.FINAL_PREP; return "Cooking remaining paddlefish"; }
        if (!loadout.ready(s)) { phase = Phase.EQUIPMENT_SWEEP; return "Rechecking loadout"; }
        phase = Phase.ENTER_BOSS; return "Configured loadout ready";
    }

    private String potion(GauntletScene s, GauntletNavigation nav, GauntletScene.Obj bowl) {
        if (s.potionCount() >= loadout.potions) return null;
        if (s.count(23881) > 0 && s.count(23830) >= 10) {
            GauntletActions.combine(s, 23830, 23881); return "Finishing Egniol potion";
        }
        if (s.count(23880) > 0 && s.count(23835) > 0) {
            GauntletActions.combine(s, 23835, 23880); return "Mixing Grym leaf";
        }
        if (s.count(23881) > 0 && s.count(23830) < 10 && s.count(23824) >= 10) {
            GauntletActions.combine(s, 23865, 23824); return "Grinding potion dust";
        }
        if (s.count(23839) > 0) {
            GauntletScene.Obj pump = nearestObject(s, nav, 35981);
            if (pump != null && GauntletActions.approach(nav, pump)) GauntletActions.object(pump, "Fill-from");
            return "Filling corrupted vials";
        }
        int containers = s.potionCount() + s.count(23839) + s.count(23880) + s.count(23881);
        if (containers < loadout.potions && s.count(23824) >= 10 && s.slots < 28) {
            if (!GauntletActions.recipe(23839) && GauntletActions.approach(nav, bowl)) GauntletActions.object(bowl, "Sing-crystal");
            return "Crafting corrupted vial";
        }
        return null;
    }

    private String enterBoss(GauntletScene s, GauntletNavigation nav) {
        GauntletScene.Obj barrier = s.objects.stream().filter(o -> (o.id == 35982 || o.id == 35983) && o.action("Pass"))
                .filter(o -> nav.distance(o) != Integer.MAX_VALUE).min(Comparator.comparingInt(nav::distance)).orElse(null);
        if (barrier != null) {
            if (GauntletActions.approach(nav, barrier)) GauntletActions.object(barrier, "Pass");
            return "Loadout ready; passing Hunllef barrier";
        }
        return "Loadout ready; locating Hunllef barrier";
    }
    private String finishDoor(GauntletScene s, GauntletNavigation nav) {
        if (pendingDoor == null) return null;
        boolean closed = s.objects.stream().anyMatch(o -> o.point.equals(pendingDoor) && o.action("Light"));
        if (!closed) {
            openedDoors.add(pendingDoor);
            if (crossing != null && nav.distance(crossing) != Integer.MAX_VALUE && nav.distance(crossing) > 1) {
                GauntletActions.walk(nav.waypoint(crossing));
                if (s.tick - doorTick < 10) return "Moving through newly opened room";
            }
            pendingDoor = crossing = null;
        } else if (s.tick - doorTick >= 4) pendingDoor = crossing = null;
        else return "Waiting for room to open";
        return null;
    }
    private String explore(GauntletScene s, GauntletNavigation nav, boolean outer) {
        GauntletScene.Obj door = s.objects.stream().filter(o -> (o.id == 35998 || o.id == 35999) && o.action("Light"))
                .filter(o -> s.tick - attemptedDoors.getOrDefault(o.point, -100) >= 8)
                .filter(o -> nav.distance(o) != Integer.MAX_VALUE)
                .min(Comparator.comparingInt(o -> nav.distance(o) -
                        (outer && home != null ? o.point.distanceTo(home) / 2 : 0))).orElse(null);
        if (door == null) return "No reachable unopened room; waiting for scene update";
        if (!GauntletActions.approach(nav, door)) return outer ? "Exploring outer rooms for demi-bosses" : "Moving to resource room";
        int dx = door.point.getX() - s.player.getX(), dy = door.point.getY() - s.player.getY();
        if (Math.abs(dx) > Math.abs(dy)) { dx = Integer.signum(dx); dy = 0; }
        else { dy = Integer.signum(dy); dx = 0; }
        if (GauntletActions.object(door, "Light")) {
            pendingDoor = door.point; crossing = new WorldPoint(door.point.getX() + dx * 3, door.point.getY() + dy * 3, s.plane);
            doorTick = s.tick; attemptedDoors.put(door.point, s.tick);
        }
        return "Lighting next room";
    }
}
