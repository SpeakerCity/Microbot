package net.runelite.client.plugins.microbot.dartfletching;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(DartFletchingConfig.GROUP)
public interface DartFletchingConfig extends Config {
    String GROUP = "dartfletching";

    @ConfigItem(keyName = "dartType", name = "Dart type", position = 0,
            description = "Darts to make. Atlatl uses headless Atlatl darts instead of feathers. Restart after changing type.")
    default DartType dartType() {
        return DartType.MITHRIL;
    }

    @Range(min = 0, max = 10000)
    @ConfigItem(keyName = "minDelay", name = "Min delay (ms)", position = 1,
            description = "Minimum extra pause between clicks. Game confirmation and mouse travel can take longer.")
    default int minDelay() {
        return 150;
    }

    @Range(min = 0, max = 10000)
    @ConfigItem(keyName = "maxDelay", name = "Max delay (ms)", position = 2,
            description = "Maximum extra pause between clicks; must be at least the minimum delay.")
    default int maxDelay() {
        return 350;
    }

    @ConfigItem(keyName = "mouseVariation", name = "Hover and arc movements", position = 3,
            description = "Occasionally hover or trace a small semicircle/circle inside the item before clicking Use.")
    default boolean mouseVariation() {
        return true;
    }
}
