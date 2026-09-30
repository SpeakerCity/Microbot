package net.runelite.client.plugins.microbot.corruptedgauntlet;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import java.util.*;

/** All positions use the same live instance coordinates, never mixed template coordinates. */
final class GauntletScene {
    int tick, hp, maxHp, prayer, animation, targetIndex = -1, slots, selectedItem = -1;
    int baseX, baseY, plane;
    int worldViewId = -1;
    boolean loggedIn, inside, bossStarted, rewardAvailable, moving;
    WorldPoint player;
    String timer = "";
    int[][] collision;
    final Map<Integer, Integer> inventory = new HashMap<>();
    final Set<Integer> equipped = new HashSet<>();
    final Map<Skill, Integer> xp = new EnumMap<>(Skill.class);
    final List<Obj> objects = new ArrayList<>();
    final List<Mob> npcs = new ArrayList<>();
    final List<Drop> drops = new ArrayList<>();

    static GauntletScene read() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GauntletScene s = new GauntletScene();
            Client c = Microbot.getClient();
            if (c == null) return s;
            s.tick = c.getTickCount();
            s.loggedIn = c.getGameState() == GameState.LOGGED_IN && c.getLocalPlayer() != null;
            if (!s.loggedIn) return s;
            Player p = c.getLocalPlayer();
            s.player = p.getWorldLocation();
            s.plane = s.player.getPlane();
            s.animation = p.getAnimation();
            s.moving = p.getPoseAnimation() != p.getIdlePoseAnimation();
            if (p.getInteracting() instanceof NPC) s.targetIndex = ((NPC) p.getInteracting()).getIndex();
            s.bossStarted = c.getVarbitValue(VarbitID.GAUNTLET_BOSS_STARTED) == 1;
            s.inside = s.bossStarted || c.getVarbitValue(VarbitID.PLAYER_IN_GAUNTLET) == 1;
            s.rewardAvailable = c.getVarbitValue(VarbitID.GAUNTLET_REWARD_AVAILABLE) > 0;
            s.hp = c.getBoostedSkillLevel(Skill.HITPOINTS);
            s.maxHp = c.getRealSkillLevel(Skill.HITPOINTS);
            s.prayer = c.getBoostedSkillLevel(Skill.PRAYER);
            for (Skill skill : Skill.values()) if (skill != Skill.OVERALL) s.xp.put(skill, c.getSkillExperience(skill));
            Rs2Inventory.items().forEach(i -> { s.inventory.merge(i.getId(), i.getQuantity(), Integer::sum); s.slots++; });
            Rs2Equipment.items().forEach(i -> s.equipped.add(i.getId()));
            if (Rs2Inventory.isItemSelected()) s.selectedItem = Rs2Inventory.getSelectedItemId();
            Widget timer = c.getWidget(InterfaceID.GauntletOverlay.TIMER_TEXT);
            if (timer != null && !timer.isHidden()) s.timer = timer.getText().replaceAll("<[^>]*>", "");
            WorldView w = p.getWorldView();
            if (w == null) return null;
            s.worldViewId = w.getId();
            s.baseX = w.getBaseX(); s.baseY = w.getBaseY();
            CollisionData[] maps = w.getCollisionMaps();
            if (maps != null && maps[s.plane] != null) {
                int[][] flags = maps[s.plane].getFlags();
                s.collision = new int[flags.length][];
                for (int x = 0; x < flags.length; x++) s.collision[x] = flags[x].clone();
            }
            Microbot.getRs2TileObjectCache().getStream().filter(o -> o.getPlane() == s.plane)
                    .filter(o -> o.getWorldView() != null && o.getWorldView().getId() == w.getId())
                    .forEach(o -> {
                        WorldPoint point = o.getWorldLocation();
                        if (point == null || point.distanceTo(s.player) > 24) return;
                        ObjectComposition rawComposition = c.getObjectDefinition(o.getId());
                        if (rawComposition == null) return;
                        int[] impostorIds = rawComposition.getImpostorIds();
                        ObjectComposition composition = impostorIds == null ? rawComposition : rawComposition.getImpostor();
                        if (composition == null) composition = rawComposition;
                        String name = composition.getName() == null ? "" : composition.getName();
                        String[] actions = composition.getActions();
                        boolean gauntletObject = (o.getId() >= 35964 && o.getId() <= 36151)
                                || name.toLowerCase(Locale.ROOT).contains("gauntlet")
                                || name.toLowerCase(Locale.ROOT).contains("singing")
                                || hasEnterAction(actions);
                        if (gauntletObject && point.distanceTo(s.player) <= 24) {
                            s.objects.add(new Obj(o.getId(), composition.getId(), rawComposition.getName(), name,
                                    impostorIds == null ? "[]" : Arrays.toString(impostorIds), o.getWorldView().getId(),
                                    point, actions, Math.max(1, composition.getSizeX()),
                                    Math.max(1, composition.getSizeY())));
                        }
                    });
            Microbot.getRs2NpcCache().getStream().filter(n -> n.getId() >= 9035 && n.getId() <= 9048)
                    .forEach(n -> s.npcs.add(new Mob(n.getId(), n.getIndex(), n.getWorldLocation(),
                            n.getWorldArea().getWidth(), n.getWorldArea().getHeight(), safeHeadIcon(n),
                            n.isDead(), n.getInteracting() == p)));
            Microbot.getRs2TileItemCache().getStream().forEach(i -> s.drops.add(new Drop(i.getId(), i.getWorldLocation())));
            return s;
        }).orElse(null);
    }

    private static boolean hasEnterAction(String[] actions) {
        return actions != null && Arrays.stream(actions).filter(Objects::nonNull)
                .anyMatch(a -> a.toLowerCase(Locale.ROOT).contains("enter"));
    }

    /** NPC prayer overheads are optional; never call the logging helper for non-boss NPCs. */
    private static HeadIcon safeHeadIcon(net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel model) {
        if (model.getId() < 9035 || model.getId() > 9038 || model.getNpc() == null) return null;
        short[] spriteIds = model.getNpc().getOverheadSpriteIds();
        if (spriteIds == null) return null;
        for (int spriteId : spriteIds) {
            if (spriteId >= 0 && spriteId < HeadIcon.values().length) return HeadIcon.values()[spriteId];
        }
        return null;
    }

    int count(int id) { return inventory.getOrDefault(id, 0); }
    boolean has(int id) { return count(id) > 0 || equipped.contains(id); }
    int foodCount() { return count(ItemID.RAW_PADDLEFISH) + count(ItemID.PADDLEFISH); }
    int potionCount() { return count(23882) + count(23883) + count(23884) + count(23885); }
    Obj object(int id) { return objects.stream().filter(o -> o.id == id).findFirst().orElse(null); }
    Mob boss() { return npcs.stream().filter(n -> n.id >= 9035 && n.id <= 9038 && !n.dead).findFirst().orElse(null); }

    static class Obj {
        final int id, resolvedId, width, height, worldViewId;
        final String baseName, name, impostorIds;
        final WorldPoint point;
        final List<String> actions;
        Obj(int id, int resolvedId, String baseName, String name, String impostorIds, int worldViewId,
            WorldPoint point, String[] actions, int width, int height) {
            this.id = id; this.resolvedId = resolvedId; this.baseName = baseName; this.name = name;
            this.impostorIds = impostorIds; this.worldViewId = worldViewId;
            this.point = point; this.width = width; this.height = height;
            this.actions = actions == null ? Collections.emptyList() : Arrays.asList(actions);
        }
        boolean action(String name) { return actions.stream().anyMatch(a -> name.equalsIgnoreCase(a)); }
    }
    static final class Mob extends Obj {
        final int index;
        final HeadIcon icon;
        final boolean dead, attacking;
        Mob(int id, int index, WorldPoint p, int width, int height, HeadIcon icon, boolean dead, boolean attacking) {
            super(id, id, "NPC", "NPC", "[]", -1, p, null, width, height);
            this.index = index; this.icon = icon; this.dead = dead; this.attacking = attacking;
        }
    }
    static final class Drop {
        final int id;
        final WorldPoint point;
        Drop(int id, WorldPoint point) { this.id = id; this.point = point; }
    }
}
