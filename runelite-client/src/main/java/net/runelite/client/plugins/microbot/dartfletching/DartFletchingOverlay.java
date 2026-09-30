package net.runelite.client.plugins.microbot.dartfletching;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import lombok.Setter;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class DartFletchingOverlay extends OverlayPanel {
    @Setter
    private volatile DartFletchingScript script;

    @Inject
    public DartFletchingOverlay() {
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(240, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        DartFletchingScript current = script;
        if (current == null) {
            return null;
        }
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder().text("Dart Fletching").build());
        panelComponent.getChildren().add(LineComponent.builder().left(current.getStatus()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Darts made")
                .right(Long.toString(current.getDartsMade())).build());
        return super.render(graphics);
    }
}
