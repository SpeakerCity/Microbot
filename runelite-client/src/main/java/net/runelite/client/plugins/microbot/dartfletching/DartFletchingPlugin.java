package net.runelite.client.plugins.microbot.dartfletching;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.inject.Provider;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Club's Dart",
        description = "Fletch mithril through dragon darts and Atlatl darts with confirmed actions",
        tags = {"microbot", "fletching", "darts", "atlatl"},
        enabledByDefault = false,
        isExternal = true,
        version = "1.0.1"
)
public class DartFletchingPlugin extends Plugin {
    // PluginManager constructs Plugin subclasses using their no-argument constructor.
    @Inject
    private Provider<DartFletchingScript> scripts;
    @Inject
    private OverlayManager overlays;
    @Inject
    private DartFletchingOverlay overlay;
    @Inject
    private DartFletchingConfig config;
    private DartFletchingScript script;

    @Provides
    DartFletchingConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(DartFletchingConfig.class);
    }

    @Override
    protected void startUp() {
        if (script != null) {
            return;
        }
        script = scripts.get();
        overlay.setScript(script);
        overlays.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        if (script != null) {
            script.shutdown();
            script = null;
        }
        overlays.remove(overlay);
        overlay.setScript(null);
    }
}
