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

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyWorld;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.api.BlockVec;
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
import net.countercraft.movecraft.utils.TownyUtils;
import net.countercraft.movecraft.utils.TownyWorldHeightLimits;
import net.countercraft.movecraft.utils.WGCustomFlagsUtils;
import org.apache.commons.collections.ListUtils;
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

    public TranslationTask(Craft c, Movecraft plugin, Settings settings, I18nSupport i18n, CraftManager craftManager,
                           TranslationTaskData data)
    {
        super(c);
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
        this.data = data;
    }

    @Override public void execute() {
        BlockVec[] blocksList = data.getBlockList();

        final int[] fallThroughBlocks = new int[]{
                0, 8, 9, 10, 11, 31, 37, 38, 39, 40, 50, 51, 55, 59, 63, 65, 68, 69, 70, 72, 75, 76, 77, 78, 83, 85, 93,
                94, 111, 141, 142, 143, 171};

        // blockedByWater=false means an ocean-going vessel
        boolean waterCraft = !getCraft().getType().blockedByWater();
        boolean hoverCraft = getCraft().getType().getCanHover();

        boolean airCraft = getCraft().getType().blockedByWater();

        int hoverLimit = getCraft().getType().getHoverLimit();

        Player craftPilot = craftManager.getPlayerFromCraft(getCraft());

        int[][][] hb = getCraft().getHitBox();
        if (hb == null) return;

        // start by finding the crafts borders
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
        int maxX = getCraft().getMinX() + hb.length;
        int maxZ = getCraft().getMinZ() +
                   hb[0].length;  // safe because if the first x array doesn't have a z array, then it wouldn't be
        // the first x array
        int minX = getCraft().getMinX();
        int minZ = getCraft().getMinZ();

/*		// Load any chunks that you are moving into that are not loaded 
        for (int posX=minX+data.getDx();posX<=maxX+data.getDx();posX++) {
			for (int posZ=minZ+data.getDz();posZ<=maxZ+data.getDz();posZ++) {
				if(getCraft().getW().isChunkLoaded(posX>>4, posZ>>4) == false) {
					getCraft().getW().loadChunk(posX>>4, posZ>>4);
				}
			}
		}*/

        // treat sinking crafts specially
        if (getCraft().getSinking()) {
            waterCraft = true;
            hoverCraft = false;
//			Movecraft.getInstance().getLogger().log( Level.INFO, "Translation task at: "+System.currentTimeMillis() );
        }

        // check the maxheightaboveground limitation, move 1 down if that limit is exceeded
        if (getCraft().getType().getMaxHeightAboveGround() > 0 && data.getDy() >= 0) {
            int x = getCraft().getMaxX() + getCraft().getMinX();
            x = x >> 1;
            int y = getCraft().getMaxY();
            int z = getCraft().getMaxZ() + getCraft().getMinZ();
            z = z >> 1;
            int cy = getCraft().getMinY();
            boolean done = false;
            while (!done) {
                cy = cy - 1;
                if (getCraft().getW().getBlockTypeIdAt(x, cy, z) != 0) done = true;
                if (cy <= 1) done = true;
            }
            if (y - cy > getCraft().getType().getMaxHeightAboveGround()) {
                data.setDy(-1);
            }
        }

        // Find the waterline from the surrounding terrain or from the static level in the craft type
        int waterLine = 0;
        if (waterCraft) {
            if (getCraft().getType().getStaticWaterLevel() == 0) {
                // figure out the water level by examining blocks next to the outer boundaries of the craft
                for (int posY = maxY + 1; (posY >= minY - 1) && (waterLine == 0); posY--) {
                    int numWater = 0;
                    int numAir = 0;
                    int posZ = minZ - 1;
                    int posX;
                    for (posX = minX - 1; (posX <= maxX + 1) && (waterLine == 0); posX++) {
                        int typeID = getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId();
                        if (typeID == 9) numWater++;
                        if (typeID == 0) numAir++;
                    }
                    posZ = maxZ + 1;
                    for (posX = minX - 1; (posX <= maxX + 1) && (waterLine == 0); posX++) {
                        int typeID = getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId();
                        if (typeID == 9) numWater++;
                        if (typeID == 0) numAir++;
                    }
                    posX = minX - 1;
                    for (posZ = minZ; (posZ <= maxZ) && (waterLine == 0); posZ++) {
                        int typeID = getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId();
                        if (typeID == 9) numWater++;
                        if (typeID == 0) numAir++;
                    }
                    posX = maxX + 1;
                    for (posZ = minZ; (posZ <= maxZ) && (waterLine == 0); posZ++) {
                        int typeID = getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId();
                        if (typeID == 9) numWater++;
                        if (typeID == 0) numAir++;
                    }
                    if (numWater > numAir) {
                        waterLine = posY;
                    }
                }
            } else {
                if (waterLine <= maxY + 1) {
                    waterLine = getCraft().getType().getStaticWaterLevel();
                }
            }

            // now add all the air blocks found within the craft's hitbox immediately above the waterline and below
            // to the craft blocks so they will be translated
            HashSet<BlockVec> newHSBlockList = new HashSet<>(Arrays.asList(blocksList));
            int posY = waterLine + 1;
            for (int posX = minX; posX < maxX; posX++) {
                for (int posZ = minZ; posZ < maxZ; posZ++) {
                    if (hb[posX - minX] != null) {
                        if (hb[posX - minX][posZ - minZ] != null) {
                            if (getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId() == 0 &&
                                posY > hb[posX - minX][posZ - minZ][0] && posY < hb[posX - minX][posZ - minZ][1]) {
                                BlockVec l = new BlockVec(posX, posY, posZ);
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
                        if (getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId() == 0) {
                            BlockVec l = new BlockVec(posX, posY, posZ);
                            newHSBlockList.add(l);
                        }
                    }
                }
            }

            blocksList = newHSBlockList.toArray(new BlockVec[newHSBlockList.size()]);
        }

        // check for fuel, burn some from a furnace if needed. Blocks of coal are supported, in addition to coal and
        // charcoal
        double fuelBurnRate = getCraft().getType().getFuelBurnRate();
        // going down doesn't require fuel
        if (data.getDy() == -1 && data.getDx() == 0 && data.getDz() == 0) fuelBurnRate = 0.0;

        if (fuelBurnRate != 0.0 && !getCraft().getSinking()) {
            if (getCraft().getBurningFuel() < fuelBurnRate) {
                Block fuelHolder = null;
                for (BlockVec bTest : blocksList) {
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
                    fail(i18n.get("Translation - Failed Craft out of fuel"));
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

        List<BlockVec> tempBlockList = new ArrayList<>();
        HashSet<BlockVec> existingBlockSet = new HashSet<>(Arrays.asList(blocksList));
        HashSet<EntityUpdateCommand> entityUpdateSet = new HashSet<>();
        Set<MapUpdateCommand> updateSet = new HashSet<>();

        data.setCollisionExplosion(false);
        Set<MapUpdateCommand> explosionSet = new HashSet<>();

        List<Material> harvestBlocks = getCraft().getType().getHarvestBlocks();
        List<BlockVec> harvestedBlocks = new ArrayList<>();
        List<BlockVec> droppedBlocks = new ArrayList<>();
        List<BlockVec> destroyedBlocks = new ArrayList<>();
        List<Material> harvesterBladeBlocks = getCraft().getType().getHarvesterBladeBlocks();

        int hoverOver = data.getDy();
        boolean hoverUseGravity = getCraft().getType().getUseGravity();
        boolean checkHover = (data.getDx() != 0 || data.getDz() != 0);// we want to check only horizontal moves
        boolean canHoverOverWater = getCraft().getType().getCanHoverOverWater();
        boolean townyEnabled = plugin.getTownyPlugin() != null;
        boolean validateTownyExplosion = false;

        Set<TownBlock> townBlockSet = new HashSet<>();
        TownyWorld townyWorld = null;
        TownyWorldHeightLimits townyWorldHeightLimits = null;

        if (townyEnabled && settings.TownyBlockMoveOnSwitchPerm) {
            townyWorld = TownyUtils.getTownyWorld(getCraft().getW());
            if (townyWorld != null) {
                townyEnabled = townyWorld.isUsingTowny();
                if (townyEnabled) {
                    townyWorldHeightLimits = TownyUtils.getWorldLimits(settings, getCraft().getW());
                    if (getCraft().getType().getCollisionExplosion() != 0.0F) {
                        validateTownyExplosion = true;
                    }
                }
            }
        } else {
            townyEnabled = false;
        }

        int craftMinY = 0;
        int craftMaxY = 0;
        boolean clearNewData = false;
        boolean explosionBlockedByTowny = false;
        boolean moveBlockedByTowny = false;
        String townName = "";
        for (int i = 0; i < blocksList.length; i++) {
            BlockVec oldLoc = blocksList[i];
            BlockVec newLoc = oldLoc.translate(data.getDx(), data.getDy(), data.getDz());

            if (newLoc.y > data.getMaxHeight() && newLoc.y > oldLoc.y) {
                fail(i18n.get("Translation - Failed Craft hit height limit"));
                break;
            }
            if (newLoc.y < data.getMinHeight() && newLoc.y < oldLoc.y && !getCraft().getSinking()) {
                fail(i18n.get("Translation - Failed Craft hit minimum height limit"));
                break;
            }

            Location plugLoc = new Location(getCraft().getW(), newLoc.x, newLoc.y, newLoc.z);
            if (craftPilot != null) {
                // See if they are permitted to build in the area, if WorldGuard integration is turned on
                if (plugin.getWorldGuardPlugin() != null && settings.WorldGuardBlockMoveOnBuildPerm) {
                    if (!plugin.getWorldGuardPlugin().canBuild(craftPilot, plugLoc)) {
                        fail(String.format(i18n.get(
                                "Translation - Failed Player is not permitted to build in this WorldGuard region") +
                                           " @ %d,%d,%d", oldLoc.x, oldLoc.y, oldLoc.z));
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
                    if (!WGCustomFlagsUtils.validateFlag(plugin.getWorldGuardPlugin(), plugLoc, plugin.FLAG_MOVE, lp)) {
                        fail(String.format(i18n.get("WGCustomFlags - Translation Failed") + " @ %d,%d,%d", oldLoc.x,
                                           oldLoc.y, oldLoc.z));
                        break;
                    }
                }
                if (townyEnabled) {
                    TownBlock townBlock = TownyUtils.getTownBlock(plugLoc);
                    if (townBlock != null && !townBlockSet.contains(townBlock)) {
                        if (validateTownyExplosion) {
                            if (!explosionBlockedByTowny) {
                                if (!TownyUtils.validateExplosion(townBlock)) {
                                    explosionBlockedByTowny = true;
                                }
                            }
                        }
                        if (TownyUtils.validateCraftMoveEvent(plugin.getTownyPlugin(), p, plugLoc, townyWorld)) {
                            townBlockSet.add(townBlock);
                        } else {
                            int y = plugLoc.getBlockY();
                            boolean oChange = false;
                            if (craftMinY > y) {
                                craftMinY = y;
                                oChange = true;
                            }
                            if (craftMaxY < y) {
                                craftMaxY = y;
                                oChange = true;
                            }
                            if (oChange) {
                                Town town = TownyUtils.getTown(townBlock);
                                if (town != null) {
                                    Location locSpawn = TownyUtils.getTownSpawn(townBlock);
                                    boolean failed = false;
                                    if (locSpawn != null) {
                                        if (!townyWorldHeightLimits.validate(y, locSpawn.getBlockY())) {
                                            failed = true;
                                        }
                                    } else {
                                        failed = true;
                                    }
                                    if (failed) {
                                        if (plugin.getWorldGuardPlugin() != null &&
                                            plugin.getWGCustomFlagsPlugin() != null &&
                                            settings.WGCustomFlagsUsePilotFlag) {
                                            LocalPlayer lp = plugin.getWorldGuardPlugin().wrapPlayer(p);
                                            ApplicableRegionSet regions = plugin.getWorldGuardPlugin()
                                                                                .getRegionManager(plugLoc.getWorld())
                                                                                .getApplicableRegions(plugLoc);
                                            if (regions.size() != 0) {
                                                if (WGCustomFlagsUtils
                                                        .validateFlag(plugin.getWorldGuardPlugin(), plugLoc,
                                                                      plugin.FLAG_MOVE, lp)) {
                                                    failed = false;
                                                }
                                            }
                                        }
                                    }
                                    if (failed) {
                                        townName = town.getName();
                                        moveBlockedByTowny = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            //check for chests around
            Material testMaterial = getCraft().getW().getBlockAt(oldLoc.x, oldLoc.y, oldLoc.z).getType();
            if (testMaterial == Material.CHEST || testMaterial == Material.TRAPPED_CHEST) {
                if (!checkChests(testMaterial, newLoc, existingBlockSet)) {
                    //prevent chests collision
                    fail(String.format(i18n.get("Translation - Failed Craft is obstructed") + " @ %d,%d,%d,%s",
                                       newLoc.x, newLoc.y, newLoc.z,
                                       getCraft().getW().getBlockAt(newLoc.x, newLoc.y, newLoc.z).getType()
                                                 .toString()));
                    break;
                }
            }

            boolean blockObstructed = false;
            if (getCraft().getSinking()) {
                int testID = getCraft().getW().getBlockAt(newLoc.x, newLoc.y, newLoc.z).getTypeId();
                blockObstructed =
                        !(Arrays.binarySearch(fallThroughBlocks, testID) >= 0) && !existingBlockSet.contains(newLoc);
            } else if (!waterCraft) {
                // New block is not air or a piston head and is not part of the existing ship
                testMaterial = getCraft().getW().getBlockAt(newLoc.x, newLoc.y, newLoc.z).getType();
                blockObstructed = (testMaterial != Material.AIR && testMaterial != Material.PISTON_EXTENSION) &&
                                  !existingBlockSet.contains(newLoc);
            } else {
                // New block is not air or water or a piston head and is not part of the existing ship
                testMaterial = getCraft().getW().getBlockAt(newLoc.x, newLoc.y, newLoc.z).getType();
                blockObstructed = (testMaterial != Material.AIR && testMaterial != Material.STATIONARY_WATER &&
                                   testMaterial != Material.WATER && testMaterial != Material.PISTON_EXTENSION) &&
                                  !existingBlockSet.contains(newLoc);
            }

            boolean ignoreBlock = false;
            // air never obstructs anything
            if (getCraft().getW().getBlockAt(oldLoc.x, oldLoc.y, oldLoc.z).getType() == Material.AIR &&
                blockObstructed) {
                ignoreBlock = true;
                blockObstructed = false;
            }

            testMaterial = getCraft().getW().getBlockAt(newLoc.x, newLoc.y, newLoc.z).getType();
            boolean bladeOK = true;
            if (blockObstructed) {
                if (hoverCraft || !harvestBlocks.isEmpty()) {
                    // New block is not harvested block
                    if (harvestBlocks.contains(testMaterial) && !existingBlockSet.contains(newLoc)) {
                        Material tmpType = getCraft().getW().getBlockAt(oldLoc.x, oldLoc.y, oldLoc.z).getType();
                        if (!harvesterBladeBlocks.isEmpty()) {
                            if (!harvesterBladeBlocks.contains(tmpType)) {
                                bladeOK = false;
                            }
                        }
                        if (bladeOK) {
                            blockObstructed = false;
                            boolean harvestBlock = true;
                            tryPutToDestroyBox(testMaterial, newLoc, harvestedBlocks, droppedBlocks, destroyedBlocks);
                            harvestedBlocks.add(newLoc);
                        }
                    }
                }
            }

            if (blockObstructed || moveBlockedByTowny) {
                if (hoverCraft && checkHover) {
                    //we check one up ever, if it is hovercraft and one down if it's using gravity
                    if (hoverOver == 0 && newLoc.y + 1 <= data.getMaxHeight()) {
                        //first was checked actual level, now check if we can go up
                        hoverOver = 1;
                        data.setDy(1);
                        clearNewData = true;
                    } else if (hoverOver >= 1) {
                        //check other options to go up
                        if (hoverOver < hoverLimit + 1 && newLoc.y + 1 <= data.getMaxHeight()) {
                            data.setDy(hoverOver + 1);
                            hoverOver += 1;
                            clearNewData = true;
                        } else {
                            if (hoverUseGravity && newLoc.y - hoverOver - 1 >= data.getMinHeight()) {
                                //we are on the maximum of top
                                //if we can't go up so we test bottom side
                                data.setDy(-1);
                                hoverOver = -1;
                            } else {
                                // no way - back to original dY, turn off hovercraft for this move
                                // and get original data again for all explosions
                                data.setDy(0);
                                hoverOver = 0;
                                hoverCraft = false;
                                hoverUseGravity = false;
                            }
                            clearNewData = true;
                        }
                    } else if (hoverOver <= -1) {
                        //we cant go down for 1 block, check more to hoverLimit
                        if (hoverOver > -hoverLimit - 1 && newLoc.y - 1 >= data.getMinHeight()) {
                            data.setDy(hoverOver - 1);
                            hoverOver -= 1;
                            clearNewData = true;
                        } else {
                            // no way - back to original dY, turn off hovercraft for this move
                            // and get original data again for all explosions
                            data.setDy(0);
                            hoverOver = 0;
                            hoverUseGravity = false;
                            clearNewData = true;
                            hoverCraft = false;
                        }
                    } else {
                        // no way - reached MaxHeight during looking new way upstairs
                        if (hoverUseGravity && newLoc.y - 1 >= data.getMinHeight()) {
                            //we are on the maximum of top
                            //if we can't go up so we test bottom side
                            data.setDy(-1);
                            hoverOver = -1;
                        } else {
                            // - back to original dY, turn off hovercraft for this move
                            // and get original data again for all explosions
                            data.setDy(0);
                            hoverOver = 0;
                            hoverUseGravity = false;
                            hoverCraft = false;
                        }
                        clearNewData = true;
                    }
                    // End hovercraft stuff
                } else {
                    // handle sinking ship collisions
                    if (getCraft().getSinking()) {
                        if (getCraft().getType().getExplodeOnCrash() != 0.0F && !explosionBlockedByTowny) {
                            int explosionKey = (int) (0 - (getCraft().getType().getExplodeOnCrash() * 100));
                            if (getCraft().getW().getBlockAt(oldLoc.x, oldLoc.y, oldLoc.z).getType() != Material.AIR) {
                                explosionSet.add(new MapUpdateCommand(oldLoc, explosionKey, (byte) 0, getCraft()));
                                data.setCollisionExplosion(true);
                            }
                        } else {
                            // use the explosion code to clean up the craft, but not with enough force to do anything
                            if (getCraft().getW().getBlockAt(oldLoc.x, oldLoc.y, oldLoc.z).getType() != Material.AIR) {
                                int explosionKey = 0 - 1;
                                explosionSet.add(new MapUpdateCommand(oldLoc, explosionKey, (byte) 0, getCraft()));
                                data.setCollisionExplosion(true);
                            }
                        }
                    } else {
                        // Explode if the craft is set to have a CollisionExplosion. Also keep moving for spectacular
                        // ramming collisions
                        if (getCraft().getType().getCollisionExplosion() == 0.0F) {
                            if (moveBlockedByTowny) {
                                fail(String.format(i18n.get("Towny - Translation Failed") + " %s @ %d,%d,%d", townName,
                                                   oldLoc.x, oldLoc.y, oldLoc.z));
                            } else {
                                fail(String.format(
                                        i18n.get("Translation - Failed Craft is obstructed") + " @ %d,%d,%d,%s",
                                        oldLoc.x, oldLoc.y, oldLoc.z,
                                        getCraft().getW().getBlockAt(newLoc.x, newLoc.y, newLoc.z).getType()
                                                  .toString()));
                                getCraft().setCruising(false);
                            }
                            break;
                        } else if (explosionBlockedByTowny) {
                            if (getCraft().getW().getBlockAt(oldLoc.x, oldLoc.y, oldLoc.z).getType() != Material.AIR) {
                                int explosionKey = 0 - 1;
                                explosionSet.add(new MapUpdateCommand(oldLoc, explosionKey, (byte) 0, getCraft()));
                                data.setCollisionExplosion(true);
                            }
                        } else {
                            int explosionKey = (int) (0 - (getCraft().getType().getCollisionExplosion() * 100));
                            if (getCraft().getW().getBlockAt(oldLoc.x, oldLoc.y, oldLoc.z).getType() != Material.AIR) {
                                explosionSet.add(new MapUpdateCommand(oldLoc, explosionKey, (byte) 0, getCraft()));
                                data.setCollisionExplosion(true);
                            }
                        }
                    }
                }
            } else {
                //block not obstructed
                int oldID = getCraft().getW().getBlockTypeIdAt(oldLoc.x, oldLoc.y, oldLoc.z);
                byte oldData = getCraft().getW().getBlockAt(oldLoc.x, oldLoc.y, oldLoc.z).getData();
                // remove water from sinking crafts
                if (getCraft().getSinking()) {
                    if ((oldID == 8 || oldID == 9) && oldLoc.y > waterLine) oldID = 0;
                }

                if (!ignoreBlock) {
                    updateSet.add(new MapUpdateCommand(oldLoc, newLoc, oldID, oldData, getCraft()));
                    tempBlockList.add(newLoc);
                }

                if (i == blocksList.length - 1) {
                    if ((hoverCraft && hoverUseGravity) ||
                        (hoverUseGravity && newLoc.y > data.getMaxHeight() && hoverOver == 0)) {
                        //hovecraft using gravity or something else using gravity and flying over its limit
                        int iFreeSpace = 0;
                        //canHoverOverWater adds 1 to dY for better check water under craft
                        // best way should be expand selected region to each first blocks under craft
                        if (hoverOver == 0) {
                            //we go directly forward so we check if we can go down
                            for (int ii = -1; ii > -hoverLimit - 2 - (canHoverOverWater ? 0 : 1); ii--) {
                                if (!isFreeSpace(data.getDx(), hoverOver + ii, data.getDz(), blocksList,
                                                 existingBlockSet, waterCraft, hoverCraft, harvestBlocks,
                                                 canHoverOverWater, checkHover)) {
                                    break;
                                }
                                iFreeSpace++;
                            }
                            if (data.failed()) {
                                break;
                            }
                            if (iFreeSpace > hoverLimit - (canHoverOverWater ? 0 : 1)) {
                                data.setDy(-1);
                                hoverOver = -1;
                                clearNewData = true;
                            }
                        } else if (hoverOver == 1 && !airCraft) {
                            //prevent fly higher than hoverLimit
                            for (int ii = -1; ii > -hoverLimit - 2; ii--) {
                                if (!isFreeSpace(data.getDx(), hoverOver + ii, data.getDz(), blocksList,
                                                 existingBlockSet, waterCraft, hoverCraft, harvestBlocks,
                                                 canHoverOverWater, checkHover)) {
                                    break;
                                }
                                iFreeSpace++;
                            }
                            if (data.failed()) {
                                break;
                            }
                            if (iFreeSpace > hoverLimit) {
                                if (bladeOK) {
                                    fail(i18n.get("Translation - Failed Craft hit height limit"));
                                } else {
                                    fail(String.format(
                                            i18n.get("Translation - Failed Craft is obstructed") + " @ %d,%d,%d,%s",
                                            oldLoc.x, oldLoc.y, oldLoc.z,
                                            getCraft().getW().getBlockAt(newLoc.x, newLoc.y, newLoc.z).getType()
                                                      .toString()));
                                }
                                break;
                            }
                        } else if (hoverOver > 1) {
                            //prevent jump through block
                            for (int ii = 1; ii < hoverOver - 1; ii++) {
                                if (!isFreeSpace(0, ii, 0, blocksList, existingBlockSet, waterCraft, hoverCraft,
                                                 harvestBlocks, canHoverOverWater, checkHover)) {
                                    break;
                                }
                                iFreeSpace++;
                            }
                            if (data.failed()) {
                                break;
                            }
                            if (iFreeSpace + 2 < hoverOver) {
                                data.setDy(-1);
                                hoverOver = -1;
                                clearNewData = true;
                            }
                        } else if (hoverOver < -1) {
                            //prevent jump through block
                            for (int ii = -1; ii > hoverOver + 1; ii--) {
                                if (!isFreeSpace(0, ii, 0, blocksList, existingBlockSet, waterCraft, hoverCraft,
                                                 harvestBlocks, canHoverOverWater, checkHover)) {
                                    break;
                                }
                                iFreeSpace++;
                            }
                            if (data.failed()) {
                                break;
                            }
                            if (iFreeSpace + 2 < -hoverOver) {
                                data.setDy(0);
                                hoverOver = 0;
                                hoverCraft = false;
                                clearNewData = true;
                            }
                        }
                        if (!canHoverOverWater) {
                            if (hoverOver >= 1) {
                                //others hoverOver values we have checked jet
                                for (int ii = hoverOver - 1; ii > hoverOver - hoverLimit - 2; ii--) {
                                    if (!isFreeSpace(0, ii, 0, blocksList, existingBlockSet, waterCraft, hoverCraft,
                                                     harvestBlocks, canHoverOverWater, checkHover)) {
                                        break;
                                    }
                                    iFreeSpace++;
                                }
                                if (data.failed()) {
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
                data.setCollisionExplosion(false);
                explosionSet.clear();
                clearNewData = false;
                townBlockSet.clear();
                craftMinY = 0;
                craftMaxY = 0;
            }
        } //END OF: for ( int i = 0; i < blocksList.length; i++ ) {

        if (data.collisionExplosion()) {
            // mark the craft to check for sinking, remove the exploding blocks from the blocklist, and submit the
            // explosions for map update
            for (MapUpdateCommand m : explosionSet) {

                if (existingBlockSet.contains(m.getNewBlockLocation())) {
                    existingBlockSet.remove(m.getNewBlockLocation());
                    if (settings.FadeWrecksAfter > 0) {
                        int typeID = getCraft().getW().getBlockAt(m.getNewBlockLocation().x, m.getNewBlockLocation().y,
                                                                  m.getNewBlockLocation().z).getTypeId();
                        if (typeID != 0 && typeID != 9) {
                            plugin.blockFadeTimeMap.put(m.getNewBlockLocation(), System.currentTimeMillis());
                            plugin.blockFadeTypeMap.put(m.getNewBlockLocation(), typeID);
                            if (m.getNewBlockLocation().y <= waterLine) {
                                plugin.blockFadeWaterMap.put(m.getNewBlockLocation(), true);
                            } else {
                                plugin.blockFadeWaterMap.put(m.getNewBlockLocation(), false);
                            }
                            plugin.blockFadeWorldMap.put(m.getNewBlockLocation(), getCraft().getW());
                        }
                    }
                }

                // if the craft is sinking, remove all solid blocks above the one that hit the ground from the craft
                // for smoothing sinking
                if (getCraft().getSinking() &&
                    (getCraft().getType().getExplodeOnCrash() == 0.0 || explosionBlockedByTowny)) {
                    int posy = m.getNewBlockLocation().y + 1;
                    int testID = getCraft().getW()
                                           .getBlockAt(m.getNewBlockLocation().x, posy, m.getNewBlockLocation().z)
                                           .getTypeId();

                    while (posy <= maxY && !(Arrays.binarySearch(fallThroughBlocks, testID) >= 0)) {
                        BlockVec testLoc = new BlockVec(m.getNewBlockLocation().x, posy,
                                                        m.getNewBlockLocation().z);
                        if (existingBlockSet.contains(testLoc)) {
                            existingBlockSet.remove(testLoc);
                            if (settings.FadeWrecksAfter > 0) {
                                int typeID = getCraft().getW().getBlockAt(testLoc.x, testLoc.y, testLoc.z).getTypeId();
                                if (typeID != 0 && typeID != 9) {
                                    plugin.blockFadeTimeMap.put(testLoc, System.currentTimeMillis());
                                    plugin.blockFadeTypeMap.put(testLoc, typeID);
                                    if (testLoc.y <= waterLine) {
                                        plugin.blockFadeWaterMap.put(testLoc, true);
                                    } else {
                                        plugin.blockFadeWaterMap.put(testLoc, false);
                                    }
                                    plugin.blockFadeWorldMap.put(testLoc, getCraft().getW());
                                }
                            }
                        }
                        posy = posy + 1;
                        testID = getCraft().getW()
                                           .getBlockAt(m.getNewBlockLocation().x, posy, m.getNewBlockLocation().z)
                                           .getTypeId();
                    }
                }
            }

            BlockVec[] newBlockList = existingBlockSet.toArray(new BlockVec[existingBlockSet.size()]);
            data.setBlockList(newBlockList);
            data.setUpdates(explosionSet.toArray(new MapUpdateCommand[1]));

            fail(i18n.get("Translation - Failed Craft is obstructed"));
            if (getCraft().getSinking()) {
                if (getCraft().getType().getSinkPercent() != 0.0) {
                    getCraft().setLastBlockCheck(0);
                }
                getCraft().setLastCruiseUpdate(-1);
            }
        }

        if (!data.failed()) {
            BlockVec[] newBlockList = tempBlockList.toArray(new BlockVec[tempBlockList.size()]);
            data.setBlockList(newBlockList);

            //prevents torpedo and rocket pilots :)
            if (getCraft().getType().getMoveEntities() && !getCraft().getSinking()) {
                // Move entities within the craft
                List<Entity> eList = null;
                int numTries = 0;
                while ((eList == null) && (numTries < 100)) {
                    try {
                        eList = getCraft().getW().getEntities();
                    } catch (java.util.ConcurrentModificationException e) {
                        numTries++;
                    }
                }

                for (Entity pTest : eList) {
                    if (MathUtils.playerIsWithinBoundingPolygon(getCraft().getHitBox(), getCraft().getMinX(),
                                                                getCraft().getMinZ(),
                                                                MathUtils.bukkit2MovecraftLoc(pTest.getLocation()))) {
                        if (pTest.getType() == EntityType.PLAYER) {
                            Player player = (Player) pTest;
                            getCraft().getMovedPlayers().put(player, System.currentTimeMillis());
                        } // only move players for now, reduce monsters on airships
                        //if(pTest.getType()!=org.bukkit.entity.EntityType.DROPPED_ITEM ) {
                        if (pTest instanceof LivingEntity) {
                            Location tempLoc = pTest.getLocation();
                            if (getCraft().getPilotLocked() && pTest == craftManager.getPlayerFromCraft(getCraft())) {
                                tempLoc.setX(getCraft().getPilotLockedX());
                                tempLoc.setY(getCraft().getPilotLockedY());
                                tempLoc.setZ(getCraft().getPilotLockedZ());
                            }
                            tempLoc = tempLoc.add(data.getDx(), data.getDy(), data.getDz());
                            Location newPLoc = new Location(getCraft().getW(), tempLoc.getX(), tempLoc.getY(),
                                                            tempLoc.getZ());
                            newPLoc.setPitch(pTest.getLocation().getPitch());
                            newPLoc.setYaw(pTest.getLocation().getYaw());

                            EntityUpdateCommand eUp = new EntityUpdateCommand(pTest.getLocation().clone(), newPLoc,
                                                                              pTest);
                            entityUpdateSet.add(eUp);
                            if (getCraft().getPilotLocked() && pTest == craftManager.getPlayerFromCraft(getCraft())) {
                                getCraft().setPilotLockedX(tempLoc.getX());
                                getCraft().setPilotLockedY(tempLoc.getY());
                                getCraft().setPilotLockedZ(tempLoc.getZ());
                            }
                        }
                    }
                }
            } else {
                //add releaseTask without playermove to manager
                if (!getCraft().getType().getCruiseOnPilot() && !getCraft().getSinking())  // not necessary to release
                    // cruiseonpilot crafts, because they will already be released
                    craftManager.addReleaseTask(getCraft());
            }

            // remove water near sinking crafts
            if (getCraft().getSinking()) {
                int posX;
                int posY = maxY;
                int posZ;
                if (posY > waterLine) {
                    for (posX = minX - 1; posX <= maxX + 1; posX++) {
                        for (posZ = minZ - 1; posZ <= maxZ + 1; posZ++) {
                            if (getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId() == 9 ||
                                getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId() == 8) {
                                BlockVec loc = new BlockVec(posX, posY, posZ);
                                updateSet.add(new MapUpdateCommand(loc, 0, (byte) 0, getCraft()));
                            }
                        }
                    }
                }
                for (posY = maxY + 1; (posY >= minY - 1) && (posY > waterLine); posY--) {
                    posZ = minZ - 1;
                    for (posX = minX - 1; posX <= maxX + 1; posX++) {
                        if (getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId() == 9 ||
                            getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId() == 8) {
                            BlockVec loc = new BlockVec(posX, posY, posZ);
                            updateSet.add(new MapUpdateCommand(loc, 0, (byte) 0, getCraft()));
                        }
                    }
                    posZ = maxZ + 1;
                    for (posX = minX - 1; posX <= maxX + 1; posX++) {
                        if (getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId() == 9 ||
                            getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId() == 8) {
                            BlockVec loc = new BlockVec(posX, posY, posZ);
                            updateSet.add(new MapUpdateCommand(loc, 0, (byte) 0, getCraft()));
                        }
                    }
                    posX = minX - 1;
                    for (posZ = minZ - 1; posZ <= maxZ + 1; posZ++) {
                        if (getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId() == 9 ||
                            getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId() == 8) {
                            BlockVec loc = new BlockVec(posX, posY, posZ);
                            updateSet.add(new MapUpdateCommand(loc, 0, (byte) 0, getCraft()));
                        }
                    }
                    posX = maxX + 1;
                    for (posZ = minZ - 1; posZ <= maxZ + 1; posZ++) {
                        if (getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId() == 9 ||
                            getCraft().getW().getBlockAt(posX, posY, posZ).getTypeId() == 8) {
                            BlockVec loc = new BlockVec(posX, posY, posZ);
                            updateSet.add(new MapUpdateCommand(loc, 0, (byte) 0, getCraft()));
                        }
                    }
                }
            }

            //Set blocks that are no longer craft to air

//
// /**********************************************************************************************************
//                        *   I had problems with ListUtils (I tried commons-collections 3.2.1. and 4.0 without
// success)
//                        *   so I replaced Lists with Sets
//                        * 
//                        *   Caused by: java.lang.NoClassDefFoundError: org/apache/commons/collections/ListUtils
//                        *   at net.countercraft.movecraft.async.translation.TranslationTask.execute
// (TranslationTask.java:716)
//                        *
// mwkaicz 24-02-2015
//
// ***********************************************************************************************************/
//                        Set<BlockVec> setA = new HashSet(Arrays.asList(blocksList));
//                        Set<BlockVec> setB = new HashSet(Arrays.asList(newBlockList));
//                        setA.removeAll(setB);
//                        BlockVec[] arrA = new BlockVec[0];
//                        arrA = setA.toArray(arrA);
//                        List<BlockVec> airLocation = Arrays.asList(arrA);
            List<BlockVec> airLocation = ListUtils
                    .subtract(Arrays.asList(blocksList), Arrays.asList(newBlockList));

            for (BlockVec l1 : airLocation) {
                // for watercraft, fill blocks below the waterline with water
                if (waterCraft) {
                    if (l1.y <= waterLine) {
                        // if there is air below the ship at the current position, don't fill in with water
                        BlockVec testAir = new BlockVec(l1.x, l1.y - 1, l1.z);
                        while (existingBlockSet.contains(testAir)) {
                            testAir = new BlockVec(l1.x, testAir.y - 1, l1.z);
                        }
                        if (getCraft().getW().getBlockAt(testAir.x, testAir.y, testAir.z).getTypeId() == 0) {
                            if (getCraft().getSinking()) {
                                updateSet.add(new MapUpdateCommand(l1, 0, (byte) 0, null,
                                                                   getCraft().getType().getSmokeOnSink()));
                            } else {
                                updateSet.add(new MapUpdateCommand(l1, 0, (byte) 0, null));
                            }
                        } else {
                            updateSet.add(new MapUpdateCommand(l1, 9, (byte) 0, null));
                        }
                    } else {
                        if (getCraft().getSinking()) {
                            updateSet.add(new MapUpdateCommand(l1, 0, (byte) 0, null,
                                                               getCraft().getType().getSmokeOnSink()));
                        } else {
                            updateSet.add(new MapUpdateCommand(l1, 0, (byte) 0, null));
                        }
                    }
                } else {
                    if (getCraft().getSinking()) {
                        updateSet.add(new MapUpdateCommand(l1, 0, (byte) 0, null,
                                                           getCraft().getType().getSmokeOnSink()));
                    } else {
                        updateSet.add(new MapUpdateCommand(l1, 0, (byte) 0, null));
                    }
                }
            }

            //add destroyed parts of growed
            for (BlockVec destroyedLocation : destroyedBlocks) {
                updateSet.add(new MapUpdateCommand(destroyedLocation, 0, (byte) 0, null));
            }
            data.setUpdates(updateSet.toArray(new MapUpdateCommand[1]));
            data.setEntityUpdates(entityUpdateSet.toArray(new EntityUpdateCommand[1]));

            if (data.getDy() != 0) {
                data.setHitbox(BoundingBoxUtils.translateBoundingBoxVertically(data.getHitbox(), data.getDy()));
            }

            data.setMinX(data.getMinX() + data.getDx());
            data.setMinZ(data.getMinZ() + data.getDz());
        }

        captureYield(blocksList, harvestedBlocks, droppedBlocks);
    }

    private void fail(String message) {
        data.setFailed(true);
        data.setFailMessage(message);
    }

    public TranslationTaskData getData() {
        return data;
    }

    private boolean isFreeSpace(int x, int y, int z, BlockVec[] blocksList, Set<BlockVec> existingBlockSet,
                                boolean waterCraft, boolean hoverCraft, List<Material> harvestBlocks,
                                boolean canHoverOverWater, boolean checkHover)
    {
        boolean isFree = true;
        // this checking for hovercrafts should be faster with separating horizontal layers and checking only really
        // necessaries,
        // or more better: remember what checked in each translation, but it's beyond my current abilities, I will
        // try to solve it in future
        for (BlockVec oldLoc : blocksList) {
            BlockVec newLoc = oldLoc.translate(x, y, z);

            Material testMaterial = getCraft().getW().getBlockAt(newLoc.x, newLoc.y, newLoc.z).getType();
            if (!canHoverOverWater) {
                if (testMaterial == Material.STATIONARY_WATER || testMaterial == Material.WATER) {
                    fail(i18n.get("Translation - Failed Craft over water"));
                }
            }

            if (newLoc.y >= data.getMaxHeight() && newLoc.y > oldLoc.y && !checkHover) {
                //if ( newLoc.getY() >= data.getMaxHeight() && newLoc.getY() > oldLoc.getY()) {
                isFree = false;
                break;
            }
            if (newLoc.y <= data.getMinHeight() && newLoc.y < oldLoc.y) {
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
                blockObstructed = !(harvestBlocks.contains(testMaterial) && !existingBlockSet.contains(newLoc));
            }

            if (blockObstructed) {
                isFree = false;
                break;
            }
        }
        return isFree;
    }

    private boolean checkChests(Material mBlock, BlockVec newLoc, Set<BlockVec> existingBlockSet)
    {

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

    private void captureYield(BlockVec[] blocksList, List<BlockVec> harvestedBlocks,
                              List<BlockVec> droppedBlocks)
    {
        if (harvestedBlocks.isEmpty()) {
            return;
        }

        Map<Material, ArrayList<Block>> crates = new HashMap<>();
        HashSet<ItemDropUpdateCommand> itemDropUpdateSet = new HashSet<>();
        Set<Material> droppedSet = new HashSet<>();
        HashMap<BlockVec, ItemStack[]> droppedMap = new HashMap<>();
        harvestedBlocks.addAll(droppedBlocks);

        for (BlockVec harvestedBlock : harvestedBlocks) {
            Block block = getCraft().getW().getBlockAt(harvestedBlock.x, harvestedBlock.y, harvestedBlock.z);
            ItemStack[] drops = block.getDrops().toArray(new ItemStack[block.getDrops().size()]);
            boolean oSomethingToDrop = false;
            boolean oWheat = false;
            for (ItemStack drop : drops) {
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
                Random rand = new Random();
                int amount = rand.nextInt(4);
                if (amount > 0) {
                    ItemStack seeds = new ItemStack(Material.SEEDS, amount);
                    HashSet<ItemStack> d = new HashSet<>(Arrays.asList(drops));
                    d.add(seeds);
                    drops = d.toArray(new ItemStack[d.size()]);
                }
            }
            if (drops.length > 0 && oSomethingToDrop) {
                droppedMap.put(harvestedBlock, drops);
            }
        }

        //find chests
        for (BlockVec bTest : blocksList) {
            Block b = getCraft().getW().getBlockAt(bTest.x, bTest.y, bTest.z);
            if (b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST) {
                Inventory inv = ((InventoryHolder) b.getState()).getInventory();
                //get chests with dropped Items
                for (Material mat : droppedSet) {
                    for (Entry<Integer, ? extends ItemStack> pair : ((HashMap<Integer, ? extends ItemStack>) inv
                            .all(mat)).entrySet()) {
                        ItemStack stack = pair.getValue();
                        if (stack.getAmount() < stack.getMaxStackSize() || inv.firstEmpty() > -1) {
                            ArrayList<Block> blocks = crates.get(mat);
                            if (blocks == null) {
                                blocks = new ArrayList<>();
                            }
                            if (blocks.contains(b)) {
                            } else {
                                blocks.add(b);
                            }
                            crates.put(mat, blocks);
                        }
                    }
                }
                // get chests with free slots
                if (inv.firstEmpty() != -1) {
                    Material mat = Material.AIR;
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

        for (BlockVec harvestedBlock : harvestedBlocks) {
            if (droppedMap.containsKey(harvestedBlock)) {
                ItemStack[] drops = droppedMap.get(harvestedBlock);
                for (ItemStack drop : drops) {
                    ItemStack retStack;
                    if (droppedBlocks.contains(harvestedBlock)) {
                        retStack = drop;
                    } else {
                        retStack = putInToChests(drop, crates);
                    }
                    if (retStack != null) {
                        //drop items on position 
                        Location loc = new Location(getCraft().getW(), harvestedBlock.x, harvestedBlock.y,
                                                    harvestedBlock.z);
                        ItemDropUpdateCommand iUp = new ItemDropUpdateCommand(loc, drop);
                        itemDropUpdateSet.add(iUp);
                    }
                }
            }
        }
        data.setItemDropUpdates(itemDropUpdateSet.toArray(new ItemDropUpdateCommand[1]));
    }

    private ItemStack putInToChests(ItemStack stack, Map<Material, ArrayList<Block>> chests) {
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
        ItemStack retStack = null;

        if (chests.get(mat) != null) {
            for (Block b : chests.get(mat)) {
                Inventory inv = ((InventoryHolder) b.getState()).getInventory();
                Map<Integer, ItemStack> leftover = inv.addItem(stack); //try add stack to the chest inventory
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
                    for (int i = 0; i < leftover.size(); i++) {
                        stack = leftover.get(i);
                        break;
                    }
                } else {
                    return null;
                }
            }
        }

        mat = Material.AIR;
        if (chests.get(mat) != null) {
            for (Block b : chests.get(mat)) {
                Inventory inv = ((InventoryHolder) b.getState()).getInventory();
                Map<Integer, ItemStack> leftover = inv.addItem(stack);
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
                    for (int i = 0; i < leftover.size(); i++) {
                        return leftover.get(i);
                    }
                } else {
                    //create new stack for this material
                    if (stack != null) {
                        Material newMat = stack.getType();
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

    private void tryPutToDestroyBox(Material mat, BlockVec loc, List<BlockVec> harvestedBlocks,
                                    List<BlockVec> droppedBlocks, List<BlockVec> destroyedBlocks)
    {
        if (mat == Material.DOUBLE_PLANT ||
            mat == Material.WOODEN_DOOR ||
            mat == Material.IRON_DOOR_BLOCK
//            ||                            
//                mat.equals(Material.BANNER)    // Apparently Material.Banner was removed from the class
                ) {
            if (getCraft().getW().getBlockAt(loc.x, loc.y + 1, loc.z).getType() == mat) {
                BlockVec tmpLoc = loc.translate(0, 1, 0);
                if (!destroyedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    destroyedBlocks.add(tmpLoc);
                }
            } else if (getCraft().getW().getBlockAt(loc.x, loc.y - 1, loc.z).getType() == mat) {
                BlockVec tmpLoc = loc.translate(0, -1, 0);
                if (!destroyedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    destroyedBlocks.add(tmpLoc);
                }
            }
        } else if (mat == Material.CACTUS || mat == Material.SUGAR_CANE_BLOCK) {
            BlockVec tmpLoc = loc.translate(0, 1, 0);
            Material tmpType = getCraft().getW().getBlockAt(tmpLoc.x, tmpLoc.y, tmpLoc.z).getType();
            while (tmpType == mat) {
                if (!droppedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    droppedBlocks.add(tmpLoc);
                }
                if (!destroyedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    destroyedBlocks.add(tmpLoc);
                }
                tmpLoc = tmpLoc.translate(0, 1, 0);
                tmpType = getCraft().getW().getBlockAt(tmpLoc.x, tmpLoc.y, tmpLoc.z).getType();
            }
        } else if (mat == Material.BED_BLOCK) {
            if (getCraft().getW().getBlockAt(loc.x + 1, loc.y, loc.z).getType() == mat) {
                BlockVec tmpLoc = loc.translate(1, 0, 0);
                if (!destroyedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    destroyedBlocks.add(tmpLoc);
                }
            } else if (getCraft().getW().getBlockAt(loc.x - 1, loc.y, loc.z).getType() == mat) {
                BlockVec tmpLoc = loc.translate(-1, 0, 0);
                if (!destroyedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    destroyedBlocks.add(tmpLoc);
                }
            }
            if (getCraft().getW().getBlockAt(loc.x, loc.y, loc.z + 1).getType() == mat) {
                BlockVec tmpLoc = loc.translate(0, 0, 1);
                if (!destroyedBlocks.contains(tmpLoc) && !harvestedBlocks.contains(tmpLoc)) {
                    destroyedBlocks.add(tmpLoc);
                }
            } else if (getCraft().getW().getBlockAt(loc.x, loc.y, loc.z - 1).getType() == mat) {
                BlockVec tmpLoc = loc.translate(0, 0, -1);
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
