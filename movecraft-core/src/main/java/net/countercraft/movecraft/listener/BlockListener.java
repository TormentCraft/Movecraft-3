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

import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.api.BlockVec;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.CraftType;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.MathUtils;
import org.bukkit.Bukkit;
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

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

public class BlockListener implements Listener {
    private final Movecraft plugin;
    private final Settings settings;
    private final I18nSupport i18n;
    private final CraftManager craftManager;

    public BlockListener(Movecraft plugin, Settings settings, I18nSupport i18n, CraftManager craftManager) {
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
    }

    @SuppressWarnings("unused")
    @EventHandler public void onStopSnowForming(BlockFormEvent event) {
        BlockState info = event.getNewState();
        if (info.getType() == Material.SNOW) {
            Block below = event.getBlock().getRelative(BlockFace.DOWN);
            BlockVec mloc = new BlockVec(below.getX(), below.getY(), below.getZ());
            boolean blockInCraft = false;
            Set<Craft> crafts = craftManager.getCraftsInWorld(info.getWorld());
            for (Craft craft : crafts) {
                if (craft != null && craft.isCraftBlock(mloc)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockBreak(final BlockBreakEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (settings.ProtectPilotedCrafts) {
            BlockVec mloc = MathUtils.bukkit2MovecraftLoc(e.getBlock().getLocation());
            boolean blockInCraft = false;
            for (Craft craft : craftManager.getCraftsInWorld(e.getBlock().getWorld())) {
                if (craft != null) {
                    for (BlockVec tloc : craft.getBlockList()) {
                        if (tloc.x == mloc.x && tloc.y == mloc.y && tloc.z == mloc.z) blockInCraft = true;
                    }
                }
            }
            if (blockInCraft) {
                e.getPlayer().sendMessage(i18n.get("BLOCK IS PART OF A PILOTED CRAFT"));
                e.setCancelled(true);
            }
        }
    }

    // prevent items from dropping from moving crafts
    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.HIGHEST) public void onItemSpawn(final ItemSpawnEvent e) {
        if (e.isCancelled()) {
            return;
        }
        for (Craft tcraft : craftManager.getCraftsInWorld(e.getLocation().getWorld())) {
            if ((!tcraft.isNotProcessing()) && MathUtils
                    .playerIsWithinBoundingPolygon(tcraft.getHitBox(), tcraft.getMinX(), tcraft.getMinZ(),
                                                   MathUtils.bukkit2MovecraftLoc(e.getLocation()))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // prevent water from spreading on moving crafts
    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.HIGHEST) public void onBlockFromTo(BlockFromToEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Block block = e.getToBlock();
        if (block.getType() == Material.WATER) {
            for (Craft tcraft : craftManager.getCraftsInWorld(block.getWorld())) {
                if ((!tcraft.isNotProcessing()) && MathUtils
                        .playerIsWithinBoundingPolygon(tcraft.getHitBox(), tcraft.getMinX(), tcraft.getMinZ(),
                                                       MathUtils.bukkit2MovecraftLoc(block.getLocation()))) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    // prevent fragile items from dropping on moving crafts
    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.HIGHEST) public void onPhysics(BlockPhysicsEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Block block = event.getBlock();
        final int[] fragileBlocks = new int[]{
                26, 34, 50, 55, 63, 64, 65, 68, 69, 70, 71, 72, 75, 76, 77, 93, 94, 96, 131, 132, 143, 147, 148, 149,
                150, 151, 171, 193, 194, 195, 196, 197};
        for (Craft tcraft : craftManager.getCraftsInWorld(block.getWorld())) {
            if ((!tcraft.isNotProcessing()) && MathUtils
                    .playerIsWithinBoundingPolygon(tcraft.getHitBox(), tcraft.getMinX(), tcraft.getMinZ(),
                                                   MathUtils.bukkit2MovecraftLoc(block.getLocation()))) {
                boolean isFragile = (Arrays.binarySearch(fragileBlocks, block.getTypeId()) >= 0);
                if (isFragile) {
//						BlockFace face = ((Attachable) block).getAttachedFace();
//					    if (!event.getBlock().getRelative(face).getType().isSolid()) {
                    event.setCancelled(true);
                    return;
//					    }
                }
            }
        }
    }

    private CraftType getCraftTypeFromString(String s) {
        for (CraftType t : craftManager.getCraftTypes()) {
            if (s.equalsIgnoreCase(t.getCraftName())) {
                return t;
            }
        }

        return null;
    }

    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.NORMAL) public void onSignChange(SignChangeEvent event) {
        Player p = event.getPlayer();
        if (p == null) return;
        String signText = org.bukkit.ChatColor.stripColor(event.getLine(0));
        // did the player try to create a craft command sign?
        if (getCraftTypeFromString(signText) != null) {
            if (!settings.RequireCreatePerm) {
                return;
            }
            if (!p.hasPermission("movecraft." + org.bukkit.ChatColor.stripColor(event.getLine(0)) + ".create")) {
                p.sendMessage(i18n.get("Insufficient Permissions"));
                event.setCancelled(true);
            }
        }
        if (signText.equalsIgnoreCase("Cruise: OFF") || signText.equalsIgnoreCase("Cruise: ON")) {
            if (!p.hasPermission("movecraft.cruisesign") && settings.RequireCreatePerm) {
                p.sendMessage(i18n.get("Insufficient Permissions"));
                event.setCancelled(true);
            }
        }
        if (signText.equalsIgnoreCase("Crew:")) {
            String crewName = org.bukkit.ChatColor.stripColor(event.getLine(1));
            if (!p.getName().equalsIgnoreCase(crewName)) {
                p.sendMessage(i18n.get("You can only create a Crew: sign for yourself"));
                event.setLine(1, p.getName());
//				event.setCancelled(true);
            }
        }
    }

    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.LOW) public void onBlockIgnite(BlockIgniteEvent event) {
        if (!settings.FireballPenetration) return;
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
                if (plugin.getWorldGuardPlugin() != null &&
                    (settings.WorldGuardBlockMoveOnBuildPerm || settings.WorldGuardBlockSinkOnPVPPerm)) {
                    ApplicableRegionSet set = plugin.getWorldGuardPlugin().getRegionManager(testBlock.getWorld())
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
    @EventHandler(priority = EventPriority.NORMAL) public void explodeEvent(EntityExplodeEvent e) {
        // Remove any blocks from the list that were adjacent to water, to prevent spillage
        Iterator<Block> i = e.blockList().iterator();
        if (!settings.DisableSpillProtection) while (i.hasNext()) {
            Block b = i.next();
            boolean isNearWater = false;
            if (b.getY() > b.getWorld().getSeaLevel()) for (int mx = -1; mx <= 1; mx++) {
                for (int mz = -1; mz <= 1; mz++) {
                    for (int my = 0; my <= 1; my++) {
                        if (b.getRelative(mx, my, mz).getType() == Material.STATIONARY_WATER ||
                            b.getRelative(mx, my, mz).getType() == Material.WATER) isNearWater = true;
                    }
                }
            }
            if (isNearWater) {
                i.remove();
            }
        }

        if (e.getEntity() == null) return;
        for (Player p : e.getEntity().getWorld().getPlayers()) {
            org.bukkit.entity.Entity tnt = e.getEntity();

            if (e.getEntityType() == EntityType.PRIMED_TNT && settings.TracerRateTicks != 0) {
                long minDistSquared = 60 * 60;
                long maxDistSquared = Bukkit.getServer().getViewDistance() * 16;
                maxDistSquared = maxDistSquared - 16;
                maxDistSquared = maxDistSquared * maxDistSquared;
                // is the TNT within the view distance (rendered world) of the player, yet further than 60 blocks?
                if (p.getLocation().distanceSquared(tnt.getLocation()) < maxDistSquared &&
                    p.getLocation().distanceSquared(tnt.getLocation()) >=
                    minDistSquared) {  // we use squared because its faster
                    final Location loc = tnt.getLocation();
                    final Player fp = p;
                    final World fw = e.getEntity().getWorld();
                    // then make a glowstone to look like the explosion, place it a little later so it isn't right in
                    // the middle of the volley
                    BukkitTask placeCobweb = new BukkitRunnable() {
                        @Override public void run() {
                            fp.sendBlockChange(loc, 89, (byte) 0);
                        }
                    }.runTaskLater(plugin, 5);
                    // then remove it
                    BukkitTask removeCobweb = new BukkitRunnable() {
                        @Override public void run() {
                            fp.sendBlockChange(loc, 0, (byte) 0);
                        }
                    }.runTaskLater(plugin, 160);
                }
            }
        }
    }
}
