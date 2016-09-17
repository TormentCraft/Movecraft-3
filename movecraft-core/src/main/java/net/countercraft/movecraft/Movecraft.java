/*
 * This file is part of Movecraft.
 *
 *     Movecraft is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movecraft is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movecraft.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.countercraft.movecraft;

import at.pavlov.cannons.Cannons;
import com.mewin.WGCustomFlags.WGCustomFlagsPlugin;
import com.palmergames.bukkit.towny.Towny;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
import net.countercraft.movecraft.api.BlockVec;
import net.countercraft.movecraft.api.MovecraftPlugin;
import net.countercraft.movecraft.async.AsyncManager;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.listener.BlockListener;
import net.countercraft.movecraft.listener.CommandListener;
import net.countercraft.movecraft.listener.CraftHelpListener;
import net.countercraft.movecraft.listener.InteractListener;
import net.countercraft.movecraft.listener.PlayerListener;
import net.countercraft.movecraft.listener.WorldEditInteractListener;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.metrics.MovecraftMetrics;
import net.countercraft.movecraft.utils.MapUpdateManager;
import net.countercraft.movecraft.utils.TownyUtils;
import net.countercraft.movecraft.utils.WGCustomFlagsUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Movecraft extends JavaPlugin implements MovecraftPlugin {
    private WorldGuardPlugin worldGuardPlugin;
    private WorldEditPlugin worldEditPlugin;
    private WGCustomFlagsPlugin wgCustomFlagsPlugin = null;
    private Economy economy;
    private Cannons cannonsPlugin = null;
    private Towny townyPlugin = null;
    public StateFlag FLAG_PILOT = null; //new StateFlag("movecraft-pilot", true);
    public StateFlag FLAG_MOVE = null; //new StateFlag("movecraft-move", true);
    public StateFlag FLAG_ROTATE = null; //new StateFlag("movecraft-rotate", true);
    public StateFlag FLAG_SINK = null; //new StateFlag("movecraft-sink", true);

    private final Settings settings = new Settings();
    private final I18nSupport i18nSupport = I18nSupport.read(this, new Locale("en"));
    private AsyncManager asyncManager;
    private CraftManager craftManager;
    private MapUpdateManager mapUpdateManager;
    private Logger logger;

    private boolean shuttingDown;
    public Map<BlockVec, Long> blockFadeTimeMap = new HashMap<>();
    public Map<BlockVec, Integer> blockFadeTypeMap = new HashMap<>();
    public Map<BlockVec, Boolean> blockFadeWaterMap = new HashMap<>();
    public Map<BlockVec, World> blockFadeWorldMap = new HashMap<>();
    public boolean siegeInProgress = false;
    public String currentSiegeName = null;
    public String currentSiegePlayer = null;
    public long currentSiegeStartTime = 0;

    @Override public void onDisable() {
        // Process the storage crates to disk
        shuttingDown = true;
    }

    @Override public void onEnable() {
        // Read in config
        this.saveDefaultConfig();

        settings.LOCALE = getConfig().getString("Locale");
        settings.Debug = getConfig().getBoolean("Debug", false);
        settings.DisableSpillProtection = getConfig().getBoolean("DisableSpillProtection", false);
        // if the PilotTool is specified in the config.yml file, use it
        if (getConfig().getInt("PilotTool") == 0) {
            logger.log(Level.INFO, "No PilotTool setting, using default of 280");
        } else {
            logger.log(Level.INFO, "Recognized PilotTool setting of: " + getConfig().getInt("PilotTool"));
            settings.PilotTool = getConfig().getInt("PilotTool");
        }
        // if the CompatibilityMode is specified in the config.yml file, use it.
        // Otherwise set to false.
        settings.CompatibilityMode = getConfig().getBoolean("CompatibilityMode", false);
        if (!settings.CompatibilityMode) {
            try {
                Class.forName("net.minecraft.server.v1_10_R1.Chunk");
            } catch (ClassNotFoundException e) {
                settings.CompatibilityMode = true;
                logger.log(Level.INFO,
                           "WARNING: CompatibilityMode was set to false, but required build-specific classes were not" +
                           " found. FORCING COMPATIBILITY MODE");
            }
        }
        logger.log(Level.INFO, "CompatiblityMode is set to {0}", settings.CompatibilityMode);
        settings.SinkRateTicks = getConfig().getInt("SinkRateTicks", 20);
        settings.SinkCheckTicks = getConfig().getDouble("SinkCheckTicks", 100.0);
        settings.TracerRateTicks = getConfig().getDouble("TracerRateTicks", 5.0);
        settings.ManOverBoardTimeout = getConfig().getInt("ManOverBoardTimeout", 30);
        settings.FireballLifespan = getConfig().getInt("FireballLifespan", 6);
        settings.FireballPenetration = getConfig().getBoolean("FireballPenetration", true);
        settings.BlockQueueChunkSize = getConfig().getInt("BlockQueueChunkSize", 1000);
        settings.ProtectPilotedCrafts = getConfig().getBoolean("ProtectPilotedCrafts", false);
        settings.AllowCrewSigns = getConfig().getBoolean("AllowCrewSigns", true);
        settings.SetHomeToCrewSign = getConfig().getBoolean("SetHomeToCrewSign", true);
        settings.RequireCreatePerm = getConfig().getBoolean("RequireCreatePerm", false);
        settings.TNTContactExplosives = getConfig().getBoolean("TNTContactExplosives", true);
        settings.FadeWrecksAfter = getConfig().getInt("FadeWrecksAfter", 0);

        //load the sieges.yml file
        File siegesFile = new File(getDataFolder().getAbsolutePath() + "/sieges.yml");
        InputStream input = null;
        try {
            input = new FileInputStream(siegesFile);
        } catch (FileNotFoundException e) {
            settings.SiegeName = null;
            input = null;
        }
        if (input != null) {
            Yaml yaml = new Yaml();
            Map data = (Map) yaml.load(input);
            Map<String, Map> siegesMap = (Map<String, Map>) data.get("sieges");
            settings.SiegeName = siegesMap.keySet();

            settings.SiegeRegion = new HashMap<>();
            settings.SiegeCraftsToWin = new HashMap<>();
            settings.SiegeCost = new HashMap<>();
            settings.SiegeDoubleCost = new HashMap<>();
            settings.SiegeIncome = new HashMap<>();
            settings.SiegeScheduleStart = new HashMap<>();
            settings.SiegeScheduleEnd = new HashMap<>();
            settings.SiegeControlRegion = new HashMap<>();
            settings.SiegeDelay = new HashMap<>();
            settings.SiegeDuration = new HashMap<>();
            for (Map.Entry<String, Map> entry : siegesMap.entrySet()) {
                final Map siegeInfo = entry.getValue();
                final String siegeName = entry.getKey();
                settings.SiegeRegion.put(siegeName, (String) siegeInfo.get("SiegeRegion"));
                settings.SiegeCraftsToWin.put(siegeName, (ArrayList<String>) siegeInfo.get("CraftsToWin"));
                settings.SiegeCost.put(siegeName, (Integer) siegeInfo.get("CostToSiege"));
                settings.SiegeDoubleCost.put(siegeName, (Boolean) siegeInfo.get("DoubleCostPerOwnedSiegeRegion"));
                settings.SiegeIncome.put(siegeName, (Integer) siegeInfo.get("DailyIncome"));
                settings.SiegeScheduleStart.put(siegeName, (Integer) siegeInfo.get("ScheduleStart"));
                settings.SiegeScheduleEnd.put(siegeName, (Integer) siegeInfo.get("ScheduleEnd"));
                settings.SiegeControlRegion.put(siegeName, (String) siegeInfo.get("RegionToControl"));
                settings.SiegeDelay.put(siegeName, (Integer) siegeInfo.get("DelayBeforeStart"));
                settings.SiegeDuration.put(siegeName, (Integer) siegeInfo.get("SiegeDuration"));
            }
            logger.log(Level.INFO, "Siege configuration loaded.");
        }
        //load up WorldGuard if it's present

        //ROK - disable completely since we can't control it.
        Plugin wGPlugin = null;
        if (getConfig().getBoolean("WGIntegationEnabled", false)) {
            getServer().getPluginManager().getPlugin("WorldGuard");
        }

        if (!(wGPlugin instanceof WorldGuardPlugin)) {
            logger.log(Level.INFO,
                       "Movecraft did not find a compatible version of WorldGuard. Disabling WorldGuard integration");
            settings.SiegeName = null;
        } else {
            logger.log(Level.INFO, "Found a compatible version of WorldGuard. Enabling WorldGuard integration");
            settings.WorldGuardBlockMoveOnBuildPerm = getConfig().getBoolean("WorldGuardBlockMoveOnBuildPerm", false);
            settings.WorldGuardBlockSinkOnPVPPerm = getConfig().getBoolean("WorldGuardBlockSinkOnPVPPerm", false);
            logger.log(Level.INFO, "Settings: WorldGuardBlockMoveOnBuildPerm - {0}, WorldGuardBlockSinkOnPVPPerm - {1}",
                       new Object[]{settings.WorldGuardBlockMoveOnBuildPerm, settings.WorldGuardBlockSinkOnPVPPerm});
        }
        worldGuardPlugin = (WorldGuardPlugin) wGPlugin;

        //load up WorldEdit if it's present
        Plugin wEPlugin = getServer().getPluginManager().getPlugin("WorldEdit");
        if (!(wEPlugin instanceof WorldEditPlugin)) {
            logger.log(Level.INFO,
                       "Movecraft did not find a compatible version of WorldEdit. Disabling WorldEdit integration");
        } else {
            logger.log(Level.INFO, "Found a compatible version of WorldEdit. Enabling WorldEdit integration");
            settings.RepairTicksPerBlock = getConfig().getInt("RepairTicksPerBlock", 0);
        }
        worldEditPlugin = (WorldEditPlugin) wEPlugin;

        // next is Cannons
        Plugin plug = getServer().getPluginManager().getPlugin("Cannons");
        if (plug instanceof Cannons) {
            cannonsPlugin = (Cannons) plug;
            logger.log(Level.INFO, "Found a compatible version of Cannons. Enabling Cannons integration");
        }

        if (worldGuardPlugin != null || worldGuardPlugin instanceof WorldGuardPlugin) {
            if (worldGuardPlugin.isEnabled()) {
                Plugin tempWGCustomFlagsPlugin = getServer().getPluginManager().getPlugin("WGCustomFlags");
                if (tempWGCustomFlagsPlugin instanceof WGCustomFlagsPlugin) {
                    logger.log(Level.INFO,
                               "Found a compatible version of WGCustomFlags. Enabling WGCustomFlags integration.");
                    wgCustomFlagsPlugin = (WGCustomFlagsPlugin) tempWGCustomFlagsPlugin;
                    FLAG_PILOT = WGCustomFlagsUtils.getNewStateFlag("movecraft-pilot", true);
                    FLAG_MOVE = WGCustomFlagsUtils.getNewStateFlag("movecraft-move", true);
                    FLAG_ROTATE = WGCustomFlagsUtils.getNewStateFlag("movecraft-rotate", true);
                    FLAG_SINK = WGCustomFlagsUtils.getNewStateFlag("movecraft-sink", true);
                    WGCustomFlagsUtils
                            .registerFlags(wgCustomFlagsPlugin, FLAG_PILOT, FLAG_MOVE, FLAG_ROTATE, FLAG_SINK);
                    settings.WGCustomFlagsUsePilotFlag = getConfig().getBoolean("WGCustomFlagsUsePilotFlag", false);
                    settings.WGCustomFlagsUseMoveFlag = getConfig().getBoolean("WGCustomFlagsUseMoveFlag", false);
                    settings.WGCustomFlagsUseRotateFlag = getConfig().getBoolean("WGCustomFlagsUseRotateFlag", false);
                    settings.WGCustomFlagsUseSinkFlag = getConfig().getBoolean("WGCustomFlagsUseSinkFlag", false);
                    logger.log(Level.INFO, "Settings: WGCustomFlagsUsePilotFlag - {0}",
                               settings.WGCustomFlagsUsePilotFlag);
                    logger.log(Level.INFO, "Settings: WGCustomFlagsUseMoveFlag - {0}",
                               settings.WGCustomFlagsUseMoveFlag);
                    logger.log(Level.INFO, "Settings: WGCustomFlagsUseRotateFlag - {0}",
                               settings.WGCustomFlagsUseRotateFlag);
                    logger.log(Level.INFO, "Settings: WGCustomFlagsUseSinkFlag - {0}",
                               settings.WGCustomFlagsUseSinkFlag);
                } else {
                    logger.log(Level.INFO,
                               "Movecraft did not find a compatible version of WGCustomFlags. Disabling WGCustomFlags" +
                               " integration.");
                }
            }
        }

        Plugin tempTownyPlugin = getServer().getPluginManager().getPlugin("Towny");
        if (tempTownyPlugin instanceof Towny) {
            logger.log(Level.INFO, "Found a compatible version of Towny. Enabling Towny integration.");
            townyPlugin = (Towny) tempTownyPlugin;
            settings.TownProtectionHeightLimits = TownyUtils.loadTownyConfig(getConfig());
            settings.TownyBlockMoveOnSwitchPerm = getConfig().getBoolean("TownyBlockMoveOnSwitchPerm", false);
            settings.TownyBlockSinkOnNoPVP = getConfig().getBoolean("TownyBlockSinkOnNoPVP", false);
            logger.log(Level.INFO, "Settings: TownyBlockMoveOnSwitchPerm - {0}", settings.TownyBlockMoveOnSwitchPerm);
            logger.log(Level.INFO, "Settings: TownyBlockSinkOnNoPVP - {0}", settings.TownyBlockSinkOnNoPVP);
        } else {
            logger.log(Level.INFO,
                       "Movecraft did not find a compatible version of Towny. Disabling Towny integration.");
        }

        // and now Vault
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                settings.RepairMoneyPerBlock = getConfig().getDouble("RepairMoneyPerBlock", 0.0);
                logger.log(Level.INFO, "Found a compatible Vault plugin.");
            } else {
                logger.log(Level.INFO, "Could not find compatible Vault plugin. Disabling Vault integration.");
                economy = null;
                settings.SiegeName = null;
            }
        } else {
            logger.log(Level.INFO, "Could not find compatible Vault plugin. Disabling Vault integration.");
            economy = null;
            settings.SiegeName = null;
        }
        String[] localisations = {"en", "cz", "nl"};
        for (String s : localisations) {
            if (!new File(getDataFolder() + "/localisation/movecraftlang_" + s + ".properties").exists()) {
                this.saveResource("localisation/movecraftlang_" + s + ".properties", false);
            }
        }

        if (shuttingDown && settings.IGNORE_RESET) {
            logger.log(Level.SEVERE, i18nSupport.get("Startup - Error - Reload error"));
            logger.log(Level.INFO, i18nSupport.get("Startup - Error - Disable warning for reload"));
            getPluginLoader().disablePlugin(this);
        } else {
            craftManager = new CraftManager(settings, i18nSupport, this);
            craftManager.initCraftTypes();
            mapUpdateManager = new MapUpdateManager(settings, i18nSupport, this);
            asyncManager = new AsyncManager(settings, i18nSupport, craftManager, this, mapUpdateManager);

            // Startup procedure
            asyncManager.runTaskTimer(this, 0, 1);
            mapUpdateManager.runTaskTimer(this, 0, 1);

            getServer().getPluginManager()
                       .registerEvents(new InteractListener(this, settings, i18nSupport, craftManager, asyncManager),
                                       this);
            if (worldEditPlugin != null) {
                getServer().getPluginManager().registerEvents(
                        new WorldEditInteractListener(this, settings, i18nSupport, mapUpdateManager, craftManager),
                        this);
            }
//			getServer().getPluginManager().registerEvents(
//					new CommandListener(), this);
            CommandListener commandListener = new CommandListener(this, settings, i18nSupport, craftManager,
                                                                  asyncManager);
            this.getCommand("release").setExecutor(commandListener);
            //this.getCommand("pilot").setExecutor(commandListener);
            this.getCommand("rotateleft").setExecutor(commandListener);
            this.getCommand("rotateright").setExecutor(commandListener);
            this.getCommand("rotate").setExecutor(commandListener);
            this.getCommand("cruise").setExecutor(commandListener);
            this.getCommand("cruiseoff").setExecutor(commandListener);
            this.getCommand("craftreport").setExecutor(commandListener);
            this.getCommand("manoverboard").setExecutor(commandListener);
            this.getCommand("contacts").setExecutor(commandListener);
            //this.getCommand("siege").setExecutor(new CommandListener());
            this.getCommand("craft").setExecutor(new CraftHelpListener(craftManager));

            getServer().getPluginManager()
                       .registerEvents(new BlockListener(this, settings, i18nSupport, craftManager), this);
            getServer().getPluginManager()
                       .registerEvents(new PlayerListener(this, settings, i18nSupport, craftManager), this);

            new MovecraftMetrics(craftManager.getCraftTypes().length);

            logger.log(Level.INFO,
                       String.format(i18nSupport.get("Startup - Enabled message"), getDescription().getVersion()));
        }
    }

    @Override public void onLoad() {
        super.onLoad();
        logger = getLogger();
    }

    public WorldGuardPlugin getWorldGuardPlugin() {
        return worldGuardPlugin;
    }

    public WorldEditPlugin getWorldEditPlugin() {
        return worldEditPlugin;
    }

    public Economy getEconomy() {
        return economy;
    }

    public Cannons getCannonsPlugin() {
        return cannonsPlugin;
    }

    public WGCustomFlagsPlugin getWGCustomFlagsPlugin() {
        return wgCustomFlagsPlugin;
    }

    public Towny getTownyPlugin() {
        return townyPlugin;
    }

    @Override public net.countercraft.movecraft.api.CraftManager getCraftManager() {
        return craftManager;
    }
}

