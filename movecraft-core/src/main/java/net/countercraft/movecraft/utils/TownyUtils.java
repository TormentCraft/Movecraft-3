package net.countercraft.movecraft.utils;

import com.palmergames.bukkit.towny.Towny;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.exceptions.TownyException;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.PlayerCache;
import com.palmergames.bukkit.towny.object.PlayerCache.TownBlockStatus;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.object.TownyUniverse;
import com.palmergames.bukkit.towny.object.TownyWorld;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import com.palmergames.bukkit.towny.war.flagwar.TownyWarConfig;
import net.countercraft.movecraft.config.Settings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author mwkaicz <mwkaicz@gmail.com>
 */
public final class TownyUtils {
    public static final String TOWN_MIN = "worldMin";
    public static final String TOWN_MAX = "worldMax";
    public static final String TOWN_ABOVE = "aboveTownSpawn";
    public static final String TOWN_UNDER = "underTownSpawn";
    public static final String TOWN_HEIGHT_LIMITS = "TownyWorldHeightLimits";

    public static TownyWorld getTownyWorld(World w) {
        TownyWorld tw;
        try {
            tw = TownyUniverse.getDataSource().getWorld(w.getName());
            if (!tw.isUsingTowny()) return null;
        } catch (NotRegisteredException e) {
            return null;
        }
        return tw;
    }

    public static TownBlock getTownBlock(Location loc) {
        Coord coo = Coord.parseCoord(loc);
        TownyWorld tw = getTownyWorld(loc.getWorld());
        TownBlock tb = null;
        try {
            if (tw != null) {
                tb = tw.getTownBlock(coo);
            }
        } catch (NotRegisteredException ex) {
            //Logger.getLogger(TownyUtils.class.getName()).log(Level.SEVERE, null, ex);
            //free land
        }
        return tb;
    }

    public static Town getTown(TownBlock townBlock) {
        try {
            return townBlock.getTown();
        } catch (TownyException e) {
            //Logger.getLogger(TownyUtils.class.getName()).log(Level.SEVERE, null, ex);
            //none
        }
        return null;
    }

    public static Location getTownSpawn(TownBlock townBlock) {
        if (townBlock == null) return null;
        Town t = getTown(townBlock);
        if (t != null) {
            try {
                return t.getSpawn();
            } catch (TownyException ex) {
                return null;
            }
        }
        return null;
    }

    public static boolean validateResident(Player player) {
        try {
            Resident resident = TownyUniverse.getDataSource().getResident(player.getName());
            return true;
        } catch (TownyException e) {
            //System.out.print("Failed to fetch resident: " + player.getName());
            //return TownBlockStatus.NOT_REGISTERED;
        }
        return false;
    }

    public static boolean validateCraftMoveEvent(Towny towny, Player player, Location loc, TownyWorld world) {
        // Get switch permissions (updates if none exist)
        if (player != null && !validateResident(player)) {
            return true; //probably NPC or CBWrapper Dummy player
        }
        int id = Material.STONE_BUTTON.getId();
        byte data = 0;

        boolean bSwitch = PlayerCacheUtil.getCachePermission(player, loc, id, data, TownyPermission.ActionType.SWITCH);

        // Allow move if we are permitted to switch
        if (bSwitch) return true;

        PlayerCache cache = towny.getCache(player);
        TownBlockStatus status = cache.getStatus();

        if (cache.hasBlockErrMsg()) return false;

        if (status == TownBlockStatus.WARZONE) {
            return TownyWarConfig.isAllowingSwitchesInWarZone();
        } else {
            return false;
        }
    }

    public static boolean validatePVP(TownBlock tb) {
        Town t = getTown(tb);
        if (t != null) {
            return t.getPermissions().pvp || tb.getPermissions().pvp;
        } else {
            return tb.getPermissions().pvp;
        }
    }

    public static boolean validateExplosion(TownBlock tb) {
        Town t = getTown(tb);
        if (t != null) {
            return t.getPermissions().explosion || tb.getPermissions().explosion;
        } else {
            return tb.getPermissions().explosion;
        }
    }

    public static TownyWorldHeightLimits getWorldLimits(Settings settings, World w) {
        String worldName = w.getName();

        if (settings.TownProtectionHeightLimits.containsKey(worldName)) {
            return settings.TownProtectionHeightLimits.get(worldName);
        }

        TownyWorld tw = getTownyWorld(w);

        if (tw != null && tw.isUsingTowny()) {
            return new TownyWorldHeightLimits();
        } else return null;
    }

    public static Map<String, TownyWorldHeightLimits> loadTownyConfig(ConfigurationSection fc) {
        Map<String, TownyWorldHeightLimits> townyWorldHeightLimits = new HashMap<>();
        ConfigurationSection csObj = fc.getConfigurationSection(TOWN_HEIGHT_LIMITS);

        if (csObj != null) {
            Set<String> worlds = csObj.getKeys(false);
            for (String key : worlds) {
                TownyWorldHeightLimits twhl = new TownyWorldHeightLimits();
                twhl.world_min = csObj.getInt(key + '.' + TOWN_MIN, TownyWorldHeightLimits.DEFAULT_WORLD_MIN);
                twhl.world_max = csObj.getInt(key + '.' + TOWN_MAX, TownyWorldHeightLimits.DEFAULT_WORLD_MAX);
                twhl.above_town = csObj.getInt(key + '.' + TOWN_ABOVE, TownyWorldHeightLimits.DEFAULT_TOWN_ABOVE);
                twhl.under_town = csObj.getInt(key + '.' + TOWN_UNDER, TownyWorldHeightLimits.DEFAULT_TOWN_UNDER);
                townyWorldHeightLimits.put(key, twhl);
            }
        }

        return townyWorldHeightLimits;
    }
}
