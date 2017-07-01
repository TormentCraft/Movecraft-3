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
import com.alexknvl.shipcraft.math.BlockVec;
import com.mewin.WGCustomFlags.WGCustomFlagsPlugin;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StateFlag;
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
import net.countercraft.movecraft.utils.MapUpdateManager;
import net.countercraft.movecraft.utils.WGCustomFlagsUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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
    public Map<BlockVec, Material> blockFadeTypeMap = new HashMap<>();
    public Map<BlockVec, Boolean> blockFadeWaterMap = new HashMap<>();
    public Map<BlockVec, World> blockFadeWorldMap = new HashMap<>();

    @Override public void onDisable() {
        // Process the storage crates to disk
        this.shuttingDown = true;
    }

    @Override public void onEnable() {
        // Read in config
        this.saveDefaultConfig();

        final FileConfiguration config = this.getConfig();

        this.settings.LOCALE = config.getString("Locale");
        this.settings.Debug = config.getBoolean("Debug", false);
        this.settings.DisableSpillProtection = config.getBoolean("DisableSpillProtection", false);
        // if the PilotTool is specified in the config.yml file, use it
        if (config.getInt("PilotTool") == 0) {
            this.logger.log(Level.INFO, "No PilotTool setting, using default of 280");
        } else {
            this.logger.log(Level.INFO, "Recognized PilotTool setting of: " + config.getInt("PilotTool"));
            this.settings.PilotTool = config.getInt("PilotTool");
        }
        // if the CompatibilityMode is specified in the config.yml file, use it.
        // Otherwise set to false.
        this.settings.CompatibilityMode = config.getBoolean("CompatibilityMode", false);
        if (!this.settings.CompatibilityMode) {
            try {
                Class.forName("net.minecraft.server.v1_12_R1.Chunk");
            } catch (final ClassNotFoundException e) {
                this.settings.CompatibilityMode = true;
                this.logger.log(Level.INFO,
                           "WARNING: CompatibilityMode was set to false, but required build-specific classes were not" +
                           " found. FORCING COMPATIBILITY MODE");
            }
        }
        this.logger.log(Level.INFO, "CompatiblityMode is set to {0}", this.settings.CompatibilityMode);
        this.settings.SinkRateTicks = config.getInt("SinkRateTicks", 20);
        this.settings.SinkCheckTicks = config.getDouble("SinkCheckTicks", 100.0);
        this.settings.TracerRateTicks = config.getDouble("TracerRateTicks", 5.0);
        this.settings.ManOverBoardTimeout = config.getInt("ManOverBoardTimeout", 30);
        this.settings.FireballLifespan = config.getInt("FireballLifespan", 6);
        this.settings.FireballPenetration = config.getBoolean("FireballPenetration", true);
        this.settings.BlockQueueChunkSize = config.getInt("BlockQueueChunkSize", 1000);
        this.settings.ProtectPilotedCrafts = config.getBoolean("ProtectPilotedCrafts", false);
        this.settings.AllowCrewSigns = config.getBoolean("AllowCrewSigns", true);
        this.settings.SetHomeToCrewSign = config.getBoolean("SetHomeToCrewSign", true);
        this.settings.RequireCreatePerm = config.getBoolean("RequireCreatePerm", false);
        this.settings.TNTContactExplosives = config.getBoolean("TNTContactExplosives", true);
        this.settings.FadeWrecksAfter = config.getInt("FadeWrecksAfter", 0);

        //load up WorldGuard if it's present

        //ROK - disable completely since we can't control it.
        final Plugin wGPlugin = null;
        if (config.getBoolean("WGIntegationEnabled", false)) {
            this.getServer().getPluginManager().getPlugin("WorldGuard");
        }

        if (!(wGPlugin instanceof WorldGuardPlugin)) {
            this.logger.log(Level.INFO,
                            "Movecraft did not find a compatible version of WorldGuard. Disabling WorldGuard integration");
        } else {
            this.logger.log(Level.INFO, "Found a compatible version of WorldGuard. Enabling WorldGuard integration");
            this.settings.WorldGuardBlockMoveOnBuildPerm = config.getBoolean("WorldGuardBlockMoveOnBuildPerm", false);
            this.settings.WorldGuardBlockSinkOnPVPPerm = config.getBoolean("WorldGuardBlockSinkOnPVPPerm", false);
            this.logger.log(Level.INFO, "Settings: WorldGuardBlockMoveOnBuildPerm - {0}, WorldGuardBlockSinkOnPVPPerm - {1}",
                            new Object[]{this.settings.WorldGuardBlockMoveOnBuildPerm, this.settings.WorldGuardBlockSinkOnPVPPerm});
        }
        this.worldGuardPlugin = (WorldGuardPlugin) wGPlugin;

        //load up WorldEdit if it's present
        final Plugin wEPlugin = this.getServer().getPluginManager().getPlugin("WorldEdit");
        if (!(wEPlugin instanceof WorldEditPlugin)) {
            this.logger.log(Level.INFO,
                            "Movecraft did not find a compatible version of WorldEdit. Disabling WorldEdit integration");
        } else {
            this.logger.log(Level.INFO, "Found a compatible version of WorldEdit. Enabling WorldEdit integration");
            this.settings.RepairTicksPerBlock = config.getInt("RepairTicksPerBlock", 0);
        }
        this.worldEditPlugin = (WorldEditPlugin) wEPlugin;

        // next is Cannons
        final Plugin plug = this.getServer().getPluginManager().getPlugin("Cannons");
        if (plug instanceof Cannons) {
            this.cannonsPlugin = (Cannons) plug;
            this.logger.log(Level.INFO, "Found a compatible version of Cannons. Enabling Cannons integration");
        }

        if (this.worldGuardPlugin != null || this.worldGuardPlugin instanceof WorldGuardPlugin) {
            if (this.worldGuardPlugin.isEnabled()) {
                final Plugin tempWGCustomFlagsPlugin = this.getServer().getPluginManager().getPlugin("WGCustomFlags");
                if (tempWGCustomFlagsPlugin instanceof WGCustomFlagsPlugin) {
                    this.logger.log(Level.INFO,
                                    "Found a compatible version of WGCustomFlags. Enabling WGCustomFlags integration.");
                    this.wgCustomFlagsPlugin = (WGCustomFlagsPlugin) tempWGCustomFlagsPlugin;
                    this.FLAG_PILOT = WGCustomFlagsUtils.getNewStateFlag("movecraft-pilot", true);
                    this.FLAG_MOVE = WGCustomFlagsUtils.getNewStateFlag("movecraft-move", true);
                    this.FLAG_ROTATE = WGCustomFlagsUtils.getNewStateFlag("movecraft-rotate", true);
                    this.FLAG_SINK = WGCustomFlagsUtils.getNewStateFlag("movecraft-sink", true);
                    WGCustomFlagsUtils
                            .registerFlags(this.wgCustomFlagsPlugin, this.FLAG_PILOT, this.FLAG_MOVE, this.FLAG_ROTATE, this.FLAG_SINK);
                    this.settings.WGCustomFlagsUsePilotFlag = config.getBoolean("WGCustomFlagsUsePilotFlag", false);
                    this.settings.WGCustomFlagsUseMoveFlag = config.getBoolean("WGCustomFlagsUseMoveFlag", false);
                    this.settings.WGCustomFlagsUseRotateFlag = config.getBoolean("WGCustomFlagsUseRotateFlag", false);
                    this.settings.WGCustomFlagsUseSinkFlag = config.getBoolean("WGCustomFlagsUseSinkFlag", false);
                    this.logger.log(Level.INFO, "Settings: WGCustomFlagsUsePilotFlag - {0}", this.settings.WGCustomFlagsUsePilotFlag);
                    this.logger.log(Level.INFO, "Settings: WGCustomFlagsUseMoveFlag - {0}", this.settings.WGCustomFlagsUseMoveFlag);
                    this.logger.log(Level.INFO, "Settings: WGCustomFlagsUseRotateFlag - {0}", this.settings.WGCustomFlagsUseRotateFlag);
                    this.logger.log(Level.INFO, "Settings: WGCustomFlagsUseSinkFlag - {0}", this.settings.WGCustomFlagsUseSinkFlag);
                } else {
                    this.logger.log(Level.INFO,
                               "Movecraft did not find a compatible version of WGCustomFlags. Disabling WGCustomFlags" +
                               " integration.");
                }
            }
        }

        // and now Vault
        if (this.getServer().getPluginManager().getPlugin("Vault") != null) {
            final RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                this.economy = rsp.getProvider();
                this.settings.RepairMoneyPerBlock = config.getDouble("RepairMoneyPerBlock", 0.0);
                this.logger.log(Level.INFO, "Found a compatible Vault plugin.");
            } else {
                this.logger.log(Level.INFO, "Could not find compatible Vault plugin. Disabling Vault integration.");
                this.economy = null;
            }
        } else {
            this.logger.log(Level.INFO, "Could not find compatible Vault plugin. Disabling Vault integration.");
            this.economy = null;
        }
        final String[] localisations = {"en", "cz", "nl"};
        for (final String s : localisations) {
            if (!new File(this.getDataFolder() + "/localisation/movecraftlang_" + s + ".properties").exists()) {
                this.saveResource("localisation/movecraftlang_" + s + ".properties", false);
            }
        }

        if (this.shuttingDown && this.settings.IGNORE_RESET) {
            this.logger.log(Level.SEVERE, this.i18nSupport.get("Startup - Error - Reload error"));
            this.logger.log(Level.INFO, this.i18nSupport.get("Startup - Error - Disable warning for reload"));
            this.getPluginLoader().disablePlugin(this);
        } else {
            this.craftManager = new CraftManager(this.settings, this.i18nSupport, this);
            this.craftManager.initCraftTypes();
            this.mapUpdateManager = new MapUpdateManager(this.settings, this.i18nSupport, this);
            this.asyncManager = new AsyncManager(this.settings, this.i18nSupport, this.craftManager, this, this.mapUpdateManager);

            // Startup procedure
            this.asyncManager.runTaskTimer(this, 0, 1);
            this.mapUpdateManager.runTaskTimer(this, 0, 1);

            this.getServer().getPluginManager()
                .registerEvents(new InteractListener(this, this.settings, this.i18nSupport, this.craftManager,
                                                            this.asyncManager),
                                       this);
            if (this.worldEditPlugin != null) {
                this.getServer().getPluginManager().registerEvents(
                        new WorldEditInteractListener(this, this.settings, this.i18nSupport, this.mapUpdateManager, this.craftManager),
                        this);
            }

            final CommandListener commandListener = new CommandListener(this.settings, this.i18nSupport, this.craftManager,
                                                                        this.asyncManager);

            this.getCommand("pilot").setExecutor(commandListener);
            this.getCommand("release").setExecutor(commandListener);

            this.getCommand("rotateleft").setExecutor(commandListener);
            this.getCommand("rotateright").setExecutor(commandListener);
            this.getCommand("rotate").setExecutor(commandListener);
            this.getCommand("cruise").setExecutor(commandListener);
            this.getCommand("cruiseoff").setExecutor(commandListener);
            this.getCommand("craftreport").setExecutor(commandListener);
            this.getCommand("manoverboard").setExecutor(commandListener);
            this.getCommand("contacts").setExecutor(commandListener);
            this.getCommand("craft").setExecutor(new CraftHelpListener(this.craftManager));

            this.getServer().getPluginManager()
                .registerEvents(new BlockListener(this, this.settings, this.i18nSupport, this.craftManager), this);
            this.getServer().getPluginManager()
                .registerEvents(new PlayerListener(this, this.settings, this.i18nSupport, this.craftManager), this);

            this.logger.log(Level.INFO,
                            String.format(this.i18nSupport.get("Startup - Enabled message"), this.getDescription()
                                                                                                 .getVersion()));
        }
    }

    @Override public void onLoad() {
        super.onLoad();
        this.logger = this.getLogger();
    }

    public WorldGuardPlugin getWorldGuardPlugin() {
        return this.worldGuardPlugin;
    }

    public WorldEditPlugin getWorldEditPlugin() {
        return this.worldEditPlugin;
    }

    public Economy getEconomy() {
        return this.economy;
    }

    public Cannons getCannonsPlugin() {
        return this.cannonsPlugin;
    }

    public WGCustomFlagsPlugin getWGCustomFlagsPlugin() {
        return this.wgCustomFlagsPlugin;
    }

    @Override public net.countercraft.movecraft.api.CraftManager getCraftManager() {
        return this.craftManager;
    }
}

