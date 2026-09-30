# Club's Dart

Search for **Club's Dart** in Microbot's plugin panel. Choose a dart type, set
**Min delay (ms)** and **Max delay (ms)**, then enable the plugin with the materials
in your inventory. Keep one slot free for the finished stack. Disable and re-enable
after changing dart type or after a stopped/error status. Delay and movement
settings are read while running.

| Type | Fletching | Materials |
| --- | ---: | --- |
| Mithril | 52 | Mithril dart tips + feathers |
| Adamant | 67 | Adamant dart tips + feathers |
| Rune | 81 | Rune dart tips + feathers |
| Amethyst | 90 | Amethyst dart tips + feathers |
| Dragon | 95 | Dragon dart tips + feathers |
| Atlatl | 74 | Atlatl dart tips + headless Atlatl darts |

For Atlatl, bring at least 100 of each input. After using the tips on the headless
darts, Club's Dart confirms the visible Atlatl Make-X/production menu with Space.
It waits until all
100 finished darts appear in the inventory before starting another batch. A
partial batch does not trigger another attempt; it stops after two minutes without
reaching 100 darts.

This finishes darts from existing materials; it does not smith tips, cut amethyst,
prepare headless darts, buy supplies, or bank.

The delay is an extra pause between clicks, sampled inclusively from the configured
range (0–10000 ms). Mouse travel and game confirmation can extend the interval.
Click points vary inside the current item bounds. Optional short hovers,
semicircles, and circles stay inside that item. Mouse movement uses Microbot's
existing input system and respects manual input takeover.

Each pair of Use clicks waits for the tip selection and then for both ingredient
stacks to decrease and the finished dart stack to increase. A missing response
stops the script after eight seconds; it does not repeatedly retry. The overlay
shows the reason and the number of confirmed darts made.

When the game's crafting menu appears, the plugin selects Make for the selected
dart. An active ordinary dart batch is left alone until production has been idle
for five seconds and the player is no longer animating. For ordinary two-click
darts, use the in-game settings search to find the dart Make-X setting and disable
it if desired.

Requirements were checked against the [Fletching training guide](https://oldschool.runescape.wiki/w/Fletching_training)
and [Atlatl crafting update](https://oldschool.runescape.wiki/w/Update:Varlamore:_The_Final_Dawn_Out_Now).

## Build and test

```text
./gradlew :client:compileJava
./gradlew :client:runUnitTests --tests '*dartfletching.*'
./gradlew :client:dartFletchingJar
./gradlew :client:assemble
```

The plugin is included in clients built from this checkout. The `dartFletchingJar`
task also creates `runelite-client/build/libs/ClubsDartPlugin.jar`. Place it in
`~/.runelite/microbot-plugins/` and restart a compatible Microbot client. Do not
install a second copy when using a client with this plugin built in.

Unit tests cover confirmation gating, failure timeouts, crafting batches, delays,
Atlatl materials, missing supplies, click bounds, and plugin construction. Live
game behavior still needs validation with each recipe and both crafting settings.
