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

import net.countercraft.movecraft.Permissions;
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

    private static Location getCraftTeleportPoint(final Craft craft, final World world) {
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
        return new Location(world, telX, telY, telZ);
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

    static final class InsufficientPermissionsException extends Exception {
        private static final long serialVersionUID = -7764750243263992533L;
    }

    static final class PlayerOnlyCommandException extends Exception {
        private static final long serialVersionUID = 1726514287074215228L;
    }

    static final class CommandRequiresPilotedCraftException extends Exception {
        private static final long serialVersionUID = 1578133834052849743L;
    }

    static final class InvalidCommandSyntaxException extends Exception {
        private static final long serialVersionUID = 8962188695118319340L;
    }

    private void handleCommand(final CommandSender sender, final String name, final String[] args)
    throws InsufficientPermissionsException, PlayerOnlyCommandException, CommandRequiresPilotedCraftException
    {
        if (!(sender instanceof Player)) throw new PlayerOnlyCommandException();

        final Player player = (Player) sender;
        final Craft playerCraft = this.craftManager.getCraftByPlayer(player);

        if (name.equalsIgnoreCase("release")) {
            if (!player.hasPermission(Permissions.COMMANDS) && !player.hasPermission(Permissions.COMMAND_RELEASE))
                throw new InsufficientPermissionsException();

            if (playerCraft == null)
                throw new CommandRequiresPilotedCraftException();

            this.craftManager.removeCraft(playerCraft);
            player.getPlayer().sendMessage(this.i18n.get("Player - Craft has been released" ));
        }

        if (name.equalsIgnoreCase("pilot")) {
            if (!player.hasPermission(Permissions.COMMANDS) && !player.hasPermission(Permissions.COMMAND_PILOT))
                throw new InsufficientPermissionsException();

            if (args.length > 0) {
                if (!player.hasPermission(Permissions.PILOT(args[0])))
                    throw new InsufficientPermissionsException();

                final BlockVec startPoint = MathUtils.bukkit2MovecraftLoc(player.getLocation());
                final Optional<CraftType> ct = this.craftManager.getCraftTypeFromString(args[0]);

                if (ct.isPresent()) {
                    final Craft craft = new Craft(ct.get(), player.getWorld());

                    if (playerCraft == null) {
                        this.asyncManager.detect(craft, player, player, startPoint);
                    } else {
                        this.craftManager.removeCraft(playerCraft);
                        this.asyncManager.detect(craft, player, player, startPoint);
                    }
                } else {
                    player.sendMessage(this.i18n.get("Unknown craft type."));
                }
            }
        }

        if (name.equalsIgnoreCase("rotate")) {
            if (!player.hasPermission(Permissions.COMMANDS) && !player.hasPermission("movecraft.commands.rotate"))
                throw new InsufficientPermissionsException();

            if (playerCraft == null) throw new CommandRequiresPilotedCraftException();

            if (!player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".rotate"))
                throw new InsufficientPermissionsException();

            final BlockVec midPoint = CommandListener.getCraftMidPoint(playerCraft);
            final Rotation rotation = (args.length > 0 && args[0].equalsIgnoreCase("left"))
                                      ? Rotation.ANTICLOCKWISE
                                      : Rotation.CLOCKWISE;
            this.asyncManager.rotate(playerCraft, rotation, midPoint);
        }

        if (name.equalsIgnoreCase("rotateleft")) {
            if (!player.hasPermission(Permissions.COMMANDS) && !player.hasPermission("movecraft.commands.rotateleft"))
                throw new InsufficientPermissionsException();

            if (playerCraft == null) throw new CommandRequiresPilotedCraftException();

            if (!player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".rotate"))
                throw new InsufficientPermissionsException();

            final BlockVec midPoint = CommandListener.getCraftMidPoint(playerCraft);
            this.asyncManager.rotate(playerCraft, Rotation.ANTICLOCKWISE, midPoint);
        }

        if (name.equalsIgnoreCase("rotateright")) {
            if (!player.hasPermission(Permissions.COMMANDS) && !player.hasPermission("movecraft.commands.rotateright"))
                throw new InsufficientPermissionsException();

            if (playerCraft == null)
                throw new CommandRequiresPilotedCraftException();

            if (!player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".rotate")) {
                throw new InsufficientPermissionsException();
            }

            final BlockVec midPoint = CommandListener.getCraftMidPoint(playerCraft);
            this.asyncManager.rotate(playerCraft, Rotation.CLOCKWISE, midPoint);
        }

        if (name.equalsIgnoreCase("cruise")) {
            if (!player.hasPermission("movecraft.commands") && !player.hasPermission("movecraft.commands.cruise"))
                throw new InsufficientPermissionsException();

            if (playerCraft == null)
                throw new CommandRequiresPilotedCraftException();

            if (!player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".move"))
                throw new InsufficientPermissionsException();

            if (playerCraft.getType().getCanCruise()) {
                if (args.length == 0) {
                    final Location loc = player.getLocation();
                    final float yaw = loc.getYaw();
                    final float pitch = loc.getPitch();

                    final Direction dir = Direction.fromYawPitch(yaw, pitch);
                    playerCraft.setCruiseDirection(dir);
                    playerCraft.setCruising(!dir.equals(Direction.OFF));
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
        }

        if (name.equalsIgnoreCase("cruiseoff")) {
            if (playerCraft == null)
                throw new CommandRequiresPilotedCraftException();

            playerCraft.setCruising(false);
        }

        if (name.equalsIgnoreCase("craftreport")) {
            if (!player.hasPermission(Permissions.COMMANDS) && !player.hasPermission("movecraft.commands.craftreport"))
                throw new InsufficientPermissionsException();

            boolean noCraftsFound = true;
            for (final Craft craft : this.craftManager.getCraftsInWorld(player.getWorld())) {
                if (craft != null) {
                    final String notificationPlayer;
                    if (craft.getNotificationPlayer() != null)
                        notificationPlayer = craft.getNotificationPlayer().getName();
                    else notificationPlayer = "NULL";

                    final String output = MessageFormat.format("{0} {1} {2} @ {3},{4},{5}",
                                                               craft.getType().getCraftName(),
                                                               notificationPlayer,
                                                               craft.getBlockList().length,
                                                               craft.getMinX(), craft.getMinY(), craft.getMinZ());

                    player.sendMessage(output);
                    noCraftsFound = false;
                }
            }
            if (noCraftsFound) {
                player.sendMessage("No crafts found");
            }
        }

        if (name.equalsIgnoreCase("contacts")) {
            if (playerCraft == null)
                throw new CommandRequiresPilotedCraftException();

            boolean foundContact = false;
            for (final Craft tcraft : this.craftManager.getCraftsInWorld(playerCraft.getWorld())) {
                final long cposx = (playerCraft.getMaxX() + playerCraft.getMinX()) / 2;
                final long cposy = (playerCraft.getMaxY() + playerCraft.getMinY()) / 2;
                final long cposz = (playerCraft.getMaxZ() + playerCraft.getMinZ()) / 2;

                final long tposx = (tcraft.getMaxX() + tcraft.getMinX()) / 2;
                final long tposy = (tcraft.getMaxY() + tcraft.getMinY()) / 2;
                final long tposz = (tcraft.getMaxZ() + tcraft.getMinZ()) / 2;

                final long detectionRange;
                if (tposy > 65) {
                    detectionRange = (long) (Math.sqrt(tcraft.getOrigBlockCount()) *
                                             tcraft.getType().getDetectionMultiplier());
                } else {
                    detectionRange = (long) (Math.sqrt(tcraft.getOrigBlockCount()) *
                                             tcraft.getType().getUnderwaterDetectionMultiplier());
                }

                final long dx = cposx - tposx;
                final long dy = cposy - tposy;
                final long dz = cposz - tposz;
                final long dr = dx * dx + dy * dy + dz * dz;
                if (dr < detectionRange * detectionRange &&
                    tcraft.getNotificationPlayer() != playerCraft.getNotificationPlayer()) {
                    // craft has been detected
                    foundContact = true;

                    final String direction;
                    if (Math.abs(dx) > Math.abs(dz)) {
                        if (dx < 0) direction = "east";
                        else direction = "west";
                    } else if (dz < 0) {
                        direction = "south";
                    } else direction = "north";

                    final String notification = MessageFormat
                            .format("Contact: {0} commanded by {1}, size: {2}, range: {3} to the {4}.",
                                    tcraft.getType().getCraftName(), tcraft.getNotificationPlayer().getDisplayName(),
                                    tcraft.getOrigBlockCount(), Math.sqrt(dr), direction);
                    playerCraft.getNotificationPlayer().sendMessage(notification);
                }
            }
            if (!foundContact) player.sendMessage(this.i18n.get("No contacts within range"));
        }

        if (name.equalsIgnoreCase("manOverBoard")) {
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
        }
    }

    @Override public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        try {
            this.handleCommand(sender, cmd.getName(), args);
            return true;
        } catch (final InsufficientPermissionsException e) {
            sender.sendMessage(this.i18n.get("Insufficient Permissions"));
            return false;
        } catch (final PlayerOnlyCommandException e) {
            sender.sendMessage(this.i18n.get("This command can only be run by a player."));
            return false;
        } catch (final CommandRequiresPilotedCraftException e) {
            sender.sendMessage(this.i18n.get("You must be piloting a craft."));
            return false;
        }
    }
}
