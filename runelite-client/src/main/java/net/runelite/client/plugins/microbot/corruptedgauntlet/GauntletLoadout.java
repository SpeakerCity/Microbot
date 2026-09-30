package net.runelite.client.plugins.microbot.corruptedgauntlet;

import net.runelite.api.ItemID;
import java.util.ArrayList;
import java.util.List;

/** An immutable target for one attempt. Recipe costs are incremental, not cumulative. */
final class GauntletLoadout {
    static final int[] ARMOR = {23840, 23843, 23846};
    static final int[] MATERIALS = {23837, 23838, 23836};
    final int food, potions, armorTier, weaponTier;
    final int[] weapons;

    GauntletLoadout(CorruptedGauntletConfig config) {
        food = config.foodCount();
        potions = config.prayerPotionCount();
        armorTier = config.armorTier().ordinal() + 1;
        weaponTier = config.weaponTier().ordinal() + 1;
        weapons = new int[]{base(config.weaponStyle()), base(config.secondWeaponStyle())};
    }

    String validationError() {
        if (weapons[0] == weapons[1]) return "Choose two different weapon styles";
        if (food + potions + 2 > 28) return "Food + potions must be at most 26 (weapon and harpoon need space)";
        return null;
    }

    static int base(CorruptedGauntletConfig.WeaponStyle style) {
        switch (style) {
            case STAFF: return 23852;
            case HALBERD: return 23849;
            default: return 23855;
        }
    }

    static int component(int base) { return base == 23852 ? 23833 : base == 23855 ? 23832 : 23831; }
    static int demi(int base) { return base == 23852 ? 9047 : base == 23855 ? 9048 : 9046; }
    static int tier(GauntletScene s, int base) {
        for (int t = 3; t > 0; t--) if (s.has(base + t - 1)) return t;
        return 0;
    }

    boolean equipmentReady(GauntletScene s) {
        for (int base : ARMOR) if (tier(s, base) < armorTier) return false;
        for (int base : weapons) if (tier(s, base) < weaponTier) return false;
        return true;
    }

    boolean ready(GauntletScene s) {
        return equipmentReady(s) && s.count(ItemID.PADDLEFISH) >= food && s.potionCount() >= potions;
    }

    List<Recipe> remaining(GauntletScene s) {
        List<Recipe> result = new ArrayList<>();
        for (int base : weapons) {
            for (int t = tier(s, base) + 1; t <= weaponTier; t++) {
                result.add(new Recipe(base + t - 1, t == 1 ? -1 : base + t - 2,
                        0, t == 1 ? 20 : t == 2 ? 60 : 0,
                        t == 1 ? ItemID.WEAPON_FRAME : t == 3 ? component(base) : -1));
            }
        }
        for (int piece = 0; piece < ARMOR.length; piece++) {
            int base = ARMOR[piece];
            for (int t = tier(s, base) + 1; t <= armorTier; t++) {
                result.add(new Recipe(base + t - 1, t == 1 ? -1 : base + t - 2,
                        t == 1 ? 1 : t == 2 ? (piece == 1 ? 2 : 1) : 2, t * 20 + 20, -1));
            }
        }
        return result;
    }

    int materialNeeded(GauntletScene s) { return remaining(s).stream().mapToInt(r -> r.material).sum(); }
    int shardsNeeded(GauntletScene s) {
        int missingPotions = Math.max(0, potions - s.potionCount());
        int unfinished = s.count(ItemID.GRYM_POTION_UNF);
        int empty = s.count(ItemID.VIAL_23839) + s.count(ItemID.WATERFILLED_VIAL);
        return remaining(s).stream().mapToInt(r -> r.shards).sum()
                + 10 * Math.max(0, missingPotions - unfinished - empty)
                + Math.max(0, missingPotions * 10 - s.count(ItemID.CORRUPTED_DUST));
    }

    boolean needs(GauntletScene s, int id, boolean fish) {
        for (int material : MATERIALS) if (id == material) return s.count(id) < materialNeeded(s);
        if (id == ItemID.CORRUPTED_SHARDS) return s.count(id) < shardsNeeded(s);
        if (id == ItemID.WEAPON_FRAME) {
            int missing = 0;
            for (int base : weapons) if (tier(s, base) == 0) missing++;
            return s.count(id) < missing;
        }
        if (id == ItemID.GRYM_LEAF) return s.count(id) + s.count(ItemID.GRYM_POTION_UNF) + s.potionCount() < potions;
        if (id == ItemID.RAW_PADDLEFISH) return fish && s.foodCount() < food;
        for (int base : weapons) if (id == component(base)) return weaponTier == 3 && tier(s, base) < 3 && s.count(id) == 0;
        return false;
    }

    static final class Recipe {
        final int output, previous, material, shards, component;
        Recipe(int output, int previous, int material, int shards, int component) {
            this.output = output; this.previous = previous; this.material = material;
            this.shards = shards; this.component = component;
        }
        boolean affordable(GauntletScene s) {
            if (previous > 0 && !s.has(previous)) return false;
            if (component > 0 && s.count(component) == 0) return false;
            for (int id : MATERIALS) if (s.count(id) < material) return false;
            return s.count(ItemID.CORRUPTED_SHARDS) >= shards;
        }
    }
}
