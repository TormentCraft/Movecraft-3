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

import com.alexknvl.shipcraft.math.BlockVec;
import com.alexknvl.shipcraft.math.RotationXZ;
import com.google.common.collect.Sets;
import com.sk89q.worldguard.LocalPlayer;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.async.AsyncTask;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.BlockUtils;
import net.countercraft.movecraft.detail.EntityUpdateCommand;
import net.countercraft.movecraft.detail.MapUpdateCommand;
import net.countercraft.movecraft.utils.MathUtils;
import net.countercraft.movecraft.utils.WGCustomFlagsUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

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
    private final RotationXZ rotation;
    private final World world;
    private final boolean isSubCraft;

    public RotationTask(final Craft c, final Movecraft plugin, final Settings settings, final I18nSupport i18n, final CraftManager craftManager,
                        final BlockVec originPoint, final BlockVec[] blockList, final RotationXZ rotation, final World world)
    {
        super(c);
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
        this.originPoint = originPoint;
        this.blockList = blockList;
        this.rotation = rotation;
        this.world = world;
        this.isSubCraft = false;
    }

    public RotationTask(final Craft c, final Movecraft plugin, final Settings settings, final I18nSupport i18n, final CraftManager craftManager,
                        final BlockVec originPoint, final BlockVec[] blockList, final RotationXZ rotation, final World world,
                        final boolean isSubCraft)
    {
        super(c);
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
        this.originPoint = originPoint;
        this.blockList = blockList;
        this.rotation = rotation;
        this.world = world;
        this.isSubCraft = isSubCraft;
    }

    @Override public void execute() {
        final int[][][] hb = this.getCraft().getHitBox();
        if (hb == null) return;

        // Determine craft borders
        int minY = 65535;
        int maxY = -65535;
        for (final int[][] i1 : hb) {
            for (final int[] i2 : i1) {
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

        // safe because if the first x array doesn't have a z array, then it wouldn't
        // be the first x array
        Integer maxX = this.getCraft().getMinX() + hb.length;
        Integer maxZ = this.getCraft().getMinZ() + hb[0].length;

        this.minX = this.getCraft().getMinX();
        this.minZ = this.getCraft().getMinZ();

        final Player craftPilot = this.craftManager.getPlayerFromCraft(this.getCraft());

        // blockedByWater=false means an ocean-going vessel
        final boolean waterCraft = !this.getCraft().getType().blockedByWater();

        int waterLine = 0;
        if (waterCraft) {
            // next figure out the water level by examining blocks next to the outer boundaries of the craft
            for (int posY = maxY; (posY >= minY) && (waterLine == 0); posY--) {
                int posX;
                int posZ = this.getCraft().getMinZ() - 1;
                for (posX = this.getCraft().getMinX() - 1; (posX <= maxX + 1) && (waterLine == 0); posX++) {
                    if (this.world.getBlockAt(posX, posY, posZ).getType() == Material.STATIONARY_WATER) {
                        waterLine = posY;
                    }
                }
                posZ = maxZ + 1;
                for (posX = this.getCraft().getMinX() - 1; (posX <= maxX + 1) && (waterLine == 0); posX++) {
                    if (this.world.getBlockAt(posX, posY, posZ).getType() == Material.STATIONARY_WATER) {
                        waterLine = posY;
                    }
                }
                posX = this.getCraft().getMinX() - 1;
                for (posZ = this.getCraft().getMinZ(); (posZ <= maxZ) && (waterLine == 0); posZ++) {
                    if (this.world.getBlockAt(posX, posY, posZ).getType() == Material.STATIONARY_WATER) {
                        waterLine = posY;
                    }
                }
                posX = maxX + 1;
                for (posZ = this.getCraft().getMinZ(); (posZ <= maxZ) && (waterLine == 0); posZ++) {
                    if (this.world.getBlockAt(posX, posY, posZ).getType() == Material.STATIONARY_WATER) {
                        waterLine = posY;
                    }
                }
            }

            // now add all the air blocks found within the crafts borders below the waterline to the craft blocks so
            // they will be rotated
            final HashSet<BlockVec> newHSBlockList = new HashSet<>(Arrays.asList(this.blockList));
            for (int posY = waterLine; posY >= minY; posY--) {
                for (int posX = this.getCraft().getMinX(); posX <= maxX; posX++) {
                    for (int posZ = this.getCraft().getMinZ(); posZ <= maxZ; posZ++) {
                        if (this.world.getBlockAt(posX, posY, posZ).getType() == Material.AIR) {
                            final BlockVec l = new BlockVec(posX, posY, posZ);
                            newHSBlockList.add(l);
                        }
                    }
                }
            }
            this.blockList = newHSBlockList.toArray(new BlockVec[newHSBlockList.size()]);
        }

        // Check for fuel, burn some from a furnace if needed.
        // Blocks of coal are supported, in addition to coal and charcoal.
        final double fuelBurnRate = this.getCraft().getType().getFuelBurnRate();
        if (fuelBurnRate != 0.0 && !this.getCraft().getSinking()) {
            if (this.getCraft().getBurningFuel() < fuelBurnRate) {
                Block fuelHolder = null;
                for (final BlockVec bTest : this.blockList) {
                    final Block b = this.getCraft().getWorld().getBlockAt(bTest.x(), bTest.y(), bTest.z());
                    if (b.getType() == Material.FURNACE) {
                        final InventoryHolder inventoryHolder = (InventoryHolder) b.getState();
                        final Inventory inv = inventoryHolder.getInventory();
                        if (inv.contains(Material.COAL) || inv.contains(Material.COAL_BLOCK)) {
                            fuelHolder = b;
                        }
                    }
                }
                if (fuelHolder == null) {
                    this.failed = true;
                    this.failMessage = this.i18n.get("Translation - Failed Craft out of fuel");
                } else {
                    final InventoryHolder inventoryHolder = (InventoryHolder) fuelHolder.getState();
                    final Inventory inv = inventoryHolder.getInventory();
                    if (inv.contains(Material.COAL)) {
                        final ItemStack iStack = inv.getItem(inv.first(Material.COAL));
                        final int amount = iStack.getAmount();
                        if (amount == 1) {
                            inv.remove(iStack);
                        } else {
                            iStack.setAmount(amount - 1);
                        }
                        this.getCraft().setBurningFuel(this.getCraft().getBurningFuel() + 7.0);
                    } else {
                        final ItemStack iStack = inv.getItem(inv.first(Material.COAL_BLOCK));
                        final int amount = iStack.getAmount();
                        if (amount == 1) {
                            inv.remove(iStack);
                        } else {
                            iStack.setAmount(amount - 1);
                        }
                        this.getCraft().setBurningFuel(this.getCraft().getBurningFuel() + 79.0);
                    }
                }
            } else {
                this.getCraft().setBurningFuel(this.getCraft().getBurningFuel() - fuelBurnRate);
            }
        }

        // Rotate the block set.
        final BlockVec[] centeredBlockList = new BlockVec[this.blockList.length];
        final BlockVec[] originalBlockList = this.blockList.clone();
        final Set<BlockVec> existingBlockSet = Sets.newHashSet(originalBlockList);
        final Set<MapUpdateCommand> mapUpdates = new HashSet<>();
        final HashSet<EntityUpdateCommand> entityUpdateSet = new HashSet<>();

        // Make the centered block list, and check for a cruise control sign to reset to off.
        for (int i = 0; i < this.blockList.length; i++) {
            centeredBlockList[i] = this.blockList[i].subtract(this.originPoint);
        }

        for (int i = 0; i < this.blockList.length; i++) {
            this.blockList[i] = centeredBlockList[i].rotate(this.rotation).add(this.originPoint);
            final Material typeID = this.world
                    .getBlockAt(this.blockList[i].x(), this.blockList[i].y(), this.blockList[i].z()).getType();

            final MaterialData testMaterialData = this.world
                    .getBlockAt(originalBlockList[i].x(), originalBlockList[i].y(), originalBlockList[i].z())
                    .getState().getData();
            final Material testMaterial = testMaterialData.getItemType();

            if (testMaterial == Material.CHEST || testMaterial == Material.TRAPPED_CHEST) {
                if (!this.checkChests(testMaterial, this.blockList[i], existingBlockSet)) {
                    //prevent chests collision
                    this.failed = true;
                    this.failMessage = String
                            .format(this.i18n.get("Rotation - Craft is obstructed") + " @ %d,%d,%d", this.blockList[i].x(), this.blockList[i].y(), this.blockList[i].z());
                    break;
                }
            }
            final Location plugLoc = new Location(this.world, this.blockList[i].x(), this.blockList[i].y(), this.blockList[i].z());
            if (craftPilot != null) {
                // See if they are permitted to build in the area, if WorldGuard integration is turned on
                if (this.plugin.getWorldGuardPlugin() != null && this.settings.WorldGuardBlockMoveOnBuildPerm) {
                    if (!this.plugin.getWorldGuardPlugin().canBuild(craftPilot, plugLoc)) {
                        this.failed = true;
                        this.failMessage = String.format(this.i18n.get(
                                "Rotation - Player is not permitted to build in this WorldGuard region") +
                                                         " @ %d,%d,%d", this.blockList[i].x(), this.blockList[i].y(), this.blockList[i].z());
                        break;
                    }
                }
            }

            final Player p = (craftPilot == null) ? this.getCraft().getNotificationPlayer() : craftPilot;

            if (p != null) {
                if (this.plugin.getWorldGuardPlugin() != null && this.plugin.getWGCustomFlagsPlugin() != null && this.settings.WGCustomFlagsUsePilotFlag) {
                    final LocalPlayer lp = this.plugin.getWorldGuardPlugin().wrapPlayer(p);
                    if (!WGCustomFlagsUtils
                            .validateFlag(this.plugin.getWorldGuardPlugin(), plugLoc, this.plugin.FLAG_ROTATE, lp)) {
                        this.failed = true;
                        this.failMessage = String
                                .format(this.i18n.get("WGCustomFlags - Rotation Failed") + " @ %d,%d,%d", this.blockList[i].x(),
                                        this.blockList[i].y(), this.blockList[i].z());
                        break;
                    }
                }
            }

            final boolean canMove =
                    typeID == Material.AIR ||
                    typeID == Material.PISTON_EXTENSION ||
                    existingBlockSet.contains(this.blockList[i]) ||
                    (waterCraft && typeID == Material.STATIONARY_WATER);

            // Allow watercraft to rotate through water.
            if (canMove) {
                final int id = testMaterialData.getItemTypeId();
                byte data = testMaterialData.getData();
                if (BlockUtils.blockRequiresRotation(id)) {
                    data = BlockUtils.rotate(data, id, this.rotation);
                }
                mapUpdates.add(new MapUpdateCommand(originalBlockList[i], this.blockList[i], id, data, this.rotation,
                                                    this.getCraft()));
            } else {
                this.failed = true;
                this.failMessage = String
                        .format(this.i18n.get("Rotation - Craft is obstructed") + " @ %d,%d,%d", this.blockList[i].x(),
                                this.blockList[i].y(), this.blockList[i].z());
                break;
            }
        }

        if (!this.failed) {
            //rotate entities in the craft
            final Location tOP = new Location(this.getCraft().getWorld(), this.originPoint.x(), this.originPoint.y(),
                                              this.originPoint.z());

            List<Entity> eList = null;
            int numTries = 0;

            while ((eList == null) && (numTries < 100)) {
                try {
                    eList = this.getCraft().getWorld().getEntities();
                } catch (final java.util.ConcurrentModificationException e) {
                    numTries++;
                }
            }
            for (final Entity pTest : this.getCraft().getWorld().getEntities()) {
                if (MathUtils.playerIsWithinBoundingPolygon(this.getCraft().getHitBox(), this.getCraft().getMinX(),
                                                            this.getCraft().getMinZ(),
                                                            BlockVec.from(pTest.getLocation()))) {
                    if (pTest.getType() == EntityType.DROPPED_ITEM) {
                        //	pTest.remove();   removed to test cleaner fragile item removal
                    } else {
                        // Player is onboard this craft
                        tOP.setX(tOP.getBlockX() + 0.5);
                        tOP.setZ(tOP.getBlockZ() + 0.5);
                        final Location playerLoc = pTest.getLocation();
                        if (this.getCraft().getPilotLocked() && pTest == this.craftManager.getPlayerFromCraft(this.getCraft())) {
                            playerLoc.setX(this.getCraft().getPilotLockedX());
                            playerLoc.setY(this.getCraft().getPilotLockedY());
                            playerLoc.setZ(this.getCraft().getPilotLockedZ());
                        }
                        final Location adjustedPLoc = playerLoc.subtract(tOP);

                        final double[] rotatedCoords = MathUtils
                                .rotateVecNoRound(this.rotation, adjustedPLoc.getX(), adjustedPLoc.getZ());
                        final Location rotatedPloc = new Location(this.getCraft().getWorld(), rotatedCoords[0], playerLoc.getY(),
                                                                  rotatedCoords[1]);
                        final Location newPLoc = rotatedPloc.add(tOP);

                        newPLoc.setPitch(playerLoc.getPitch());
                        float newYaw = playerLoc.getYaw();
                        if (this.rotation.equals(RotationXZ.cw())) {
                            newYaw = newYaw + 90.0F;
                            if (newYaw >= 360.0F) {
                                newYaw = newYaw - 360.0F;
                            }
                        }
                        if (this.rotation.equals(RotationXZ.ccw())) {
                            newYaw = newYaw - 90;
                            if (newYaw < 0.0F) {
                                newYaw = newYaw + 360.0F;
                            }
                        }
                        newPLoc.setYaw(newYaw);

                        if (this.getCraft().getPilotLocked() && pTest == this.craftManager.getPlayerFromCraft(this.getCraft())) {
                            this.getCraft().setPilotLockedX(newPLoc.getX());
                            this.getCraft().setPilotLockedY(newPLoc.getY());
                            this.getCraft().setPilotLockedZ(newPLoc.getZ());
                        }
                        final EntityUpdateCommand eUp = new EntityUpdateCommand(pTest.getLocation().clone(), newPLoc, pTest);
                        entityUpdateSet.add(eUp);
                        if (this.getCraft().getPilotLocked() && pTest == this.craftManager.getPlayerFromCraft(this.getCraft())) {
                            this.getCraft().setPilotLockedX(newPLoc.getX());
                            this.getCraft().setPilotLockedY(newPLoc.getY());
                            this.getCraft().setPilotLockedZ(newPLoc.getZ());
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
						Location rotatedPloc = new Location( getCraft().getWorld(), rotatedCoords[0], spawnLoc.getY(),
						rotatedCoords[1] );
						Location newBedSpawn = rotatedPloc.add( tOP );

						p.setBedSpawnLocation(newBedSpawn, true);
					}
				}
			}*/

            // Calculate air changes
            final Set<BlockVec> airLocation = Sets.difference(
                    Sets.newHashSet(originalBlockList),
                    Sets.newHashSet(this.blockList));

            for (final BlockVec l1 : airLocation) {
                if (waterCraft) {
                    // if its below the waterline, fill in with water. Otherwise fill in with air.
                    if (l1.y() <= waterLine) {
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
            this.minX = null;
            this.minZ = null;

            for (final BlockVec l : this.blockList) {
                if (maxX == null || l.x() > maxX) {
                    maxX = l.x();
                }
                if (maxZ == null || l.z() > maxZ) {
                    maxZ = l.z();
                }
                if (this.minX == null || l.x() < this.minX) {
                    this.minX = l.x();
                }
                if (this.minZ == null || l.z() < this.minZ) {
                    this.minZ = l.z();
                }
            }

            // Rerun the polygonal bounding formula for the newly formed craft
            final int sizeX = (maxX - this.minX) + 1;
            final int sizeZ = (maxZ - this.minZ) + 1;

            final int[][][] polygonalBox = new int[sizeX][][];

            for (final BlockVec l : this.blockList) {
                if (polygonalBox[l.x() - this.minX] == null) {
                    polygonalBox[l.x() - this.minX] = new int[sizeZ][];
                }

                if (polygonalBox[l.x() - this.minX][l.z() - this.minZ] == null) {

                    polygonalBox[l.x() - this.minX][l.z() - this.minZ] = new int[2];
                    polygonalBox[l.x() - this.minX][l.z() - this.minZ][0] = l.y();
                    polygonalBox[l.x() - this.minX][l.z() - this.minZ][1] = l.y();
                } else {
                    minY = polygonalBox[l.x() - this.minX][l.z() - this.minZ][0];
                    maxY = polygonalBox[l.x() - this.minX][l.z() - this.minZ][1];

                    if (l.y() < minY) {
                        polygonalBox[l.x() - this.minX][l.z() - this.minZ][0] = l.y();
                    }
                    if (l.y() > maxY) {
                        polygonalBox[l.x() - this.minX][l.z() - this.minZ][1] = l.y();
                    }
                }
            }

            this.hitbox = polygonalBox;

            // if you rotated a subcraft, update the parent with the new blocks
            if (this.isSubCraft) {
                // also find the furthest extent from center and notify the player of the new direction
                int farthestX = 0;
                int farthestZ = 0;
                for (final BlockVec loc : this.blockList) {
                    if (Math.abs(loc.x() - this.originPoint.x()) > Math.abs(farthestX)) farthestX = loc.x() -
                                                                                                    this.originPoint.x();
                    if (Math.abs(loc.z() - this.originPoint.z()) > Math.abs(farthestZ)) farthestZ = loc.z() -
                                                                                                    this.originPoint.z();
                }
                if (Math.abs(farthestX) > Math.abs(farthestZ)) {
                    if (farthestX > 0) {
                        if (this.getCraft().getNotificationPlayer() != null)
                            this.getCraft().getNotificationPlayer().sendMessage("The farthest extent now faces East");
                    } else {
                        if (this.getCraft().getNotificationPlayer() != null)
                            this.getCraft().getNotificationPlayer().sendMessage("The farthest extent now faces West");
                    }
                } else {
                    if (farthestZ > 0) {
                        if (this.getCraft().getNotificationPlayer() != null)
                            this.getCraft().getNotificationPlayer().sendMessage("The farthest extent now faces South");
                    } else {
                        if (this.getCraft().getNotificationPlayer() != null)
                            this.getCraft().getNotificationPlayer().sendMessage("The farthest extent now faces North");
                    }
                }

                final Set<Craft> craftsInWorld = this.craftManager.getCraftsInWorld(this.getCraft().getWorld());
                for (final Craft craft : craftsInWorld) {
                    if (BlockUtils.arrayContainsOverlap(craft.getBlockList(), originalBlockList) &&
                        craft != this.getCraft()) {
                        // found a parent craft
                        if (!craft.isNotProcessing()) {
                            this.failed = true;
                            this.failMessage = this.i18n.get("Parent Craft is busy");
                            return;
                        }

                        final Set<BlockVec> parentBlockList = Sets.difference(
                                Sets.newHashSet(craft.getBlockList()),
                                Sets.newHashSet(originalBlockList));
                        parentBlockList.addAll(Arrays.asList(this.blockList));
                        craft.setBlockList(parentBlockList.toArray(new BlockVec[1]));

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
                                final int parentMinY = parentPolygonalBox[l.x() - parentMinX][l.z() - parentMinZ][0];
                                final int parentMaxY = parentPolygonalBox[l.x() - parentMinX][l.z() - parentMinZ][1];

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
        }
    }

    public BlockVec getOriginPoint() {
        return this.originPoint;
    }

    public boolean isFailed() {
        return this.failed;
    }

    public String getFailMessage() {
        return this.failMessage;
    }

    public BlockVec[] getBlockList() {
        return this.blockList;
    }

    public MapUpdateCommand[] getUpdates() {
        return this.updates;
    }

    public EntityUpdateCommand[] getEntityUpdates() {
        return this.entityUpdates;
    }

    public int[][][] getHitbox() {
        return this.hitbox;
    }

    public int getMinX() {
        return this.minX;
    }

    public int getMinZ() {
        return this.minZ;
    }

    public RotationXZ getRotation() {
        return this.rotation;
    }

    public boolean getIsSubCraft() {
        return this.isSubCraft;
    }

    private boolean checkChests(final Material mBlock, final BlockVec newLoc, final Set<BlockVec> existingBlockSet) {
        BlockVec aroundNewLoc = newLoc.translate(1, 0, 0);
        Material testMaterial = this.getCraft().getWorld().getBlockAt(aroundNewLoc.x(), aroundNewLoc.y(), aroundNewLoc.z()).getType();
        if (testMaterial == mBlock) {
            if (!existingBlockSet.contains(aroundNewLoc)) {
                return false;
            }
        }

        aroundNewLoc = newLoc.translate(-1, 0, 0);
        testMaterial = this.getCraft().getWorld().getBlockAt(aroundNewLoc.x(), aroundNewLoc.y(), aroundNewLoc.z()).getType();
        if (testMaterial == mBlock) {
            if (!existingBlockSet.contains(aroundNewLoc)) {
                return false;
            }
        }

        aroundNewLoc = newLoc.translate(0, 0, 1);
        testMaterial = this.getCraft().getWorld().getBlockAt(aroundNewLoc.x(), aroundNewLoc.y(), aroundNewLoc.z()).getType();
        if (testMaterial == mBlock) {
            if (!existingBlockSet.contains(aroundNewLoc)) {
                return false;
            }
        }

        aroundNewLoc = newLoc.translate(0, 0, -1);
        testMaterial = this.getCraft().getWorld().getBlockAt(aroundNewLoc.x(), aroundNewLoc.y(), aroundNewLoc.z()).getType();
        if (testMaterial == mBlock) {
            if (!existingBlockSet.contains(aroundNewLoc)) {
                return false;
            }
        }
        return true;
    }
}
