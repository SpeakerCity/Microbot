# Corrupted Gauntlet 2.0

Enable in the Gauntlet lobby, or during preparation. Disable the plugin to stop looping.
Settings are captured at the beginning of each attempt. Choose two different weapon
styles. Food plus potions cannot exceed 26; the alternate weapon and harpoon need
the remaining inventory slots while fishing.

The Corrupted Gauntlet allows 7:30 preparation; normal Gauntlet allows 10:00.
The overlay displays the game's timer. There is no early-entry timer fallback:
manual boss entry requires the selected armour, both weapons, cooked food and
potions. If the game forces entry when time expires, combat handles the encounter.

## Flow

Initial room exploration and weak monsters → first weapon and armour → remaining
resource nodes, monsters and required demi-bosses → equipment and potions → fishing
and cooking → inventory readiness check → Hunllef → reward chest → another run.

Routes use live instance coordinates and scene collision edges. Boss combat selects
a weapon against Hunllef's protection prayer, switches protection using animations
and projectiles, and routes around dangerous floor and tornado positions.
Tick prayer flicking can be disabled in settings.

The shared singleton script publishes the overlay state. XP counts positive skill
changes after the initial sample, excluding overall XP. Reconnecting establishes a
new baseline. Loot value uses observed chest inventory gains, GE prices, and the
configured value per crystal shard. Profit is a loot-value estimate and does not
include sale fees or loot that was not received into the inventory.

## Build and install

Run `build-plugin.ps1 -JavaHome <working-JDK-path>`. The script compiles the client
sources and packages only this plugin into
`runelite-client/build/libs/CorruptedGauntletPlugin.jar`.
Replace the old JAR in `%USERPROFILE%\.runelite\microbot-plugins`, restart Microbot,
and check the overlay title says **Corrupted Gauntlet 2.0**.

Compilation has been checked. A complete live preparation/combat/reward cycle has
not been verified; this build still needs an in-game trial.

References: [resource rooms](https://oldschool.runescape.wiki/w/The_Gauntlet#Resource_rooms),
[strategies](https://oldschool.runescape.wiki/w/The_Gauntlet/Strategies),
[Gauntlet Coach](https://github.com/Conroks/Gauntlet-Coach),
[CgPrepTracker](https://github.com/Rdoolz51/CgPrepTracker).
