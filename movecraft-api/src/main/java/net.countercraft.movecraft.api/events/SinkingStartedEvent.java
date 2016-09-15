package net.countercraft.movecraft.api.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Created by alex on 9/15/16.
 */
public class SinkingStartedEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled;

    @Override public boolean isCancelled() {
        return cancelled;
    }

    @Override public void setCancelled(boolean b) {
        cancelled = b;
    }

    @Override public HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
