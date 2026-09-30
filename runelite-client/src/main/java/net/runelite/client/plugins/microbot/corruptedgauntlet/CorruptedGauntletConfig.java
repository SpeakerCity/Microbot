package net.runelite.client.plugins.microbot.corruptedgauntlet;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("MicrobotCorruptedGauntlet")
public interface CorruptedGauntletConfig extends Config {
    @ConfigSection(name = "Loadout", description = "Choose the target supplies and equipment for each run", position = 0)
    String loadoutSection = "loadout";

    @ConfigItem(keyName = "foodCount", name = "Food to keep", description = "Target paddlefish count before the boss", position = 0, section = loadoutSection)
    @Range(min = 0, max = 24)
    default int foodCount() { return 20; }

    @ConfigItem(keyName = "prayerPotionCount", name = "Prayer potions", description = "Target potion count (Gauntlet potion resources are converted in the arena)", position = 1, section = loadoutSection)
    @Range(min = 0, max = 4)
    default int prayerPotionCount() { return 2; }

    @ConfigItem(keyName = "armorTier", name = "Armor tier", description = "Target corrupted armor tier", position = 2, section = loadoutSection)
    default ArmorTier armorTier() { return ArmorTier.ATTUNED; }

    @ConfigItem(keyName = "weaponTier", name = "Weapon tier", description = "Target corrupted weapon tier", position = 3, section = loadoutSection)
    default ArmorTier weaponTier() { return ArmorTier.PERFECTED; }

    @ConfigItem(keyName = "weaponStyle", name = "First weapon", description = "Preferred first weapon style", position = 4, section = loadoutSection)
    default WeaponStyle weaponStyle() { return WeaponStyle.BOW; }

    @ConfigItem(keyName = "secondWeaponStyle", name = "Second weapon", description = "Preferred second weapon style", position = 5, section = loadoutSection)
    default WeaponStyle secondWeaponStyle() { return WeaponStyle.STAFF; }

    @ConfigItem(keyName = "showOverlay", name = "Show run overlay", description = "Show session XP/hr, profit/hr, and run status", position = 6, section = loadoutSection)
    default boolean showOverlay() { return true; }

    @ConfigItem(keyName = "crystalShardValue", name = "Crystal shard value", description = "GP value per untradeable crystal shard for profit tracking; wiki guide assumes divine potion conversion", position = 7, section = loadoutSection)
    @Range(min = 0, max = 50000)
    default int crystalShardValue() { return 16900; }

    @ConfigItem(keyName = "prayerFlicking", name = "Tick prayer flicking", description = "Refresh protection prayer on each observed game tick during combat", position = 8, section = loadoutSection)
    default boolean prayerFlicking() { return true; }

    enum ArmorTier { BASIC, ATTUNED, PERFECTED }
    enum WeaponStyle { BOW, STAFF, HALBERD }
}
