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

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.CraftType;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.math.Direction;
import net.countercraft.movecraft.utils.MathUtils;
import net.countercraft.movecraft.utils.MovecraftLocation;
import net.countercraft.movecraft.utils.Rotation;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public class InteractListener implements Listener {
    private static final Map<Player, Long> timeMap = new HashMap<>();
    private static final Map<Player, Long> repairRightClickTimeMap = new HashMap<>();

    @EventHandler public void onPlayerInteract(PlayerInteractEvent event) {

        final CraftManager craftManager = CraftManager.getInstance();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Material m = event.getClickedBlock().getType();
            if (m == Material.SIGN_POST || m == Material.WALL_SIGN) {
                Sign sign = (Sign) event.getClickedBlock().getState();
                final String firstLine = ChatColor.stripColor(sign.getLine(0));

                if (firstLine == null) {
                    return;
                }

                if (firstLine.equalsIgnoreCase("Remote Sign")) {
                    MovecraftLocation sourceLocation = MathUtils
                            .bukkit2MovecraftLoc(event.getClickedBlock().getLocation());
                    Craft foundCraft = null;
                    if (craftManager.getCraftsInWorld(event.getClickedBlock().getWorld()) != null)
                        for (Craft tcraft : craftManager.getCraftsInWorld(event.getClickedBlock().getWorld())) {
                            if (MathUtils.playerIsWithinBoundingPolygon(tcraft.getHitBox(), tcraft.getMinX(),
                                                                        tcraft.getMinZ(), sourceLocation)) {
                                // don't use a craft with a null player. This is mostly to avoid trying to use subcrafts
                                if (craftManager.getPlayerFromCraft(tcraft) != null) foundCraft = tcraft;
                            }
                        }

                    if (foundCraft == null) {
                        event.getPlayer().sendMessage(I18nSupport.getInternationalisedString(
                                "ERROR: Remote Sign must be a part of a piloted craft!"));
                        return;
                    }

                    if (!foundCraft.getType().allowRemoteSign()) {
                        event.getPlayer().sendMessage(I18nSupport.getInternationalisedString(
                                "ERROR: Remote Signs not allowed on this craft!"));
                        return;
                    }

                    String targetText = org.bukkit.ChatColor.stripColor(sign.getLine(1));
                    MovecraftLocation foundLoc = null;
                    for (MovecraftLocation tloc : foundCraft.getBlockList()) {
                        Block tb = event.getClickedBlock().getWorld().getBlockAt(tloc.x, tloc.y, tloc.z);
                        if (tb.getType() == Material.SIGN_POST || tb.getType() == Material.WALL_SIGN) {
                            Sign ts = (Sign) tb.getState();
                            if (org.bukkit.ChatColor.stripColor(ts.getLine(0)) != null)
                                if (org.bukkit.ChatColor.stripColor(ts.getLine(0)) != null)
                                    if (org.bukkit.ChatColor.stripColor(ts.getLine(0)).equalsIgnoreCase(targetText))
                                        foundLoc = tloc;
                            if (org.bukkit.ChatColor.stripColor(ts.getLine(1)) != null)
                                if (org.bukkit.ChatColor.stripColor(ts.getLine(1)).equalsIgnoreCase(targetText)) {
                                    boolean isRemoteSign = false;
                                    if (org.bukkit.ChatColor.stripColor(ts.getLine(0)) != null)
                                        if (org.bukkit.ChatColor.stripColor(ts.getLine(0))
                                                                .equalsIgnoreCase("Remote Sign")) isRemoteSign = true;
                                    if (!isRemoteSign) foundLoc = tloc;
                                }
                            if (org.bukkit.ChatColor.stripColor(ts.getLine(2)) != null)
                                if (org.bukkit.ChatColor.stripColor(ts.getLine(2)).equalsIgnoreCase(targetText))
                                    foundLoc = tloc;
                            if (org.bukkit.ChatColor.stripColor(ts.getLine(3)) != null)
                                if (org.bukkit.ChatColor.stripColor(ts.getLine(3)).equalsIgnoreCase(targetText))
                                    foundLoc = tloc;
                        }
                    }
                    if (foundLoc == null) {
                        event.getPlayer().sendMessage(
                                I18nSupport.getInternationalisedString("ERROR: Could not find target sign!"));
                        return;
                    }

                    Block newBlock = event.getClickedBlock().getWorld().getBlockAt(foundLoc.x, foundLoc.y, foundLoc.z);
                    PlayerInteractEvent newEvent = new PlayerInteractEvent(event.getPlayer(), event.getAction(),
                                                                           event.getItem(), newBlock,
                                                                           event.getBlockFace());
                    onPlayerInteract(newEvent);
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Material m = event.getClickedBlock().getType();
            if (m == Material.SIGN_POST || m == Material.WALL_SIGN) {
                onSignRightClick(event);
            }
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Material m = event.getClickedBlock().getType();
            if (m == Material.SIGN_POST || m == Material.WALL_SIGN) {
                if (event.getClickedBlock() == null) {
                    return;
                }
                Sign sign = (Sign) event.getClickedBlock().getState();
                String signText = org.bukkit.ChatColor.stripColor(sign.getLine(0));

                if (signText == null) {
                    return;
                }

                if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equals("\\  ||  /") &&
                    org.bukkit.ChatColor.stripColor(sign.getLine(1)).equals("==      ==") &&
                    org.bukkit.ChatColor.stripColor(sign.getLine(2)).equals("/  ||  \\")) {
                    final Craft playerCraft = craftManager.getCraftByPlayer(event.getPlayer());
                    if (playerCraft != null) {
                        if (event.getPlayer()
                                 .hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".rotate")) {

                            Long time = timeMap.get(event.getPlayer());
                            if (time != null) {
                                long ticksElapsed = (System.currentTimeMillis() - time) / 50;

                                // if the craft should go slower underwater, make time pass more slowly there
                                if (playerCraft.getType().getHalfSpeedUnderwater() &&
                                    playerCraft.getMinY() < playerCraft.getW().getSeaLevel()) ticksElapsed = ticksElapsed >> 1;

                                if (Math.abs(ticksElapsed) < playerCraft.getType().getTickCooldown()) {
                                    event.setCancelled(true);
                                    return;
                                }
                            }

                            if (MathUtils.playerIsWithinBoundingPolygon(
                                    playerCraft.getHitBox(), playerCraft.getMinX(), playerCraft.getMinZ(),
                                    MathUtils.bukkit2MovecraftLoc(event.getPlayer().getLocation()))) {
                                if (playerCraft.getType().rotateAtMidpoint()) {
                                    MovecraftLocation midpoint = new MovecraftLocation(
                                            (playerCraft.getMaxX() + playerCraft.getMinX()) / 2,
                                            (playerCraft.getMaxY() + playerCraft.getMinY()) / 2,
                                            (playerCraft.getMaxZ() + playerCraft.getMinZ()) / 2);
                                    playerCraft
                                                .rotate(Rotation.ANTICLOCKWISE, midpoint);
                                } else {
                                    playerCraft
                                                .rotate(Rotation.ANTICLOCKWISE,
                                                        MathUtils.bukkit2MovecraftLoc(sign.getLocation()));
                                }

                                timeMap.put(event.getPlayer(), System.currentTimeMillis());
                                event.setCancelled(true);
                            }
                        } else {
                            event.getPlayer().sendMessage(
                                    I18nSupport.getInternationalisedString("Insufficient Permissions"));
                        }
                    }
                }
                if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Subcraft Rotate")) {
                    // rotate subcraft
                    String craftTypeStr = org.bukkit.ChatColor.stripColor(sign.getLine(1));
                    if (getCraftTypeFromString(craftTypeStr) != null) {
                        if (org.bukkit.ChatColor.stripColor(sign.getLine(2)).equals("") &&
                            org.bukkit.ChatColor.stripColor(sign.getLine(3)).equals("")) {
                            sign.setLine(2, "_\\ /_");
                            sign.setLine(3, "/ \\");
                            sign.update();
                        }

                        if (event.getPlayer().hasPermission("movecraft." + craftTypeStr + ".pilot") &&
                            event.getPlayer().hasPermission("movecraft." + craftTypeStr + ".rotate")) {
                            Long time = timeMap.get(event.getPlayer());
                            if (time != null) {
                                long ticksElapsed = (System.currentTimeMillis() - time) / 50;
                                if (Math.abs(ticksElapsed) < getCraftTypeFromString(craftTypeStr).getTickCooldown()) {
                                    event.setCancelled(true);
                                    return;
                                }
                            }
                            final Location loc = event.getClickedBlock().getLocation();
                            final Craft c = new Craft(getCraftTypeFromString(craftTypeStr), loc.getWorld());
                            MovecraftLocation startPoint = new MovecraftLocation(loc.getBlockX(), loc.getBlockY(),
                                                                                 loc.getBlockZ());
                            c.detect(null, event.getPlayer(), startPoint);
                            BukkitTask releaseTask = new BukkitRunnable() {

                                @Override public void run() {
                                    craftManager.removeCraft(c);
                                }
                            }.runTaskLater(Movecraft.getInstance(), (20 * 5));

                            BukkitTask rotateTask = new BukkitRunnable() {

                                @Override public void run() {
                                    c.rotate(Rotation.ANTICLOCKWISE, MathUtils.bukkit2MovecraftLoc(loc), true);
                                }
                            }.runTaskLater(Movecraft.getInstance(), (10));
                            timeMap.put(event.getPlayer(), System.currentTimeMillis());
                            event.setCancelled(true);
                        } else {
                            event.getPlayer().sendMessage(
                                    I18nSupport.getInternationalisedString("Insufficient Permissions"));
                        }
                    }
                }
            }
        }
    }

    private void onSignRightClick(PlayerInteractEvent event) {
        Sign sign = (Sign) event.getClickedBlock().getState();
        String signText = org.bukkit.ChatColor.stripColor(sign.getLine(0));

        if (signText == null) {
            return;
        }

        // don't process commands if this is a pilot tool click, do that below
        final CraftManager craftManager = CraftManager.getInstance();
        final Player player = event.getPlayer();
        final Craft playerCraft = craftManager.getCraftByPlayer(player);
        if (event.getItem() != null && event.getItem().getTypeId() == Settings.PilotTool) {
            if (playerCraft != null) return;
        }

        final CraftType craftType = getCraftTypeFromString(ChatColor.stripColor(sign.getLine(0)));
        if (craftType != null) {
            // Valid sign prompt for ship command.
            if (player.hasPermission("movecraft." + org.bukkit.ChatColor.stripColor(sign.getLine(0)) + ".pilot")) {
                // Attempt to run detection
                Location loc = event.getClickedBlock().getLocation();
                MovecraftLocation startPoint = new MovecraftLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                final Craft c = new Craft(craftType, loc.getWorld());

                if (c.getType().getCruiseOnPilot()) {
                    c.detect(null, player, startPoint);
                    c.setCruiseDirection(Direction.fromSignDirection(sign));
                    c.setLastCruisUpdate(System.currentTimeMillis());
                    c.setCruising(true);
                    BukkitTask releaseTask = new BukkitRunnable() {

                        @Override public void run() {
                            craftManager.removeCraft(c);
                        }
                    }.runTaskLater(Movecraft.getInstance(), (20 * 15));
//					CraftManager.getInstance().getReleaseEvents().put( event.getPlayer(), releaseTask );
                } else {
                    if (playerCraft == null) {
                        c.detect(player, player, startPoint);
                    } else {
                        if (playerCraft.isNotProcessing()) {
                            craftManager.removeCraft(playerCraft);
                            c.detect(player, player, startPoint);
                        }
                    }
                }

                event.setCancelled(true);
            } else {
                player.sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
            }
        } else if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("[helm]")) {
            sign.setLine(0, "\\  ||  /");
            sign.setLine(1, "==      ==");
            sign.setLine(2, "/  ||  \\");
            sign.update(true);
            event.setCancelled(true);
        } else if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equals("\\  ||  /") &&
                   org.bukkit.ChatColor.stripColor(sign.getLine(1)).equals("==      ==") &&
                   org.bukkit.ChatColor.stripColor(sign.getLine(2)).equals("/  ||  \\")) {
            if (playerCraft != null) {
                if (player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".rotate")) {
                    Long time = timeMap.get(player);
                    if (time != null) {
                        long ticksElapsed = (System.currentTimeMillis() - time) / 50;

                        // if the craft should go slower underwater, make time pass more slowly there
                        if (playerCraft.getType().getHalfSpeedUnderwater() && playerCraft.getMinY() < playerCraft.getW().getSeaLevel())
                            ticksElapsed = ticksElapsed >> 1;

                        if (Math.abs(ticksElapsed) < playerCraft.getType().getTickCooldown()) {
                            event.setCancelled(true);
                            return;
                        }
                    }

                    if (MathUtils.playerIsWithinBoundingPolygon(
                            playerCraft.getHitBox(), playerCraft.getMinX(),
                            playerCraft.getMinZ(),
                            MathUtils.bukkit2MovecraftLoc(player.getLocation()))) {
                        if (playerCraft.getType().rotateAtMidpoint()) {
                            MovecraftLocation midpoint = new MovecraftLocation(
                                    (playerCraft.getMaxX() + playerCraft.getMinX()) / 2,
                                    (playerCraft.getMaxY() + playerCraft.getMinY()) / 2,
                                    (playerCraft.getMaxZ() + playerCraft.getMinZ()) / 2);
                            playerCraft.rotate(Rotation.CLOCKWISE, midpoint);
                        } else {
                            playerCraft.rotate(Rotation.CLOCKWISE, MathUtils.bukkit2MovecraftLoc(sign.getLocation()));
                        }

                        timeMap.put(player, System.currentTimeMillis());
                        event.setCancelled(true);
                    }
                } else {
                    player.sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
                }
            }
        } else if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Subcraft Rotate")) {
            // rotate subcraft
            String craftTypeStr = org.bukkit.ChatColor.stripColor(sign.getLine(1));
            if (getCraftTypeFromString(craftTypeStr) != null) {
                if (org.bukkit.ChatColor.stripColor(sign.getLine(2)).equals("") && sign.getLine(3).equals("")) {
                    sign.setLine(2, "_\\ /_");
                    sign.setLine(3, "/ \\");
                    sign.update();
                }

                if (player.hasPermission("movecraft." + craftTypeStr + ".pilot") &&
                    player.hasPermission("movecraft." + craftTypeStr + ".rotate")) {
                    Long time = timeMap.get(player);
                    if (time != null) {
                        long ticksElapsed = (System.currentTimeMillis() - time) / 50;
                        if (Math.abs(ticksElapsed) < getCraftTypeFromString(craftTypeStr).getTickCooldown()) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                    final Location loc = event.getClickedBlock().getLocation();
                    final Craft c = new Craft(getCraftTypeFromString(craftTypeStr), loc.getWorld());
                    MovecraftLocation startPoint = new MovecraftLocation(loc.getBlockX(), loc.getBlockY(),
                                                                         loc.getBlockZ());
                    c.detect(null, player, startPoint);
                    BukkitTask releaseTask = new BukkitRunnable() {

                        @Override public void run() {
                            craftManager.removeCraft(c);
                        }
                    }.runTaskLater(Movecraft.getInstance(), (20 * 5));

                    BukkitTask rotateTask = new BukkitRunnable() {

                        @Override public void run() {
                            c.rotate(Rotation.CLOCKWISE, MathUtils.bukkit2MovecraftLoc(loc), true);
                        }
                    }.runTaskLater(Movecraft.getInstance(), (10));
                    timeMap.put(player, System.currentTimeMillis());
                    event.setCancelled(true);
                } else {
                    player.sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
                }
            }
        } else if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Cruise: OFF")) {
            if (playerCraft != null) {
                if (playerCraft.getType().getCanCruise()) {
                    playerCraft.resetSigns(false, true, true);
                    sign.setLine(0, "Cruise: ON");
                    sign.update(true);

                    playerCraft.setCruiseDirection(Direction.fromSignDirection(sign));
                    playerCraft.setLastCruisUpdate(System.currentTimeMillis());
                    playerCraft.setCruising(true);

                    if (!playerCraft.getType().getMoveEntities()) {
                        craftManager.addReleaseTask(playerCraft);
                    }
                }
            }
        } else if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Ascend: OFF")) {
            if (playerCraft != null) {
                if (playerCraft.getType().getCanCruise()) {
                    playerCraft.resetSigns(true, false, true);
                    sign.setLine(0, "Ascend: ON");
                    sign.update(true);

                    playerCraft.setCruiseDirection(Direction.UP);
                    playerCraft.setLastCruisUpdate(System.currentTimeMillis());
                    playerCraft.setCruising(true);

                    if (!playerCraft.getType().getMoveEntities()) {
                        craftManager.addReleaseTask(playerCraft);
                    }
                }
            }
        } else if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Descend: OFF")) {
            if (playerCraft != null) {
                if (playerCraft.getType().getCanCruise()) {
                    playerCraft.resetSigns(true, true, false);
                    sign.setLine(0, "Descend: ON");
                    sign.update(true);

                    playerCraft.setCruiseDirection(Direction.DOWN);
                    playerCraft.setLastCruisUpdate(System.currentTimeMillis());
                    playerCraft.setCruising(true);

                    if (!playerCraft.getType().getMoveEntities()) {
                        craftManager.addReleaseTask(playerCraft);
                    }
                }
            }
        } else if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Cruise: ON")) {
            if (playerCraft != null)
                if (playerCraft.getType().getCanCruise()) {
                    sign.setLine(0, "Cruise: OFF");
                    sign.update(true);
                    playerCraft.setCruising(false);
                }
        } else if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Ascend: ON")) {
            if (playerCraft != null)
                if (playerCraft.getType().getCanCruise()) {
                    sign.setLine(0, "Ascend: OFF");
                    sign.update(true);
                    playerCraft.setCruising(false);
                }
        } else if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Descend: ON")) {
            if (playerCraft != null)
                if (playerCraft.getType().getCanCruise()) {
                    sign.setLine(0, "Descend: OFF");
                    sign.update(true);
                    playerCraft.setCruising(false);
                }
        } else if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Teleport:")) {
            if (playerCraft != null) {
                String[] numbers = org.bukkit.ChatColor.stripColor(sign.getLine(1)).split(",");
                int tX = Integer.parseInt(numbers[0]);
                int tY = Integer.parseInt(numbers[1]);
                int tZ = Integer.parseInt(numbers[2]);

                if (player.hasPermission("movecraft." +
                                                    playerCraft
                                                                .getType().getCraftName() + ".move")) {
                    if (playerCraft.getType().getCanTeleport()) {
                        int dx = tX - sign.getX();
                        int dy = tY - sign.getY();
                        int dz = tZ - sign.getZ();
                        playerCraft.translate(dx, dy, dz);
                        ;
                    }
                } else {
                    player.sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
                }
            }
        } else if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Release")) {
            if (playerCraft != null) {
                craftManager.removeCraft(playerCraft);
            }
        } else if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Move:")) {
            if (playerCraft != null) {
                Long time = timeMap.get(player);
                if (time != null) {
                    long ticksElapsed = (System.currentTimeMillis() - time) / 50;

                    // if the craft should go slower underwater, make time pass more slowly there
                    if (playerCraft.getType().getHalfSpeedUnderwater() && playerCraft.getMinY() < playerCraft.getW().getSeaLevel())
                        ticksElapsed = ticksElapsed >> 1;

                    if (Math.abs(ticksElapsed) <
                        playerCraft.getType().getTickCooldown()) {
                        event.setCancelled(true);
                        return;
                    }
                }
                String[] numbers = org.bukkit.ChatColor.stripColor(sign.getLine(1)).split(",");
                int dx = Integer.parseInt(numbers[0]);
                int dy = Integer.parseInt(numbers[1]);
                int dz = Integer.parseInt(numbers[2]);
                int maxMove = playerCraft.getType().maxStaticMove();

                if (dx > maxMove) dx = maxMove;
                if (dx < 0 - maxMove) dx = 0 - maxMove;
                if (dy > maxMove) dy = maxMove;
                if (dy < 0 - maxMove) dy = 0 - maxMove;
                if (dz > maxMove) dz = maxMove;
                if (dz < 0 - maxMove) dz = 0 - maxMove;

                if (player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".move")) {
                    if (playerCraft.getType().getCanStaticMove()) {
                        playerCraft.translate(dx, dy, dz);
                        timeMap.put(player, System.currentTimeMillis());
                        playerCraft.setLastCruisUpdate(System.currentTimeMillis());
                    }
                } else {
                    player.sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
                }
            }
        } else if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("RMove:")) {
            if (playerCraft != null) {
                Long time = timeMap.get(player);
                if (time != null) {
                    long ticksElapsed = (System.currentTimeMillis() - time) / 50;

                    // if the craft should go slower underwater, make time pass more slowly there
                    if (playerCraft.getType().getHalfSpeedUnderwater() && playerCraft.getMinY() < playerCraft.getW().getSeaLevel())
                        ticksElapsed = ticksElapsed >> 1;

                    if (Math.abs(ticksElapsed) <
                        playerCraft.getType().getTickCooldown()) {
                        event.setCancelled(true);
                        return;
                    }
                }
                String[] numbers = org.bukkit.ChatColor.stripColor(sign.getLine(1)).split(",");
                int dLeftRight = Integer.parseInt(numbers[0]); // negative = left, positive = right
                int dy = Integer.parseInt(numbers[1]);
                int dBackwardForward = Integer.parseInt(numbers[2]); // negative = backwards, positive = forwards
                int maxMove = playerCraft.getType().maxStaticMove();

                if (dLeftRight > maxMove) dLeftRight = maxMove;
                if (dLeftRight < 0 - maxMove) dLeftRight = 0 - maxMove;
                if (dy > maxMove) dy = maxMove;
                if (dy < 0 - maxMove) dy = 0 - maxMove;
                if (dBackwardForward > maxMove) dBackwardForward = maxMove;
                if (dBackwardForward < 0 - maxMove) dBackwardForward = 0 - maxMove;
                int dx = 0;
                int dz = 0;
                switch (sign.getRawData()) {
                    case 0x3:
                        // North
                        dx = dLeftRight;
                        dz = 0 - dBackwardForward;
                        break;
                    case 0x2:
                        // South
                        dx = 0 - dLeftRight;
                        dz = dBackwardForward;
                        break;
                    case 0x4:
                        // East
                        dx = dBackwardForward;
                        dz = dLeftRight;
                        break;
                    case 0x5:
                        // West
                        dx = 0 - dBackwardForward;
                        dz = 0 - dLeftRight;
                        break;
                }

                if (player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".move")) {
                    if (playerCraft.getType().getCanStaticMove()) {
                        playerCraft.translate(dx, dy, dz);
                        timeMap.put(player, System.currentTimeMillis());
                        playerCraft.setLastCruisUpdate(System.currentTimeMillis());
                    }
                } else {
                    player.sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
                }
            }
        }
    }

    private CraftType getCraftTypeFromString(String s) {
        for (CraftType t : CraftManager.getInstance().getCraftTypes()) {
            if (s.equalsIgnoreCase(t.getCraftName())) {
                return t;
            }
        }

        return null;
    }

    @EventHandler public void onPlayerInteractStick(PlayerInteractEvent event) {
        final CraftManager craftManager = CraftManager.getInstance();
        Craft c = craftManager.getCraftByPlayer(event.getPlayer());
        // if not in command of craft, don't process pilot tool clicks
        if (c == null) return;

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Craft craft = craftManager.getCraftByPlayer(event.getPlayer());

            if (event.getItem() != null && event.getItem().getTypeId() == Settings.PilotTool) {
                event.setCancelled(true);
                if (craft != null) {
                    Long time = timeMap.get(event.getPlayer());
                    if (time != null) {
                        long ticksElapsed = (System.currentTimeMillis() - time) / 50;

                        // if the craft should go slower underwater, make time pass more slowly there
                        if (craft.getType().getHalfSpeedUnderwater() && craft.getMinY() < craft.getW().getSeaLevel())
                            ticksElapsed = ticksElapsed >> 1;

                        if (Math.abs(ticksElapsed) < craft.getType().getTickCooldown()) {
                            return;
                        }
                    }

                    if (MathUtils.playerIsWithinBoundingPolygon(craft.getHitBox(), craft.getMinX(), craft.getMinZ(),
                                                                MathUtils.bukkit2MovecraftLoc(
                                                                        event.getPlayer().getLocation()))) {

                        if (event.getPlayer().hasPermission("movecraft." + craft.getType().getCraftName() + ".move")) {
                            if (craft.getPilotLocked()) {
                                // right click moves up or down if using direct control
                                int DY = 1;
                                if (event.getPlayer().isSneaking()) DY = -1;

                                // See if the player is holding down the mouse button and update the last right
                                // clicked info
                                if (System.currentTimeMillis() - craft.getLastRightClick() < 500) {
                                    craft.setLastDX(0);
                                    craft.setLastDY(DY);
                                    craft.setLastDZ(0);
                                    craft.setKeepMoving(true);
                                } else {
                                    craft.setLastDX(0);
                                    craft.setLastDY(0);
                                    craft.setLastDZ(0);
                                    craft.setKeepMoving(false);
                                }
                                craft.setLastRightClick(System.currentTimeMillis());

                                craft.translate(0, DY, 0);
                                timeMap.put(event.getPlayer(), System.currentTimeMillis());
                                craft.setLastCruisUpdate(System.currentTimeMillis());
                            } else {
                                // Player is onboard craft and right clicking
                                float rotation = (float) Math.PI * event.getPlayer().getLocation().getYaw() / 180f;

                                float nx = -(float) Math.sin(rotation);
                                float nz = (float) Math.cos(rotation);

                                int dx = (Math.abs(nx) >= 0.5 ? 1 : 0) * (int) Math.signum(nx);
                                int dz = (Math.abs(nz) > 0.5 ? 1 : 0) * (int) Math.signum(nz);

                                float p = event.getPlayer().getLocation().getPitch();

                                int dy = -(Math.abs(p) >= 25 ? 1 : 0) * (int) Math.signum(p);

                                if (Math.abs(event.getPlayer().getLocation().getPitch()) >= 75) {
                                    dx = 0;
                                    dz = 0;
                                }

                                // See if the player is holding down the mouse button and update the last right
                                // clicked info
                                if (System.currentTimeMillis() - craft.getLastRightClick() < 500) {
                                    craft.setLastDX(dx);
                                    craft.setLastDY(dy);
                                    craft.setLastDZ(dz);
                                    craft.setKeepMoving(true);
                                } else {
                                    craft.setLastDX(0);
                                    craft.setLastDY(0);
                                    craft.setLastDZ(0);
                                    craft.setKeepMoving(false);
                                }
                                craft.setLastRightClick(System.currentTimeMillis());

                                craft.translate(dx, dy, dz);
                                timeMap.put(event.getPlayer(), System.currentTimeMillis());
                                craft.setLastCruisUpdate(System.currentTimeMillis());
                            }
                        } else {
                            event.getPlayer().sendMessage(
                                    I18nSupport.getInternationalisedString("Insufficient Permissions"));
                        }
                    }
                }
            }
        }
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (event.getItem() != null && event.getItem().getTypeId() == Settings.PilotTool) {
                Craft craft = craftManager.getCraftByPlayer(event.getPlayer());
                if (craft != null) {
                    if (craft.getPilotLocked()) {
                        craft.setPilotLocked(false);
                        event.getPlayer()
                             .sendMessage(I18nSupport.getInternationalisedString("Leaving Direct Control Mode"));
                        event.setCancelled(true);
                        return;
                    } else {
                        if (event.getPlayer().hasPermission("movecraft." + craft.getType().getCraftName() + ".move") &&
                            craft.getType().getCanDirectControl()) {
                            craft.setPilotLocked(true);
                            craft.setPilotLockedX(event.getPlayer().getLocation().getBlockX() + 0.5);
                            craft.setPilotLockedY(event.getPlayer().getLocation().getY());
                            craft.setPilotLockedZ(event.getPlayer().getLocation().getBlockZ() + 0.5);
                            event.getPlayer()
                                 .sendMessage(I18nSupport.getInternationalisedString("Entering Direct Control Mode"));
                            event.setCancelled(true);
                            return;
                        } else {
                            event.getPlayer()
                                 .sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
                        }
                    }
                }
            }
        }
    }
}
