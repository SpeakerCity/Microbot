package net.runelite.client.plugins.microbot.corruptedgauntlet;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.NPC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ActorDeath;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(name = PluginDescriptor.Mocrosoft + "Corrupted Gauntlet", version = "2.0.0", description = "Repeats Corrupted Gauntlet runs and tracks session gains", tags = {"microbot", "gauntlet", "pvm"}, enabledByDefault = false, isExternal = true)
public class CorruptedGauntletPlugin extends Plugin {
    // The external plugin loader requires a no-argument constructor before member injection.
    @Inject private CorruptedGauntletConfig config;
    @Inject private CorruptedGauntletScript script;
    @Inject private CorruptedGauntletOverlay overlay;
    @Inject private OverlayManager overlayManager;

    @Provides
    CorruptedGauntletConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CorruptedGauntletConfig.class);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        overlayManager.remove(overlay);
    }

    @Override
    protected void startUp() throws Exception {
        overlayManager.add(overlay);
        script.run(config);
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (event.getActor() instanceof NPC) {
            NPC npc = (NPC) event.getActor();
            script.onHunllefAnimation(npc.getId(), npc.getAnimation());
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        script.onProjectile(event.getProjectile().getId(), event.getProjectile().getStartCycle());
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        script.onChatMessage(event.getMessage());
    }

    @Subscribe public void onGameTick(GameTick event) { script.onGameTick(); }
    @Subscribe public void onGameStateChanged(GameStateChanged event) { script.onGameTick(); }
    @Subscribe public void onActorDeath(ActorDeath event) {
        if (event.getActor() instanceof NPC) script.onBossDeath(((NPC) event.getActor()).getId());
    }
}
