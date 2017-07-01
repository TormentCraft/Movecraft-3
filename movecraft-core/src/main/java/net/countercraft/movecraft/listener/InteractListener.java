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

import com.alexknvl.shipcraft.math.BlockVec;
import com.alexknvl.shipcraft.math.RotationXZ;
import com.alexknvl.shipcraft.math.Direction;
import net.countercraft.movecraft.async.AsyncManager;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.CraftType;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.MathUtils;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public class InteractListener implements Listener {
    private final Plugin plugin;
    private final Settings settings;
    private final I18nSupport i18n;
    private final CraftManager craftManager;
    private final AsyncManager asyncManager;
    private final Map<Player, Long> timeMap = new HashMap<>();

    public InteractListener(final Plugin plugin, final Settings settings, final I18nSupport i18n, final CraftManager craftManager,
                            final AsyncManager asyncManager)
    {
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
        this.asyncManager = asyncManager;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    @EventHandler public void onPlayerInteract(final PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            final Material clickedMaterial = event.getClickedBlock().getType();
            if (clickedMaterial == Material.SIGN_POST || clickedMaterial == Material.WALL_SIGN) {
                final Sign sign = (Sign) event.getClickedBlock().getState();
                final String firstLine = ChatColor.stripColor(sign.getLine(0));

                if (firstLine == null) {
                    return;
                }

                if (firstLine.equalsIgnoreCase("Remote Sign")) {
                    final BlockVec sourceLocation = BlockVec.from(event.getClickedBlock().getLocation());
                    Craft foundCraft = null;
                    for (final Craft tcraft : this.craftManager.getCraftsInWorld(event.getClickedBlock().getWorld())) {
                        if (MathUtils.playerIsWithinBoundingPolygon(tcraft.getHitBox(), tcraft.getMinX(),
                                                                    tcraft.getMinZ(), sourceLocation)) {
                            // don't use a craft with a null player. This is mostly to avoid trying to use subcrafts
                            if (this.craftManager.getPlayerFromCraft(tcraft) != null) foundCraft = tcraft;
                        }
                    }

                    if (foundCraft == null) {
                        event.getPlayer()
                             .sendMessage(this.i18n.get("ERROR: Remote Sign must be a part of a piloted craft!"));
                        return;
                    }

                    if (!foundCraft.getType().allowRemoteSign()) {
                        event.getPlayer().sendMessage(this.i18n.get("ERROR: Remote Signs not allowed on this craft!"));
                        return;
                    }

                    final String targetText = org.bukkit.ChatColor.stripColor(sign.getLine(1));
                    BlockVec foundLoc = null;
                    for (final BlockVec tloc : foundCraft.getBlockList()) {
                        final Block tb = event.getClickedBlock().getWorld().getBlockAt(tloc.x(), tloc.y(), tloc.z());
                        if (tb.getType() == Material.SIGN_POST || tb.getType() == Material.WALL_SIGN) {
                            final Sign ts = (Sign) tb.getState();
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
                        event.getPlayer().sendMessage(this.i18n.get("ERROR: Could not find target sign!"));
                        return;
                    }

                    final Block newBlock = event.getClickedBlock().getWorld().getBlockAt(foundLoc.x(), foundLoc.y(), foundLoc.z());
                    final PlayerInteractEvent newEvent = new PlayerInteractEvent(event.getPlayer(), event.getAction(),
                                                                                 event.getItem(), newBlock,
                                                                                 event.getBlockFace());
                    this.onPlayerInteract(newEvent);
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            final Material m = event.getClickedBlock().getType();
            if (m == Material.SIGN_POST || m == Material.WALL_SIGN) {
                this.onSignRightClick(event);
            }
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            final Material m = event.getClickedBlock().getType();
            if (m == Material.SIGN_POST || m == Material.WALL_SIGN) {
                if (event.getClickedBlock() == null) {
                    return;
                }
                final Sign sign = (Sign) event.getClickedBlock().getState();
                final String signText = org.bukkit.ChatColor.stripColor(sign.getLine(0));

                if (signText == null) {
                    return;
                }

                if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equals("\\  ||  /") &&
                    org.bukkit.ChatColor.stripColor(sign.getLine(1)).equals("==      ==") &&
                    org.bukkit.ChatColor.stripColor(sign.getLine(2)).equals("/  ||  \\")) {
                    final Craft playerCraft = this.craftManager.getCraftByPlayer(event.getPlayer());
                    if (playerCraft != null) {
                        if (event.getPlayer()
                                 .hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".rotate")) {

                            final Long time = this.timeMap.get(event.getPlayer());
                            if (time != null) {
                                long ticksElapsed = (System.currentTimeMillis() - time) / 50;

                                // if the craft should go slower underwater, make time pass more slowly there
                                if (playerCraft.getType().getHalfSpeedUnderwater() &&
                                    playerCraft.getMinY() < playerCraft.getWorld().getSeaLevel())
                                    ticksElapsed = ticksElapsed >> 1;

                                if (Math.abs(ticksElapsed) < playerCraft.getType().getTickCooldown()) {
                                    event.setCancelled(true);
                                    return;
                                }
                            }

                            if (MathUtils.playerIsWithinBoundingPolygon(playerCraft.getHitBox(), playerCraft.getMinX(),
                                                                        playerCraft.getMinZ(), BlockVec.from(event.getPlayer()
                                                                                                          .getLocation()))) {
                                if (playerCraft.getType().rotateAtMidpoint()) {
                                    final BlockVec midpoint = new BlockVec(
                                            (playerCraft.getMaxX() + playerCraft.getMinX()) / 2,
                                            (playerCraft.getMaxY() + playerCraft.getMinY()) / 2,
                                            (playerCraft.getMaxZ() + playerCraft.getMinZ()) / 2);
                                    this.asyncManager.rotate(playerCraft, RotationXZ.CCW$.MODULE$, midpoint);
                                } else {
                                    this.asyncManager.rotate(playerCraft, RotationXZ.CCW$.MODULE$,
                                                             BlockVec.from(sign.getLocation()));
                                }

                                this.timeMap.put(event.getPlayer(), System.currentTimeMillis());
                                event.setCancelled(true);
                            }
                        } else {
                            event.getPlayer().sendMessage(this.i18n.get("Insufficient Permissions"));
                        }
                    }
                }
                if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Subcraft Rotate")) {
                    // rotate subcraft
                    final String craftTypeStr = org.bukkit.ChatColor.stripColor(sign.getLine(1));
                    if (this.getCraftTypeFromString(craftTypeStr) != null) {
                        if (ChatColor.stripColor(sign.getLine(2)).isEmpty() &&
                            ChatColor.stripColor(sign.getLine(3)).isEmpty()) {
                            sign.setLine(2, "_\\ /_");
                            sign.setLine(3, "/ \\");
                            sign.update();
                        }

                        if (event.getPlayer().hasPermission("movecraft." + craftTypeStr + ".pilot") &&
                            event.getPlayer().hasPermission("movecraft." + craftTypeStr + ".rotate")) {
                            final Long time = this.timeMap.get(event.getPlayer());
                            if (time != null) {
                                final long ticksElapsed = (System.currentTimeMillis() - time) / 50;
                                if (Math.abs(ticksElapsed) < this.getCraftTypeFromString(craftTypeStr).getTickCooldown()) {
                                    event.setCancelled(true);
                                    return;
                                }
                            }
                            final Location loc = event.getClickedBlock().getLocation();
                            final Craft c = new Craft(this.getCraftTypeFromString(craftTypeStr), loc.getWorld());
                            final BlockVec startPoint = new BlockVec(loc.getBlockX(), loc.getBlockY(),
                                                                     loc.getBlockZ());
                            this.asyncManager.detect(c, null, event.getPlayer(), startPoint);
                            final BukkitTask releaseTask = new BukkitRunnable() {

                                @Override public void run() {
                                    InteractListener.this.craftManager.removeCraft(c);
                                }
                            }.runTaskLater(this.plugin, (20 * 5));

                            final BukkitTask rotateTask = new BukkitRunnable() {

                                @Override public void run() {
                                    InteractListener.this.asyncManager
                                            .rotate(c, RotationXZ.CCW$.MODULE$, BlockVec.from(loc),
                                                    true);
                                }
                            }.runTaskLater(this.plugin, (10));
                            this.timeMap.put(event.getPlayer(), System.currentTimeMillis());
                            event.setCancelled(true);
                        } else {
                            event.getPlayer().sendMessage(this.i18n.get("Insufficient Permissions"));
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unused")
    private void onSignRightClick(final PlayerInteractEvent event) {
        final Sign sign = (Sign) event.getClickedBlock().getState();
        final String firstLine = ChatColor.stripColor(sign.getLine(0));

        if (firstLine == null) {
            return;
        }

        // don't process commands if this is a pilot tool click, do that below
        final Player player = event.getPlayer();
        final Craft playerCraft = this.craftManager.getCraftByPlayer(player);
        if (event.getItem() != null && event.getItem().getTypeId() == this.settings.PilotTool) {
            if (playerCraft != null) return;
        }

        final CraftType craftType = this.getCraftTypeFromString(ChatColor.stripColor(sign.getLine(0)));
        if (craftType != null) {
            // Valid sign prompt for ship command.
            if (player.hasPermission("movecraft." + firstLine + ".pilot")) {
                // Attempt to run detection
                final Location loc = event.getClickedBlock().getLocation();
                final BlockVec startPoint = new BlockVec(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                final Craft c = new Craft(craftType, loc.getWorld());

                if (c.getType().getCruiseOnPilot()) {
                    this.asyncManager.detect(c, null, player, startPoint);
                    c.setCruiseDirection(Direction.fromSignDirection(sign));
                    c.setLastCruiseUpdate(System.currentTimeMillis());
                    c.setCruising(true);
                    final BukkitTask releaseTask = new BukkitRunnable() {

                        @Override public void run() {
                            InteractListener.this.craftManager.removeCraft(c);
                        }
                    }.runTaskLater(this.plugin, (20 * 15));
//					CraftManager.getInstance().getReleaseEvents().put( event.getPlayer(), releaseTask );
                } else {
                    if (playerCraft == null) {
                        this.asyncManager.detect(c, player, player, startPoint);
                    } else {
                        if (playerCraft.isNotProcessing()) {
                            this.craftManager.removeCraft(playerCraft);
                            this.asyncManager.detect(c, player, player, startPoint);
                        }
                    }
                }

                event.setCancelled(true);
            } else {
                player.sendMessage(this.i18n.get("Insufficient Permissions"));
            }
        } else if (firstLine.equalsIgnoreCase("[helm]")) {
            sign.setLine(0, "\\  ||  /");
            sign.setLine(1, "==      ==");
            sign.setLine(2, "/  ||  \\");
            sign.update(true);
            event.setCancelled(true);
        } else if (firstLine.equals("\\  ||  /") &&
                   org.bukkit.ChatColor.stripColor(sign.getLine(1)).equals("==      ==") &&
                   org.bukkit.ChatColor.stripColor(sign.getLine(2)).equals("/  ||  \\")) {
            if (playerCraft != null) {
                if (player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".rotate")) {
                    final Long time = this.timeMap.get(player);
                    if (time != null) {
                        long ticksElapsed = (System.currentTimeMillis() - time) / 50;

                        // if the craft should go slower underwater, make time pass more slowly there
                        if (playerCraft.getType().getHalfSpeedUnderwater() &&
                            playerCraft.getMinY() < playerCraft.getWorld().getSeaLevel()) ticksElapsed = ticksElapsed >> 1;

                        if (Math.abs(ticksElapsed) < playerCraft.getType().getTickCooldown()) {
                            event.setCancelled(true);
                            return;
                        }
                    }

                    if (MathUtils.playerIsWithinBoundingPolygon(playerCraft.getHitBox(), playerCraft.getMinX(),
                                                                playerCraft.getMinZ(),
                                                                BlockVec.from(player.getLocation()))) {
                        if (playerCraft.getType().rotateAtMidpoint()) {
                            final BlockVec midpoint = new BlockVec(
                                    (playerCraft.getMaxX() + playerCraft.getMinX()) / 2,
                                    (playerCraft.getMaxY() + playerCraft.getMinY()) / 2,
                                    (playerCraft.getMaxZ() + playerCraft.getMinZ()) / 2);
                            this.asyncManager.rotate(playerCraft, RotationXZ.CW$.MODULE$, midpoint);
                        } else {
                            this.asyncManager.rotate(playerCraft, RotationXZ.CW$.MODULE$,
                                                     BlockVec.from(sign.getLocation()));
                        }

                        this.timeMap.put(player, System.currentTimeMillis());
                        event.setCancelled(true);
                    }
                } else {
                    player.sendMessage(this.i18n.get("Insufficient Permissions"));
                }
            }
        } else if (firstLine.equalsIgnoreCase("Subcraft Rotate")) {
            // rotate subcraft
            final String subcraftTypeName = org.bukkit.ChatColor.stripColor(sign.getLine(1));
            final CraftType subcraftType = this.getCraftTypeFromString(subcraftTypeName);
            if (subcraftType != null) {
                if (ChatColor.stripColor(sign.getLine(2)).isEmpty() && sign.getLine(3).isEmpty()) {
                    sign.setLine(2, "_\\ /_");
                    sign.setLine(3, "/ \\");
                    sign.update();
                }

                if (player.hasPermission("movecraft." + subcraftTypeName + ".pilot") &&
                    player.hasPermission("movecraft." + subcraftTypeName + ".rotate")) {
                    final Long time = this.timeMap.get(player);
                    if (time != null) {
                        final long ticksElapsed = (System.currentTimeMillis() - time) / 50;
                        if (Math.abs(ticksElapsed) < subcraftType.getTickCooldown()) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                    final Location loc = event.getClickedBlock().getLocation();
                    final Craft c = new Craft(subcraftType, loc.getWorld());
                    final BlockVec startPoint = new BlockVec(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    this.asyncManager.detect(c, null, player, startPoint);
                    final BukkitTask releaseTask = new BukkitRunnable() {

                        @Override public void run() {
                            InteractListener.this.craftManager.removeCraft(c);
                        }
                    }.runTaskLater(this.plugin, (20 * 5));

                    final BukkitTask rotateTask = new BukkitRunnable() {

                        @Override public void run() {
                            InteractListener.this.asyncManager
                                    .rotate(c, RotationXZ.CW$.MODULE$, BlockVec.from(loc), true);
                        }
                    }.runTaskLater(this.plugin, (10));
                    this.timeMap.put(player, System.currentTimeMillis());
                    event.setCancelled(true);
                } else {
                    player.sendMessage(this.i18n.get("Insufficient Permissions"));
                }
            }
        } else if (firstLine.equalsIgnoreCase("Cruise: OFF")) {
            if (playerCraft != null) {
                if (playerCraft.getType().getCanCruise()) {
                    playerCraft.resetSigns(false, true, true);
                    sign.setLine(0, "Cruise: ON");
                    sign.update(true);

                    playerCraft.setCruiseDirection(Direction.fromSignDirection(sign));
                    playerCraft.setLastCruiseUpdate(System.currentTimeMillis());
                    playerCraft.setCruising(true);

                    if (!playerCraft.getType().getMoveEntities()) {
                        this.craftManager.addReleaseTask(playerCraft);
                    }
                }
            }
        } else if (firstLine.equalsIgnoreCase("Ascend: OFF")) {
            if (playerCraft != null) {
                if (playerCraft.getType().getCanCruise()) {
                    playerCraft.resetSigns(true, false, true);
                    sign.setLine(0, "Ascend: ON");
                    sign.update(true);

                    playerCraft.setCruiseDirection(Direction.Up());
                    playerCraft.setLastCruiseUpdate(System.currentTimeMillis());
                    playerCraft.setCruising(true);

                    if (!playerCraft.getType().getMoveEntities()) {
                        this.craftManager.addReleaseTask(playerCraft);
                    }
                }
            }
        } else if (firstLine.equalsIgnoreCase("Descend: OFF")) {
            if (playerCraft != null) {
                if (playerCraft.getType().getCanCruise()) {
                    playerCraft.resetSigns(true, true, false);
                    sign.setLine(0, "Descend: ON");
                    sign.update(true);

                    playerCraft.setCruiseDirection(Direction.Down());
                    playerCraft.setLastCruiseUpdate(System.currentTimeMillis());
                    playerCraft.setCruising(true);

                    if (!playerCraft.getType().getMoveEntities()) {
                        this.craftManager.addReleaseTask(playerCraft);
                    }
                }
            }
        } else if (firstLine.equalsIgnoreCase("Cruise: ON")) {
            if (playerCraft != null) if (playerCraft.getType().getCanCruise()) {
                sign.setLine(0, "Cruise: OFF");
                sign.update(true);
                playerCraft.setCruising(false);
            }
        } else if (firstLine.equalsIgnoreCase("Ascend: ON")) {
            if (playerCraft != null) if (playerCraft.getType().getCanCruise()) {
                sign.setLine(0, "Ascend: OFF");
                sign.update(true);
                playerCraft.setCruising(false);
            }
        } else if (firstLine.equalsIgnoreCase("Descend: ON")) {
            if (playerCraft != null) if (playerCraft.getType().getCanCruise()) {
                sign.setLine(0, "Descend: OFF");
                sign.update(true);
                playerCraft.setCruising(false);
            }
        } else if (firstLine.equalsIgnoreCase("Teleport:")) {
            if (playerCraft != null) {
                final String[] numbers = org.bukkit.ChatColor.stripColor(sign.getLine(1)).split(",");
                final int tX = Integer.parseInt(numbers[0]);
                final int tY = Integer.parseInt(numbers[1]);
                final int tZ = Integer.parseInt(numbers[2]);

                if (player.hasPermission("movecraft." +
                                         playerCraft.getType().getCraftName() + ".move")) {
                    if (playerCraft.getType().getCanTeleport()) {
                        final int dx = tX - sign.getX();
                        final int dy = tY - sign.getY();
                        final int dz = tZ - sign.getZ();
                        this.asyncManager.translate(playerCraft, dx, dy, dz);
                        ;
                    }
                } else {
                    player.sendMessage(this.i18n.get("Insufficient Permissions"));
                }
            }
        } else if (firstLine.equalsIgnoreCase("Release")) {
            if (playerCraft != null) {
                this.craftManager.removeCraft(playerCraft);
            }
        } else if (firstLine.equalsIgnoreCase("Move:")) {
            if (playerCraft != null) {
                final Long time = this.timeMap.get(player);
                if (time != null) {
                    long ticksElapsed = (System.currentTimeMillis() - time) / 50;

                    // if the craft should go slower underwater, make time pass more slowly there
                    if (playerCraft.getType().getHalfSpeedUnderwater() &&
                        playerCraft.getMinY() < playerCraft.getWorld().getSeaLevel()) ticksElapsed = ticksElapsed >> 1;

                    if (Math.abs(ticksElapsed) < playerCraft.getType().getTickCooldown()) {
                        event.setCancelled(true);
                        return;
                    }
                }
                final String[] numbers = org.bukkit.ChatColor.stripColor(sign.getLine(1)).split(",");
                int dx = Integer.parseInt(numbers[0]);
                int dy = Integer.parseInt(numbers[1]);
                int dz = Integer.parseInt(numbers[2]);
                final int maxMove = playerCraft.getType().maxStaticMove();

                if (dx > maxMove) dx = maxMove;
                if (dx < 0 - maxMove) dx = 0 - maxMove;
                if (dy > maxMove) dy = maxMove;
                if (dy < 0 - maxMove) dy = 0 - maxMove;
                if (dz > maxMove) dz = maxMove;
                if (dz < 0 - maxMove) dz = 0 - maxMove;

                if (player.hasPermission("movecraft." + playerCraft.getType().getCraftName() + ".move")) {
                    if (playerCraft.getType().getCanStaticMove()) {
                        this.asyncManager.translate(playerCraft, dx, dy, dz);
                        this.timeMap.put(player, System.currentTimeMillis());
                        playerCraft.setLastCruiseUpdate(System.currentTimeMillis());
                    }
                } else {
                    player.sendMessage(this.i18n.get("Insufficient Permissions"));
                }
            }
        } else if (firstLine.equalsIgnoreCase("RMove:")) {
            if (playerCraft != null) {
                final Long time = this.timeMap.get(player);
                if (time != null) {
                    long ticksElapsed = (System.currentTimeMillis() - time) / 50;

                    // if the craft should go slower underwater, make time pass more slowly there
                    if (playerCraft.getType().getHalfSpeedUnderwater() &&
                        playerCraft.getMinY() < playerCraft.getWorld().getSeaLevel()) ticksElapsed = ticksElapsed >> 1;

                    if (Math.abs(ticksElapsed) < playerCraft.getType().getTickCooldown()) {
                        event.setCancelled(true);
                        return;
                    }
                }
                final String[] numbers = org.bukkit.ChatColor.stripColor(sign.getLine(1)).split(",");
                int dLeftRight = Integer.parseInt(numbers[0]); // negative = left, positive = right
                int dy = Integer.parseInt(numbers[1]);
                int dBackwardForward = Integer.parseInt(numbers[2]); // negative = backwards, positive = forwards
                final int maxMove = playerCraft.getType().maxStaticMove();

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
                        this.asyncManager.translate(playerCraft, dx, dy, dz);
                        this.timeMap.put(player, System.currentTimeMillis());
                        playerCraft.setLastCruiseUpdate(System.currentTimeMillis());
                    }
                } else {
                    player.sendMessage(this.i18n.get("Insufficient Permissions"));
                }
            }
        }
    }

    private CraftType getCraftTypeFromString(final String s) {
        for (final CraftType t : this.craftManager.getCraftTypes()) {
            if (s.equalsIgnoreCase(t.getCraftName())) {
                return t;
            }
        }

        return null;
    }

    @SuppressWarnings("unused")
    @EventHandler public void onPlayerInteractStick(final PlayerInteractEvent event) {
        final Craft craft = this.craftManager.getCraftByPlayer(event.getPlayer());
        // if not in command of craft, don't process pilot tool clicks
        if (craft == null) return;

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getItem() != null && event.getItem().getTypeId() == this.settings.PilotTool) {
                event.setCancelled(true);
                final Long time = this.timeMap.get(event.getPlayer());
                if (time != null) {
                    long ticksElapsed = (System.currentTimeMillis() - time) / 50;

                    // if the craft should go slower underwater, make time pass more slowly there
                    if (craft.getType().getHalfSpeedUnderwater() && craft.getMinY() < craft.getWorld().getSeaLevel())
                        ticksElapsed = ticksElapsed >> 1;

                    if (Math.abs(ticksElapsed) < craft.getType().getTickCooldown()) {
                        return;
                    }
                }

                if (MathUtils.playerIsWithinBoundingPolygon(craft.getHitBox(), craft.getMinX(), craft.getMinZ(),
                                                            BlockVec.from(event.getPlayer().getLocation()))) {

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

                            this.asyncManager.translate(craft, 0, DY, 0);
                            this.timeMap.put(event.getPlayer(), System.currentTimeMillis());
                            craft.setLastCruiseUpdate(System.currentTimeMillis());
                        } else {
                            // Player is onboard craft and right clicking
                            final float rotation = (float) Math.PI * event.getPlayer().getLocation().getYaw() / 180f;

                            final float nx = -(float) Math.sin(rotation);
                            final float nz = (float) Math.cos(rotation);

                            int dx = (Math.abs(nx) >= 0.5 ? 1 : 0) * (int) Math.signum(nx);
                            int dz = (Math.abs(nz) > 0.5 ? 1 : 0) * (int) Math.signum(nz);

                            final float p = event.getPlayer().getLocation().getPitch();

                            final int dy = -(Math.abs(p) >= 25 ? 1 : 0) * (int) Math.signum(p);

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

                            this.asyncManager.translate(craft, dx, dy, dz);
                            this.timeMap.put(event.getPlayer(), System.currentTimeMillis());
                            craft.setLastCruiseUpdate(System.currentTimeMillis());
                        }
                    } else {
                        event.getPlayer().sendMessage(this.i18n.get("Insufficient Permissions"));
                    }
                }
            }
        }
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (event.getItem() != null && event.getItem().getTypeId() == this.settings.PilotTool) {
                if (craft.getPilotLocked()) {
                    craft.setPilotLocked(false);
                    event.getPlayer().sendMessage(this.i18n.get("Leaving Direct Control Mode"));
                    event.setCancelled(true);
                } else {
                    if (event.getPlayer().hasPermission("movecraft." + craft.getType().getCraftName() + ".move") &&
                        craft.getType().getCanDirectControl()) {
                        craft.setPilotLocked(true);
                        craft.setPilotLockedX(event.getPlayer().getLocation().getBlockX() + 0.5);
                        craft.setPilotLockedY(event.getPlayer().getLocation().getY());
                        craft.setPilotLockedZ(event.getPlayer().getLocation().getBlockZ() + 0.5);
                        event.getPlayer().sendMessage(this.i18n.get("Entering Direct Control Mode"));
                        event.setCancelled(true);
                    } else {
                        event.getPlayer().sendMessage(this.i18n.get("Insufficient Permissions"));
                    }
                }
            }
        }
    }
}
