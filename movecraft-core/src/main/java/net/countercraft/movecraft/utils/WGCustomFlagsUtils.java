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

    public static StateFlag getNewStateFlag(String name, boolean def) {
        Constructor<?> cc = getStateFlagConstructor();
        if (cc == null) {
            return null;
        }
        try {
            Object o = cc.newInstance(name, def);
            return (StateFlag) o;
        } catch (InstantiationException ex) {
            return null;
        } catch (IllegalAccessException ex) {
            return null;
        } catch (IllegalArgumentException ex) {
            return null;
        } catch (InvocationTargetException ex) {
            return null;
        }
    }

    public static Constructor<?> getStateFlagConstructor() {
        try {
            Class<?> c = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            return c.getConstructor(String.class, boolean.class);
        } catch (ClassNotFoundException ex) {
            return null;
        } catch (NoSuchMethodException ex) {
            return null;
        } catch (SecurityException ex) {
            return null;
        }
    }

    public static boolean registerStageFlag(WGCustomFlagsPlugin plugin, Object o) {
        if (plugin != null) {
            Constructor<?> cc = getStateFlagConstructor();
            if (cc == null) {
                return false;
            }
            try {
                plugin.addCustomFlag((Flag) o);
            } catch (Exception e) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static void registerFlags(WGCustomFlagsPlugin plugin, StateFlag... flags) {
        for (StateFlag flag : flags) {
            if (flag != null) registerStageFlag(plugin, flag);
        }
    }

    public static boolean validateFlag(WorldGuardPlugin worldGuardPlugin, Location loc, Object flag) {
        if (flag != null) {
            StateFlag.State state = (StateFlag.State) worldGuardPlugin.getRegionManager(loc.getWorld())
                                                                      .getApplicableRegions(loc).getFlag((Flag) flag);
            return state != null && state == StateFlag.State.ALLOW;
        } else {
            return true;
        }
    }

    public static boolean validateFlag(WorldGuardPlugin worldGuardPlugin, Location loc, Object flag, LocalPlayer lp) {
        if (flag != null) {
            StateFlag.State state = (StateFlag.State) worldGuardPlugin.getRegionManager(loc.getWorld())
                                                                      .getApplicableRegions(loc)
                                                                      .getFlag((Flag) flag, lp);
            return state != null && state == StateFlag.State.ALLOW;
        } else {
            return true;
        }
    }
}
