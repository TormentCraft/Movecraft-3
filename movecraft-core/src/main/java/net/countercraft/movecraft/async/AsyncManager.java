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

package net.countercraft.movecraft.async;

import at.pavlov.cannons.cannon.Cannon;
import com.alexknvl.shipcraft.math.BlockVec;
import com.alexknvl.shipcraft.math.RotationXZ;
import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import net.countercraft.movecraft.Events;
import net.countercraft.movecraft.Movecraft;
import com.alexknvl.shipcraft.math.Direction;
import net.countercraft.movecraft.api.MaterialDataPredicate;
import net.countercraft.movecraft.async.detection.DetectionTask;
import net.countercraft.movecraft.async.detection.DetectionTaskData;
import net.countercraft.movecraft.async.rotation.RotationTask;
import net.countercraft.movecraft.async.translation.TranslationTask;
import net.countercraft.movecraft.async.translation.TranslationTaskData;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.CraftType;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.BlockUtils;
import net.countercraft.movecraft.detail.MapUpdateCommand;
import net.countercraft.movecraft.detail.MapUpdateManager;
import net.countercraft.movecraft.utils.MathUtils;
import net.countercraft.movecraft.utils.WGCustomFlagsUtils;
import org.apache.commons.collections.ListUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

public final class AsyncManager extends BukkitRunnable {
    @Nonnull private final Settings settings;
    @Nonnull private final I18nSupport i18n;
    @Nonnull private final CraftManager craftManager;
    @Nonnull private final Movecraft plugin;
    @Nonnull private final MapUpdateManager mapUpdateManager;
    @Nonnull private final Events events = new Events(Bukkit.getPluginManager());

    private final Map<AsyncTask, Craft> ownershipMap = new HashMap<>();
    private final Map<org.bukkit.entity.TNTPrimed, Double> TNTTracking = new HashMap<>();
    private final Map<org.bukkit.entity.SmallFireball, Long> FireballTracking = new HashMap<>();
    private final BlockingQueue<AsyncTask> finishedAlgorithms = new LinkedBlockingQueue<>();
    private final Set<Craft> clearanceSet = new HashSet<>();
    private long lastTracerUpdate = 0;
    private long lastFireballCheck = 0;
    private long lastTNTContactCheck = 0;
    private long lastFadeCheck = 0;

    public AsyncManager(@Nonnull final Settings settings, @Nonnull final I18nSupport i18n, @Nonnull final CraftManager craftManager,
                        @Nonnull final Movecraft plugin, @Nonnull final MapUpdateManager mapUpdateManager)
    {
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
        this.plugin = plugin;
        this.mapUpdateManager = mapUpdateManager;
    }

    public void detect(final Craft craft, final Player player, final Player notificationPlayer, final BlockVec startPoint) {
        this.submitTask(new DetectionTask(craft, startPoint, craft.type.getSizeRange(), craft.type.getAllowedBlocks(),
                                          craft.type.getForbiddenBlocks(), player, notificationPlayer, craft.world,
                                          this.plugin, this.settings, this.i18n), craft);
    }

    public void translate(final Craft craft, int dx, int dy, int dz) {
        // check to see if the craft is trying to move in a direction not permitted by the type
        if (!craft.getType().allowHorizontalMovement() && !craft.getSinking()) {
            dx = 0;
            dz = 0;
        }
        if (!craft.getType().allowVerticalMovement() && !craft.getSinking()) {
            dy = 0;
        }
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }

        if (!craft.getType().allowVerticalTakeoffAndLanding() && dy != 0 && !craft.getSinking()) {
            if (dx == 0 && dz == 0) {
                return;
            }
        }

        // find region that will need to be loaded to translate this craft
        int cminX = craft.getMinX();
        int cmaxX = craft.getMinX();
        if (dx < 0) cminX = cminX + dx;
        int cminZ = craft.getMinZ();
        int cmaxZ = craft.getMinZ();
        if (dz < 0) cminZ = cminZ + dz;
        for (final BlockVec m : craft.getBlockList()) {
            if (m.x() > cmaxX) cmaxX = m.x();
            if (m.z() > cmaxZ) cmaxZ = m.z();
        }
        if (dx > 0) cmaxX = cmaxX + dx;
        if (dz > 0) cmaxZ = cmaxZ + dz;
        cminX = cminX >> 4;
        cminZ = cminZ >> 4;
        cmaxX = cmaxX >> 4;
        cmaxZ = cmaxZ >> 4;

        // load all chunks that will be needed to translate this craft
        for (int posX = cminX - 1; posX <= cmaxX + 1; posX++) {
            for (int posZ = cminZ - 1; posZ <= cmaxZ + 1; posZ++) {
                if (!craft.getWorld().isChunkLoaded(posX, posZ)) {
                    craft.getWorld().loadChunk(posX, posZ);
                }
            }
        }

        this.submitTask(new TranslationTask(craft, this.plugin, this.settings, this.i18n, this.craftManager,
                                            new TranslationTaskData(dx, dz, dy, craft.getBlockList(), craft.getHitBox(),
                                                               craft.getMinZ(), craft.getMinX(),
                                                               craft.type.getHeightRange())), craft);
    }

    public void rotate(final Craft craft, final RotationXZ rotation, final BlockVec originPoint) {
        // find region that will need to be loaded to rotate this craft
        int cminX = craft.getMinX();
        int cmaxX = craft.getMinX();
        int cminZ = craft.getMinZ();
        int cmaxZ = craft.getMinZ();
        for (final BlockVec m : craft.getBlockList()) {
            if (m.x() > cmaxX) cmaxX = m.x();
            if (m.z() > cmaxZ) cmaxZ = m.z();
        }
        final int distX = cmaxX - cminX;
        final int distZ = cmaxZ - cminZ;
        if (distX > distZ) {
            cminZ -= (distX - distZ) / 2;
            cmaxZ += (distX - distZ) / 2;
        }
        if (distZ > distX) {
            cminX -= (distZ - distX) / 2;
            cmaxX += (distZ - distX) / 2;
        }
        cminX = cminX >> 4;
        cminZ = cminZ >> 4;
        cmaxX = cmaxX >> 4;
        cmaxZ = cmaxZ >> 4;

        // load all chunks that will be needed to rotate this craft
        for (int posX = cminX; posX <= cmaxX; posX++) {
            for (int posZ = cminZ; posZ <= cmaxZ; posZ++) {
                if (!craft.getWorld().isChunkLoaded(posX, posZ)) {
                    craft.getWorld().loadChunk(posX, posZ);
                }
            }
        }

        craft.setCruiseDirection(craft.getCruiseDirection().rotateXZ(rotation));

        this.submitTask(new RotationTask(craft, this.plugin, this.settings, this.i18n, this.craftManager, originPoint, craft.getBlockList(),
                                         rotation, craft.getWorld()), craft);
    }

    public void rotate(final Craft craft, final RotationXZ rotation, final BlockVec originPoint, final boolean isSubCraft) {
        this.submitTask(new RotationTask(craft, this.plugin, this.settings, this.i18n, this.craftManager, originPoint, craft.getBlockList(),
                                         rotation, craft.getWorld(), isSubCraft), craft);
    }

    private void submitTask(final AsyncTask task, final Craft c) {
        if (c.isNotProcessing()) {
            c.setProcessing(true);
            this.ownershipMap.put(task, c);

            new BukkitRunnable() {
                @Override public void run() {
                    try {
                        task.run();
                        AsyncManager.this.submitCompletedTask(task);
                    } catch (final Exception e) {
                        AsyncManager.this.plugin.getLogger()
                                                .log(Level.SEVERE, AsyncManager.this.i18n
                                      .get("Internal - Error - Processor thread encountered an error"));
                        e.printStackTrace();
                    }
                }
            }.runTaskAsynchronously(this.plugin);
        }
    }

    private void submitCompletedTask(final AsyncTask task) {
        this.finishedAlgorithms.add(task);
    }

    private void processAlgorithmQueue() {
        int runLength = 10;
        final int queueLength = this.finishedAlgorithms.size();

        runLength = Math.min(runLength, queueLength);

        for (int i = 0; i < runLength; i++) {
            boolean sentMapUpdate = false;
            final AsyncTask poll = this.finishedAlgorithms.poll();
            final Craft c = this.ownershipMap.get(poll);

            if (poll instanceof DetectionTask) {
                // Process detection task

                final DetectionTask task = (DetectionTask) poll;
                final DetectionTaskData data = task.getData();

                final Player p = data.getPlayer();
                final Player notifyP = data.getNotificationPlayer();
                final Craft pCraft = this.craftManager.getCraftByPlayer(p);

                if (pCraft != null && p != null) {
                    //Player is already controlling a craft
                    notifyP.sendMessage(this.i18n.get("Detection - Failed - Already commanding a craft"));
                } else {
                    if (data.failed()) {
                        if (notifyP != null) notifyP.sendMessage(data.getFailMessage());
                        else this.plugin.getLogger()
                                        .log(Level.INFO, "NULL Player Craft Detection failed:" + data.getFailMessage());
                    } else {
                        final Set<Craft> craftsInWorld = this.craftManager.getCraftsInWorld(c.getWorld());
                        boolean failed = false;

                        for (final Craft craft : craftsInWorld) {

                            if (BlockUtils.arrayContainsOverlap(craft.getBlockList(), data.getBlockList()) &&
                                (c.getType().getCruiseOnPilot() || p != null)) {  // changed from p!=null
                                if (craft.getType() == c.getType() ||
                                    craft.getBlockList().length <= data.getBlockList().length) {
                                    notifyP.sendMessage(this.i18n
                                                                .get("Detection - Failed Craft is already being controlled"));
                                    failed = true;
                                } else { // if this is a different type than the overlapping craft, and is
                                    // smaller, this must be a child craft, like a fighter on a carrier
                                    if (!craft.isNotProcessing()) {
                                        failed = true;
                                        notifyP.sendMessage(this.i18n.get("Parent Craft is busy"));
                                    }

                                    // remove the new craft from the parent craft
                                    final Set<BlockVec> parentBlockList = Sets.difference(
                                            Sets.newHashSet(craft.getBlockList()),
                                            Sets.newHashSet(data.getBlockList()));
                                    craft.setBlockList(parentBlockList.toArray(new BlockVec[parentBlockList.size()]));
                                    craft.setOrigBlockCount(craft.getOrigBlockCount() - data.getBlockList().length);

                                    // Rerun the polygonal bounding formula for the parent craft
                                    Integer parentMaxX = null;
                                    Integer parentMaxZ = null;
                                    Integer parentMinX = null;
                                    Integer parentMinZ = null;
                                    for (final BlockVec l : parentBlockList) {
                                        if (parentMaxX == null || l.x() > parentMaxX) {
                                            parentMaxX = l.x();
                                        }
                                        if (parentMaxZ == null || l.z() > parentMaxZ) {
                                            parentMaxZ = l.z();
                                        }
                                        if (parentMinX == null || l.x() < parentMinX) {
                                            parentMinX = l.x();
                                        }
                                        if (parentMinZ == null || l.z() < parentMinZ) {
                                            parentMinZ = l.z();
                                        }
                                    }
                                    final int parentSizeX = (parentMaxX - parentMinX) + 1;
                                    final int parentSizeZ = (parentMaxZ - parentMinZ) + 1;
                                    final int[][][] parentPolygonalBox = new int[parentSizeX][][];
                                    for (final BlockVec l : parentBlockList) {
                                        if (parentPolygonalBox[l.x() - parentMinX] == null) {
                                            parentPolygonalBox[l.x() - parentMinX] = new int[parentSizeZ][];
                                        }
                                        if (parentPolygonalBox[l.x() - parentMinX][l.z() - parentMinZ] == null) {
                                            parentPolygonalBox[l.x() - parentMinX][l.z() - parentMinZ] = new int[2];
                                            parentPolygonalBox[l.x() - parentMinX][l.z() - parentMinZ][0] = l.y();
                                            parentPolygonalBox[l.x() - parentMinX][l.z() - parentMinZ][1] = l.y();
                                        } else {
                                            final int parentMinY = parentPolygonalBox[l.x() - parentMinX][l.z() -
                                                                                                          parentMinZ][0];
                                            final int parentMaxY = parentPolygonalBox[l.x() - parentMinX][l.z() -
                                                                                                          parentMinZ][1];

                                            if (l.y() < parentMinY) {
                                                parentPolygonalBox[l.x() - parentMinX][l.z() - parentMinZ][0] = l.y();
                                            }
                                            if (l.y() > parentMaxY) {
                                                parentPolygonalBox[l.x() - parentMinX][l.z() - parentMinZ][1] = l.y();
                                            }
                                        }
                                    }
                                    craft.setHitBox(parentPolygonalBox);
                                }
                            }
                        }
                        if (!failed) {
                            c.setBlockList(data.getBlockList());
                            c.setOrigBlockCount(data.getBlockList().length);
                            c.setHitBox(data.getHitBox());
                            c.setMinX(data.getMinX());
                            c.setMinZ(data.getMinZ());
                            c.setNotificationPlayer(notifyP);

                            if (notifyP != null) {
                                notifyP.sendMessage(this.i18n.get("Detection - Successfully piloted craft") +
                                                    " Size: " +
                                                    c.getBlockList().length);
                                this.plugin.getLogger().log(Level.INFO,
                                                            String.format(this.i18n.get("Detection - Success - Log Output"),
                                                                     notifyP.getName(), c.getType().getCraftName(),
                                                                     c.getBlockList().length, c.getMinX(),
                                                                     c.getMinZ()));
                            } else {
                                this.plugin.getLogger().log(Level.INFO,
                                                            String.format(this.i18n.get("Detection - Success - Log Output"),
                                                                     "NULL PLAYER", c.getType().getCraftName(),
                                                                     c.getBlockList().length, c.getMinX(),
                                                                     c.getMinZ()));
                            }
                            this.craftManager.addCraft(c, p);
                        }
                    }
                }
            } else if (poll instanceof TranslationTask) {
                //Process translation task

                final TranslationTask task = (TranslationTask) poll;
                final Player p = this.craftManager.getPlayerFromCraft(c);
                final Player notifyP = c.getNotificationPlayer();

                // Check that the craft hasn't been sneakily unpiloted
                //		if ( p != null ) {     cruiseOnPilot crafts don't have player pilots

                if (task.getData().failed()) {
                    //The craft translation failed
                    if (notifyP != null && !c.getSinking()) notifyP.sendMessage(task.getData().getFailMessage());

                    if (task.getData().collisionExplosion()) {
                        final MapUpdateCommand.MoveBlock[] updates = task.getData().getUpdates();
                        c.setBlockList(task.getData().getBlockList());
                        final boolean failed = this.mapUpdateManager.addWorldUpdate(c.getWorld(), updates, null, null);

                        if (failed) {
                            this.plugin.getLogger().log(Level.SEVERE, this.i18n.get("Translation - Craft collision"));
                        } else {
                            sentMapUpdate = true;
                        }
                    }
                } else {
                    //The craft is clear to move, perform the block updates

                    final MapUpdateCommand.MoveBlock[] updates = task.getData().getUpdates();
                    final MapUpdateCommand.MoveEntity[] eUpdates = task.getData().getEntityUpdates();
                    final MapUpdateCommand.DropItem[] iUpdates = task.getData().getItemDropUpdateCommands();
                    //get list of cannons before sending map updates, to avoid conflicts
                    Iterable<Cannon> shipCannons = null;
                    if (this.plugin.getCannonsPlugin() != null && c.getNotificationPlayer() != null) {
                        // convert blocklist to location list
                        final List<Location> shipLocations = new ArrayList<>();
                        for (final BlockVec loc : c.getBlockList()) {
                            final Location tloc = loc.toBukkitLocation(c.getWorld());
                            shipLocations.add(tloc);
                        }
                        shipCannons = this.plugin.getCannonsPlugin().getCannonsAPI()
                                                 .getCannons(shipLocations, c.getNotificationPlayer().getUniqueId(), true);
                    }
                    final boolean failed = this.mapUpdateManager
                            .addWorldUpdate(c.getWorld(), updates, eUpdates, iUpdates);

                    if (failed) {
                        this.plugin.getLogger().log(Level.SEVERE, this.i18n.get("Translation - Craft collision"));
                    } else {
                        sentMapUpdate = true;
                        c.setBlockList(task.getData().getBlockList());
                        c.setMinX(task.getData().getMinX());
                        c.setMinZ(task.getData().getMinZ());
                        c.setHitBox(task.getData().getHitbox());

                        // move any cannons that were present
                        if (this.plugin.getCannonsPlugin() != null && shipCannons != null) {
                            for (final Cannon can : shipCannons) {
                                can.move(new Vector(task.getData().getDx(), task.getData().getDy(),
                                                    task.getData().getDz()));
                            }
                        }
                    }
                }
            } else if (poll instanceof RotationTask) {
                // Process rotation task
                final RotationTask task = (RotationTask) poll;
                final Player p = this.craftManager.getPlayerFromCraft(c);
                final Player notifyP = c.getNotificationPlayer();

                // Check that the craft hasn't been sneakily unpiloted
                if (notifyP != null || task.getIsSubCraft()) {

                    if (task.isFailed()) {
                        //The craft translation failed, don't try to notify them if there is no pilot
                        if (notifyP != null) notifyP.sendMessage(task.getFailMessage());
                        else this.plugin.getLogger().log(Level.INFO, "NULL Player Rotation Failed: " + task.getFailMessage());
                    } else {
                        final MapUpdateCommand.MoveBlock[] updates = task.getUpdates();
                        final MapUpdateCommand.MoveEntity[] eUpdates = task.getEntityUpdates();

                        //get list of cannons before sending map updates, to avoid conflicts
                        Iterable<Cannon> shipCannons = null;
                        if (this.plugin.getCannonsPlugin() != null && c.getNotificationPlayer() != null) {
                            // convert blocklist to location list
                            final List<Location> shipLocations = new ArrayList<>();
                            for (final BlockVec loc : c.getBlockList()) {
                                final Location tloc = loc.toBukkitLocation(c.getWorld());
                                shipLocations.add(tloc);
                            }
                            shipCannons = this.plugin.getCannonsPlugin().getCannonsAPI()
                                                     .getCannons(shipLocations, c.getNotificationPlayer().getUniqueId(),
                                                            true);
                        }

                        final boolean failed = this.mapUpdateManager
                                .addWorldUpdate(c.getWorld(), updates, eUpdates, null);

                        if (failed) {
                            this.plugin.getLogger().log(Level.SEVERE, this.i18n.get("Rotation - Craft Collision"));
                        } else {
                            sentMapUpdate = true;

                            c.setBlockList(task.getBlockList());
                            c.setMinX(task.getMinX());
                            c.setMinZ(task.getMinZ());
                            c.setHitBox(task.getHitbox());

                            // rotate any cannons that were present
                            if (this.plugin.getCannonsPlugin() != null && shipCannons != null) {
                                final Location tloc = task.getOriginPoint().toBukkitLocation(task.getCraft().getWorld());
                                for (final Cannon can : shipCannons) {
                                    if (task.getRotation().equals(RotationXZ.cw())) can.rotateRight(tloc.toVector());
                                    if (task.getRotation().equals(RotationXZ.ccw())) can.rotateLeft(tloc.toVector());
                                }
                            }
                        }
                    }
                }
            }

            this.ownershipMap.remove(poll);

            // only mark the craft as having finished updating if you didn't send any updates to the map updater.
            // Otherwise the map updater will mark the crafts once it is done with them.
            if (!sentMapUpdate) {
                this.clear(c);
            }
        }
    }

    private void processCruise() {
        for (final World w : Bukkit.getWorlds()) {
            for (final Craft pcraft : this.craftManager.getCraftsInWorld(w)) {
                if ((pcraft != null) && pcraft.isNotProcessing()) {
                    if (pcraft.getCruising()) {
                        long ticksElapsed = (System.currentTimeMillis() - pcraft.getLastCruiseUpdate()) / 50;

                        // if the craft should go slower underwater, make time pass more slowly there
                        if (pcraft.getType().getHalfSpeedUnderwater() && pcraft.getMinY() < w.getSeaLevel())
                            ticksElapsed = ticksElapsed / 2;

                        if (Math.abs(ticksElapsed) >= pcraft.getType().getCruiseTickCooldown()) {
                            final Direction direction = pcraft.getCruiseDirection();
                            int dx = direction.x() * (1 + pcraft.getType().getCruiseSkipBlocks());
                            int dz = direction.z() * (1 + pcraft.getType().getCruiseSkipBlocks());
                            int dy = direction.y() * (1 + pcraft.getType().getVertCruiseSkipBlocks());

                            if (direction.y() == -1 && pcraft.getMinY() <= w.getSeaLevel()) {
                                dy = -1;
                            }

                            if (pcraft.getType().getCruiseOnPilot())
                                dy = pcraft.getType().getCruiseOnPilotVertMove();

                            if ((dx != 0 && dz != 0) || ((dx + dz) != 0 && dy != 0)) {
                                // we need to slow the skip speed...
                                if (Math.abs(dx) > 1) dx /= 2;
                                if (Math.abs(dz) > 1) dz /= 2;
                            }

                            this.translate(pcraft, dx, dy, dz);
                            pcraft.setLastDX(dx);
                            pcraft.setLastDZ(dz);
                            if (pcraft.getLastCruiseUpdate() == -1) {
                                pcraft.setLastCruiseUpdate(0);
                            } else {
                                pcraft.setLastCruiseUpdate(System.currentTimeMillis());
                            }
                        }
                    } else {
                        if (pcraft.getKeepMoving()) {
                            long rcticksElapsed = (System.currentTimeMillis() - pcraft.getLastRightClick()) / 50;

                            // if the craft should go slower underwater, make time pass more slowly there
                            if (pcraft.getType().getHalfSpeedUnderwater() && pcraft.getMinY() < w.getSeaLevel())
                                rcticksElapsed = rcticksElapsed / 2;

                            rcticksElapsed = Math.abs(rcticksElapsed);
                            // if they are holding the button down, keep moving
                            if (rcticksElapsed <= 10) {
                                final long ticksElapsed =
                                        (System.currentTimeMillis() - pcraft.getLastCruiseUpdate()) / 50;
                                if (Math.abs(ticksElapsed) >= pcraft.getType().getTickCooldown()) {
                                    this.translate(pcraft, pcraft.getLastDX(), pcraft.getLastDY(), pcraft.getLastDZ());
                                    pcraft.setLastCruiseUpdate(System.currentTimeMillis());
                                }
                            }
                        }
                        if (pcraft.getPilotLocked() && pcraft.isNotProcessing()) {

                            final Player p = this.craftManager.getPlayerFromCraft(pcraft);
                            if (p != null) if (MathUtils
                                    .playerIsWithinBoundingPolygon(pcraft.getHitBox(), pcraft.getMinX(),
                                                                   pcraft.getMinZ(), BlockVec.from(p.getLocation()))) {
                                final double movedX = p.getLocation().getX() - pcraft.getPilotLockedX();
                                final double movedZ = p.getLocation().getZ() - pcraft.getPilotLockedZ();
                                int dX = 0;
                                if (movedX > 0.15) dX = 1;
                                else if (movedX < -0.15) dX = -1;
                                int dZ = 0;
                                if (movedZ > 0.15) dZ = 1;
                                else if (movedZ < -0.15) dZ = -1;
                                if (dX != 0 || dZ != 0) {
                                    final long timeSinceLastMoveCommand =
                                            System.currentTimeMillis() - pcraft.getLastRightClick();
                                    // wait before accepting new move commands to help with bouncing. Also ignore
                                    // extreme movement
                                    if (Math.abs(movedX) < 0.2 && Math.abs(movedZ) < 0.2 &&
                                        timeSinceLastMoveCommand > 300) {

                                        pcraft.setLastRightClick(System.currentTimeMillis());
                                        long ticksElapsed =
                                                (System.currentTimeMillis() - pcraft.getLastCruiseUpdate()) / 50;

                                        // if the craft should go slower underwater, make time pass more slowly
                                        // there
                                        if (pcraft.getType().getHalfSpeedUnderwater() &&
                                            pcraft.getMinY() < w.getSeaLevel()) ticksElapsed = ticksElapsed / 2;

                                        if (Math.abs(ticksElapsed) >= pcraft.getType().getTickCooldown()) {
                                            this.translate(pcraft, dX, 0, dZ);
                                            pcraft.setLastCruiseUpdate(System.currentTimeMillis());
                                        }
                                        pcraft.setLastDX(dX);
                                        pcraft.setLastDY(0);
                                        pcraft.setLastDZ(dZ);
                                        pcraft.setKeepMoving(true);
                                    } else {
                                        final Location loc = p.getLocation();
                                        loc.setX(pcraft.getPilotLockedX());
                                        loc.setY(pcraft.getPilotLockedY());
                                        loc.setZ(pcraft.getPilotLockedZ());
                                        final Vector pVel = new Vector(0.0, 0.0, 0.0);
                                        p.teleport(loc);
                                        p.setVelocity(pVel);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isRegionBlockedPVP(final BlockVec loc, final World world) {
        if (this.plugin.getWorldGuardPlugin() == null) return false;
        if (!this.settings.WorldGuardBlockSinkOnPVPPerm) return false;

        final Location nativeLoc = loc.toBukkitLocation(world);
        final ApplicableRegionSet set = this.plugin.getWorldGuardPlugin().getRegionManager(world).getApplicableRegions(nativeLoc);
        return !set.allows(DefaultFlag.PVP);
    }

    private boolean isRegionFlagSinkAllowed(final BlockVec loc, final World world) {
        if (this.plugin.getWorldGuardPlugin() != null && this.plugin.getWGCustomFlagsPlugin() != null && this.settings.WGCustomFlagsUseSinkFlag) {
            final Location nativeLoc = loc.toBukkitLocation(world);
            return WGCustomFlagsUtils.validateFlag(this.plugin.getWorldGuardPlugin(), nativeLoc, this.plugin.FLAG_SINK);
        } else {
            return true;
        }
    }

    private void processSinking() {
        for (final World w : Bukkit.getWorlds()) {
            // check every few seconds for every craft to see if it should be sinking
            for (final Craft pcraft : this.craftManager.getCraftsInWorld(w)) {
                if (pcraft != null && !pcraft.getSinking()) {
                    if (pcraft.getType().getSinkPercent() != 0.0 && pcraft.isNotProcessing()) {
                        final long ticksElapsed = (System.currentTimeMillis() - pcraft.getLastBlockCheck()) / 50;

                        if (ticksElapsed > this.settings.SinkCheckTicks) {
                            int totalNonAirBlocks = 0;
                            int totalNonAirWaterBlocks = 0;
                            final Map<MaterialDataPredicate, Integer> foundFlyBlocks = new HashMap<>();
                            boolean regionPVPBlocked = false;
                            boolean sinkingForbiddenByFlag = false;
                            // go through each block in the blocklist, and if its in the FlyBlocks, total up the
                            // number of them
                            for (final BlockVec l : pcraft.getBlockList()) {
                                if (this.isRegionBlockedPVP(l, w)) regionPVPBlocked = true;
                                if (!this.isRegionFlagSinkAllowed(l, w)) sinkingForbiddenByFlag = true;
                                final Integer blockID = w.getBlockAt(l.x(), l.y(), l.z()).getTypeId();
                                for (final MaterialDataPredicate flyBlockDef : pcraft.getType().getFlyBlocks().keySet()) {
                                    if (flyBlockDef.checkBlock(w.getBlockAt(l.x(), l.y(), l.z()))) {
                                        foundFlyBlocks.merge(flyBlockDef, 1, (a, b) -> a + b);
                                    }
                                }

                                if (blockID != 0) {
                                    totalNonAirBlocks++;
                                }
                                if (blockID != 0 && blockID != 8 && blockID != 9) {
                                    totalNonAirWaterBlocks++;
                                }
                            }

                            // now see if any of the resulting percentages are below the threshold specified in
                            // SinkPercent
                            boolean isSinking = false;
                            for (final Map.Entry<MaterialDataPredicate, List<CraftType.Constraint>> entry : pcraft
                                    .getType().getFlyBlocks().entrySet()) {
                                final MaterialDataPredicate predicate = entry.getKey();
                                final List<CraftType.Constraint> constraints = entry.getValue();
                                final int count = Optional.fromNullable(foundFlyBlocks.get(predicate)).or(0);

                                final double percent = count / (double) totalNonAirBlocks;
                                for (final CraftType.Constraint constraint : constraints) {
                                    if (constraint.isUpper) continue;

                                    final double flyPercent = constraint.bound.asRatio(totalNonAirBlocks);
                                    final double sinkPercent = flyPercent * pcraft.getType().getSinkPercent() / 100.0;
                                    if (percent < sinkPercent) {
                                        isSinking = true;
                                    }
                                }
                            }

                            // And check the overallsinkpercent
                            if (pcraft.getType().getOverallSinkPercent() != 0.0) {
                                final double percent =
                                        (double) totalNonAirWaterBlocks / (double) pcraft.getOrigBlockCount();
                                if (percent * 100.0 < pcraft.getType().getOverallSinkPercent()) {
                                    isSinking = true;
                                }
                            }

                            if (totalNonAirBlocks == 0) {
                                isSinking = true;
                            }

                            final boolean cancelled = this.events.sinkingStarted();

                            if (isSinking &&
                                (regionPVPBlocked || sinkingForbiddenByFlag) &&
                                pcraft.isNotProcessing() || cancelled) {
                                final Player p = this.craftManager.getPlayerFromCraft(pcraft);
                                final Player notifyP = pcraft.getNotificationPlayer();
                                if (notifyP != null) {
                                    if (regionPVPBlocked) {
                                        notifyP.sendMessage(this.i18n.get(
                                                "Player- Craft should sink but PVP is not allowed in this " +
                                                "WorldGuard " +
                                                "region"));
                                    } else if (sinkingForbiddenByFlag) {
                                        notifyP.sendMessage(this.i18n
                                                                    .get("WGCustomFlags - Sinking a craft is not allowed in this " +
                                                                         "WorldGuard " +
                                                                         "region"));
                                    } else {
                                        notifyP.sendMessage(this.i18n.get("Sinking a craft is not allowed."));
                                    }
                                }
                                pcraft.setCruising(false);
                                pcraft.setKeepMoving(false);
                                this.craftManager.removeCraft(pcraft);
                            } else {
                                // if the craft is sinking, let the player know and release the craft. Otherwise
                                // update the time for the next check
                                if (isSinking && pcraft.isNotProcessing()) {
                                    final Player p = this.craftManager.getPlayerFromCraft(pcraft);
                                    final Player notifyP = pcraft.getNotificationPlayer();
                                    if (notifyP != null) notifyP.sendMessage(this.i18n.get("Player- Craft is sinking"));
                                    pcraft.setCruising(false);
                                    pcraft.setKeepMoving(false);
                                    pcraft.setSinking(true);
                                    this.craftManager.removePlayerFromCraft(pcraft);
                                    final Craft releaseCraft = pcraft;
                                    final BukkitTask releaseTask = new BukkitRunnable() {
                                        @Override public void run() {
                                            AsyncManager.this.craftManager.removeCraft(releaseCraft);
                                        }
                                    }.runTaskLater(this.plugin, (20 * 600));
                                } else {
                                    pcraft.setLastBlockCheck(System.currentTimeMillis());
                                }
                            }
                        }
                    }
                }
            }

            // sink all the sinking ships
            for (final Craft pcraft : this.craftManager.getCraftsInWorld(w)) {
                if (pcraft != null && pcraft.getSinking()) {
                    if (pcraft.getBlockList().length == 0) {
                        this.craftManager.removeCraft(pcraft);
                    }
                    if (pcraft.getMinY() < -1) {
                        this.craftManager.removeCraft(pcraft);
                    }
                    final long ticksElapsed = (System.currentTimeMillis() - pcraft.getLastCruiseUpdate()) / 50;
                    if (Math.abs(ticksElapsed) >= pcraft.getType().getSinkRateTicks()) {
                        int dx = 0;
                        int dz = 0;
                        if (pcraft.getType().getKeepMovingOnSink()) {
                            dx = pcraft.getLastDX();
                            dz = pcraft.getLastDZ();
                        }
                        this.translate(pcraft, dx, -1, dz);
                        if (pcraft.getLastCruiseUpdate() == -1) {
                            pcraft.setLastCruiseUpdate(0);
                        } else {
                            pcraft.setLastCruiseUpdate(System.currentTimeMillis());
                        }
                    }
                }
            }
        }
    }

    private void processTracers() {
        if (this.settings.TracerRateTicks == 0) return;
        final long ticksElapsed = (System.currentTimeMillis() - this.lastTracerUpdate) / 50;
        if (ticksElapsed > this.settings.TracerRateTicks) {
            for (final World w : Bukkit.getWorlds()) {
                if (w != null) {
                    for (final org.bukkit.entity.TNTPrimed tnt : w.getEntitiesByClass(org.bukkit.entity.TNTPrimed.class)) {
                        if (tnt.getVelocity().lengthSquared() > 0.25) {
                            for (final Player p : w.getPlayers()) {
                                // is the TNT within the view distance (rendered world) of the player?
                                long maxDistSquared = Bukkit.getServer().getViewDistance() * 16;
                                maxDistSquared = maxDistSquared - 16;
                                maxDistSquared = maxDistSquared * maxDistSquared;

                                if (p.getLocation().distanceSquared(tnt.getLocation()) <
                                    maxDistSquared) {  // we use squared because its faster
                                    final Location loc = tnt.getLocation();
                                    final Player fp = p;
                                    final World fw = w;
                                    // then make a cobweb to look like smoke, place it a little later so it isn't
                                    // right in the middle of the volley
                                    final BukkitTask placeCobweb = new BukkitRunnable() {
                                        @Override public void run() {
                                            fp.sendBlockChange(loc, 30, (byte) 0);
                                        }
                                    }.runTaskLater(this.plugin, 5);
                                    // then remove it
                                    final BukkitTask removeCobweb = new BukkitRunnable() {
                                        @Override public void run() {
//											fp.sendBlockChange(loc, fw.getBlockAt(loc).getType(), fw.getBlockAt(loc)
// .getData());
                                            fp.sendBlockChange(loc, 0, (byte) 0);
                                        }
                                    }.runTaskLater(this.plugin, 160);
                                }
                            }
                        }
                    }
                }
            }
            this.lastTracerUpdate = System.currentTimeMillis();
        }
    }

    private void processFireballs() {
        final long ticksElapsed = (System.currentTimeMillis() - this.lastFireballCheck) / 50;
        if (ticksElapsed > 4) {
            for (final World w : Bukkit.getWorlds()) {
                if (w != null) {
                    for (final org.bukkit.entity.SmallFireball fireball : w
                            .getEntitiesByClass(org.bukkit.entity.SmallFireball.class)) {
                        if (!(fireball.getShooter() instanceof org.bukkit.entity.LivingEntity)) { // means it was
                            // launched by a dispenser
                            if (!w.getPlayers().isEmpty()) {
                                Player p = w.getPlayers().get(0);
                                double closest = 1000000000.0;
                                for (final Player pi : w.getPlayers()) {
                                    if (pi.getLocation().distanceSquared(fireball.getLocation()) < closest) {
                                        closest = pi.getLocation().distanceSquared(fireball.getLocation());
                                        p = pi;
                                    }
                                }
                                // give it a living shooter, then set the fireball to be deleted
                                fireball.setShooter(p);
                                final org.bukkit.entity.SmallFireball ffb = fireball;
                                if (!this.FireballTracking.containsKey(fireball)) {
                                    this.FireballTracking.put(fireball, System.currentTimeMillis());
                                }
/*								BukkitTask deleteFireballTask = new BukkitRunnable() {
                                    @Override
									public void run() {
										ffb.remove();
									}
								}.runTaskLater( Movecraft.getInstance(), ( 20 * Settings.FireballLifespan ) );*/
                            }
                        }
                    }
                }
            }

            final int timelimit = 20 * this.settings.FireballLifespan * 50;
            //then, removed any exploded TNT from tracking
            final Iterator<org.bukkit.entity.SmallFireball> fireballI = this.FireballTracking.keySet().iterator();
            while (fireballI.hasNext()) {
                final org.bukkit.entity.SmallFireball fireball = fireballI.next();
                if (fireball != null) if (System.currentTimeMillis() - this.FireballTracking.get(fireball) > timelimit) {
                    fireball.remove();
                    fireballI.remove();
                }
            }

            this.lastFireballCheck = System.currentTimeMillis();
        }
    }

    private void processTNTContactExplosives() {
        final long ticksElapsed = (System.currentTimeMillis() - this.lastTNTContactCheck) / 50;
        if (ticksElapsed > 4) {
            // see if there is any new rapid moving TNT in the worlds
            for (final World w : Bukkit.getWorlds()) {
                if (w != null) {
                    for (final org.bukkit.entity.TNTPrimed tnt : w.getEntitiesByClass(org.bukkit.entity.TNTPrimed.class)) {
                        if (tnt.getVelocity().lengthSquared() > 0.35) {
                            if (!this.TNTTracking.containsKey(tnt)) {
                                this.TNTTracking.put(tnt, tnt.getVelocity().lengthSquared());
                            }
                        }
                    }
                }
            }

            //then, removed any exploded TNT from tracking
            this.TNTTracking.keySet().removeIf(tnt -> tnt.getFuseTicks() <= 0);

            //now check to see if any has abruptly changed velocity, and should explode
            for (final Map.Entry<TNTPrimed, Double> tntPrimedDoubleEntry : this.TNTTracking.entrySet()) {
                final TNTPrimed tntPrimed = tntPrimedDoubleEntry.getKey();
                final double vel = tntPrimed.getVelocity().lengthSquared();
                if (vel < this.TNTTracking.get(tntPrimed) / 10.0) {
                    tntPrimed.setFuseTicks(0);
                } else {
                    // update the tracking with the new velocity so gradual changes do not make TNT explode
                    this.TNTTracking.put(tntPrimed, vel);
                }
            }

            this.lastTNTContactCheck = System.currentTimeMillis();
        }
    }

    private void processFadingBlocks() {
        if (this.settings.FadeWrecksAfter == 0) return;
        final long ticksElapsed = (System.currentTimeMillis() - this.lastFadeCheck) / 50;
        if (ticksElapsed > 20) {
            for (final World w : Bukkit.getWorlds()) {
                if (w != null) {
                    final ArrayList<MapUpdateCommand.MoveBlock> updateCommands = new ArrayList<>();
                    CopyOnWriteArrayList<BlockVec> locations = null;

                    // I know this is horrible, but I honestly don't see another way to do this...
                    int numTries = 0;
                    while ((locations == null) && (numTries < 100)) {
                        try {
                            locations = new CopyOnWriteArrayList<>(this.plugin.blockFadeTimeMap.keySet());
                        } catch (final java.util.ConcurrentModificationException e) {
                            numTries++;
                        } catch (final java.lang.NegativeArraySizeException e) {
                            this.plugin.blockFadeTimeMap = new HashMap<>();
                            this.plugin.blockFadeTypeMap = new HashMap<>();
                            this.plugin.blockFadeWaterMap = new HashMap<>();
                            this.plugin.blockFadeWorldMap = new HashMap<>();
                            locations = new CopyOnWriteArrayList<>(this.plugin.blockFadeTimeMap.keySet());
                        }
                    }

                    for (final BlockVec loc : locations) {
                        if (this.plugin.blockFadeWorldMap.get(loc) == w) {
                            final Long time = this.plugin.blockFadeTimeMap.get(loc);
                            final Material type = this.plugin.blockFadeTypeMap.get(loc);
                            final Boolean water = this.plugin.blockFadeWaterMap.get(loc);
                            if (time != null && type != null && water != null) {
                                final long secsElapsed =
                                        (System.currentTimeMillis() - this.plugin.blockFadeTimeMap.get(loc)) / 1000;
                                // has enough time passed to fade the block?
                                if (secsElapsed > this.settings.FadeWrecksAfter) {
                                    // load the chunk if it hasn't been already
                                    final int cx = loc.x() >> 4;
                                    final int cz = loc.z() >> 4;
                                    if (!w.isChunkLoaded(cx, cz)) {
                                        w.loadChunk(cx, cz);
                                    }
                                    // check to see if the block type has changed, if so don't fade it
                                    if (w.getBlockAt(loc.x(), loc.y(), loc.z()).getType() == this.plugin.blockFadeTypeMap.get(loc)) {
                                        // should it become water? if not, then air
                                        if (this.plugin.blockFadeWaterMap.get(loc)) {
                                            final MapUpdateCommand.MoveBlock updateCom =
                                                    new MapUpdateCommand.MoveBlock(loc, Material.STATIONARY_WATER, null);
                                            updateCommands.add(updateCom);
                                        } else {
                                            final MapUpdateCommand.MoveBlock updateCom =
                                                    new MapUpdateCommand.MoveBlock(loc, Material.AIR, null);
                                            updateCommands.add(updateCom);
                                        }
                                    }
                                    this.plugin.blockFadeTimeMap.remove(loc);
                                    this.plugin.blockFadeTypeMap.remove(loc);
                                    this.plugin.blockFadeWorldMap.remove(loc);
                                    this.plugin.blockFadeWaterMap.remove(loc);
                                }
                            }
                        }
                    }
                    if (!updateCommands.isEmpty()) {
                        this.mapUpdateManager.addWorldUpdate(w, updateCommands.toArray(
                                new MapUpdateCommand.MoveBlock[updateCommands.size()]), null, null);
                    }
                }
            }

            this.lastFadeCheck = System.currentTimeMillis();
        }
    }

    @Override public void run() {
        this.clearAll();
        this.processCruise();
        this.processSinking();
        this.processTracers();
        this.processFireballs();
        this.processTNTContactExplosives();
        this.processFadingBlocks();
        this.processAlgorithmQueue();
    }

    private void clear(final Craft c) {
        this.clearanceSet.add(c);
    }

    private void clearAll() {
        for (final Craft c : this.clearanceSet) {
            c.setProcessing(false);
        }

        this.clearanceSet.clear();
    }
}
