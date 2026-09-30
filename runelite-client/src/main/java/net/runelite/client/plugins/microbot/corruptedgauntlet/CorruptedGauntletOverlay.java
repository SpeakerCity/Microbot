package net.runelite.client.plugins.microbot.corruptedgauntlet;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Locale;

public class CorruptedGauntletOverlay extends OverlayPanel {
    private final CorruptedGauntletConfig config;
    private final CorruptedGauntletScript script;

    @Inject
    CorruptedGauntletOverlay(CorruptedGauntletPlugin plugin, CorruptedGauntletConfig config, CorruptedGauntletScript script) {
        super(plugin);
        this.config = config;
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setPreferredSize(new Dimension(310, 220));
        setDragTargetable(true);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) return null;
        panelComponent.getChildren().clear();
        CorruptedGauntletScript.View v = script.view();
        panelComponent.getChildren().add(TitleComponent.builder().text("Corrupted Gauntlet 2.0").color(Color.CYAN).build());
        line("Phase", v.phase);
        line("Status", v.status);
        line("Time remaining", v.timer.isEmpty() ? "—" : v.timer);
        line("Food / potions", v.food + " / " + v.potions);
        line("Doors opened", Integer.toString(v.rooms));
        line("Wins / attempts", v.wins + " / " + v.attempts);
        line("XP gained", number(v.xp));
        line("XP/hr", number(v.xpHour));
        line("Loot value", number(v.profit) + " gp");
        line("Profit/hr", number(v.profitHour) + " gp");
        return super.render(graphics);
    }
    private String number(long value) { return String.format(Locale.US, "%,d", value); }
    private void line(String left, String right) {
        panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).build());
    }
}
