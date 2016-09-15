package net.countercraft.movecraft;

import net.countercraft.movecraft.api.events.SinkingStartedEvent;
import org.bukkit.plugin.PluginManager;

/**
 * Created by alex on 9/15/16.
 */
public final class Events {
    private final PluginManager pluginManager;

    public Events(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public boolean sinkingStarted() {
        SinkingStartedEvent sinkingStartedEvent = new SinkingStartedEvent();
        pluginManager.callEvent(sinkingStartedEvent);
        return sinkingStartedEvent.isCancelled();
    }
}
