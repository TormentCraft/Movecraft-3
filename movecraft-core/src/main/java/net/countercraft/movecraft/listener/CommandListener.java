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
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;

public class CommandListener implements CommandExecutor {
    private final Settings settings;
    private final I18nSupport i18n;
    private final CraftManager craftManager;
    private final AsyncManager asyncManager;

    public CommandListener(final Settings settings, final I18nSupport i18n, final CraftManager craftManager, final AsyncManager asyncManager)
    {
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
        this.asyncManager = asyncManager;
    }

    private static Location getCraftTeleportPoint(final Craft craft, final World w) {
        int maxDX = 0;
        int maxDZ = 0;
        int maxY = 0;
        int minY = 32767;
        for (final int[][] i1 : craft.getHitBox()) {
            maxDX++;
            if (i1 != null) {
                int indexZ = 0;
                for (final int[] i2 : i1) {
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
        final double telX = craft.getMinX() + (maxDX / 2.0);
        final double telZ = craft.getMinZ() + (maxDZ / 2.0);
        final double telY = maxY + 1.0;
        return new Location(w, telX, telY, telZ);
    }

    private static BlockVec getCraftMidPoint(final Craft craft) {
        int maxDX = 0;
        int maxDZ = 0;
        int maxY = 0;
        int minY = 32767;
        for (final int[][] i1 : craft.getHitBox()) {
            maxDX++;
            if (i1 != null) {
                int indexZ = 0;
                for (final int[] i2 : i1) {
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
        final int midX = craft.getMinX() + (maxDX / 2);
        final int midY = (minY + maxY) / 2;
        final int midZ = craft.getMinZ() + (maxDZ / 2);
        return new BlockVec(midX, midY, midZ);
    }

    @Override public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return false;
        }

        final Player player = (Player) sender;

        final Craft playerCraft = this.craftManager.getCraftByPlayer(player);

        if (cmd.getName().equalsIgnoreCase("release")) {
            if (!player.hasPermission("movecraft.commands") && !player.hasPermission("movecraft.commands.release")) {
                player.sendMessage(this.i18n.get("Insufficient Permissions"));
                return true;
            }

            if (playerCraft != null) {
                this.craftManager.removeCraft(playerCraft);
                //e.getPlayer().sendMessage( String.format( i18n.get( "Player- Craft
                // has been released" ) ) );
            } else {
                player.sendMessage(this.i18n.get("Player- Error - You do not have a craft to release!"));
            }

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("pilot")) {
            if (!player.hasPermission("movecraft.commands") && !player.hasPermission("movecraft.commands.pilot")) {
                player.sendMessage(this.i18n.get("Insufficient Permissions"));
                return true;
            }

            if (args.length > 0) {
                if (player.hasPermission("movecraft." + args[0] + ".pilot")) {
                    final BlockVec startPoint = MathUtils.bukkit2MovecraftLoc(player.getLocation());
                    final Optional<CraftType> ct = this.craftManager.getCraftTypeFromString(args[0]);

                    if (ct.isPresent()) {
                        final Craft c = new Craft(ct.get(), player.getWorld());

                        if (playerCraft == null) {
                            this.asyncManager.detect(c, player, player, startPoint);
                        } else {
                            this.craftManager.removeCraft(playerCraft);
                            this.asyncManager.detect(c, player, player, startPoint);
                        }
                    } else {
                        player.sendMessage(this.i18n.get("Unknown craft type."));
                    }

                } else {
                    player.sendMessage(this.i18n.get("Insufficient Permissions"));
                }
                return true;
            }
        }

        if (cmd.getName().equalsIgnoreCase("rotate")) {
            if (!player.hasPermission("movecraft.commands") && !player.hasPermission("movecraft.commands.rotate")) {
                player.sendMessage(this.i18n.get("Insufficient Permissions"));
                return true;
            }

            if (playerCraft != null) {
                if (player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".rotate")) {
                    final BlockVec midPoint = CommandListener.getCraftMidPoint(playerCraft);
                    final Rotation rotation = (args.length > 0 && args[0].equalsIgnoreCase("left"))
                                              ? Rotation.ANTICLOCKWISE
                                              : Rotation.CLOCKWISE;
                    this.asyncManager.rotate(playerCraft, rotation, midPoint);
                } else {
                    player.sendMessage(this.i18n.get("Insufficient Permissions"));
                }
            }

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("rotateleft")) {
            if (!player.hasPermission("movecraft.commands") && !player.hasPermission("movecraft.commands.rotateleft")) {
                player.sendMessage(this.i18n.get("Insufficient Permissions"));
                return true;
            }

            if (playerCraft != null) {
                if (player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".rotate")) {
                    final BlockVec midPoint = CommandListener.getCraftMidPoint(playerCraft);
                    this.asyncManager.rotate(playerCraft, Rotation.ANTICLOCKWISE, midPoint);
                } else {
                    player.sendMessage(this.i18n.get("Insufficient Permissions"));
                }
            }

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("rotateright")) {
            if (!player.hasPermission("movecraft.commands") &&
                !player.hasPermission("movecraft.commands.rotateright")) {
                player.sendMessage(this.i18n.get("Insufficient Permissions"));
                return true;
            }

            if (playerCraft != null) {
                if (player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".rotate")) {
                    final BlockVec midPoint = CommandListener.getCraftMidPoint(playerCraft);
                    this.asyncManager.rotate(playerCraft, Rotation.CLOCKWISE, midPoint);
                } else {
                    player.sendMessage(this.i18n.get("Insufficient Permissions"));
                }
            }

            return true;
        }

        if (cmd.getName().equalsIgnoreCase("cruise")) {
            if (!player.hasPermission("movecraft.commands") && !player.hasPermission("movecraft.commands.cruise")) {
                player.sendMessage(this.i18n.get("Insufficient Permissions"));
                return true;
            }

            if (playerCraft != null) {
                if (player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".move")) {
                    if (playerCraft.getType().getCanCruise()) {
                        if (args.length == 0) {
                            final Location loc = player.getLocation();
                            final float yaw = loc.getYaw();
                            final float pitch = loc.getPitch();

                            final Direction dir = Direction.fromYawPitch(yaw, pitch);
                            playerCraft.setCruiseDirection(dir);
                            playerCraft.setCruising(!dir.equals(Direction.OFF));
                            return true;
                        } else {
                            Direction dir = Direction.OFF;
                            for (final String a : args) {
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
                    player.sendMessage(this.i18n.get("Insufficient Permissions"));
                }
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
                player.sendMessage(this.i18n.get("Insufficient Permissions"));
                return true;
            }

            boolean noCraftsFound = true;
            for (final Craft craft : this.craftManager.getCraftsInWorld(player.getWorld())) {
                if (craft != null) {
                    final String output;
                    if (craft.getNotificationPlayer() != null) {
                        output = MessageFormat.format("{0} {1} {2} @ {3},{4},{5}",
                                                      craft.getType().getCraftName(),
                                                      craft.getNotificationPlayer().getName(),
                                                      craft.getBlockList().length,
                                                      craft.getMinX(), craft.getMinY(), craft.getMinZ());
                    } else {
                        output = MessageFormat.format("{0} NULL {1} @ {2},{3},{4}",
                                                      craft.getType().getCraftName(),
                                                      craft.getBlockList().length,
                                                      craft.getMinX(), craft.getMinY(), craft.getMinZ());
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
                for (final Craft tcraft : this.craftManager.getCraftsInWorld(playerCraft.getWorld())) {
                    long cposx = playerCraft.getMaxX() + playerCraft.getMinX();
                    long cposy = playerCraft.getMaxY() + playerCraft.getMinY();
                    long cposz = playerCraft.getMaxZ() + playerCraft.getMinZ();
                    cposx = cposx / 2;
                    cposy = cposy / 2;
                    cposz = cposz / 2;
                    long tposx = tcraft.getMaxX() + tcraft.getMinX();
                    long tposy = tcraft.getMaxY() + tcraft.getMinY();
                    long tposz = tcraft.getMaxZ() + tcraft.getMinZ();
                    tposx = tposx / 2;
                    tposy = tposy / 2;
                    tposz = tposz / 2;
                    final long diffx = cposx - tposx;
                    final long diffy = cposy - tposy;
                    final long diffz = cposz - tposz;
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
                if (!foundContact) player.sendMessage(this.i18n.get("No contacts within range"));
                return true;
            } else {
                player.sendMessage(this.i18n.get("You must be piloting a craft"));
                return true;
            }
        }

        if (cmd.getName().equalsIgnoreCase("manOverBoard")) {
            if (playerCraft != null) {
                final Location telPoint = CommandListener.getCraftTeleportPoint(playerCraft, playerCraft.getWorld());
                player.teleport(telPoint);
            } else {
                for (final World w : Bukkit.getWorlds()) {
                    for (final Craft tcraft : this.craftManager.getCraftsInWorld(w)) {
                        if (tcraft.getMovedPlayers().containsKey(player))
                            if ((System.currentTimeMillis() - tcraft.getMovedPlayers().get(player)) / 1000 <
                                this.settings.ManOverBoardTimeout) {
                                final Location telPoint = CommandListener.getCraftTeleportPoint(tcraft, w);
                                player.teleport(telPoint);
                            }
                    }
                }
            }
            return true;
        }

        return false;
    }
}
