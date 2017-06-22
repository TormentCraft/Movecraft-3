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

package net.countercraft.movecraft.listener;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.api.BlockVec;
import net.countercraft.movecraft.api.Direction;
import net.countercraft.movecraft.api.Rotation;
import net.countercraft.movecraft.async.AsyncManager;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.CraftType;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.logging.Level;

//public class CommandListener implements Listener {
public class CommandListener implements CommandExecutor {
    private final Movecraft plugin;
    private final Settings settings;
    private final I18nSupport i18n;
    private final CraftManager craftManager;
    private final AsyncManager asyncManager;

    public CommandListener(Movecraft plugin, Settings settings, I18nSupport i18n, CraftManager craftManager,
                           AsyncManager asyncManager)
    {
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
        this.asyncManager = asyncManager;
    }

    private CraftType getCraftTypeFromString(String s) {
        for (CraftType t : craftManager.getCraftTypes()) {
            if (s.equalsIgnoreCase(t.getCraftName())) {
                return t;
            }
        }

        return null;
    }

    private Location getCraftTeleportPoint(Craft craft, World w) {
        int maxDX = 0;
        int maxDZ = 0;
        int maxY = 0;
        int minY = 32767;
        for (int[][] i1 : craft.getHitBox()) {
            maxDX++;
            if (i1 != null) {
                int indexZ = 0;
                for (int[] i2 : i1) {
                    indexZ++;
                    if (i2 != null) {
                        if (i2[0] < minY) {
                            minY = i2[0];
                        }
                    }
                    if (i2 != null) {
                        if (i2[1] > maxY) {
                            maxY = i2[1];
                        }
                    }
                }
                if (indexZ > maxDZ) {
                    maxDZ = indexZ;
                }
            }
        }
        int telX = craft.getMinX() + (maxDX / 2);
        int telZ = craft.getMinZ() + (maxDZ / 2);
        int telY = maxY;
        return new Location(w, telX, telY, telZ);
    }

    private BlockVec getCraftMidPoint(Craft craft) {
        int maxDX = 0;
        int maxDZ = 0;
        int maxY = 0;
        int minY = 32767;
        for (int[][] i1 : craft.getHitBox()) {
            maxDX++;
            if (i1 != null) {
                int indexZ = 0;
                for (int[] i2 : i1) {
                    indexZ++;
                    if (i2 != null) {
                        if (i2[0] < minY) {
                            minY = i2[0];
                        }
                    }
                    if (i2 != null) {
                        if (i2[1] < maxY) {
                            maxY = i2[1];
                        }
                    }
                }
                if (indexZ > maxDZ) {
                    maxDZ = indexZ;
                }
            }
        }
        int midX = craft.getMinX() + (maxDX / 2);
        int midY = (minY + maxY) / 2;
        int midZ = craft.getMinZ() + (maxDZ / 2);
        return new BlockVec(midX, midY, midZ);
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
//	public void onCommand( PlayerCommandPreprocessEvent e ) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return false;
        }

        Player player = (Player) sender;

        final Craft playerCraft = craftManager.getCraftByPlayer(player);

        if (cmd.getName().equalsIgnoreCase("release")) {
            if (!player.hasPermission("movecraft.commands") && !player.hasPermission("movecraft.commands.release")) {
                player.sendMessage(i18n.get("Insufficient Permissions"));
                return true;
            }

            if (playerCraft != null) {
                craftManager.removeCraft(playerCraft);
                //e.getPlayer().sendMessage( String.format( i18n.get( "Player- Craft
                // has been released" ) ) );
            } else {
                player.sendMessage(i18n.get("Player- Error - You do not have a craft to release!"));
            }

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("pilot")) {
            if (!player.hasPermission("movecraft.commands") && !player.hasPermission("movecraft.commands.pilot")) {
                player.sendMessage(i18n.get("Insufficient Permissions"));
                return true;
            }

            if (args.length > 0) {
                if (player.hasPermission("movecraft." + args[0] + ".pilot")) {
                    BlockVec startPoint = MathUtils.bukkit2MovecraftLoc(player.getLocation());
                    Craft c = new Craft(getCraftTypeFromString(args[0]), player.getWorld());

                    if (playerCraft == null) {
                        asyncManager.detect(c, player, player, startPoint);
                    } else {
                        craftManager.removeCraft(playerCraft);
                        asyncManager.detect(c, player, player, startPoint);
                    }
                } else {
                    player.sendMessage(i18n.get("Insufficient Permissions"));
                }
                return true;
            }
        }

        if (cmd.getName().equalsIgnoreCase("rotate")) {
            if (!player.hasPermission("movecraft.commands") && !player.hasPermission("movecraft.commands.rotate")) {
                player.sendMessage(i18n.get("Insufficient Permissions"));
                return true;
            }

            if (playerCraft == null) {
            } else if (player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".rotate")) {
                BlockVec midPoint = getCraftMidPoint(playerCraft);
                Rotation rotation = (args.length > 0 && args[0].equalsIgnoreCase("left")) ? Rotation.ANTICLOCKWISE
                                                                                          : Rotation.CLOCKWISE;
                asyncManager.rotate(playerCraft, rotation, midPoint);
            } else {
                player.sendMessage(i18n.get("Insufficient Permissions"));
            }

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("rotateleft")) {
            if (!player.hasPermission("movecraft.commands") && !player.hasPermission("movecraft.commands.rotateleft")) {
                player.sendMessage(i18n.get("Insufficient Permissions"));
                return true;
            }

            if (playerCraft == null) {
            } else if (player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".rotate")) {
                BlockVec midPoint = getCraftMidPoint(playerCraft);
                asyncManager.rotate(playerCraft, Rotation.ANTICLOCKWISE, midPoint);
            } else {
                player.sendMessage(i18n.get("Insufficient Permissions"));
            }

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("rotateright")) {
            if (!player.hasPermission("movecraft.commands") &&
                !player.hasPermission("movecraft.commands.rotateright")) {
                player.sendMessage(i18n.get("Insufficient Permissions"));
                return true;
            }

            if (playerCraft == null) {
            } else if (player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".rotate")) {
                BlockVec midPoint = getCraftMidPoint(playerCraft);
                asyncManager.rotate(playerCraft, Rotation.CLOCKWISE, midPoint);
            } else {
                player.sendMessage(i18n.get("Insufficient Permissions"));
            }

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("cruise")) {
            if (!player.hasPermission("movecraft.commands") && !player.hasPermission("movecraft.commands.cruise")) {
                player.sendMessage(i18n.get("Insufficient Permissions"));
                return true;
            }

            if (playerCraft == null) {
            } else if (player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".move")) {
                if (playerCraft.getType().getCanCruise()) {
                    if (args.length == 0) {
                        Location loc = player.getLocation();
                        float yaw = loc.getYaw();
                        float pitch = loc.getPitch();

                        Direction dir = Direction.fromYawPitch(yaw, pitch);
                        playerCraft.setCruiseDirection(dir);
                        playerCraft.setCruising(!dir.equals(Direction.OFF));
                        return true;
                    } else {
                        Direction dir = Direction.OFF;
                        for (String a : args) {
                            if (a.isEmpty()) {
                                continue;
                            }

                            dir = dir.combine(Direction.namedOr(a, Direction.OFF));

                            if (Objects.equals(dir, Direction.OFF)) break;
                        }

                        playerCraft.setCruiseDirection(dir);
                        playerCraft.setCruising(!dir.equals(Direction.OFF));
                    }
                }
            } else {
                player.sendMessage(i18n.get("Insufficient Permissions"));
            }

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("cruiseoff")) {
            if (playerCraft != null) {
                playerCraft.setCruising(false);
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("craftreport")) {
            if (!player.hasPermission("movecraft.commands") &&
                !player.hasPermission("movecraft.commands.craftreport")) {
                player.sendMessage(i18n.get("Insufficient Permissions"));
                return true;
            }

            boolean noCraftsFound = true;
            if (craftManager.getCraftsInWorld(player.getWorld()) != null)
                for (Craft craft : craftManager.getCraftsInWorld(player.getWorld())) {
                    if (craft != null) {
                        String output = new String();
                        if (craft.getNotificationPlayer() != null) {
                            output = craft.getType().getCraftName() + " " + craft.getNotificationPlayer().getName() +
                                     " " + craft.getBlockList().length + " @ " + craft.getMinX() + "," +
                                     craft.getMinY() + "," + craft.getMinZ();
                        } else {
                            output = craft.getType().getCraftName() + " NULL " + craft.getBlockList().length + " @ " +
                                     craft.getMinX() + "," + craft.getMinY() + "," + craft.getMinZ();
                        }
                        player.sendMessage(output);
                        noCraftsFound = false;
                    }
                }
            if (noCraftsFound) {
                player.sendMessage("No crafts found");
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("contacts")) {
            if (playerCraft != null) {
                boolean foundContact = false;
                for (Craft tcraft : craftManager.getCraftsInWorld(playerCraft.getW())) {
                    long cposx = playerCraft.getMaxX() + playerCraft.getMinX();
                    long cposy = playerCraft.getMaxY() + playerCraft.getMinY();
                    long cposz = playerCraft.getMaxZ() + playerCraft.getMinZ();
                    cposx = cposx >> 1;
                    cposy = cposy >> 1;
                    cposz = cposz >> 1;
                    long tposx = tcraft.getMaxX() + tcraft.getMinX();
                    long tposy = tcraft.getMaxY() + tcraft.getMinY();
                    long tposz = tcraft.getMaxZ() + tcraft.getMinZ();
                    tposx = tposx >> 1;
                    tposy = tposy >> 1;
                    tposz = tposz >> 1;
                    long diffx = cposx - tposx;
                    long diffy = cposy - tposy;
                    long diffz = cposz - tposz;
                    long distsquared = Math.abs(diffx) * Math.abs(diffx);
                    distsquared += Math.abs(diffy) * Math.abs(diffy);
                    distsquared += Math.abs(diffz) * Math.abs(diffz);
                    long detectionRange = 0;
                    if (tposy > 65) {
                        detectionRange = (long) (Math.sqrt(tcraft.getOrigBlockCount()) *
                                                 tcraft.getType().getDetectionMultiplier());
                    } else {
                        detectionRange = (long) (Math.sqrt(tcraft.getOrigBlockCount()) *
                                                 tcraft.getType().getUnderwaterDetectionMultiplier());
                    }
                    if (distsquared < detectionRange * detectionRange &&
                        tcraft.getNotificationPlayer() != playerCraft.getNotificationPlayer()) {
                        // craft has been detected
                        foundContact = true;
                        String notification = "Contact: ";
                        notification += tcraft.getType().getCraftName();
                        notification += " commanded by ";
                        notification += tcraft.getNotificationPlayer().getDisplayName();
                        notification += ", size: ";
                        notification += tcraft.getOrigBlockCount();
                        notification += ", range: ";
                        notification += (int) Math.sqrt(distsquared);
                        notification += " to the";
                        if (Math.abs(diffx) > Math.abs(diffz)) if (diffx < 0) notification += " east.";
                        else notification += " west.";
                        else if (diffz < 0) notification += " south.";
                        else notification += " north.";

                        playerCraft.getNotificationPlayer().sendMessage(notification);
                    }
                }
                if (!foundContact) player.sendMessage(i18n.get("No contacts within range"));
                return true;
            } else {
                player.sendMessage(i18n.get("You must be piloting a craft"));
                return true;
            }
        }

        if (cmd.getName().equalsIgnoreCase("manOverBoard")) {
            if (playerCraft != null) {
                Location telPoint = getCraftTeleportPoint(playerCraft, playerCraft.getW());
                player.teleport(telPoint);
            } else {
                for (World w : Bukkit.getWorlds()) {
                    if (craftManager.getCraftsInWorld(w) != null)
                        for (Craft tcraft : craftManager.getCraftsInWorld(w)) {
                            if (tcraft.getMovedPlayers().containsKey(player))
                                if ((System.currentTimeMillis() - tcraft.getMovedPlayers().get(player)) / 1000 <
                                    settings.ManOverBoardTimeout) {
                                    Location telPoint = getCraftTeleportPoint(tcraft, w);
                                    player.teleport(telPoint);
                                }
                        }
                }
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("siege")) {
            if (!player.hasPermission("movecraft.siege")) {
                player.sendMessage(i18n.get("Insufficient Permissions"));
                return true;
            }
            if (settings.SiegeName == null) {
                player.sendMessage(i18n.get("Siege is not configured on this server"));
                return true;
            }
            if (plugin.siegeInProgress) {
                player.sendMessage(i18n.get("A Siege is already taking place"));
                return true;
            }
            String foundSiegeName = null;
            LocalPlayer lp = plugin.getWorldGuardPlugin().wrapPlayer(player);
            ApplicableRegionSet regions = plugin.getWorldGuardPlugin().getRegionManager(player.getWorld())
                                                .getApplicableRegions(player.getLocation());
            if (regions.size() != 0) {
                for (ProtectedRegion tRegion : regions.getRegions()) {
                    for (String tSiegeName : settings.SiegeName) {
                        if (tRegion.getId().equalsIgnoreCase(settings.SiegeRegion.get(tSiegeName)))
                            foundSiegeName = tSiegeName;
                    }
                }
            }
            if (foundSiegeName != null) {
                long cost = settings.SiegeCost.get(foundSiegeName);
                int numControlledSieges = 0;
                for (String tSiegeName : settings.SiegeName) {
                    ProtectedRegion tregion = plugin.getWorldGuardPlugin().getRegionManager(player.getWorld())
                                                    .getRegion(settings.SiegeControlRegion.get(tSiegeName));
                    if (tregion.getOwners().contains(player.getName())) {
                        numControlledSieges++;
                        cost = cost * 2;
                    }
                }

                if (plugin.getEconomy().has(player, cost)) {
                    Calendar rightNow = Calendar.getInstance(TimeZone.getTimeZone("MST"));
                    int hour = rightNow.get(Calendar.HOUR_OF_DAY);
                    int minute = rightNow.get(Calendar.MINUTE);
                    int currMilitaryTime = hour * 100 + minute;
                    if ((currMilitaryTime > settings.SiegeScheduleStart.get(foundSiegeName)) &&
                        (currMilitaryTime < settings.SiegeScheduleEnd.get(foundSiegeName))) {
                        Bukkit.getServer().broadcastMessage(String.format(
                                "%s is preparing to siege %s! All players wishing to participate in the defense " +
                                "should head there immediately! Siege will begin in %d minutes",
                                player.getDisplayName(), foundSiegeName, settings.SiegeDelay.get(foundSiegeName) / 60));
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1, 0);
                        }
                        final String taskPlayerDisplayName = player.getDisplayName();
                        final String taskPlayerName = player.getName();
                        final String taskSiegeName = foundSiegeName;
                        BukkitTask warningtask1 = new BukkitRunnable() {
                            @Override public void run() {
                                Bukkit.getServer().broadcastMessage(String.format(
                                        "%s is preparing to siege %s! All players wishing to participate in the " +
                                        "defense should head there immediately! Siege will begin in %d minutes",
                                        taskPlayerDisplayName, taskSiegeName,
                                        (settings.SiegeDelay.get(taskSiegeName) / 60) / 4 * 3));
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1, 0);
                                }
                            }
                        }.runTaskLater(plugin, (20 * settings.SiegeDelay.get(taskSiegeName) / 4 * 1));
                        BukkitTask warningtask2 = new BukkitRunnable() {
                            @Override public void run() {
                                Bukkit.getServer().broadcastMessage(String.format(
                                        "%s is preparing to siege %s! All players wishing to participate in the " +
                                        "defense should head there immediately! Siege will begin in %d minutes",
                                        taskPlayerDisplayName, taskSiegeName,
                                        (settings.SiegeDelay.get(taskSiegeName) / 60) / 4 * 2));
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1, 0);
                                }
                            }
                        }.runTaskLater(plugin, (20 * settings.SiegeDelay.get(taskSiegeName) / 4 * 2));
                        BukkitTask warningtask3 = new BukkitRunnable() {
                            @Override public void run() {
                                Bukkit.getServer().broadcastMessage(String.format(
                                        "%s is preparing to siege %s! All players wishing to participate in the " +
                                        "defense should head there immediately! Siege will begin in %d minutes",
                                        taskPlayerDisplayName, taskSiegeName,
                                        (settings.SiegeDelay.get(taskSiegeName) / 60) / 4 * 1));
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1, 0);
                                }
                            }
                        }.runTaskLater(plugin, (20 * settings.SiegeDelay.get(taskSiegeName) / 4 * 3));
                        BukkitTask commencetask = new BukkitRunnable() {
                            @Override public void run() {
                                Bukkit.getServer().broadcastMessage(String.format(
                                        "The Siege of %s has commenced! The siege leader is %s. Destroy the enemy " +
                                        "vessels!", taskSiegeName, taskPlayerDisplayName,
                                        (settings.SiegeDuration.get(taskSiegeName) / 60)));
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1, 0);
                                }
                                plugin.currentSiegeName = taskSiegeName;
                                plugin.currentSiegePlayer = taskPlayerName;
                                plugin.currentSiegeStartTime = System.currentTimeMillis();
                            }
                        }.runTaskLater(plugin, (20 * settings.SiegeDelay.get(taskSiegeName)));
                        plugin.getLogger().log(Level.INFO, String.format("Siege: %s commenced by %s for a cost of %d",
                                                                         foundSiegeName, player.getName(), cost));
                        plugin.getEconomy().withdrawPlayer(player, cost);
                        plugin.siegeInProgress = true;
                    } else {
                        player.sendMessage(i18n.get("The time is not during the Siege schedule"));
                        return true;
                    }
                } else {
                    player.sendMessage(String.format("You do not have enough money. You need %d", cost));
                    return true;
                }
            } else {
                player.sendMessage(i18n.get("Could not find a siege configuration for the region you are in"));
                return true;
            }
        }

        return false;
    }
}
