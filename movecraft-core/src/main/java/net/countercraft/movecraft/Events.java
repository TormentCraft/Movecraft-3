package net.countercraft.movecraft;

import net.countercraft.movecraft.api.events.SinkingStartedEvent;
import org.bukkit.plugin.PluginManager;

public final class Events {
    private final PluginManager pluginManager;

    public Events(final PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public boolean sinkingStarted() {
        final SinkingStartedEvent sinkingStartedEvent = new SinkingStartedEvent();
        this.pluginManager.callEvent(sinkingStartedEvent);
        return sinkingStartedEvent.isCancelled();
    }
}
