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
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.Permissions;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.BlockUtils;
import net.countercraft.movecraft.utils.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Set;

public class BlockListener implements Listener {
    private final Movecraft plugin;
    private final Settings settings;
    private final I18nSupport i18n;
    private final CraftManager craftManager;

    public BlockListener(final Movecraft plugin, final Settings settings, final I18nSupport i18n, final CraftManager craftManager) {
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
    }

    @SuppressWarnings("unused")
    @EventHandler public void onStopSnowForming(final BlockFormEvent event) {
        final BlockState info = event.getNewState();
        if (info.getType() == Material.SNOW) {
            final Block below = event.getBlock().getRelative(BlockFace.DOWN);
            final BlockVec mloc = new BlockVec(below.getX(), below.getY(), below.getZ());
            final boolean blockInCraft = false;
            final Set<Craft> crafts = this.craftManager.getCraftsInWorld(info.getWorld());
            for (final Craft craft : crafts) {
                if (craft != null && craft.isCraftBlock(mloc)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockBreak(final BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (this.settings.ProtectPilotedCrafts) {
            final BlockVec mloc = BlockVec.from(event.getBlock().getLocation());
            boolean blockInCraft = false;
            for (final Craft craft : this.craftManager.getCraftsInWorld(event.getBlock().getWorld())) {
                if (craft != null) {
                    for (final BlockVec tloc : craft.getBlockList()) {
                        if (tloc.equals(mloc)) blockInCraft = true;
                    }
                }
            }
            if (blockInCraft) {
                event.getPlayer().sendMessage(this.i18n.get("BLOCK IS PART OF A PILOTED CRAFT"));
                event.setCancelled(true);
            }
        }
    }

    // prevent items from dropping from moving crafts
    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.HIGHEST) public void onItemSpawn(final ItemSpawnEvent event) {
        if (event.isCancelled()) {
            return;
        }
        for (final Craft tcraft : this.craftManager.getCraftsInWorld(event.getLocation().getWorld())) {
            if ((!tcraft.isNotProcessing()) && MathUtils
                    .playerIsWithinBoundingPolygon(tcraft.getHitBox(), tcraft.getMinX(), tcraft.getMinZ(),
                                                   BlockVec.from(event.getLocation()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // prevent water from spreading on moving crafts
    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockFromTo(final BlockFromToEvent event) {
        if (event.isCancelled()) {
            return;
        }
        final Block block = event.getToBlock();
        if (block.getType() == Material.WATER) {
            for (final Craft craft : this.craftManager.getCraftsInWorld(block.getWorld())) {
                if ((!craft.isNotProcessing()) && MathUtils
                        .playerIsWithinBoundingPolygon(craft.getHitBox(), craft.getMinX(), craft.getMinZ(),
                                                       BlockVec.from(block.getLocation()))) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // prevent fragile items from dropping on moving crafts
    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.HIGHEST) public void onPhysics(final BlockPhysicsEvent event) {
        if (event.isCancelled()) {
            return;
        }
        final Block block = event.getBlock();
        for (final Craft tcraft : this.craftManager.getCraftsInWorld(block.getWorld())) {
            if ((!tcraft.isNotProcessing()) && MathUtils
                    .playerIsWithinBoundingPolygon(tcraft.getHitBox(), tcraft.getMinX(), tcraft.getMinZ(),
                                                   BlockVec.from(block.getLocation()))) {
                if (BlockUtils.FRAGILE_BLOCKS.contains(block.getType())) {
//						BlockFace face = ((Attachable) block).getAttachedFace();
//					    if (!event.getBlock().getRelative(face).getType().isSolid()) {
                    event.setCancelled(true);
                    return;
//					    }
                }
            }
        }
    }

    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.NORMAL) public void onSignChange(final SignChangeEvent event) {
        final Player p = event.getPlayer();
        if (p == null) return;

        final String firstLine = ChatColor.stripColor(event.getLine(0));

        // did the player try to create a craft command sign?
        if (this.craftManager.getCraftTypeFromString(firstLine).isPresent()) {
            if (!this.settings.RequireCreatePerm) {
                return;
            }
            if (!p.hasPermission(Permissions.CREATE(firstLine))) {
                p.sendMessage(this.i18n.get("Insufficient Permissions"));
                event.setCancelled(true);
            }
        }
        if (firstLine.equalsIgnoreCase("Cruise: OFF") || firstLine.equalsIgnoreCase("Cruise: ON")) {
            if (!p.hasPermission(Permissions.CREATE_CRUISE_SIGN) && this.settings.RequireCreatePerm) {
                p.sendMessage(this.i18n.get("Insufficient Permissions"));
                event.setCancelled(true);
            }
        }
        if (firstLine.equalsIgnoreCase("Crew:")) {
            final String crewName = org.bukkit.ChatColor.stripColor(event.getLine(1));
            if (!p.getName().equalsIgnoreCase(crewName)) {
                p.sendMessage(this.i18n.get("You can only create a Crew: sign for yourself"));
                event.setLine(1, p.getName());
//				event.setCancelled(true);
            }
        }
    }

    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.LOW) public void onBlockIgnite(final BlockIgniteEvent event) {
        if (!this.settings.FireballPenetration) return;
        if (event.isCancelled()) return;
        // replace blocks with fire occasionally, to prevent fast craft from simply ignoring fire
        if (event.getCause() == BlockIgniteEvent.IgniteCause.FIREBALL) {
            Block testBlock = event.getBlock().getRelative(-1, 0, 0);
            if (!testBlock.getType().isBurnable()) testBlock = event.getBlock().getRelative(1, 0, 0);
            if (!testBlock.getType().isBurnable()) testBlock = event.getBlock().getRelative(0, 0, -1);
            if (!testBlock.getType().isBurnable()) testBlock = event.getBlock().getRelative(0, 0, 1);

            if (testBlock.getType().isBurnable()) {
                boolean isBurnAllowed = true;
                // check to see if fire spread is allowed, don't check if worldguard integration is not enabled
                if (this.plugin.getWorldGuardPlugin() != null &&
                    (this.settings.WorldGuardBlockMoveOnBuildPerm || this.settings.WorldGuardBlockSinkOnPVPPerm)) {
                    final ApplicableRegionSet set = this.plugin.getWorldGuardPlugin().getRegionManager(testBlock.getWorld())
                                                               .getApplicableRegions(testBlock.getLocation());
                    if (!set.allows(DefaultFlag.FIRE_SPREAD)) {
                        isBurnAllowed = false;
                    }
                }
                if (isBurnAllowed) testBlock.setType(org.bukkit.Material.AIR);
            }
        }
    }

	/*@EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPhysicsEvent(BlockPhysicsEvent e) {
		Location loc=e.getBlock().getLocation();
		if(MapUpdateManager.getInstance().getProtectedBlocks().contains(loc))
			e.setCancelled(true);
	}
	
	//@EventHandler(priority = EventPriority.HIGHEST)
	public void onBlockRedstoneEvent(BlockRedstoneEvent e) {
		Location loc=e.getBlock().getLocation();
		if(MapUpdateManager.getInstance().getProtectedBlocks().contains(loc))
			e.setNewCurrent(0);
	}*/

    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.NORMAL) public void explodeEvent(final EntityExplodeEvent event) {
        // Remove any blocks from the list that were adjacent to water, to prevent spillage
        if (!this.settings.DisableSpillProtection) {
            final Iterator<Block> iter = event.blockList().iterator();

            while (iter.hasNext()) {
                final Block block = iter.next();
                boolean isNearWater = false;
                if (block.getY() > block.getWorld().getSeaLevel()) for (int mx = -1; mx <= 1; mx++) {
                    for (int mz = -1; mz <= 1; mz++) {
                        for (int my = 0; my <= 1; my++) {
                            if (block.getRelative(mx, my, mz).getType() == Material.STATIONARY_WATER ||
                                block.getRelative(mx, my, mz).getType() == Material.WATER) isNearWater = true;
                        }
                    }
                }
                if (isNearWater) {
                    iter.remove();
                }
            }
        }

        if (event.getEntity() == null) return;
        for (final Player p : event.getEntity().getWorld().getPlayers()) {
            final org.bukkit.entity.Entity tnt = event.getEntity();

            if (event.getEntityType() == EntityType.PRIMED_TNT && this.settings.TracerRateTicks != 0) {
                final long minDistSquared = 60 * 60;
                long maxDistSquared = Bukkit.getServer().getViewDistance() * 16;
                maxDistSquared = maxDistSquared - 16;
                maxDistSquared = maxDistSquared * maxDistSquared;
                // is the TNT within the view distance (rendered world) of the player, yet further than 60 blocks?
                if (p.getLocation().distanceSquared(tnt.getLocation()) < maxDistSquared &&
                    p.getLocation().distanceSquared(tnt.getLocation()) >=
                    minDistSquared) {  // we use squared because its faster
                    final Location loc = tnt.getLocation();
                    final Player fp = p;
                    final World fw = event.getEntity().getWorld();
                    // then make a glowstone to look like the explosion, place it a little later so it isn't right in
                    // the middle of the volley
                    final BukkitTask placeCobweb = new BukkitRunnable() {
                        @Override public void run() {
                            fp.sendBlockChange(loc, 89, (byte) 0);
                        }
                    }.runTaskLater(this.plugin, 5);
                    // then remove it
                    final BukkitTask removeCobweb = new BukkitRunnable() {
                        @Override public void run() {
                            fp.sendBlockChange(loc, 0, (byte) 0);
                        }
                    }.runTaskLater(this.plugin, 160);
                }
            }
        }
    }
}
