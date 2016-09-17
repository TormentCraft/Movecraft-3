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

package net.countercraft.movecraft.async.detection;

import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyWorld;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.api.BlockVec;
import net.countercraft.movecraft.api.IntRange;
import net.countercraft.movecraft.async.AsyncTask;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.BlockNames;
import net.countercraft.movecraft.utils.BoundingBoxUtils;
import net.countercraft.movecraft.utils.TownyUtils;
import net.countercraft.movecraft.utils.TownyWorldHeightLimits;
import net.countercraft.movecraft.utils.WGCustomFlagsUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class DetectionTask extends AsyncTask {
    private final Movecraft plugin;
    private final Settings settings;
    private final I18nSupport i18n;
    private final BlockVec startLocation;
    private final IntRange sizeRange;
    private Integer maxX;
    private Integer maxY;
    private Integer maxZ;
    private Integer minY;
    private final Stack<BlockVec> blockStack = new Stack<>();
    private final Set<BlockVec> blockList = new HashSet<>();
    private final Set<BlockVec> visited = new HashSet<>();
    private final Map<List<Integer>, Integer> blockTypeCount = new HashMap<>();
    private Map<List<Integer>, List<Double>> dFlyBlocks;
    private final DetectionTaskData data;

    private int craftMinY = 0;
    private int craftMaxY = 0;
    private boolean townyEnabled = false;
    Set<TownBlock> townBlockSet = new HashSet<>();
    TownyWorld townyWorld = null;
    TownyWorldHeightLimits townyWorldHeightLimits = null;

    public DetectionTask(Craft c, BlockVec startLocation, IntRange sizeRange, Integer[] allowedBlocks,
                         Integer[] forbiddenBlocks, Player player, Player notificationPlayer, World w, Movecraft plugin,
                         Settings settings, I18nSupport i18n)
    {
        super(c);
        this.startLocation = startLocation;
        this.sizeRange = sizeRange;
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        data = new DetectionTaskData(w, player, notificationPlayer, allowedBlocks, forbiddenBlocks);

        this.townyEnabled = plugin.getTownyPlugin() != null;
        if (townyEnabled && settings.TownyBlockMoveOnSwitchPerm) {
            this.townyWorld = TownyUtils.getTownyWorld(getCraft().getW());
            if (townyWorld != null) {
                this.townyEnabled = townyWorld.isUsingTowny();
                if (townyEnabled) townyWorldHeightLimits = TownyUtils.getWorldLimits(settings, getCraft().getW());
            }
        } else {
            this.townyEnabled = false;
        }
    }

    @Override public void execute() {

        Map<List<Integer>, List<Double>> flyBlocks = new HashMap<>(getCraft().getType().getFlyBlocks());
        dFlyBlocks = flyBlocks;

        blockStack.push(startLocation);

        do {
            detectSurrounding(blockStack.pop());
        } while (!blockStack.isEmpty());

        if (data.failed()) {
            return;
        }

        if (isWithinLimit(blockList.size(), sizeRange, false)) {

            data.setBlockList(finaliseBlockList(blockList));

            if (confirmStructureRequirements(flyBlocks, blockTypeCount)) {
                data.setHitBox(BoundingBoxUtils
                                       .formBoundingBox(data.getBlockList(), data.getMinX(), maxX, data.getMinZ(),
                                                        maxZ));
            }
        }
    }

    private void detectBlock(int x, int y, int z) {

        BlockVec workingLocation = new BlockVec(x, y, z);

        if (notVisited(workingLocation, visited)) {

            int testID = 0;
            int testData = 0;
            try {
                testData = data.getWorld().getBlockAt(x, y, z).getData();
                testID = data.getWorld().getBlockTypeIdAt(x, y, z);
            } catch (Exception e) {
                fail(String.format(i18n.get("Detection - Craft too large"), sizeRange.max));
            }

            if ((testID == 8) || (testID == 9)) {
                data.setWaterContact(true);
            }
            if (testID == 63 || testID == 68) {
                BlockState state = data.getWorld().getBlockAt(x, y, z).getState();
                if (state instanceof Sign) {
                    Sign s = (Sign) state;
                    if (s.getLine(0).equalsIgnoreCase("Pilot:") && data.getPlayer() != null) {
                        String playerName = data.getPlayer().getName();
                        boolean foundPilot = false;
                        if (s.getLine(1).equalsIgnoreCase(playerName) || s.getLine(2).equalsIgnoreCase(playerName) ||
                            s.getLine(3).equalsIgnoreCase(playerName)) {
                            foundPilot = true;
                        }
                        if (!foundPilot && (!data.getPlayer().hasPermission("movecraft.bypasslock"))) {
                            fail(i18n.get("Not one of the registered pilots on this craft"));
                        }
                    }
                }
            }

            if (isForbiddenBlock(testID, testData)) {
                fail(i18n.get("Detection - Forbidden block found") +
                     String.format("\nInvalid Block: %s at (%d, %d, %d)", BlockNames.itemName(testID, testData, true),
                                   x, y, z));
            } else if (isAllowedBlock(testID, testData)) {
                //check for double chests
                if (testID == 54) {
                    boolean foundDoubleChest = false;
                    if (data.getWorld().getBlockTypeIdAt(x - 1, y, z) == 54) {
                        foundDoubleChest = true;
                    }
                    if (data.getWorld().getBlockTypeIdAt(x + 1, y, z) == 54) {
                        foundDoubleChest = true;
                    }
                    if (data.getWorld().getBlockTypeIdAt(x, y, z - 1) == 54) {
                        foundDoubleChest = true;
                    }
                    if (data.getWorld().getBlockTypeIdAt(x, y, z + 1) == 54) {
                        foundDoubleChest = true;
                    }
                    if (foundDoubleChest) {
                        fail(i18n.get("Detection - ERROR: Double chest found"));
                    }
                }
                //check for double trapped chests
                if (testID == 146) {
                    boolean foundDoubleChest = false;
                    if (data.getWorld().getBlockTypeIdAt(x - 1, y, z) == 146) {
                        foundDoubleChest = true;
                    }
                    if (data.getWorld().getBlockTypeIdAt(x + 1, y, z) == 146) {
                        foundDoubleChest = true;
                    }
                    if (data.getWorld().getBlockTypeIdAt(x, y, z - 1) == 146) {
                        foundDoubleChest = true;
                    }
                    if (data.getWorld().getBlockTypeIdAt(x, y, z + 1) == 146) {
                        foundDoubleChest = true;
                    }
                    if (foundDoubleChest) {
                        fail(i18n.get("Detection - ERROR: Double chest found"));
                    }
                }

                Location loc = new Location(data.getWorld(), x, y, z);
                Player p;
                if (data.getPlayer() == null) {
                    p = data.getNotificationPlayer();
                } else {
                    p = data.getPlayer();
                }
                if (p != null) {
                    if (plugin.getWorldGuardPlugin() != null &&
                        plugin.getWGCustomFlagsPlugin() != null &&
                        settings.WGCustomFlagsUsePilotFlag) {
                        LocalPlayer lp = plugin.getWorldGuardPlugin().wrapPlayer(p);
                        if (!WGCustomFlagsUtils
                                .validateFlag(plugin.getWorldGuardPlugin(), loc, plugin.FLAG_PILOT, lp)) {
                            fail(String.format(i18n.get("WGCustomFlags - Detection Failed") + " @ %d,%d,%d", x, y, z));
                        }
                    }

                    if (this.townyEnabled) {
                        TownBlock townBlock = TownyUtils.getTownBlock(loc);
                        if (townBlock != null && !this.townBlockSet.contains(townBlock)) {
                            if (TownyUtils.validateCraftMoveEvent(plugin.getTownyPlugin(), p, loc, this.townyWorld)) {
                                this.townBlockSet.add(townBlock);
                            } else {
                                int tY = loc.getBlockY();
                                boolean oChange = false;
                                if (this.craftMinY > tY) {
                                    this.craftMinY = tY;
                                    oChange = true;
                                }
                                if (this.craftMaxY < tY) {
                                    this.craftMaxY = tY;
                                    oChange = true;
                                }
                                if (oChange) {
                                    Town town = TownyUtils.getTown(townBlock);
                                    if (town != null) {
                                        Location locSpawn = TownyUtils.getTownSpawn(townBlock);
                                        boolean failed = false;
                                        if (locSpawn != null) {
                                            if (!this.townyWorldHeightLimits.validate(y, locSpawn.getBlockY())) {
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
                                                                                    .getRegionManager(loc.getWorld())
                                                                                    .getApplicableRegions(loc);
                                                if (regions.size() != 0) {
                                                    if (WGCustomFlagsUtils
                                                            .validateFlag(plugin.getWorldGuardPlugin(), loc,
                                                                          plugin.FLAG_PILOT, lp)) {
                                                        failed = false;
                                                    }
                                                }
                                            }
                                        }
                                        if (failed) {
                                            fail(String.format(i18n.get("Towny - Detection Failed") + " %s @ %d,%d,%d",
                                                               town.getName(), x, y, z));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                addToBlockList(workingLocation);
                Integer blockID = testID;
                Integer dataID = testData;
                Integer shiftedID = (blockID << 4) + dataID + 10000;
                for (List<Integer> flyBlockDef : dFlyBlocks.keySet()) {
                    if (flyBlockDef.contains(blockID) || flyBlockDef.contains(shiftedID)) {
                        addToBlockCount(flyBlockDef);
                    } else {
                        addToBlockCount(null);
                    }
                }

                if (isWithinLimit(blockList.size(), new IntRange(0, sizeRange.max), true)) {

                    addToDetectionStack(workingLocation);

                    calculateBounds(workingLocation);
                }
            }
        }
    }

    private boolean isAllowedBlock(int test, int testData) {

        for (int i : data.getAllowedBlocks()) {
            if ((i == test) || (i == (test << 4) + testData + 10000)) {
                return true;
            }
        }

        return false;
    }

    private boolean isForbiddenBlock(int test, int testData) {

        for (int i : data.getForbiddenBlocks()) {
            if ((i == test) || (i == (test << 4) + testData + 10000)) {
                return true;
            }
        }

        return false;
    }

    public DetectionTaskData getData() {
        return data;
    }

    private boolean notVisited(BlockVec l, Set<BlockVec> locations) {
        if (locations.contains(l)) {
            return false;
        } else {
            locations.add(l);
            return true;
        }
    }

    private void addToBlockList(BlockVec l) {
        blockList.add(l);
    }

    private void addToDetectionStack(BlockVec l) {
        blockStack.push(l);
    }

    private void addToBlockCount(List<Integer> id) {
        Integer count = blockTypeCount.get(id);

        if (count == null) {
            count = 0;
        }

        blockTypeCount.put(id, count + 1);
    }

    private void detectSurrounding(BlockVec l) {
        int x = l.x;
        int y = l.y;
        int z = l.z;

        for (int xMod = -1; xMod < 2; xMod += 2) {
            for (int yMod = -1; yMod < 2; yMod++) {
                detectBlock(x + xMod, y + yMod, z);
            }
        }

        for (int zMod = -1; zMod < 2; zMod += 2) {
            for (int yMod = -1; yMod < 2; yMod++) {
                detectBlock(x, y + yMod, z + zMod);
            }
        }

        for (int yMod = -1; yMod < 2; yMod += 2) {
            detectBlock(x, y + yMod, z);
        }
    }

    private void calculateBounds(BlockVec l) {
        if (maxX == null || l.x > maxX) {
            maxX = l.x;
        }
        if (maxY == null || l.y > maxY) {
            maxY = l.y;
        }
        if (maxZ == null || l.z > maxZ) {
            maxZ = l.z;
        }
        if (data.getMinX() == null || l.x < data.getMinX()) {
            data.setMinX(l.x);
        }
        if (minY == null || l.y < minY) {
            minY = l.y;
        }
        if (data.getMinZ() == null || l.z < data.getMinZ()) {
            data.setMinZ(l.z);
        }
    }

    private boolean isWithinLimit(int size, IntRange sizeRange, boolean continueOver) {
        if (size < sizeRange.min) {
            fail(String.format(i18n.get("Detection - Craft too small"), sizeRange.min) +
                 String.format("\nBlocks found: %d", size));
            return false;
        } else if ((!continueOver && size > sizeRange.max) || (continueOver && size > (sizeRange.max + 1000))) {
            fail(String.format(i18n.get("Detection - Craft too large"), sizeRange.max) +
                 String.format("\nBlocks found: %d", size));
            return false;
        } else {
            return true;
        }
    }

    private BlockVec[] finaliseBlockList(Set<BlockVec> blockSet) {
        //BlockVec[] finalList=blockSet.toArray( new BlockVec[1] );
        ArrayList<BlockVec> finalList = new ArrayList<>();

        // Sort the blocks from the bottom up to minimize lower altitude block updates
        for (int posx = data.getMinX(); posx <= this.maxX; posx++) {
            for (int posz = data.getMinZ(); posz <= this.maxZ; posz++) {
                for (int posy = this.minY; posy <= this.maxY; posy++) {
                    BlockVec test = new BlockVec(posx, posy, posz);
                    if (blockSet.contains(test)) finalList.add(test);
                }
            }
        }
        return finalList.toArray(new BlockVec[1]);
    }

    private boolean confirmStructureRequirements(Map<List<Integer>, List<Double>> flyBlocks,
                                                 Map<List<Integer>, Integer> countData)
    {
        if (getCraft().getType().getRequireWaterContact()) {
            if (!data.getWaterContact()) {
                fail(i18n.get("Detection - Failed - Water contact required but not found"));
                return false;
            }
        }
        for (Map.Entry<List<Integer>, List<Double>> entry : flyBlocks.entrySet()) {
            Integer numberOfBlocks = countData.get(entry.getKey());

            if (numberOfBlocks == null) {
                numberOfBlocks = 0;
            }

            float blockPercentage = (((float) numberOfBlocks / data.getBlockList().length) * 100);
            Double minPercentage = entry.getValue().get(0);
            Double maxPercentage = entry.getValue().get(1);
            String blockName = BlockNames.itemName(entry.getKey().get(0));

            if (minPercentage < 10000.0) {
                if (blockPercentage < minPercentage) {
                    fail(String.format(i18n.get("Not enough flyblock") + ": %s %.2f%% < %.2f%%", blockName,
                                       blockPercentage, minPercentage));
                    return false;
                }
            } else {
                if (numberOfBlocks < entry.getValue().get(0) - 10000.0) {
                    fail(String.format(i18n.get("Not enough flyblock") + ": %s %d < %d", blockName, numberOfBlocks,
                                       entry.getValue().get(0).intValue() - 10000));
                    return false;
                }
            }
            if (maxPercentage < 10000.0) {
                if (blockPercentage > maxPercentage) {
                    fail(String.format(i18n.get("Too much flyblock") + ": %s %.2f%% > %.2f%%", blockName,
                                       blockPercentage, maxPercentage));
                    return false;
                }
            } else {
                if (numberOfBlocks > entry.getValue().get(1) - 10000.0) {
                    fail(String.format(i18n.get("Too much flyblock") + ": %s %d > %d", blockName, numberOfBlocks,
                                       entry.getValue().get(1).intValue() - 10000));
                    return false;
                }
            }
        }

        return true;
    }

    private void fail(String message) {
        data.setFailed(true);
        data.setFailMessage(message);
    }
}
