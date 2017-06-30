package net.countercraft.movecraft.utils;

import com.mewin.WGCustomFlags.WGCustomFlagsPlugin;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.Location;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * @author mwkaicz <mwkaicz@gmail.com>
 */
public class WGCustomFlagsUtils {

    public static StateFlag getNewStateFlag(final String name, final boolean def) {
        final Constructor<?> cc = getStateFlagConstructor();
        if (cc == null) {
            return null;
        }
        try {
            final Object o = cc.newInstance(name, def);
            return (StateFlag) o;
        } catch (InstantiationException | InvocationTargetException | IllegalArgumentException | IllegalAccessException ex) {
            return null;
        }
    }

    public static Constructor<?> getStateFlagConstructor() {
        try {
            final Class<?> c = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            return c.getConstructor(String.class, boolean.class);
        } catch (ClassNotFoundException | SecurityException | NoSuchMethodException ex) {
            return null;
        }
    }

    public static boolean registerStageFlag(final WGCustomFlagsPlugin plugin, final Object o) {
        if (plugin != null) {
            final Constructor<?> cc = getStateFlagConstructor();
            if (cc == null) {
                return false;
            }
            try {
                plugin.addCustomFlag((Flag) o);
            } catch (final Exception e) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static void registerFlags(final WGCustomFlagsPlugin plugin, final StateFlag... flags) {
        for (final StateFlag flag : flags) {
            if (flag != null) registerStageFlag(plugin, flag);
        }
    }

    public static boolean validateFlag(final WorldGuardPlugin worldGuardPlugin, final Location loc, final Object flag) {
        if (flag != null) {
            final StateFlag.State state = (StateFlag.State) worldGuardPlugin.getRegionManager(loc.getWorld())
                                                                            .getApplicableRegions(loc).getFlag((Flag) flag);
            return state != null && state == StateFlag.State.ALLOW;
        } else {
            return true;
        }
    }

    public static boolean validateFlag(final WorldGuardPlugin worldGuardPlugin, final Location loc, final Object flag, final LocalPlayer lp) {
        if (flag != null) {
            final StateFlag.State state = (StateFlag.State) worldGuardPlugin.getRegionManager(loc.getWorld())
                                                                            .getApplicableRegions(loc)
                                                                            .getFlag((Flag) flag, lp);
            return state != null && state == StateFlag.State.ALLOW;
        } else {
            return true;
        }
    }
}
