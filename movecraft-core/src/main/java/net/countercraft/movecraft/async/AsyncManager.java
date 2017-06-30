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
import com.google.common.base.Optional;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.countercraft.movecraft.Events;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.api.BlockVec;
import net.countercraft.movecraft.api.Direction;
import net.countercraft.movecraft.api.MaterialDataPredicate;
import net.countercraft.movecraft.api.Rotation;
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
import net.countercraft.movecraft.utils.EntityUpdateCommand;
import net.countercraft.movecraft.utils.ItemDropUpdateCommand;
import net.countercraft.movecraft.utils.MapUpdateCommand;
import net.countercraft.movecraft.utils.MapUpdateManager;
import net.countercraft.movecraft.utils.MathUtils;
import net.countercraft.movecraft.utils.WGCustomFlagsUtils;
import org.apache.commons.collections.ListUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
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
    private final @Nonnull Settings settings;
    private final @Nonnull I18nSupport i18n;
    private final @Nonnull CraftManager craftManager;
    private final @Nonnull Movecraft plugin;
    private final @Nonnull MapUpdateManager mapUpdateManager;
    private final @Nonnull Events events = new Events(Bukkit.getPluginManager());

    private final Map<AsyncTask, Craft> ownershipMap = new HashMap<>();
    private final Map<org.bukkit.entity.TNTPrimed, Double> TNTTracking = new HashMap<>();
    private final Map<Craft, HashMap<Craft, Long>> recentContactTracking = new HashMap<>();
    private final Map<org.bukkit.entity.SmallFireball, Long> FireballTracking = new HashMap<>();
    private final BlockingQueue<AsyncTask> finishedAlgorithms = new LinkedBlockingQueue<>();
    private final Set<Craft> clearanceSet = new HashSet<>();
    private long lastTracerUpdate = 0;
    private long lastFireballCheck = 0;
    private long lastTNTContactCheck = 0;
    private long lastFadeCheck = 0;
    private long lastContactCheck = 0;

    public AsyncManager(@Nonnull Settings settings, @Nonnull I18nSupport i18n, @Nonnull CraftManager craftManager,
                        @Nonnull Movecraft plugin, @Nonnull MapUpdateManager mapUpdateManager)
    {
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
        this.plugin = plugin;
        this.mapUpdateManager = mapUpdateManager;
    }

    public void detect(Craft craft, Player player, Player notificationPlayer, BlockVec startPoint) {
        submitTask(new DetectionTask(craft, startPoint, craft.type.getSizeRange(), craft.type.getAllowedBlocks(),
                                     craft.type.getForbiddenBlocks(), player, notificationPlayer, craft.w, plugin,
                                     settings, i18n),

                   craft);
    }

    public void translate(Craft craft, int dx, int dy, int dz) {
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
        for (BlockVec m : craft.getBlockList()) {
            if (m.x > cmaxX) cmaxX = m.x;
            if (m.z > cmaxZ) cmaxZ = m.z;
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
                if (!craft.getW().isChunkLoaded(posX, posZ)) {
                    craft.getW().loadChunk(posX, posZ);
                }
            }
        }

        submitTask(new TranslationTask(craft, plugin, settings, i18n, craftManager,
                                       new TranslationTaskData(dx, dz, dy, craft.getBlockList(), craft.getHitBox(),
                                                               craft.getMinZ(), craft.getMinX(),
                                                               craft.type.getHeightRange())), craft);
    }

    public void rotate(Craft craft, Rotation rotation, BlockVec originPoint) {
        // find region that will need to be loaded to rotate this craft
        int cminX = craft.getMinX();
        int cmaxX = craft.getMinX();
        int cminZ = craft.getMinZ();
        int cmaxZ = craft.getMinZ();
        for (BlockVec m : craft.getBlockList()) {
            if (m.x > cmaxX) cmaxX = m.x;
            if (m.z > cmaxZ) cmaxZ = m.z;
        }
        int distX = cmaxX - cminX;
        int distZ = cmaxZ - cminZ;
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
                if (!craft.getW().isChunkLoaded(posX, posZ)) {
                    craft.getW().loadChunk(posX, posZ);
                }
            }
        }

        craft.setCruiseDirection(craft.getCruiseDirection().rotateXZ(rotation));

        submitTask(new RotationTask(craft, plugin, settings, i18n, craftManager, originPoint, craft.getBlockList(),
                                    rotation, craft.getW()), craft);
    }

    public void rotate(Craft craft, Rotation rotation, BlockVec originPoint, boolean isSubCraft) {
        submitTask(new RotationTask(craft, plugin, settings, i18n, craftManager, originPoint, craft.getBlockList(),
                                    rotation, craft.getW(), isSubCraft), craft);
    }

    public void submitTask(final AsyncTask task, Craft c) {
        if (c.isNotProcessing()) {
            c.setProcessing(true);
            ownershipMap.put(task, c);

            new BukkitRunnable() {
                @Override public void run() {
                    try {
                        task.run();
                        submitCompletedTask(task);
                    } catch (Exception e) {
                        plugin.getLogger()
                              .log(Level.SEVERE, i18n.get("Internal - Error - Processor thread encountered an error"));
                        e.printStackTrace();
                    }
                }
            }.runTaskAsynchronously(plugin);
        }
    }

    public void submitCompletedTask(AsyncTask task) {
        finishedAlgorithms.add(task);
    }

    void processAlgorithmQueue() {
        int runLength = 10;
        int queueLength = finishedAlgorithms.size();

        runLength = Math.min(runLength, queueLength);

        for (int i = 0; i < runLength; i++) {
            boolean sentMapUpdate = false;
            AsyncTask poll = finishedAlgorithms.poll();
            Craft c = ownershipMap.get(poll);

            if (poll instanceof DetectionTask) {
                // Process detection task

                DetectionTask task = (DetectionTask) poll;
                DetectionTaskData data = task.getData();

                Player p = data.getPlayer();
                Player notifyP = data.getNotificationPlayer();
                Craft pCraft = craftManager.getCraftByPlayer(p);

                if (pCraft != null && p != null) {
                    //Player is already controlling a craft
                    notifyP.sendMessage(i18n.get("Detection - Failed - Already commanding a craft"));
                } else {
                    if (data.failed()) {
                        if (notifyP != null) notifyP.sendMessage(data.getFailMessage());
                        else plugin.getLogger()
                                   .log(Level.INFO, "NULL Player Craft Detection failed:" + data.getFailMessage());
                    } else {
                        Set<Craft> craftsInWorld = craftManager.getCraftsInWorld(c.getW());
                        boolean failed = false;

                        if (craftsInWorld != null) {
                            for (Craft craft : craftsInWorld) {

                                if (BlockUtils.arrayContainsOverlap(craft.getBlockList(), data.getBlockList()) &&
                                    (c.getType().getCruiseOnPilot() || p != null)) {  // changed from p!=null
                                    if (craft.getType() == c.getType() ||
                                        craft.getBlockList().length <= data.getBlockList().length) {
                                        notifyP.sendMessage(
                                                i18n.get("Detection - Failed Craft is already being controlled"));
                                        failed = true;
                                    } else { // if this is a different type than the overlapping craft, and is
                                        // smaller, this must be a child craft, like a fighter on a carrier
                                        if (!craft.isNotProcessing()) {
                                            failed = true;
                                            notifyP.sendMessage(i18n.get("Parent Craft is busy"));
                                        }

                                        // remove the new craft from the parent craft
                                        List<BlockVec> parentBlockList = ListUtils
                                                .subtract(Arrays.asList(craft.getBlockList()),
                                                          Arrays.asList(data.getBlockList()));
                                        craft.setBlockList(parentBlockList.toArray(new BlockVec[1]));
                                        craft.setOrigBlockCount(craft.getOrigBlockCount() - data.getBlockList().length);

                                        // Rerun the polygonal bounding formula for the parent craft
                                        Integer parentMaxX = null;
                                        Integer parentMaxZ = null;
                                        Integer parentMinX = null;
                                        Integer parentMinZ = null;
                                        for (BlockVec l : parentBlockList) {
                                            if (parentMaxX == null || l.x > parentMaxX) {
                                                parentMaxX = l.x;
                                            }
                                            if (parentMaxZ == null || l.z > parentMaxZ) {
                                                parentMaxZ = l.z;
                                            }
                                            if (parentMinX == null || l.x < parentMinX) {
                                                parentMinX = l.x;
                                            }
                                            if (parentMinZ == null || l.z < parentMinZ) {
                                                parentMinZ = l.z;
                                            }
                                        }
                                        int parentSizeX = (parentMaxX - parentMinX) + 1;
                                        int parentSizeZ = (parentMaxZ - parentMinZ) + 1;
                                        int[][][] parentPolygonalBox = new int[parentSizeX][][];
                                        for (BlockVec l : parentBlockList) {
                                            if (parentPolygonalBox[l.x - parentMinX] == null) {
                                                parentPolygonalBox[l.x - parentMinX] = new int[parentSizeZ][];
                                            }
                                            if (parentPolygonalBox[l.x - parentMinX][l.z - parentMinZ] == null) {
                                                parentPolygonalBox[l.x - parentMinX][l.z - parentMinZ] = new int[2];
                                                parentPolygonalBox[l.x - parentMinX][l.z - parentMinZ][0] = l.y;
                                                parentPolygonalBox[l.x - parentMinX][l.z - parentMinZ][1] = l.y;
                                            } else {
                                                int parentMinY = parentPolygonalBox[l.x - parentMinX][l.z -
                                                                                                      parentMinZ][0];
                                                int parentMaxY = parentPolygonalBox[l.x - parentMinX][l.z -
                                                                                                      parentMinZ][1];

                                                if (l.y < parentMinY) {
                                                    parentPolygonalBox[l.x - parentMinX][l.z - parentMinZ][0] = l.y;
                                                }
                                                if (l.y > parentMaxY) {
                                                    parentPolygonalBox[l.x - parentMinX][l.z - parentMinZ][1] = l.y;
                                                }
                                            }
                                        }
                                        craft.setHitBox(parentPolygonalBox);
                                    }
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
                                notifyP.sendMessage(i18n.get("Detection - Successfully piloted craft") +
                                                    " Size: " +
                                                    c.getBlockList().length);
                                plugin.getLogger().log(Level.INFO,
                                                       String.format(i18n.get("Detection - Success - Log Output"),
                                                                     notifyP.getName(), c.getType().getCraftName(),
                                                                     c.getBlockList().length, c.getMinX(),
                                                                     c.getMinZ()));
                            } else {
                                plugin.getLogger().log(Level.INFO,
                                                       String.format(i18n.get("Detection - Success - Log Output"),
                                                                     "NULL PLAYER", c.getType().getCraftName(),
                                                                     c.getBlockList().length, c.getMinX(),
                                                                     c.getMinZ()));
                            }
                            craftManager.addCraft(c, p);
                        }
                    }
                }
            } else if (poll instanceof TranslationTask) {
                //Process translation task

                TranslationTask task = (TranslationTask) poll;
                Player p = craftManager.getPlayerFromCraft(c);
                Player notifyP = c.getNotificationPlayer();

                // Check that the craft hasn't been sneakily unpiloted
                //		if ( p != null ) {     cruiseOnPilot crafts don't have player pilots

                if (task.getData().failed()) {
                    //The craft translation failed
                    if (notifyP != null && !c.getSinking()) notifyP.sendMessage(task.getData().getFailMessage());

                    if (task.getData().collisionExplosion()) {
                        MapUpdateCommand[] updates = task.getData().getUpdates();
                        c.setBlockList(task.getData().getBlockList());
                        boolean failed = mapUpdateManager.addWorldUpdate(c.getW(), updates, null, null);

                        if (failed) {
                            plugin.getLogger().log(Level.SEVERE, i18n.get("Translation - Craft collision"));
                        } else {
                            sentMapUpdate = true;
                        }
                    }
                } else {
                    //The craft is clear to move, perform the block updates

                    MapUpdateCommand[] updates = task.getData().getUpdates();
                    EntityUpdateCommand[] eUpdates = task.getData().getEntityUpdates();
                    ItemDropUpdateCommand[] iUpdates = task.getData().getItemDropUpdateCommands();
                    //get list of cannons before sending map updates, to avoid conflicts
                    Iterable<Cannon> shipCannons = null;
                    if (plugin.getCannonsPlugin() != null && c.getNotificationPlayer() != null) {
                        // convert blocklist to location list
                        List<Location> shipLocations = new ArrayList<>();
                        for (BlockVec loc : c.getBlockList()) {
                            Location tloc = new Location(c.getW(), loc.x, loc.y, loc.z);
                            shipLocations.add(tloc);
                        }
                        shipCannons = plugin.getCannonsPlugin().getCannonsAPI()
                                            .getCannons(shipLocations, c.getNotificationPlayer().getUniqueId(), true);
                    }
                    boolean failed = mapUpdateManager.addWorldUpdate(c.getW(), updates, eUpdates, iUpdates);

                    if (failed) {
                        plugin.getLogger().log(Level.SEVERE, i18n.get("Translation - Craft collision"));
                    } else {
                        sentMapUpdate = true;
                        c.setBlockList(task.getData().getBlockList());
                        c.setMinX(task.getData().getMinX());
                        c.setMinZ(task.getData().getMinZ());
                        c.setHitBox(task.getData().getHitbox());

                        // move any cannons that were present
                        if (plugin.getCannonsPlugin() != null && shipCannons != null) {
                            for (Cannon can : shipCannons) {
                                can.move(new Vector(task.getData().getDx(), task.getData().getDy(),
                                                    task.getData().getDz()));
                            }
                        }
                    }
                }
            } else if (poll instanceof RotationTask) {
                // Process rotation task
                RotationTask task = (RotationTask) poll;
                Player p = craftManager.getPlayerFromCraft(c);
                Player notifyP = c.getNotificationPlayer();

                // Check that the craft hasn't been sneakily unpiloted
                if (notifyP != null || task.getIsSubCraft()) {

                    if (task.isFailed()) {
                        //The craft translation failed, don't try to notify them if there is no pilot
                        if (notifyP != null) notifyP.sendMessage(task.getFailMessage());
                        else
                            plugin.getLogger().log(Level.INFO, "NULL Player Rotation Failed: " + task.getFailMessage());
                    } else {
                        MapUpdateCommand[] updates = task.getUpdates();
                        EntityUpdateCommand[] eUpdates = task.getEntityUpdates();

                        //get list of cannons before sending map updates, to avoid conflicts
                        Iterable<Cannon> shipCannons = null;
                        if (plugin.getCannonsPlugin() != null && c.getNotificationPlayer() != null) {
                            // convert blocklist to location list
                            List<Location> shipLocations = new ArrayList<>();
                            for (BlockVec loc : c.getBlockList()) {
                                Location tloc = new Location(c.getW(), loc.x, loc.y, loc.z);
                                shipLocations.add(tloc);
                            }
                            shipCannons = plugin.getCannonsPlugin().getCannonsAPI()
                                                .getCannons(shipLocations, c.getNotificationPlayer().getUniqueId(),
                                                            true);
                        }

                        boolean failed = mapUpdateManager.addWorldUpdate(c.getW(), updates, eUpdates, null);

                        if (failed) {
                            plugin.getLogger().log(Level.SEVERE, i18n.get("Rotation - Craft Collision"));
                        } else {
                            sentMapUpdate = true;

                            c.setBlockList(task.getBlockList());
                            c.setMinX(task.getMinX());
                            c.setMinZ(task.getMinZ());
                            c.setHitBox(task.getHitbox());

                            // rotate any cannons that were present
                            if (plugin.getCannonsPlugin() != null && shipCannons != null) {
                                Location tloc = new Location(task.getCraft().getW(), task.getOriginPoint().x,
                                                             task.getOriginPoint().y, task.getOriginPoint().z);
                                for (Cannon can : shipCannons) {
                                    if (task.getRotation() == Rotation.CLOCKWISE) can.rotateRight(tloc.toVector());
                                    if (task.getRotation() == Rotation.ANTICLOCKWISE) can.rotateLeft(tloc.toVector());
                                }
                            }
                        }
                    }
                }
            }

            ownershipMap.remove(poll);

            // only mark the craft as having finished updating if you didn't send any updates to the map updater.
            // Otherwise the map updater will mark the crafts once it is done with them.
            if (!sentMapUpdate) {
                clear(c);
            }
        }
    }

    public void processCruise() {
        for (World w : Bukkit.getWorlds()) {
            for (Craft pcraft : craftManager.getCraftsInWorld(w)) {
                if ((pcraft != null) && pcraft.isNotProcessing()) {
                    if (pcraft.getCruising()) {
                        long ticksElapsed = (System.currentTimeMillis() - pcraft.getLastCruiseUpdate()) / 50;

                        // if the craft should go slower underwater, make time pass more slowly there
                        if (pcraft.getType().getHalfSpeedUnderwater() && pcraft.getMinY() < w.getSeaLevel())
                            ticksElapsed = ticksElapsed >> 1;

                        if (Math.abs(ticksElapsed) >= pcraft.getType().getCruiseTickCooldown()) {
                            Direction direction = pcraft.getCruiseDirection();
                            int dx = direction.x * (1 + pcraft.getType().getCruiseSkipBlocks());
                            int dz = direction.z * (1 + pcraft.getType().getCruiseSkipBlocks());
                            int dy = direction.y * (1 + pcraft.getType().getVertCruiseSkipBlocks());

                            if (direction.y == -1 && pcraft.getMinY() <= w.getSeaLevel()) {
                                dy = -1;
                            }

                            if (pcraft.getType().getCruiseOnPilot())
                                dy = pcraft.getType().getCruiseOnPilotVertMove();

                            if ((dx != 0 && dz != 0) || ((dx + dz) != 0 && dy != 0)) {
                                // we need to slow the skip speed...
                                if (Math.abs(dx) > 1) dx /= 2;
                                if (Math.abs(dz) > 1) dz /= 2;
                            }

                            translate(pcraft, dx, dy, dz);
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
                                rcticksElapsed = rcticksElapsed >> 1;

                            rcticksElapsed = Math.abs(rcticksElapsed);
                            // if they are holding the button down, keep moving
                            if (rcticksElapsed <= 10) {
                                long ticksElapsed =
                                        (System.currentTimeMillis() - pcraft.getLastCruiseUpdate()) / 50;
                                if (Math.abs(ticksElapsed) >= pcraft.getType().getTickCooldown()) {
                                    translate(pcraft, pcraft.getLastDX(), pcraft.getLastDY(), pcraft.getLastDZ());
                                    pcraft.setLastCruiseUpdate(System.currentTimeMillis());
                                }
                            }
                        }
                        if (pcraft.getPilotLocked() && pcraft.isNotProcessing()) {

                            Player p = craftManager.getPlayerFromCraft(pcraft);
                            if (p != null) if (MathUtils
                                    .playerIsWithinBoundingPolygon(pcraft.getHitBox(), pcraft.getMinX(),
                                                                   pcraft.getMinZ(), MathUtils
                                                                           .bukkit2MovecraftLoc(p.getLocation()))) {
                                double movedX = p.getLocation().getX() - pcraft.getPilotLockedX();
                                double movedZ = p.getLocation().getZ() - pcraft.getPilotLockedZ();
                                int dX = 0;
                                if (movedX > 0.15) dX = 1;
                                else if (movedX < -0.15) dX = -1;
                                int dZ = 0;
                                if (movedZ > 0.15) dZ = 1;
                                else if (movedZ < -0.15) dZ = -1;
                                if (dX != 0 || dZ != 0) {
                                    long timeSinceLastMoveCommand =
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
                                            pcraft.getMinY() < w.getSeaLevel()) ticksElapsed = ticksElapsed >> 1;

                                        if (Math.abs(ticksElapsed) >= pcraft.getType().getTickCooldown()) {
                                            translate(pcraft, dX, 0, dZ);
                                            pcraft.setLastCruiseUpdate(System.currentTimeMillis());
                                        }
                                        pcraft.setLastDX(dX);
                                        pcraft.setLastDY(0);
                                        pcraft.setLastDZ(dZ);
                                        pcraft.setKeepMoving(true);
                                    } else {
                                        Location loc = p.getLocation();
                                        loc.setX(pcraft.getPilotLockedX());
                                        loc.setY(pcraft.getPilotLockedY());
                                        loc.setZ(pcraft.getPilotLockedZ());
                                        Vector pVel = new Vector(0.0, 0.0, 0.0);
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

    private boolean isRegionBlockedPVP(BlockVec loc, World w) {
        if (plugin.getWorldGuardPlugin() == null) return false;
        if (!settings.WorldGuardBlockSinkOnPVPPerm) return false;

        Location nativeLoc = new Location(w, loc.x, loc.y, loc.z);
        ApplicableRegionSet set = plugin.getWorldGuardPlugin().getRegionManager(w).getApplicableRegions(nativeLoc);
        return !set.allows(DefaultFlag.PVP);
    }

    private boolean isRegionFlagSinkAllowed(BlockVec loc, World w) {
        if (plugin.getWorldGuardPlugin() != null &&
            plugin.getWGCustomFlagsPlugin() != null && settings.WGCustomFlagsUseSinkFlag) {
            Location nativeLoc = new Location(w, loc.x, loc.y, loc.z);
            return WGCustomFlagsUtils.validateFlag(plugin.getWorldGuardPlugin(), nativeLoc, plugin.FLAG_SINK);
        } else {
            return true;
        }
    }

    public void processSinking() {
        for (World w : Bukkit.getWorlds()) {
            // check every few seconds for every craft to see if it should be sinking
            for (Craft pcraft : craftManager.getCraftsInWorld(w)) {
                if (pcraft != null && !pcraft.getSinking()) {
                    if (pcraft.getType().getSinkPercent() != 0.0 && pcraft.isNotProcessing()) {
                        long ticksElapsed = (System.currentTimeMillis() - pcraft.getLastBlockCheck()) / 50;

                        if (ticksElapsed > settings.SinkCheckTicks) {
                            int totalNonAirBlocks = 0;
                            int totalNonAirWaterBlocks = 0;
                            Map<MaterialDataPredicate, Integer> foundFlyBlocks = new HashMap<>();
                            boolean regionPVPBlocked = false;
                            boolean sinkingForbiddenByFlag = false;
                            // go through each block in the blocklist, and if its in the FlyBlocks, total up the
                            // number of them
                            for (BlockVec l : pcraft.getBlockList()) {
                                if (isRegionBlockedPVP(l, w)) regionPVPBlocked = true;
                                if (!isRegionFlagSinkAllowed(l, w)) sinkingForbiddenByFlag = true;
                                Integer blockID = w.getBlockAt(l.x, l.y, l.z).getTypeId();
                                for (MaterialDataPredicate flyBlockDef : pcraft.getType().getFlyBlocks().keySet()) {
                                    if (flyBlockDef.checkBlock(w.getBlockAt(l.x, l.y, l.z))) {
                                        Integer count = foundFlyBlocks.get(flyBlockDef);
                                        if (count == null) {
                                            foundFlyBlocks.put(flyBlockDef, 1);
                                        } else {
                                            foundFlyBlocks.put(flyBlockDef, count + 1);
                                        }
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
                            for (Map.Entry<MaterialDataPredicate, List<CraftType.Constraint>> entry : pcraft
                                    .getType().getFlyBlocks().entrySet()) {
                                MaterialDataPredicate predicate = entry.getKey();
                                List<CraftType.Constraint> constraints = entry.getValue();
                                int count = Optional.fromNullable(foundFlyBlocks.get(predicate)).or(0);

                                double percent = count / (double) totalNonAirBlocks;
                                for (CraftType.Constraint constraint : constraints) {
                                    if (constraint.isUpper) continue;

                                    double flyPercent = constraint.bound.asRatio(totalNonAirBlocks);
                                    double sinkPercent = flyPercent * pcraft.getType().getSinkPercent() / 100.0;
                                    if (percent < sinkPercent) {
                                        isSinking = true;
                                    }
                                }
                            }

                            // And check the overallsinkpercent
                            if (pcraft.getType().getOverallSinkPercent() != 0.0) {
                                double percent =
                                        (double) totalNonAirWaterBlocks / (double) pcraft.getOrigBlockCount();
                                if (percent * 100.0 < pcraft.getType().getOverallSinkPercent()) {
                                    isSinking = true;
                                }
                            }

                            if (totalNonAirBlocks == 0) {
                                isSinking = true;
                            }

                            boolean cancelled = events.sinkingStarted();

                            if (isSinking &&
                                (regionPVPBlocked || sinkingForbiddenByFlag) &&
                                pcraft.isNotProcessing() || cancelled) {
                                Player p = craftManager.getPlayerFromCraft(pcraft);
                                Player notifyP = pcraft.getNotificationPlayer();
                                if (notifyP != null) {
                                    if (regionPVPBlocked) {
                                        notifyP.sendMessage(i18n.get(
                                                "Player- Craft should sink but PVP is not allowed in this " +
                                                "WorldGuard " +
                                                "region"));
                                    } else if (sinkingForbiddenByFlag) {
                                        notifyP.sendMessage(
                                                i18n.get("WGCustomFlags - Sinking a craft is not allowed in this " +
                                                         "WorldGuard " +
                                                         "region"));
                                    } else {
                                        notifyP.sendMessage(i18n.get("Sinking a craft is not allowed."));
                                    }
                                }
                                pcraft.setCruising(false);
                                pcraft.setKeepMoving(false);
                                craftManager.removeCraft(pcraft);
                            } else {
                                // if the craft is sinking, let the player know and release the craft. Otherwise
                                // update the time for the next check
                                if (isSinking && pcraft.isNotProcessing()) {
                                    Player p = craftManager.getPlayerFromCraft(pcraft);
                                    Player notifyP = pcraft.getNotificationPlayer();
                                    if (notifyP != null) notifyP.sendMessage(i18n.get("Player- Craft is sinking"));
                                    pcraft.setCruising(false);
                                    pcraft.setKeepMoving(false);
                                    pcraft.setSinking(true);
                                    craftManager.removePlayerFromCraft(pcraft);
                                    final Craft releaseCraft = pcraft;
                                    BukkitTask releaseTask = new BukkitRunnable() {
                                        @Override public void run() {
                                            craftManager.removeCraft(releaseCraft);
                                        }
                                    }.runTaskLater(plugin, (20 * 600));
                                } else {
                                    pcraft.setLastBlockCheck(System.currentTimeMillis());
                                }
                            }
                        }
                    }
                }
            }

            // sink all the sinking ships
            for (Craft pcraft : craftManager.getCraftsInWorld(w)) {
                if (pcraft != null && pcraft.getSinking()) {
                    if (pcraft.getBlockList().length == 0) {
                        craftManager.removeCraft(pcraft);
                    }
                    if (pcraft.getMinY() < -1) {
                        craftManager.removeCraft(pcraft);
                    }
                    long ticksElapsed = (System.currentTimeMillis() - pcraft.getLastCruiseUpdate()) / 50;
                    if (Math.abs(ticksElapsed) >= pcraft.getType().getSinkRateTicks()) {
                        int dx = 0;
                        int dz = 0;
                        if (pcraft.getType().getKeepMovingOnSink()) {
                            dx = pcraft.getLastDX();
                            dz = pcraft.getLastDZ();
                        }
                        translate(pcraft, dx, -1, dz);
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

    public void processTracers() {
        if (settings.TracerRateTicks == 0) return;
        long ticksElapsed = (System.currentTimeMillis() - lastTracerUpdate) / 50;
        if (ticksElapsed > settings.TracerRateTicks) {
            for (World w : Bukkit.getWorlds()) {
                if (w != null) {
                    for (org.bukkit.entity.TNTPrimed tnt : w.getEntitiesByClass(org.bukkit.entity.TNTPrimed.class)) {
                        if (tnt.getVelocity().lengthSquared() > 0.25) {
                            for (Player p : w.getPlayers()) {
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
                                    BukkitTask placeCobweb = new BukkitRunnable() {
                                        @Override public void run() {
                                            fp.sendBlockChange(loc, 30, (byte) 0);
                                        }
                                    }.runTaskLater(plugin, 5);
                                    // then remove it
                                    BukkitTask removeCobweb = new BukkitRunnable() {
                                        @Override public void run() {
//											fp.sendBlockChange(loc, fw.getBlockAt(loc).getType(), fw.getBlockAt(loc)
// .getData());
                                            fp.sendBlockChange(loc, 0, (byte) 0);
                                        }
                                    }.runTaskLater(plugin, 160);
                                }
                            }
                        }
                    }
                }
            }
            lastTracerUpdate = System.currentTimeMillis();
        }
    }

    public void processFireballs() {
        long ticksElapsed = (System.currentTimeMillis() - lastFireballCheck) / 50;
        if (ticksElapsed > 4) {
            for (World w : Bukkit.getWorlds()) {
                if (w != null) {
                    for (org.bukkit.entity.SmallFireball fireball : w
                            .getEntitiesByClass(org.bukkit.entity.SmallFireball.class)) {
                        if (!(fireball.getShooter() instanceof org.bukkit.entity.LivingEntity)) { // means it was
                            // launched by a dispenser
                            if (!w.getPlayers().isEmpty()) {
                                Player p = w.getPlayers().get(0);
                                double closest = 1000000000.0;
                                for (Player pi : w.getPlayers()) {
                                    if (pi.getLocation().distanceSquared(fireball.getLocation()) < closest) {
                                        closest = pi.getLocation().distanceSquared(fireball.getLocation());
                                        p = pi;
                                    }
                                }
                                // give it a living shooter, then set the fireball to be deleted
                                fireball.setShooter(p);
                                final org.bukkit.entity.SmallFireball ffb = fireball;
                                if (!FireballTracking.containsKey(fireball)) {
                                    FireballTracking.put(fireball, System.currentTimeMillis());
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

            int timelimit = 20 * settings.FireballLifespan * 50;
            //then, removed any exploded TNT from tracking
            Iterator<org.bukkit.entity.SmallFireball> fireballI = FireballTracking.keySet().iterator();
            while (fireballI.hasNext()) {
                org.bukkit.entity.SmallFireball fireball = fireballI.next();
                if (fireball != null) if (System.currentTimeMillis() - FireballTracking.get(fireball) > timelimit) {
                    fireball.remove();
                    fireballI.remove();
                }
            }

            lastFireballCheck = System.currentTimeMillis();
        }
    }

    public void processTNTContactExplosives() {
        long ticksElapsed = (System.currentTimeMillis() - lastTNTContactCheck) / 50;
        if (ticksElapsed > 4) {
            // see if there is any new rapid moving TNT in the worlds
            for (World w : Bukkit.getWorlds()) {
                if (w != null) {
                    for (org.bukkit.entity.TNTPrimed tnt : w.getEntitiesByClass(org.bukkit.entity.TNTPrimed.class)) {
                        if (tnt.getVelocity().lengthSquared() > 0.35) {
                            if (!TNTTracking.containsKey(tnt)) {
                                TNTTracking.put(tnt, tnt.getVelocity().lengthSquared());
                            }
                        }
                    }
                }
            }

            //then, removed any exploded TNT from tracking
            Iterator<org.bukkit.entity.TNTPrimed> tntI = TNTTracking.keySet().iterator();
            while (tntI.hasNext()) {
                org.bukkit.entity.TNTPrimed tnt = tntI.next();
                if (tnt.getFuseTicks() <= 0) {
                    tntI.remove();
                }
            }

            //now check to see if any has abruptly changed velocity, and should explode
            for (org.bukkit.entity.TNTPrimed tnt : TNTTracking.keySet()) {
                double vel = tnt.getVelocity().lengthSquared();
                if (vel < TNTTracking.get(tnt) / 10.0) {
                    tnt.setFuseTicks(0);
                } else {
                    // update the tracking with the new velocity so gradual changes do not make TNT explode
                    TNTTracking.put(tnt, vel);
                }
            }

            lastTNTContactCheck = System.currentTimeMillis();
        }
    }

    public void processFadingBlocks() {
        if (settings.FadeWrecksAfter == 0) return;
        long ticksElapsed = (System.currentTimeMillis() - lastFadeCheck) / 50;
        if (ticksElapsed > 20) {
            for (World w : Bukkit.getWorlds()) {
                if (w != null) {
                    ArrayList<MapUpdateCommand> updateCommands = new ArrayList<>();
                    CopyOnWriteArrayList<BlockVec> locations = null;

                    // I know this is horrible, but I honestly don't see another way to do this...
                    int numTries = 0;
                    while ((locations == null) && (numTries < 100)) {
                        try {
                            locations = new CopyOnWriteArrayList<>(plugin.blockFadeTimeMap.keySet());
                        } catch (java.util.ConcurrentModificationException e) {
                            numTries++;
                        } catch (java.lang.NegativeArraySizeException e) {
                            plugin.blockFadeTimeMap = new HashMap<>();
                            plugin.blockFadeTypeMap = new HashMap<>();
                            plugin.blockFadeWaterMap = new HashMap<>();
                            plugin.blockFadeWorldMap = new HashMap<>();
                            locations = new CopyOnWriteArrayList<>(plugin.blockFadeTimeMap.keySet());
                        }
                    }

                    for (BlockVec loc : locations) {
                        if (plugin.blockFadeWorldMap.get(loc) == w) {
                            Long time = plugin.blockFadeTimeMap.get(loc);
                            Integer type = plugin.blockFadeTypeMap.get(loc);
                            Boolean water = plugin.blockFadeWaterMap.get(loc);
                            if (time != null && type != null && water != null) {
                                long secsElapsed =
                                        (System.currentTimeMillis() - plugin.blockFadeTimeMap.get(loc)) / 1000;
                                // has enough time passed to fade the block?
                                if (secsElapsed > settings.FadeWrecksAfter) {
                                    // load the chunk if it hasn't been already
                                    int cx = loc.x >> 4;
                                    int cz = loc.z >> 4;
                                    if (!w.isChunkLoaded(cx, cz)) {
                                        w.loadChunk(cx, cz);
                                    }
                                    // check to see if the block type has changed, if so don't fade it
                                    if (w.getBlockTypeIdAt(loc.x, loc.y, loc.z) == plugin.blockFadeTypeMap.get(loc)) {
                                        // should it become water? if not, then air
                                        if (plugin.blockFadeWaterMap.get(loc)) {
                                            MapUpdateCommand updateCom = new MapUpdateCommand(loc, 9, (byte) 0, null);
                                            updateCommands.add(updateCom);
                                        } else {
                                            MapUpdateCommand updateCom = new MapUpdateCommand(loc, 0, (byte) 0, null);
                                            updateCommands.add(updateCom);
                                        }
                                    }
                                    plugin.blockFadeTimeMap.remove(loc);
                                    plugin.blockFadeTypeMap.remove(loc);
                                    plugin.blockFadeWorldMap.remove(loc);
                                    plugin.blockFadeWaterMap.remove(loc);
                                }
                            }
                        }
                    }
                    if (!updateCommands.isEmpty()) {
                        mapUpdateManager.addWorldUpdate(w, updateCommands.toArray(new MapUpdateCommand[1]), null, null);
                    }
                }
            }

            lastFadeCheck = System.currentTimeMillis();
        }
    }

    public void processDetection() {
        long ticksElapsed = (System.currentTimeMillis() - lastContactCheck) / 50;
        if (ticksElapsed > 21) {
            for (World w : Bukkit.getWorlds()) {
                for (Craft ccraft : craftManager.getCraftsInWorld(w)) {
                    if (craftManager.getPlayerFromCraft(ccraft) != null) {
                        if (!recentContactTracking.containsKey(ccraft)) {
                            recentContactTracking.put(ccraft, new HashMap<Craft, Long>());
                        }
                        for (Craft tcraft : craftManager.getCraftsInWorld(w)) {
                            long cposx = ccraft.getMaxX() + ccraft.getMinX();
                            long cposy = ccraft.getMaxY() + ccraft.getMinY();
                            long cposz = ccraft.getMaxZ() + ccraft.getMinZ();
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
                                tcraft.getNotificationPlayer() != ccraft.getNotificationPlayer()) {
                                // craft has been detected

                                // has the craft not been seen in the last minute, or is completely new?
                                if (recentContactTracking.get(ccraft).get(tcraft) == null ||
                                    System.currentTimeMillis() - recentContactTracking.get(ccraft).get(tcraft) >
                                    60000) {
                                    String notification = "New contact: ";
                                    notification += tcraft.getType().getCraftName();
                                    notification += " commanded by ";
                                    if (tcraft.getNotificationPlayer() != null) {
                                        notification += tcraft.getNotificationPlayer().getDisplayName();
                                    } else {
                                        notification += "NULL";
                                    }
                                    notification += ", size: ";
                                    notification += tcraft.getOrigBlockCount();
                                    notification += ", range: ";
                                    notification += (int) Math.sqrt(distsquared);
                                    notification += " to the";
                                    if (Math.abs(diffx) > Math.abs(diffz)) if (diffx < 0) notification += " east.";
                                    else notification += " west.";
                                    else if (diffz < 0) notification += " south.";
                                    else notification += " north.";

                                    ccraft.getNotificationPlayer().sendMessage(notification);
                                    w.playSound(ccraft.getNotificationPlayer().getLocation(),
                                                Sound.BLOCK_ANVIL_LAND, 1.0f, 2.0f);
                                    final World sw = w;
                                    final Player sp = ccraft.getNotificationPlayer();
                                    BukkitTask replaysound = new BukkitRunnable() {
                                        @Override public void run() {
                                            sw.playSound(sp.getLocation(), Sound.BLOCK_ANVIL_LAND, 10.0f, 2.0f);
                                        }
                                    }.runTaskLater(plugin, (5));
                                }

                                long timestamp = System.currentTimeMillis();
                                recentContactTracking.get(ccraft).put(tcraft, timestamp);
                            }
                        }
                    }
                }
            }

            lastContactCheck = System.currentTimeMillis();
        }
    }

    @Override public void run() {
        clearAll();
        processCruise();
        processSinking();
        processTracers();
        processFireballs();
        processTNTContactExplosives();
        processFadingBlocks();
        processDetection();
        processAlgorithmQueue();
    }

    private void clear(Craft c) {
        clearanceSet.add(c);
    }

    private void clearAll() {
        for (Craft c : clearanceSet) {
            c.setProcessing(false);
        }

        clearanceSet.clear();
    }
}
