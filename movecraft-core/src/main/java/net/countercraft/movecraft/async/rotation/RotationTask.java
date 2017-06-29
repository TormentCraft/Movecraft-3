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

package net.countercraft.movecraft.async.rotation;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.api.BlockVec;
import net.countercraft.movecraft.api.Rotation;
import net.countercraft.movecraft.async.AsyncTask;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.BlockUtils;
import net.countercraft.movecraft.utils.EntityUpdateCommand;
import net.countercraft.movecraft.utils.MapUpdateCommand;
import net.countercraft.movecraft.utils.MathUtils;
import net.countercraft.movecraft.utils.WGCustomFlagsUtils;
import org.apache.commons.collections.ListUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RotationTask extends AsyncTask {
    private final Movecraft plugin;
    private final Settings settings;
    private final I18nSupport i18n;
    private final CraftManager craftManager;
    private final BlockVec originPoint;
    private boolean failed = false;
    private String failMessage;
    private BlockVec[] blockList;    // used to be final, not sure why. Changed by Mark / Loraxe42
    private MapUpdateCommand[] updates;
    private EntityUpdateCommand[] entityUpdates;
    private int[][][] hitbox;
    private Integer minX, minZ;
    private final Rotation rotation;
    private final World w;
    private final boolean isSubCraft;

    public RotationTask(Craft c, Movecraft plugin, Settings settings, I18nSupport i18n, CraftManager craftManager,
                        BlockVec originPoint, BlockVec[] blockList, Rotation rotation, World w)
    {
        super(c);
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
        this.originPoint = originPoint;
        this.blockList = blockList;
        this.rotation = rotation;
        this.w = w;
        this.isSubCraft = false;
    }

    public RotationTask(Craft c, Movecraft plugin, Settings settings, I18nSupport i18n, CraftManager craftManager,
                        BlockVec originPoint, BlockVec[] blockList, Rotation rotation, World w,
                        boolean isSubCraft)
    {
        super(c);
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
        this.originPoint = originPoint;
        this.blockList = blockList;
        this.rotation = rotation;
        this.w = w;
        this.isSubCraft = isSubCraft;
    }

    @Override public void execute() {

        int[][][] hb = getCraft().getHitBox();
        if (hb == null) return;

        // Determine craft borders
        int minY = 65535;
        int maxY = -65535;
        for (int[][] i1 : hb) {
            for (int[] i2 : i1) {
                if (i2 != null) {
                    if (i2[0] < minY) {
                        minY = i2[0];
                    }
                    if (i2[1] > maxY) {
                        maxY = i2[1];
                    }
                }
            }
        }
        Integer maxX = getCraft().getMinX() + hb.length;
        Integer maxZ = getCraft().getMinZ() +
                       hb[0].length;  // safe because if the first x array doesn't have a z array, then it wouldn't
        // be the first x array
        minX = getCraft().getMinX();
        minZ = getCraft().getMinZ();

        int distX = maxX - minX;
        int distZ = maxZ - minZ;

        Player craftPilot = craftManager.getPlayerFromCraft(getCraft());

        // blockedByWater=false means an ocean-going vessel
        boolean waterCraft = !getCraft().getType().blockedByWater();

        int waterLine = 0;
        if (waterCraft) {
            // next figure out the water level by examining blocks next to the outer boundaries of the craft
            for (int posY = maxY; (posY >= minY) && (waterLine == 0); posY--) {
                int posX;
                int posZ = getCraft().getMinZ() - 1;
                for (posX = getCraft().getMinX() - 1; (posX <= maxX + 1) && (waterLine == 0); posX++) {
                    if (w.getBlockAt(posX, posY, posZ).getTypeId() == 9) {
                        waterLine = posY;
                    }
                }
                posZ = maxZ + 1;
                for (posX = getCraft().getMinX() - 1; (posX <= maxX + 1) && (waterLine == 0); posX++) {
                    if (w.getBlockAt(posX, posY, posZ).getTypeId() == 9) {
                        waterLine = posY;
                    }
                }
                posX = getCraft().getMinX() - 1;
                for (posZ = getCraft().getMinZ(); (posZ <= maxZ) && (waterLine == 0); posZ++) {
                    if (w.getBlockAt(posX, posY, posZ).getTypeId() == 9) {
                        waterLine = posY;
                    }
                }
                posX = maxX + 1;
                for (posZ = getCraft().getMinZ(); (posZ <= maxZ) && (waterLine == 0); posZ++) {
                    if (w.getBlockAt(posX, posY, posZ).getTypeId() == 9) {
                        waterLine = posY;
                    }
                }
            }

            // now add all the air blocks found within the crafts borders below the waterline to the craft blocks so
            // they will be rotated
            HashSet<BlockVec> newHSBlockList = new HashSet<>(Arrays.asList(blockList));
            for (int posY = waterLine; posY >= minY; posY--) {
                for (int posX = getCraft().getMinX(); posX <= maxX; posX++) {
                    for (int posZ = getCraft().getMinZ(); posZ <= maxZ; posZ++) {
                        if (w.getBlockAt(posX, posY, posZ).getTypeId() == 0) {
                            BlockVec l = new BlockVec(posX, posY, posZ);
                            newHSBlockList.add(l);
                        }
                    }
                }
            }
            blockList = newHSBlockList.toArray(new BlockVec[newHSBlockList.size()]);
        }

        // check for fuel, burn some from a furnace if needed. Blocks of coal are supported, in addition to coal and
        // charcoal
        double fuelBurnRate = getCraft().getType().getFuelBurnRate();
        if (fuelBurnRate != 0.0 && !getCraft().getSinking()) {
            if (getCraft().getBurningFuel() < fuelBurnRate) {
                Block fuelHolder = null;
                for (BlockVec bTest : blockList) {
                    Block b = getCraft().getW().getBlockAt(bTest.x, bTest.y, bTest.z);
                    if (b.getTypeId() == 61) {
                        InventoryHolder inventoryHolder = (InventoryHolder) b.getState();
                        if (inventoryHolder.getInventory().contains(263) ||
                            inventoryHolder.getInventory().contains(173)) {
                            fuelHolder = b;
                        }
                    }
                }
                if (fuelHolder == null) {
                    failed = true;
                    failMessage = i18n.get("Translation - Failed Craft out of fuel");
                } else {
                    InventoryHolder inventoryHolder = (InventoryHolder) fuelHolder.getState();
                    if (inventoryHolder.getInventory().contains(263)) {
                        ItemStack iStack = inventoryHolder.getInventory()
                                                          .getItem(inventoryHolder.getInventory().first(263));
                        int amount = iStack.getAmount();
                        if (amount == 1) {
                            inventoryHolder.getInventory().remove(iStack);
                        } else {
                            iStack.setAmount(amount - 1);
                        }
                        getCraft().setBurningFuel(getCraft().getBurningFuel() + 7.0);
                    } else {
                        ItemStack iStack = inventoryHolder.getInventory()
                                                          .getItem(inventoryHolder.getInventory().first(173));
                        int amount = iStack.getAmount();
                        if (amount == 1) {
                            inventoryHolder.getInventory().remove(iStack);
                        } else {
                            iStack.setAmount(amount - 1);
                        }
                        getCraft().setBurningFuel(getCraft().getBurningFuel() + 79.0);
                    }
                }
            } else {
                getCraft().setBurningFuel(getCraft().getBurningFuel() - fuelBurnRate);
            }
        }

        // Rotate the block set
        BlockVec[] centeredBlockList = new BlockVec[blockList.length];
        BlockVec[] originalBlockList = blockList.clone();
        Set<BlockVec> existingBlockSet = new HashSet<>(Arrays.asList(originalBlockList));
        Set<MapUpdateCommand> mapUpdates = new HashSet<>();
        HashSet<EntityUpdateCommand> entityUpdateSet = new HashSet<>();

        // make the centered block list, and check for a cruise control sign to reset to off
        for (int i = 0; i < blockList.length; i++) {
            centeredBlockList[i] = blockList[i].subtract(originPoint);
        }
        if (getCraft().getCruising()) {
            getCraft().resetSigns(true, true, true);
        }

        getCraft().setCruising(false);

        int craftMinY = 0;
        int craftMaxY = 0;
        for (int i = 0; i < blockList.length; i++) {

            blockList[i] = MathUtils.rotateVec(rotation, centeredBlockList[i]).add(originPoint);
            int typeID = w.getBlockTypeIdAt(blockList[i].x, blockList[i].y, blockList[i].z);

            Material testMaterial = w.getBlockAt(originalBlockList[i].x, originalBlockList[i].y, originalBlockList[i].z)
                                     .getType();

            if (testMaterial == Material.CHEST || testMaterial == Material.TRAPPED_CHEST) {
                if (!checkChests(testMaterial, blockList[i], existingBlockSet)) {
                    //prevent chests collision
                    failed = true;
                    failMessage = String
                            .format(i18n.get("Rotation - Craft is obstructed") + " @ %d,%d,%d", blockList[i].x,
                                    blockList[i].y, blockList[i].z);
                    break;
                }
            }
            Location plugLoc = new Location(w, blockList[i].x, blockList[i].y, blockList[i].z);
            if (craftPilot != null) {
                // See if they are permitted to build in the area, if WorldGuard integration is turned on
                if (plugin.getWorldGuardPlugin() != null && settings.WorldGuardBlockMoveOnBuildPerm) {
                    if (!plugin.getWorldGuardPlugin().canBuild(craftPilot, plugLoc)) {
                        failed = true;
                        failMessage = String.format(i18n.get(
                                "Rotation - Player is not permitted to build in this WorldGuard region") +
                                                    " @ %d,%d,%d", blockList[i].x, blockList[i].y, blockList[i].z);
                        break;
                    }
                }
            }

            Player p;
            if (craftPilot == null) {
                p = getCraft().getNotificationPlayer();
            } else {
                p = craftPilot;
            }
            if (p != null) {
                if (plugin.getWorldGuardPlugin() != null &&
                    plugin.getWGCustomFlagsPlugin() != null && settings.WGCustomFlagsUsePilotFlag) {
                    LocalPlayer lp = plugin.getWorldGuardPlugin().wrapPlayer(p);
                    if (!WGCustomFlagsUtils
                            .validateFlag(plugin.getWorldGuardPlugin(), plugLoc, plugin.FLAG_ROTATE, lp)) {
                        failed = true;
                        failMessage = String
                                .format(i18n.get("WGCustomFlags - Rotation Failed") + " @ %d,%d,%d", blockList[i].x,
                                        blockList[i].y, blockList[i].z);
                        break;
                    }
                }
            }

            if (waterCraft) {
                // allow watercraft to rotate through water
                if ((typeID != 0 && typeID != 9 && typeID != 34) && !existingBlockSet.contains(blockList[i])) {
                    failed = true;
                    failMessage = String
                            .format(i18n.get("Rotation - Craft is obstructed") + " @ %d,%d,%d", blockList[i].x,
                                    blockList[i].y, blockList[i].z);
                    break;
                } else {
                    int id = w.getBlockTypeIdAt(originalBlockList[i].x, originalBlockList[i].y, originalBlockList[i].z);
                    byte data = w.getBlockAt(originalBlockList[i].x, originalBlockList[i].y, originalBlockList[i].z)
                                 .getData();
                    if (BlockUtils.blockRequiresRotation(id)) {
                        data = BlockUtils.rotate(data, id, rotation);
                    }
                    mapUpdates.add(new MapUpdateCommand(originalBlockList[i], blockList[i], id, data, rotation,
                                                        getCraft()));
                }
            } else {
                if ((typeID != 0 && typeID != 34) && !existingBlockSet.contains(blockList[i])) {
                    failed = true;
                    failMessage = String
                            .format(i18n.get("Rotation - Craft is obstructed") + " @ %d,%d,%d", blockList[i].x,
                                    blockList[i].y, blockList[i].z);
                    break;
                } else {
                    int id = w.getBlockTypeIdAt(originalBlockList[i].x, originalBlockList[i].y, originalBlockList[i].z);
                    byte data = w.getBlockAt(originalBlockList[i].x, originalBlockList[i].y, originalBlockList[i].z)
                                 .getData();
                    if (BlockUtils.blockRequiresRotation(id)) {
                        data = BlockUtils.rotate(data, id, rotation);
                    }
                    mapUpdates.add(new MapUpdateCommand(originalBlockList[i], blockList[i], id, data, rotation,
                                                        getCraft()));
                }
            }
        }

        if (!failed) {
            //rotate entities in the craft
            Location tOP = new Location(getCraft().getW(), originPoint.x, originPoint.y, originPoint.z);

            List<Entity> eList = null;
            int numTries = 0;

            while ((eList == null) && (numTries < 100)) {
                try {
                    eList = getCraft().getW().getEntities();
                } catch (java.util.ConcurrentModificationException e) {
                    numTries++;
                }
            }
            for (Entity pTest : getCraft().getW().getEntities()) {
                if (MathUtils.playerIsWithinBoundingPolygon(getCraft().getHitBox(), getCraft().getMinX(),
                                                            getCraft().getMinZ(),
                                                            MathUtils.bukkit2MovecraftLoc(pTest.getLocation()))) {
                    if (pTest.getType() == EntityType.DROPPED_ITEM) {
                        //	pTest.remove();   removed to test cleaner fragile item removal
                    } else {
                        // Player is onboard this craft
                        tOP.setX(tOP.getBlockX() + 0.5);
                        tOP.setZ(tOP.getBlockZ() + 0.5);
                        Location playerLoc = pTest.getLocation();
                        if (getCraft().getPilotLocked() && pTest == craftManager.getPlayerFromCraft(getCraft())) {
                            playerLoc.setX(getCraft().getPilotLockedX());
                            playerLoc.setY(getCraft().getPilotLockedY());
                            playerLoc.setZ(getCraft().getPilotLockedZ());
                        }
                        Location adjustedPLoc = playerLoc.subtract(tOP);

                        double[] rotatedCoords = MathUtils
                                .rotateVecNoRound(rotation, adjustedPLoc.getX(), adjustedPLoc.getZ());
                        Location rotatedPloc = new Location(getCraft().getW(), rotatedCoords[0], playerLoc.getY(),
                                                            rotatedCoords[1]);
                        Location newPLoc = rotatedPloc.add(tOP);

                        newPLoc.setPitch(playerLoc.getPitch());
                        float newYaw = playerLoc.getYaw();
                        if (rotation == Rotation.CLOCKWISE) {
                            newYaw = newYaw + 90.0F;
                            if (newYaw >= 360.0F) {
                                newYaw = newYaw - 360.0F;
                            }
                        }
                        if (rotation == Rotation.ANTICLOCKWISE) {
                            newYaw = newYaw - 90;
                            if (newYaw < 0.0F) {
                                newYaw = newYaw + 360.0F;
                            }
                        }
                        newPLoc.setYaw(newYaw);

                        if (getCraft().getPilotLocked() && pTest == craftManager.getPlayerFromCraft(getCraft())) {
                            getCraft().setPilotLockedX(newPLoc.getX());
                            getCraft().setPilotLockedY(newPLoc.getY());
                            getCraft().setPilotLockedZ(newPLoc.getZ());
                        }
                        EntityUpdateCommand eUp = new EntityUpdateCommand(pTest.getLocation().clone(), newPLoc, pTest);
                        entityUpdateSet.add(eUp);
                        if (getCraft().getPilotLocked() && pTest == craftManager.getPlayerFromCraft(getCraft())) {
                            getCraft().setPilotLockedX(newPLoc.getX());
                            getCraft().setPilotLockedY(newPLoc.getY());
                            getCraft().setPilotLockedZ(newPLoc.getZ());
                        }
                    }
                }
            }

/*			//update player spawn locations if they spawned where the ship used to be
            for(Player p : plugin.getServer().getOnlinePlayers()) {
				if(p.getBedSpawnLocation()!=null) {
					if( MathUtils.playerIsWithinBoundingPolygon( getCraft().getHitBox(), getCraft().getMinX(),
					getCraft().getMinZ(), MathUtils.bukkit2MovecraftLoc( p.getBedSpawnLocation() ) ) ) {
						Location spawnLoc = p.getBedSpawnLocation();
						Location adjustedPLoc = spawnLoc.subtract( tOP ); 

						double[] rotatedCoords = MathUtils.rotateVecNoRound( rotation, adjustedPLoc.getX(),
						adjustedPLoc.getZ() );
						Location rotatedPloc = new Location( getCraft().getW(), rotatedCoords[0], spawnLoc.getY(),
						rotatedCoords[1] );
						Location newBedSpawn = rotatedPloc.add( tOP );

						p.setBedSpawnLocation(newBedSpawn, true);
					}
				}
			}*/

            // Calculate air changes
            List<BlockVec> airLocation = ListUtils
                    .subtract(Arrays.asList(originalBlockList), Arrays.asList(blockList));

            for (BlockVec l1 : airLocation) {
                if (waterCraft) {
                    // if its below the waterline, fill in with water. Otherwise fill in with air.
                    if (l1.y <= waterLine) {
                        mapUpdates.add(new MapUpdateCommand(l1, 9, (byte) 0, null));
                    } else {
                        mapUpdates.add(new MapUpdateCommand(l1, 0, (byte) 0, null));
                    }
                } else {
                    mapUpdates.add(new MapUpdateCommand(l1, 0, (byte) 0, null));
                }
            }

            this.updates = mapUpdates.toArray(new MapUpdateCommand[1]);
            this.entityUpdates = entityUpdateSet.toArray(new EntityUpdateCommand[1]);

            maxX = null;
            maxZ = null;
            minX = null;
            minZ = null;

            for (BlockVec l : blockList) {
                if (maxX == null || l.x > maxX) {
                    maxX = l.x;
                }
                if (maxZ == null || l.z > maxZ) {
                    maxZ = l.z;
                }
                if (minX == null || l.x < minX) {
                    minX = l.x;
                }
                if (minZ == null || l.z < minZ) {
                    minZ = l.z;
                }
            }

            // Rerun the polygonal bounding formula for the newly formed craft
            int sizeX = (maxX - minX) + 1;
            int sizeZ = (maxZ - minZ) + 1;

            int[][][] polygonalBox = new int[sizeX][][];

            for (BlockVec l : blockList) {
                if (polygonalBox[l.x - minX] == null) {
                    polygonalBox[l.x - minX] = new int[sizeZ][];
                }

                if (polygonalBox[l.x - minX][l.z - minZ] == null) {

                    polygonalBox[l.x - minX][l.z - minZ] = new int[2];
                    polygonalBox[l.x - minX][l.z - minZ][0] = l.y;
                    polygonalBox[l.x - minX][l.z - minZ][1] = l.y;
                } else {
                    minY = polygonalBox[l.x - minX][l.z - minZ][0];
                    maxY = polygonalBox[l.x - minX][l.z - minZ][1];

                    if (l.y < minY) {
                        polygonalBox[l.x - minX][l.z - minZ][0] = l.y;
                    }
                    if (l.y > maxY) {
                        polygonalBox[l.x - minX][l.z - minZ][1] = l.y;
                    }
                }
            }

            this.hitbox = polygonalBox;

            // if you rotated a subcraft, update the parent with the new blocks
            if (this.isSubCraft) {
                // also find the furthest extent from center and notify the player of the new direction
                int farthestX = 0;
                int farthestZ = 0;
                for (BlockVec loc : blockList) {
                    if (Math.abs(loc.x - originPoint.x) > Math.abs(farthestX)) farthestX = loc.x - originPoint.x;
                    if (Math.abs(loc.z - originPoint.z) > Math.abs(farthestZ)) farthestZ = loc.z - originPoint.z;
                }
                if (Math.abs(farthestX) > Math.abs(farthestZ)) {
                    if (farthestX > 0) {
                        if (getCraft().getNotificationPlayer() != null)
                            getCraft().getNotificationPlayer().sendMessage("The farthest extent now faces East");
                    } else {
                        if (getCraft().getNotificationPlayer() != null)
                            getCraft().getNotificationPlayer().sendMessage("The farthest extent now faces West");
                    }
                } else {
                    if (farthestZ > 0) {
                        if (getCraft().getNotificationPlayer() != null)
                            getCraft().getNotificationPlayer().sendMessage("The farthest extent now faces South");
                    } else {
                        if (getCraft().getNotificationPlayer() != null)
                            getCraft().getNotificationPlayer().sendMessage("The farthest extent now faces North");
                    }
                }

                Craft[] craftsInWorld = craftManager.getCraftsInWorld(getCraft().getW());
                for (Craft craft : craftsInWorld) {
                    if (BlockUtils.arrayContainsOverlap(craft.getBlockList(), originalBlockList) &&
                        craft != getCraft()) {
                        // found a parent craft
                        if (!craft.isNotProcessing()) {
                            failed = true;
                            failMessage = i18n.get("Parent Craft is busy");
                            return;
                        }

                        List<BlockVec> parentBlockList = ListUtils
                                .subtract(Arrays.asList(craft.getBlockList()), Arrays.asList(originalBlockList));
                        parentBlockList.addAll(Arrays.asList(blockList));
                        craft.setBlockList(parentBlockList.toArray(new BlockVec[1]));

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
                                int parentMinY = parentPolygonalBox[l.x - parentMinX][l.z - parentMinZ][0];
                                int parentMaxY = parentPolygonalBox[l.x - parentMinX][l.z - parentMinZ][1];

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
    }

    public BlockVec getOriginPoint() {
        return originPoint;
    }

    public boolean isFailed() {
        return failed;
    }

    public String getFailMessage() {
        return failMessage;
    }

    public BlockVec[] getBlockList() {
        return blockList;
    }

    public MapUpdateCommand[] getUpdates() {
        return updates;
    }

    public EntityUpdateCommand[] getEntityUpdates() {
        return entityUpdates;
    }

    public int[][][] getHitbox() {
        return hitbox;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinZ() {
        return minZ;
    }

    public Rotation getRotation() {
        return rotation;
    }

    public boolean getIsSubCraft() {
        return isSubCraft;
    }

    private boolean checkChests(Material mBlock, BlockVec newLoc, Set<BlockVec> existingBlockSet) {
        BlockVec aroundNewLoc = newLoc.translate(1, 0, 0);
        Material testMaterial = getCraft().getW().getBlockAt(aroundNewLoc.x, aroundNewLoc.y, aroundNewLoc.z).getType();
        if (testMaterial == mBlock) {
            if (!existingBlockSet.contains(aroundNewLoc)) {
                return false;
            }
        }

        aroundNewLoc = newLoc.translate(-1, 0, 0);
        testMaterial = getCraft().getW().getBlockAt(aroundNewLoc.x, aroundNewLoc.y, aroundNewLoc.z).getType();
        if (testMaterial == mBlock) {
            if (!existingBlockSet.contains(aroundNewLoc)) {
                return false;
            }
        }

        aroundNewLoc = newLoc.translate(0, 0, 1);
        testMaterial = getCraft().getW().getBlockAt(aroundNewLoc.x, aroundNewLoc.y, aroundNewLoc.z).getType();
        if (testMaterial == mBlock) {
            if (!existingBlockSet.contains(aroundNewLoc)) {
                return false;
            }
        }

        aroundNewLoc = newLoc.translate(0, 0, -1);
        testMaterial = getCraft().getW().getBlockAt(aroundNewLoc.x, aroundNewLoc.y, aroundNewLoc.z).getType();
        if (testMaterial == mBlock) {
            if (!existingBlockSet.contains(aroundNewLoc)) {
                return false;
            }
        }
        return true;
    }
}
