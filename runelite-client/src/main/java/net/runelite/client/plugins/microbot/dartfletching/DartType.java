package net.runelite.client.plugins.microbot.dartfletching;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

@Getter
@RequiredArgsConstructor
public enum DartType {
    MITHRIL("Mithril", ItemID.MITHRIL_DART_TIP, ItemID.FEATHER, ItemID.MITHRIL_DART, 52),
    ADAMANT("Adamant", ItemID.ADAMANT_DART_TIP, ItemID.FEATHER, ItemID.ADAMANT_DART, 67),
    RUNE("Rune", ItemID.RUNE_DART_TIP, ItemID.FEATHER, ItemID.RUNE_DART, 81),
    AMETHYST("Amethyst", ItemID.AMETHYST_DART_TIP, ItemID.FEATHER, ItemID.AMETHYST_DART, 90),
    DRAGON("Dragon", ItemID.DRAGON_DART_TIP, ItemID.FEATHER, ItemID.DRAGON_DART, 95),
    ATLATL("Atlatl", ItemID.ATLATL_DART_TIPS, ItemID.HEADLESS_ATLATL_DART, ItemID.ATLATL_DART, 74);

    private final String label;
    private final int tipId;
    private final int materialId;
    private final int productId;
    private final int level;

    public String productName() {
        return label + " dart";
    }

    @Override
    public String toString() {
        return label;
    }
}
