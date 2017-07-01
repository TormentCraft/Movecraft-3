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

package net.countercraft.movecraft.async.translation;

import com.alexknvl.shipcraft.math.BlockVec;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.sk89q.worldguard.LocalPlayer;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.api.MaterialDataPredicate;
import net.countercraft.movecraft.async.AsyncTask;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.BoundingBoxUtils;
import net.countercraft.movecraft.utils.EntityUpdateCommand;
import net.countercraft.movecraft.utils.ItemDropUpdateCommand;
import net.countercraft.movecraft.utils.MapUpdateCommand;
import net.countercraft.movecraft.utils.MathUtils;
import net.countercraft.movecraft.utils.WGCustomFlagsUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

public class TranslationTask extends AsyncTask {
    private final Movecraft plugin;
    private final Settings settings;
    private final I18nSupport i18n;
    private final CraftManager craftManager;
    private final TranslationTaskData data;

    public TranslationTask(final Craft c, final Movecraft plugin, final Settings settings, final I18nSupport i18n, final CraftManager craftManager,
                           final TranslationTaskData data)
    {
        super(c);
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
        this.data = data;
    }

    private final Set<Material> fallThroughBlocks = Sets.newEnumSet(Lists.newArrayList(
            Material.AIR, Material.WATER, Material.STATIONARY_WATER, Material.LAVA, Material.STATIONARY_LAVA,
            Material.LONG_GRASS, Material.YELLOW_FLOWER, Material.RED_ROSE, Material.BROWN_MUSHROOM,
            Material.RED_MUSHROOM, Material.TORCH, Material.FIRE, Material.REDSTONE_WIRE, Material.CROPS,
            Material.SIGN_POST, Material.LADDER, Material.WALL_SIGN, Material.LEVER, Material.STONE_PLATE,
            Material.WOOD_PLATE, Material.REDSTONE_TORCH_OFF, Material.REDSTONE_TORCH_ON, Material.STONE_BUTTON,
            Material.SNOW, Material.SUGAR_CANE_BLOCK, Material.FENCE, Material.DIODE_BLOCK_OFF,
            Material.DIODE_BLOCK_ON, Material.WATER_LILY, Material.CARROT, Material.POTATO, Material.WOOD_BUTTON,
            Material.CARPET), Material.class);

    @Override public void execute() {
        BlockVec[] blocksList = this.data.getBlockList();

        // blockedByWater=false means an ocean-going vessel
        boolean waterCraft = !this.getCraft().getType().blockedByWater();
        boolean hoverCraft = this.getCraft().getType().getCanHover();

        final boolean airCraft = this.getCraft().getType().blockedByWater();

        final int hoverLimit = this.getCraft().getType().getHoverLimit();

        final Player craftPilot = this.craftManager.getPlayerFromCraft(this.getCraft());

        final int[][][] hb = this.getCraft().getHitBox();
        if (hb == null) return;

        // start by finding the crafts borders
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
        // Safe because if the first x array doesn't have a z array,
        // then it wouldn't be the first x array.
        final int maxX = this.getCraft().getMinX() + hb.length;
        final int maxZ = this.getCraft().getMinZ() + hb[0].length;
        final int minX = this.getCraft().getMinX();
        final int minZ = this.getCraft().getMinZ();

		// Load any chunks that you are moving into that are not loaded
        /*for (int posX=minX+data.getDx();posX<=maxX+data.getDx();posX++) {
			for (int posZ=minZ+data.getDz();posZ<=maxZ+data.getDz();posZ++) {
				if(getCraft().getWorld().isChunkLoaded(posX>>4, posZ>>4) == false) {
					getCraft().getWorld().loadChunk(posX>>4, posZ>>4);
				}
			}
		}*/

        // treat sinking crafts specially
        if (this.getCraft().getSinking()) {
            waterCraft = true;
            hoverCraft = false;
			// Movecraft.getInstance().getLogger().log( Level.INFO, "Translation task at: "+System.currentTimeMillis() );
        }

        // check the maxheightaboveground limitation, move 1 down if that limit is exceeded
        if (this.getCraft().getType().getMaxHeightAboveGround() > 0 && this.data.getDy() >= 0) {
            int x = this.getCraft().getMaxX() + this.getCraft().getMinX();
            x = x >> 1;
            final int y = this.getCraft().getMaxY();
            int z = this.getCraft().getMaxZ() + this.getCraft().getMinZ();
            z = z >> 1;
            int cy = this.getCraft().getMinY();
            boolean done = false;
            while (!done) {
                cy = cy - 1;
                if (this.getCraft().getWorld().getBlockAt(x, cy, z).getType() != Material.AIR) done = true;
                if (cy <= 1) done = true;
            }
            if (y - cy > this.getCraft().getType().getMaxHeightAboveGround()) {
                this.data.setDy(-1);
            }
        }

        // Find the waterline from the surrounding terrain or from the static level in the craft type
        int waterLine = 0;
        if (waterCraft) {
            if (this.getCraft().getType().getStaticWaterLevel() == 0) {
                // figure out the water level by examining blocks next to the outer boundaries of the craft
                for (int posY = maxY + 1; (posY >= minY - 1) && (waterLine == 0); posY--) {
                    int numWater = 0;
                    int numAir = 0;
                    int posZ = minZ - 1;
                    int posX;
                    for (posX = minX - 1; (posX <= maxX + 1) && (waterLine == 0); posX++) {
                        final Material typeID = this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType();
                        if (typeID == Material.STATIONARY_WATER) numWater++;
                        if (typeID == Material.AIR) numAir++;
                    }
                    posZ = maxZ + 1;
                    for (posX = minX - 1; (posX <= maxX + 1) && (waterLine == 0); posX++) {
                        final Material typeID = this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType();
                        if (typeID == Material.STATIONARY_WATER) numWater++;
                        if (typeID == Material.AIR) numAir++;
                    }
                    posX = minX - 1;
                    for (posZ = minZ; (posZ <= maxZ) && (waterLine == 0); posZ++) {
                        final Material typeID = this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType();
                        if (typeID == Material.STATIONARY_WATER) numWater++;
                        if (typeID == Material.AIR) numAir++;
                    }
                    posX = maxX + 1;
                    for (posZ = minZ; (posZ <= maxZ) && (waterLine == 0); posZ++) {
                        final Material typeID = this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType();
                        if (typeID == Material.STATIONARY_WATER) numWater++;
                        if (typeID == Material.AIR) numAir++;
                    }
                    if (numWater > numAir) {
                        waterLine = posY;
                    }
                }
            } else {
                if (waterLine <= maxY + 1) {
                    waterLine = this.getCraft().getType().getStaticWaterLevel();
                }
            }

            // now add all the air blocks found within the craft's hitbox immediately above the waterline and below
            // to the craft blocks so they will be translated
            final HashSet<BlockVec> newHSBlockList = Sets.newHashSet(blocksList);
            int posY = waterLine + 1;
            for (int posX = minX; posX < maxX; posX++) {
                for (int posZ = minZ; posZ < maxZ; posZ++) {
                    if (hb[posX - minX] != null) {
                        if (hb[posX - minX][posZ - minZ] != null) {
                            if (this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType() == Material.AIR &&
                                posY > hb[posX - minX][posZ - minZ][0] && posY < hb[posX - minX][posZ - minZ][1]) {
                                final BlockVec l = new BlockVec(posX, posY, posZ);
                                newHSBlockList.add(l);
                            }
                        }
                    }
                }
            }
            // dont check the hitbox for the underwater portion. Otherwise open-hulled ships would flood.
            for (posY = waterLine; posY >= minY; posY--) {
                for (int posX = minX; posX < maxX; posX++) {
                    for (int posZ = minZ; posZ < maxZ; posZ++) {
                        if (this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType() == Material.AIR) {
                            final BlockVec l = new BlockVec(posX, posY, posZ);
                            newHSBlockList.add(l);
                        }
                    }
                }
            }

            blocksList = newHSBlockList.toArray(new BlockVec[newHSBlockList.size()]);
        }

        // Check for fuel, burn some from a furnace if needed.
        // Blocks of coal are supported, in addition to coal and charcoal.
        double fuelBurnRate = this.getCraft().getType().getFuelBurnRate();
        // Going down doesn't require fuel.
        if (this.data.getDy() == -1 && this.data.getDx() == 0 && this.data.getDz() == 0) fuelBurnRate = 0.0;

        if (fuelBurnRate != 0.0 && !this.getCraft().getSinking()) {
            if (this.getCraft().getBurningFuel() < fuelBurnRate) {
                Block fuelHolder = null;
                for (final BlockVec bTest : blocksList) {
                    final Block b = this.getCraft().getWorld().getBlockAt(bTest.x(), bTest.y(), bTest.z());
                    if (b.getType() == Material.FURNACE) {
                        final InventoryHolder inventoryHolder = (InventoryHolder) b.getState();
                        if (inventoryHolder.getInventory().contains(Material.COAL) ||
                            inventoryHolder.getInventory().contains(Material.COAL_BLOCK)) {
                            fuelHolder = b;
                        }
                    }
                }
                if (fuelHolder == null) {
                    this.fail(this.i18n.get("Translation - Failed Craft out of fuel"));
                } else {
                    final InventoryHolder inventoryHolder = (InventoryHolder) fuelHolder.getState();
                    if (inventoryHolder.getInventory().contains(Material.COAL)) {
                        final ItemStack iStack = inventoryHolder.getInventory()
                                                                .getItem(inventoryHolder.getInventory().first(Material.COAL));
                        final int amount = iStack.getAmount();
                        if (amount == 1) {
                            inventoryHolder.getInventory().remove(iStack);
                        } else {
                            iStack.setAmount(amount - 1);
                        }
                        this.getCraft().setBurningFuel(this.getCraft().getBurningFuel() + 7.0);
                    } else {
                        final ItemStack iStack = inventoryHolder.getInventory()
                                                                .getItem(inventoryHolder.getInventory().first(Material.COAL_BLOCK));
                        final int amount = iStack.getAmount();
                        if (amount == 1) {
                            inventoryHolder.getInventory().remove(iStack);
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

        final List<BlockVec> tempBlockList = new ArrayList<>();
        final HashSet<BlockVec> existingBlockSet = new HashSet<>(Arrays.asList(blocksList));
        final HashSet<EntityUpdateCommand> entityUpdateSet = new HashSet<>();
        final Set<MapUpdateCommand> updateSet = new HashSet<>();

        this.data.setCollisionExplosion(false);
        final Set<MapUpdateCommand> explosionSet = new HashSet<>();

        final MaterialDataPredicate harvestBlocks = this.getCraft().getType().getHarvestBlocks();
        final List<BlockVec> harvestedBlocks = new ArrayList<>();
        final List<BlockVec> droppedBlocks = new ArrayList<>();
        final List<BlockVec> destroyedBlocks = new ArrayList<>();
        final MaterialDataPredicate harvesterBladeBlocks = this.getCraft().getType().getHarvesterBladeBlocks();

        int hoverOver = this.data.getDy();
        boolean hoverUseGravity = this.getCraft().getType().getUseGravity();
        final boolean checkHover = (this.data.getDx() != 0 || this.data.getDz() != 0);// we want to check only horizontal moves
        final boolean canHoverOverWater = this.getCraft().getType().getCanHoverOverWater();

        boolean clearNewData = false;

        for (int i = 0; i < blocksList.length; i++) {
            final BlockVec oldLoc = blocksList[i];
            final BlockVec newLoc = oldLoc.translate(this.data.getDx(), this.data.getDy(), this.data.getDz());

            if (newLoc.y() > this.data.heightRange.max() && newLoc.y() > oldLoc.y()) {
                this.fail(this.i18n.get("Translation - Failed Craft hit height limit"));
                break;
            }
            if (newLoc.y() < this.data.heightRange.min() && newLoc.y() < oldLoc.y() && !this.getCraft().getSinking()) {
                this.fail(this.i18n.get("Translation - Failed Craft hit minimum height limit"));
                break;
            }

            final Location plugLoc = new Location(this.getCraft().getWorld(), newLoc.x(), newLoc.y(), newLoc.z());
            if (craftPilot != null) {
                // See if they are permitted to build in the area, if WorldGuard integration is turned on
                if (this.plugin.getWorldGuardPlugin() != null && this.settings.WorldGuardBlockMoveOnBuildPerm) {
                    if (!this.plugin.getWorldGuardPlugin().canBuild(craftPilot, plugLoc)) {
                        this.fail(String.format(this.i18n.get(
                                "Translation - Failed Player is not permitted to build in this WorldGuard region") +
                                                " @ %d,%d,%d", oldLoc.x(), oldLoc.y(), oldLoc.z()));
                        break;
                    }
                }
            }
            final Player p;
            if (craftPilot == null) {
                p = this.getCraft().getNotificationPlayer();
            } else {
                p = craftPilot;
            }
            if (p != null) {
                if (this.plugin.getWorldGuardPlugin() != null && this.plugin.getWGCustomFlagsPlugin() != null && this.settings.WGCustomFlagsUsePilotFlag) {
                    final LocalPlayer lp = this.plugin.getWorldGuardPlugin().wrapPlayer(p);
                    if (!WGCustomFlagsUtils.validateFlag(this.plugin.getWorldGuardPlugin(), plugLoc, this.plugin.FLAG_MOVE, lp)) {
                        this.fail(String.format(this.i18n.get("WGCustomFlags - Translation Failed") + " @ %d,%d,%d", oldLoc.x(),
                                                oldLoc.y(), oldLoc.z()));
                        break;
                    }
                }
            }

            // Check for chests around.
            Material testMaterial = this.getCraft().getWorld().getBlockAt(oldLoc.x(), oldLoc.y(), oldLoc.z()).getType();
            if (testMaterial == Material.CHEST || testMaterial == Material.TRAPPED_CHEST) {
                if (!this.checkChests(testMaterial, newLoc, existingBlockSet)) {
                    // Prevent chests collision.
                    this.fail(String.format(this.i18n.get("Translation - Failed Craft is obstructed") + " @ %d,%d,%d,%s",
                                            newLoc.x(), newLoc.y(), newLoc.z(),
                                            this.getCraft().getWorld().getBlockAt(newLoc.x(), newLoc.y(), newLoc.z()).getType()
                                           .toString()));
                    break;
                }
            }

            boolean blockObstructed;
            if (this.getCraft().getSinking()) {
                final Material testID = this.getCraft().getWorld().getBlockAt(newLoc.x(), newLoc.y(), newLoc.z()).getType();
                blockObstructed = !this.fallThroughBlocks.contains(testID) && !existingBlockSet.contains(newLoc);
            } else if (!waterCraft) {
                // New block is not air or a piston head and is not part of the existing ship
                testMaterial = this.getCraft().getWorld().getBlockAt(newLoc.x(), newLoc.y(), newLoc.z()).getType();
                blockObstructed = (testMaterial != Material.AIR && testMaterial != Material.PISTON_EXTENSION) &&
                                  !existingBlockSet.contains(newLoc);
            } else {
                // New block is not air or water or a piston head and is not part of the existing ship
                testMaterial = this.getCraft().getWorld().getBlockAt(newLoc.x(), newLoc.y(), newLoc.z()).getType();
                blockObstructed = (testMaterial != Material.AIR && testMaterial != Material.STATIONARY_WATER &&
                                   testMaterial != Material.WATER && testMaterial != Material.PISTON_EXTENSION) &&
                                  !existingBlockSet.contains(newLoc);
            }

            boolean ignoreBlock = false;
            // air never obstructs anything
            if (this.getCraft().getWorld().getBlockAt(oldLoc.x(), oldLoc.y(), oldLoc.z()).getType() == Material.AIR &&
                blockObstructed) {
                ignoreBlock = true;
                blockObstructed = false;
            }

            final Block newLocBlock = this.getCraft().getWorld().getBlockAt(newLoc.x(), newLoc.y(), newLoc.z());
            final Block oldLocBlock = this.getCraft().getWorld().getBlockAt(oldLoc.x(), oldLoc.y(), oldLoc.z());

            boolean bladeOK = true;
            if (blockObstructed) {
                if (hoverCraft || !harvestBlocks.isTrivial()) {
                    // New block is not harvested block.
                    if (harvestBlocks.checkBlock(newLocBlock) && !existingBlockSet.contains(newLoc)) {
                        if (!harvesterBladeBlocks.isTrivial()) {
                            if (!harvesterBladeBlocks.checkBlock(oldLocBlock)) {
                                bladeOK = false;
                            }
                        }
                        if (bladeOK) {
                            blockObstructed = false;
                            this.tryPutToDestroyBox(testMaterial, newLoc, harvestedBlocks, droppedBlocks, destroyedBlocks);
                            harvestedBlocks.add(newLoc);
                        } else {
                            bladeOK = false;
                        }
                    }
                }
            }

            if (blockObstructed) {
                if (hoverCraft && checkHover) {
                    //we check one up ever, if it is hovercraft and one down if it's using gravity
                    if (hoverOver == 0 && newLoc.y() + 1 <= this.data.heightRange.max()) {
                        //first was checked actual level, now check if we can go up
                        hoverOver = 1;
                        this.data.setDy(1);
                        clearNewData = true;
                    } else if (hoverOver >= 1) {
                        //check other options to go up
                        if (hoverOver < hoverLimit + 1 && newLoc.y() + 1 <= this.data.heightRange.max()) {
                            this.data.setDy(hoverOver + 1);
                            hoverOver += 1;
                            clearNewData = true;
                        } else {
                            if (hoverUseGravity && newLoc.y() - hoverOver - 1 >= this.data.heightRange.min()) {
                                //we are on the maximum of top
                                //if we can't go up so we test bottom side
                                this.data.setDy(-1);
                                hoverOver = -1;
                            } else {
                                // no way - back to original dY, turn off hovercraft for this move
                                // and get original data again for all explosions
                                this.data.setDy(0);
                                hoverOver = 0;
                                hoverCraft = false;
                                hoverUseGravity = false;
                            }
                            clearNewData = true;
                        }
                    } else if (hoverOver <= -1) {
                        //we cant go down for 1 block, check more to hoverLimit
                        if (hoverOver > -hoverLimit - 1 && newLoc.y() - 1 >= this.data.heightRange.min()) {
                            this.data.setDy(hoverOver - 1);
                            hoverOver -= 1;
                            clearNewData = true;
                        } else {
                            // no way - back to original dY, turn off hovercraft for this move
                            // and get original data again for all explosions
                            this.data.setDy(0);
                            hoverOver = 0;
                            hoverUseGravity = false;
                            clearNewData = true;
                            hoverCraft = false;
                        }
                    } else {
                        // no way - reached MaxHeight during looking new way upstairs
                        if (hoverUseGravity && newLoc.y() - 1 >= this.data.heightRange.min()) {
                            //we are on the maximum of top
                            //if we can't go up so we test bottom side
                            this.data.setDy(-1);
                            hoverOver = -1;
                        } else {
                            // - back to original dY, turn off hovercraft for this move
                            // and get original data again for all explosions
                            this.data.setDy(0);
                            hoverOver = 0;
                            hoverUseGravity = false;
                            hoverCraft = false;
                        }
                        clearNewData = true;
                    }
                    // End hovercraft stuff
                } else {
                    // handle sinking ship collisions
                    if (this.getCraft().getSinking()) {
                        if (this.getCraft().getType().getExplodeOnCrash() != 0.0F) {
                            final int explosionKey = (int) (0 - (this.getCraft().getType().getExplodeOnCrash() * 100));
                            if (this.getCraft().getWorld().getBlockAt(oldLoc.x(), oldLoc.y(), oldLoc.z()).getType() != Material.AIR) {
                                explosionSet.add(new MapUpdateCommand(oldLoc, explosionKey, (byte) 0, this.getCraft()));
                                this.data.setCollisionExplosion(true);
                            }
                        } else {
                            // use the explosion code to clean up the craft, but not with enough force to do anything
                            if (this.getCraft().getWorld().getBlockAt(oldLoc.x(), oldLoc.y(), oldLoc.z()).getType() != Material.AIR) {
                                final int explosionKey = 0 - 1;
                                explosionSet.add(new MapUpdateCommand(oldLoc, explosionKey, (byte) 0, this.getCraft()));
                                this.data.setCollisionExplosion(true);
                            }
                        }
                    } else {
                        // Explode if the craft is set to have a CollisionExplosion. Also keep moving for spectacular
                        // ramming collisions
                        if (this.getCraft().getType().getCollisionExplosion() == 0.0F) {
                            this.fail(String.format(
                                    this.i18n.get("Translation - Failed Craft is obstructed") + " @ %d,%d,%d,%s",
                                    oldLoc.x(), oldLoc.y(), oldLoc.z(),
                                    this.getCraft().getWorld().getBlockAt(newLoc.x(), newLoc.y(), newLoc.z()).getType()
                                        .toString()));
                            this.getCraft().setCruising(false);
                            break;
                        } else {
                            final int explosionKey = (int) (0 - (this.getCraft().getType().getCollisionExplosion() * 100));
                            if (this.getCraft().getWorld().getBlockAt(oldLoc.x(), oldLoc.y(), oldLoc.z()).getType() != Material.AIR) {
                                explosionSet.add(new MapUpdateCommand(oldLoc, explosionKey, (byte) 0, this.getCraft()));
                                this.data.setCollisionExplosion(true);
                            }
                        }
                    }
                }
            } else {
                //block not obstructed
                final MaterialData oldData = this.getCraft().getWorld().getBlockAt(oldLoc.x(), oldLoc.y(), oldLoc.z()).getState().getData();
                Material oldID = oldData.getItemType();
                //byte oldData = getCraft().getWorld().getBlockAt(oldLoc.x, oldLoc.y, oldLoc.z).getData();
                // remove water from sinking crafts
                if (this.getCraft().getSinking()) {
                    if ((oldID == Material.WATER || oldID == Material.STATIONARY_WATER) && oldLoc.y() > waterLine)
                        oldID = Material.AIR;
                }

                if (!ignoreBlock) {
                    updateSet.add(new MapUpdateCommand(oldLoc, newLoc, oldID.getId(), oldData.getData(), this.getCraft()));
                    tempBlockList.add(newLoc);
                }

                if (i == blocksList.length - 1) {
                    if ((hoverCraft && hoverUseGravity) ||
                        (hoverUseGravity && newLoc.y() > this.data.heightRange.max() && hoverOver == 0)) {
                        // Hovercraft using gravity or something else using gravity and flying over its limit.
                        int iFreeSpace = 0;
                        //canHoverOverWater adds 1 to dY for better check water under craft
                        // best way should be expand selected region to each first blocks under craft
                        if (hoverOver == 0) {
                            //we go directly forward so we check if we can go down
                            for (int ii = -1; ii > -hoverLimit - 2 - (canHoverOverWater ? 0 : 1); ii--) {
                                if (!this.isFreeSpace(this.data.getDx(), hoverOver + ii, this.data.getDz(), blocksList,
                                                      existingBlockSet, waterCraft, hoverCraft, harvestBlocks,
                                                      canHoverOverWater, checkHover)) {
                                    break;
                                }
                                iFreeSpace++;
                            }
                            if (this.data.failed()) {
                                break;
                            }
                            if (iFreeSpace > hoverLimit - (canHoverOverWater ? 0 : 1)) {
                                this.data.setDy(-1);
                                hoverOver = -1;
                                clearNewData = true;
                            }
                        } else if (hoverOver == 1 && !airCraft) {
                            //prevent fly higher than hoverLimit
                            for (int ii = -1; ii > -hoverLimit - 2; ii--) {
                                if (!this.isFreeSpace(this.data.getDx(), hoverOver + ii, this.data.getDz(), blocksList,
                                                      existingBlockSet, waterCraft, hoverCraft, harvestBlocks,
                                                      canHoverOverWater, checkHover)) {
                                    break;
                                }
                                iFreeSpace++;
                            }
                            if (this.data.failed()) {
                                break;
                            }
                            if (iFreeSpace > hoverLimit) {
                                if (bladeOK) {
                                    this.fail(this.i18n.get("Translation - Failed Craft hit height limit"));
                                } else {
                                    this.fail(String.format(
                                            this.i18n.get("Translation - Failed Craft is obstructed") + " @ %d,%d,%d,%s",
                                            oldLoc.x(), oldLoc.y(), oldLoc.z(),
                                            this.getCraft().getWorld().getBlockAt(newLoc.x(), newLoc.y(), newLoc.z()).getType()
                                                .toString()));
                                }
                                break;
                            }
                        } else if (hoverOver > 1) {
                            //prevent jump through block
                            for (int ii = 1; ii < hoverOver - 1; ii++) {
                                if (!this.isFreeSpace(0, ii, 0, blocksList, existingBlockSet, waterCraft, hoverCraft,
                                                      harvestBlocks, canHoverOverWater, checkHover)) {
                                    break;
                                }
                                iFreeSpace++;
                            }
                            if (this.data.failed()) {
                                break;
                            }
                            if (iFreeSpace + 2 < hoverOver) {
                                this.data.setDy(-1);
                                hoverOver = -1;
                                clearNewData = true;
                            }
                        } else if (hoverOver < -1) {
                            //prevent jump through block
                            for (int ii = -1; ii > hoverOver + 1; ii--) {
                                if (!this.isFreeSpace(0, ii, 0, blocksList, existingBlockSet, waterCraft, hoverCraft,
                                                      harvestBlocks, canHoverOverWater, checkHover)) {
                                    break;
                                }
                                iFreeSpace++;
                            }
                            if (this.data.failed()) {
                                break;
                            }
                            if (iFreeSpace + 2 < -hoverOver) {
                                this.data.setDy(0);
                                hoverOver = 0;
                                hoverCraft = false;
                                clearNewData = true;
                            }
                        }
                        if (!canHoverOverWater) {
                            if (hoverOver >= 1) {
                                //others hoverOver values we have checked jet
                                for (int ii = hoverOver - 1; ii > hoverOver - hoverLimit - 2; ii--) {
                                    if (!this.isFreeSpace(0, ii, 0, blocksList, existingBlockSet, waterCraft, hoverCraft,
                                                          harvestBlocks, canHoverOverWater, checkHover)) {
                                        break;
                                    }
                                    iFreeSpace++;
                                }
                                if (this.data.failed()) {
                                    break;
                                }
                            }
                        }
                    }
                }
            } //END OF: if (blockObstructed) 

            if (clearNewData) {
                i = -1;
                tempBlockList.clear();
                updateSet.clear();
                harvestedBlocks.clear();
                this.data.setCollisionExplosion(false);
                explosionSet.clear();
                clearNewData = false;
            }
        } //END OF: for ( int i = 0; i < blocksList.length; i++ ) {

        if (this.data.collisionExplosion()) {
            // mark the craft to check for sinking, remove the exploding blocks from the blocklist, and submit the
            // explosions for map update
            for (final MapUpdateCommand m : explosionSet) {

                if (existingBlockSet.contains(m.newBlockLocation)) {
                    existingBlockSet.remove(m.newBlockLocation);
                    if (this.settings.FadeWrecksAfter > 0) {
                        final Material typeID = this.getCraft().getWorld().getBlockAt(m.newBlockLocation.x(), m.newBlockLocation.y(),
                                                                                      m.newBlockLocation.z()).getType();
                        if (typeID != Material.AIR && typeID != Material.STATIONARY_WATER) {
                            this.plugin.blockFadeTimeMap.put(m.newBlockLocation, System.currentTimeMillis());
                            this.plugin.blockFadeTypeMap.put(m.newBlockLocation, typeID);
                            if (m.newBlockLocation.y() <= waterLine) {
                                this.plugin.blockFadeWaterMap.put(m.newBlockLocation, true);
                            } else {
                                this.plugin.blockFadeWaterMap.put(m.newBlockLocation, false);
                            }
                            this.plugin.blockFadeWorldMap.put(m.newBlockLocation, this.getCraft().getWorld());
                        }
                    }
                }

                // if the craft is sinking, remove all solid blocks above the one that hit the ground from the craft
                // for smoothing sinking
                if (this.getCraft().getSinking() &&
                    (this.getCraft().getType().getExplodeOnCrash() == 0.0)) {
                    int posy = m.newBlockLocation.y() + 1;
                    Material testID = this.getCraft().getWorld()
                                          .getBlockAt(m.newBlockLocation.x(), posy, m.newBlockLocation.z())
                                          .getType();

                    while (posy <= maxY && !this.fallThroughBlocks.contains(testID)) {
                        final BlockVec testLoc = new BlockVec(m.newBlockLocation.x(), posy, m.newBlockLocation.z());
                        if (existingBlockSet.contains(testLoc)) {
                            existingBlockSet.remove(testLoc);
                            if (this.settings.FadeWrecksAfter > 0) {
                                final Material typeID = this.getCraft().getWorld().getBlockAt(testLoc.x(), testLoc.y(), testLoc.z()).getType();
                                if (typeID != Material.AIR && typeID != Material.STATIONARY_WATER) {
                                    this.plugin.blockFadeTimeMap.put(testLoc, System.currentTimeMillis());
                                    this.plugin.blockFadeTypeMap.put(testLoc, typeID);
                                    if (testLoc.y() <= waterLine) {
                                        this.plugin.blockFadeWaterMap.put(testLoc, true);
                                    } else {
                                        this.plugin.blockFadeWaterMap.put(testLoc, false);
                                    }
                                    this.plugin.blockFadeWorldMap.put(testLoc, this.getCraft().getWorld());
                                }
                            }
                        }
                        posy = posy + 1;
                        testID = this.getCraft().getWorld()
                                     .getBlockAt(m.newBlockLocation.x(), posy, m.newBlockLocation.z())
                                     .getType();
                    }
                }
            }

            final BlockVec[] newBlockList = existingBlockSet.toArray(new BlockVec[existingBlockSet.size()]);
            this.data.setBlockList(newBlockList);
            this.data.setUpdates(explosionSet.toArray(new MapUpdateCommand[1]));

            this.fail(this.i18n.get("Translation - Failed Craft is obstructed"));
            if (this.getCraft().getSinking()) {
                if (this.getCraft().getType().getSinkPercent() != 0.0) {
                    this.getCraft().setLastBlockCheck(0);
                }
                this.getCraft().setLastCruiseUpdate(-1);
            }
        }

        if (!this.data.failed()) {
            final BlockVec[] newBlockList = tempBlockList.toArray(new BlockVec[tempBlockList.size()]);
            this.data.setBlockList(newBlockList);

            //prevents torpedo and rocket pilots :)
            if (this.getCraft().getType().getMoveEntities() && !this.getCraft().getSinking()) {
                // Move entities within the craft
                List<Entity> eList = null;
                int numTries = 0;
                while ((eList == null) && (numTries < 100)) {
                    try {
                        eList = this.getCraft().getWorld().getEntities();
                    } catch (final java.util.ConcurrentModificationException e) {
                        numTries++;
                    }
                }

                for (final Entity pTest : eList) {
                    if (MathUtils.playerIsWithinBoundingPolygon(this.getCraft().getHitBox(), this.getCraft().getMinX(),
                                                                this.getCraft().getMinZ(),
                                                                BlockVec.from(pTest.getLocation()))) {
                        if (pTest.getType() == EntityType.PLAYER) {
                            final Player player = (Player) pTest;
                            this.getCraft().getMovedPlayers().put(player, System.currentTimeMillis());
                        } // only move players for now, reduce monsters on airships
                        //if(pTest.getType()!=org.bukkit.entity.EntityType.DROPPED_ITEM ) {
                        if (pTest instanceof LivingEntity) {
                            Location tempLoc = pTest.getLocation();
                            if (this.getCraft().getPilotLocked() && pTest == this.craftManager.getPlayerFromCraft(this.getCraft())) {
                                tempLoc.setX(this.getCraft().getPilotLockedX());
                                tempLoc.setY(this.getCraft().getPilotLockedY());
                                tempLoc.setZ(this.getCraft().getPilotLockedZ());
                            }
                            tempLoc = tempLoc.add(this.data.getDx(), this.data.getDy(), this.data.getDz());
                            final Location newPLoc = new Location(this.getCraft().getWorld(), tempLoc.getX(), tempLoc.getY(),
                                                                  tempLoc.getZ());
                            newPLoc.setPitch(pTest.getLocation().getPitch());
                            newPLoc.setYaw(pTest.getLocation().getYaw());

                            final EntityUpdateCommand eUp = new EntityUpdateCommand(pTest.getLocation().clone(), newPLoc,
                                                                                    pTest);
                            entityUpdateSet.add(eUp);
                            if (this.getCraft().getPilotLocked() && pTest == this.craftManager.getPlayerFromCraft(this.getCraft())) {
                                this.getCraft().setPilotLockedX(tempLoc.getX());
                                this.getCraft().setPilotLockedY(tempLoc.getY());
                                this.getCraft().setPilotLockedZ(tempLoc.getZ());
                            }
                        }
                    }
                }
            } else {
                //add releaseTask without playermove to manager
                if (!this.getCraft().getType().getCruiseOnPilot() && !this.getCraft().getSinking())  // not necessary to release
                    // cruiseonpilot crafts, because they will already be released
                    this.craftManager.addReleaseTask(this.getCraft());
            }

            // remove water near sinking crafts
            if (this.getCraft().getSinking()) {
                int posX;
                int posY = maxY;
                int posZ;
                if (posY > waterLine) {
                    for (posX = minX - 1; posX <= maxX + 1; posX++) {
                        for (posZ = minZ - 1; posZ <= maxZ + 1; posZ++) {
                            if (this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType() == Material.STATIONARY_WATER ||
                                this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType() == Material.WATER) {
                                final BlockVec loc = new BlockVec(posX, posY, posZ);
                                updateSet.add(new MapUpdateCommand(loc, 0, (byte) 0, this.getCraft()));
                            }
                        }
                    }
                }
                for (posY = maxY + 1; (posY >= minY - 1) && (posY > waterLine); posY--) {
                    posZ = minZ - 1;
                    for (posX = minX - 1; posX <= maxX + 1; posX++) {
                        if (this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType() == Material.STATIONARY_WATER ||
                            this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType() == Material.WATER) {
                            final BlockVec loc = new BlockVec(posX, posY, posZ);
                            updateSet.add(new MapUpdateCommand(loc, 0, (byte) 0, this.getCraft()));
                        }
                    }
                    posZ = maxZ + 1;
                    for (posX = minX - 1; posX <= maxX + 1; posX++) {
                        if (this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType() == Material.STATIONARY_WATER ||
                            this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType() == Material.WATER) {
                            final BlockVec loc = new BlockVec(posX, posY, posZ);
                            updateSet.add(new MapUpdateCommand(loc, 0, (byte) 0, this.getCraft()));
                        }
                    }
                    posX = minX - 1;
                    for (posZ = minZ - 1; posZ <= maxZ + 1; posZ++) {
                        if (this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType() == Material.STATIONARY_WATER ||
                            this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType() == Material.WATER) {
                            final BlockVec loc = new BlockVec(posX, posY, posZ);
                            updateSet.add(new MapUpdateCommand(loc, 0, (byte) 0, this.getCraft()));
                        }
                    }
                    posX = maxX + 1;
                    for (posZ = minZ - 1; posZ <= maxZ + 1; posZ++) {
                        if (this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType() == Material.STATIONARY_WATER ||
                            this.getCraft().getWorld().getBlockAt(posX, posY, posZ).getType() == Material.WATER) {
                            final BlockVec loc = new BlockVec(posX, posY, posZ);
                            updateSet.add(new MapUpdateCommand(loc, 0, (byte) 0, this.getCraft()));
                        }
                    }
                }
            }

            //Set blocks that are no longer craft to air.
            final Set<BlockVec> airLocation = Sets.difference(Sets.newHashSet(blocksList), Sets.newHashSet(newBlockList));

            for (final BlockVec l1 : airLocation) {
                // for watercraft, fill blocks below the waterline with water
                if (waterCraft) {
                    if (l1.y() <= waterLine) {
                        // if there is air below the ship at the current position, don't fill in with water
                        BlockVec testAir = new BlockVec(l1.x(), l1.y() - 1, l1.z());
                        while (existingBlockSet.contains(testAir)) {
                            testAir = new BlockVec(l1.x(), testAir.y() - 1, l1.z());
                        }
                        if (this.getCraft().getWorld().getBlockAt(testAir.x(), testAir.y(), testAir.z()).getType() == Material.AIR) {
                            if (this.getCraft().getSinking()) {
                                updateSet.add(new MapUpdateCommand(l1, 0, (byte) 0, null,
                                                                   this.getCraft().getType().getSmokeOnSink()));
                            } else {
                                updateSet.add(new MapUpdateCommand(l1, 0, (byte) 0, null));
                            }
                        } else {
                            updateSet.add(new MapUpdateCommand(l1, 9, (byte) 0, null));
                        }
                    } else {
                        if (this.getCraft().getSinking()) {
                            updateSet.add(new MapUpdateCommand(l1, 0, (byte) 0, null,
                                                               this.getCraft().getType().getSmokeOnSink()));
                        } else {
                            updateSet.add(new MapUpdateCommand(l1, 0, (byte) 0, null));
                        }
                    }
                } else {
                    if (this.getCraft().getSinking()) {
                        updateSet.add(new MapUpdateCommand(l1, 0, (byte) 0, null,
                                                           this.getCraft().getType().getSmokeOnSink()));
                    } else {
                        updateSet.add(new MapUpdateCommand(l1, 0, (byte) 0, null));
                    }
                }
            }

            //add destroyed parts of growed
            for (final BlockVec destroyedLocation : destroyedBlocks) {
                updateSet.add(new MapUpdateCommand(destroyedLocation, 0, (byte) 0, null));
            }
            this.data.setUpdates(updateSet.toArray(new MapUpdateCommand[1]));
            this.data.setEntityUpdates(entityUpdateSet.toArray(new EntityUpdateCommand[1]));

            if (this.data.getDy() != 0) {
                this.data.setHitbox(BoundingBoxUtils.translateBoundingBoxVertically(this.data.getHitbox(), this.data.getDy()));
            }

            this.data.setMinX(this.data.getMinX() + this.data.getDx());
            this.data.setMinZ(this.data.getMinZ() + this.data.getDz());
        }

        this.captureYield(blocksList, harvestedBlocks, droppedBlocks);
    }

    private void fail(final String message) {
        this.data.setFailed(true);
        this.data.setFailMessage(message);
    }

    public TranslationTaskData getData() {
        return this.data;
    }

    private boolean isFreeSpace(final int x, final int y, final int z, final BlockVec[] blocksList, final Set<BlockVec> existingBlockSet,
                                final boolean waterCraft, final boolean hoverCraft, final MaterialDataPredicate harvestBlocks,
                                final boolean canHoverOverWater, final boolean checkHover)
    {
        boolean isFree = true;
        // this checking for hovercrafts should be faster with separating horizontal layers and checking only really
        // necessaries,
        // or more better: remember what checked in each translation, but it's beyond my current abilities, I will
        // try to solve it in future
        for (final BlockVec oldLoc : blocksList) {
            final BlockVec newLoc = oldLoc.translate(x, y, z);

            final Material testMaterial = this.getCraft().getWorld().getBlockAt(newLoc.x(), newLoc.y(), newLoc.z()).getType();
            if (!canHoverOverWater) {
                if (testMaterial == Material.STATIONARY_WATER || testMaterial == Material.WATER) {
                    this.fail(this.i18n.get("Translation - Failed Craft over water"));
                }
            }

            if (newLoc.y() >= this.data.heightRange.max() && newLoc.y() > oldLoc.y() && !checkHover) {
                //if ( newLoc.getY() >= data.getMaxHeight() && newLoc.getY() > oldLoc.getY()) {
                isFree = false;
                break;
            }
            if (newLoc.y() <= this.data.heightRange.min() && newLoc.y() < oldLoc.y()) {
                isFree = false;
                break;
            }

            boolean blockObstructed;
            if (waterCraft) {
                // New block is not air or water or a piston head and is not part of the existing ship
                blockObstructed = (testMaterial != Material.AIR && testMaterial != Material.STATIONARY_WATER &&
                                   testMaterial != Material.WATER && testMaterial != Material.PISTON_EXTENSION) &&
                                  !existingBlockSet.contains(newLoc);
            } else {
                // New block is not air or a piston head and is not part of the existing ship
                blockObstructed = (testMaterial != Material.AIR && testMaterial != Material.PISTON_EXTENSION) &&
                                  !existingBlockSet.contains(newLoc);
            }
            if (blockObstructed && hoverCraft) {
                // New block is not harvested block and is not part of the existing craft
                blockObstructed = !(harvestBlocks.check(testMaterial) && !existingBlockSet.contains(newLoc));
            }

            if (blockObstructed) {
                isFree = false;
                break;
            }
        }
        return isFree;
    }

    private boolean checkChests(final Material mBlock, final BlockVec newLoc, final Set<BlockVec> existingBlockSet)
    {

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

    private void captureYield(final BlockVec[] blocksList, final List<BlockVec> harvestedBlocks, final List<BlockVec> droppedBlocks)
    {
        if (harvestedBlocks.isEmpty()) {
            return;
        }

        final Map<Material, ArrayList<Block>> crates = new HashMap<>();
        final HashSet<ItemDropUpdateCommand> itemDropUpdateSet = new HashSet<>();
        final Set<Material> droppedSet = new HashSet<>();
        final HashMap<BlockVec, ItemStack[]> droppedMap = new HashMap<>();
        harvestedBlocks.addAll(droppedBlocks);

        for (final BlockVec harvestedBlock : harvestedBlocks) {
            final Block block = this.getCraft().getWorld().getBlockAt(harvestedBlock.x(), harvestedBlock.y(), harvestedBlock.z());
            ItemStack[] drops = block.getDrops().toArray(new ItemStack[block.getDrops().size()]);
            boolean oSomethingToDrop = false;
            boolean oWheat = false;
            for (final ItemStack drop : drops) {
                if (drop != null) {
                    oSomethingToDrop = true;
                    if (!droppedSet.contains(drop.getType())) {
                        droppedSet.add(drop.getType());
                    }
                    if (drop.getType() == Material.WHEAT) {
                        oWheat = true;
                    }
                }
            }
            if (oWheat) {
                final Random rand = new Random();
                final int amount = rand.nextInt(4);
                if (amount > 0) {
                    final ItemStack seeds = new ItemStack(Material.SEEDS, amount);
                    final HashSet<ItemStack> d = new HashSet<>(Arrays.asList(drops));
                    d.add(seeds);
                    drops = d.toArray(new ItemStack[d.size()]);
                }
            }
            if (drops.length > 0 && oSomethingToDrop) {
                droppedMap.put(harvestedBlock, drops);
            }
        }

        //find chests
        for (final BlockVec bTest : blocksList) {
            final Block b = this.getCraft().getWorld().getBlockAt(bTest.x(), bTest.y(), bTest.z());
            if (b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST) {
                final Inventory inv = ((InventoryHolder) b.getState()).getInventory();
                //get chests with dropped Items
                for (final Material mat : droppedSet) {
                    for (final Entry<Integer, ? extends ItemStack> pair : ((HashMap<Integer, ? extends ItemStack>) inv
                            .all(mat)).entrySet()) {
                        final ItemStack stack = pair.getValue();
                        if (stack.getAmount() < stack.getMaxStackSize() || inv.firstEmpty() > -1) {
                            ArrayList<Block> blocks = crates.get(mat);
                            if (blocks == null) {
                                blocks = new ArrayList<>();
                            }
                            if (!blocks.contains(b)) {
                                blocks.add(b);
                            }
                            crates.put(mat, blocks);
                        }
                    }
                }
                // get chests with free slots
                if (inv.firstEmpty() != -1) {
                    final Material mat = Material.AIR;
                    ArrayList<Block> blocks = crates.get(mat);
                    if (blocks == null) {
                        blocks = new ArrayList<>();
                    }
                    if (!blocks.contains(b)) {
                        blocks.add(b);
                    }
                    crates.put(mat, blocks);
                }
            }
        }

        for (final BlockVec harvestedBlock : harvestedBlocks) {
            if (droppedMap.containsKey(harvestedBlock)) {
                final ItemStack[] drops = droppedMap.get(harvestedBlock);
                for (final ItemStack drop : drops) {
                    final ItemStack retStack;
                    if (droppedBlocks.contains(harvestedBlock)) {
                        retStack = drop;
                    } else {
                        retStack = this.putInToChests(drop, crates);
                    }
                    if (retStack != null) {
                        //drop items on position 
                        final Location loc = new Location(this.getCraft().getWorld(), harvestedBlock.x(), harvestedBlock.y(),
                                                          harvestedBlock.z());
                        final ItemDropUpdateCommand iUp = new ItemDropUpdateCommand(loc, drop);
                        itemDropUpdateSet.add(iUp);
                    }
                }
            }
        }
        this.data.setItemDropUpdates(itemDropUpdateSet.toArray(new ItemDropUpdateCommand[1]));
    }

    private ItemStack putInToChests(ItemStack stack, final Map<Material, ArrayList<Block>> chests) {
        if (stack == null) {
            return null;
        }
        if (chests == null) {
            return stack;
        }
        if (chests.isEmpty()) {
            return stack;
        }

        Material mat = stack.getType();

        if (chests.get(mat) != null) {
            for (final Block b : chests.get(mat)) {
                final Inventory inv = ((InventoryHolder) b.getState()).getInventory();
                final Map<Integer, ItemStack> leftover = inv.addItem(stack); //try add stack to the chest inventory
                if (leftover != null) {
                    ArrayList<Block> blocks = chests.get(mat);
                    if (blocks == null) {
                        blocks = new ArrayList<>();
                    }

                    if (!blocks.isEmpty()) {
                        chests.put(mat, blocks); //restore chests array in HashMap
                    } else if (chests.get(mat) == null) {
                        if (inv.firstEmpty() == -1) {
                            chests.remove(mat); //remove  array of chests with this material 
                        }
                    }
                    if (leftover.isEmpty()) {
                        return null;
                    }
                    if (0 < leftover.size()) {
                        stack = leftover.get(0);
                    }
                } else {
                    return null;
                }
            }
        }

        mat = Material.AIR;
        if (chests.get(mat) != null) {
            for (final Block b : chests.get(mat)) {
                final Inventory inv = ((InventoryHolder) b.getState()).getInventory();
                final Map<Integer, ItemStack> leftover = inv.addItem(stack);
                if (leftover != null && !leftover.isEmpty()) {
                    stack = null;
                    ArrayList<Block> blocks = chests.get(mat);
                    if (blocks == null) {
                        blocks = new ArrayList<>();
                    }
                    if (!blocks.isEmpty()) {
                        if (inv.firstEmpty() != -1) {
                            chests.put(mat, blocks);
                        }
                    } else if (chests.get(mat) == null) {
                        if (inv.firstEmpty() == -1) {
                            chests.remove(mat); //remove  array of chests with this material 
                        }
                    }
                    if (0 < leftover.size()) {
                        return leftover.get(0);
                    }
                } else {
                    //create new stack for this material
                    if (stack != null) {
                        final Material newMat = stack.getType();
                        ArrayList<Block> newBlocks = chests.get(newMat);
                        if (newBlocks == null) {
                            newBlocks = new ArrayList<>();
                        }
                        newBlocks.add(b);
                        chests.put(newMat, newBlocks);
                    }
                    return null;
                }
            }
        }

        return stack;
    }

    private void tryPutToDestroyBox(final Material mat, final BlockVec loc, final List<BlockVec> harvestedBlocks,
                                    final List<BlockVec> droppedBlocks, final List<BlockVec> destroyedBlocks)
    {
        if (mat == Material.DOUBLE_PLANT ||
            mat == Material.WOODEN_DOOR ||
            mat == Material.IRON_DOOR_BLOCK
//            ||                            
//                mat.equals(Material.BANNER)    // Apparently Material.Banner was removed from the class
                ) {
            if (this.getCraft().getWorld().getBlockAt(loc.x(), loc.y() + 1, loc.z()).getType() == mat) {
                final BlockVec tmpLoc = loc.translate(0, 1, 0);
                if (!destroyedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    destroyedBlocks.add(tmpLoc);
                }
            } else if (this.getCraft().getWorld().getBlockAt(loc.x(), loc.y() - 1, loc.z()).getType() == mat) {
                final BlockVec tmpLoc = loc.translate(0, -1, 0);
                if (!destroyedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    destroyedBlocks.add(tmpLoc);
                }
            }
        } else if (mat == Material.CACTUS || mat == Material.SUGAR_CANE_BLOCK) {
            BlockVec tmpLoc = loc.translate(0, 1, 0);
            Material tmpType = this.getCraft().getWorld().getBlockAt(tmpLoc.x(), tmpLoc.y(), tmpLoc.z()).getType();
            while (tmpType == mat) {
                if (!droppedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    droppedBlocks.add(tmpLoc);
                }
                if (!destroyedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    destroyedBlocks.add(tmpLoc);
                }
                tmpLoc = tmpLoc.translate(0, 1, 0);
                tmpType = this.getCraft().getWorld().getBlockAt(tmpLoc.x(), tmpLoc.y(), tmpLoc.z()).getType();
            }
        } else if (mat == Material.BED_BLOCK) {
            if (this.getCraft().getWorld().getBlockAt(loc.x() + 1, loc.y(), loc.z()).getType() == mat) {
                final BlockVec tmpLoc = loc.translate(1, 0, 0);
                if (!destroyedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    destroyedBlocks.add(tmpLoc);
                }
            } else if (this.getCraft().getWorld().getBlockAt(loc.x() - 1, loc.y(), loc.z()).getType() == mat) {
                final BlockVec tmpLoc = loc.translate(-1, 0, 0);
                if (!destroyedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    destroyedBlocks.add(tmpLoc);
                }
            }
            if (this.getCraft().getWorld().getBlockAt(loc.x(), loc.y(), loc.z() + 1).getType() == mat) {
                final BlockVec tmpLoc = loc.translate(0, 0, 1);
                if (!destroyedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    destroyedBlocks.add(tmpLoc);
                }
            } else if (this.getCraft().getWorld().getBlockAt(loc.x(), loc.y(), loc.z() - 1).getType() == mat) {
                final BlockVec tmpLoc = loc.translate(0, 0, -1);
                if (!destroyedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    destroyedBlocks.add(tmpLoc);
                }
            }
        }
        //clear from previous because now it is in harvest
        if (destroyedBlocks.contains(loc)) {
            destroyedBlocks.remove(loc);
        }
        if (droppedBlocks.contains(loc)) {
            droppedBlocks.remove(loc);
        }
    }
}
