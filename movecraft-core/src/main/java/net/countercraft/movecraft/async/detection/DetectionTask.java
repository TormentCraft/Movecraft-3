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

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.api.BlockVec;
import net.countercraft.movecraft.api.IntRange;
import net.countercraft.movecraft.api.MaterialDataPredicate;
import net.countercraft.movecraft.async.AsyncTask;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftType;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.BlockNames;
import net.countercraft.movecraft.utils.BoundingBoxUtils;
import net.countercraft.movecraft.utils.WGCustomFlagsUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.material.MaterialData;

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
    private final Map<MaterialDataPredicate, Integer> blockTypeCount = new HashMap<>();
    private Map<MaterialDataPredicate, List<CraftType.Constraint>> dFlyBlocks;
    private final DetectionTaskData data;

    private int craftMinY = 0;
    private int craftMaxY = 0;

    public DetectionTask(Craft c, BlockVec startLocation, IntRange sizeRange, MaterialDataPredicate allowedBlocks,
                         MaterialDataPredicate forbiddenBlocks, Player player, Player notificationPlayer, World w,
                         Movecraft plugin, Settings settings, I18nSupport i18n)
    {
        super(c);
        this.startLocation = startLocation;
        this.sizeRange = sizeRange;
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        data = new DetectionTaskData(w, player, notificationPlayer, allowedBlocks, forbiddenBlocks);
    }

    @Override public void execute() {
        Map<MaterialDataPredicate, List<CraftType.Constraint>> flyBlocks = getCraft().getType().getFlyBlocks();
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

            if (confirmStructureRequirements(flyBlocks, blockTypeCount, data.getBlockList().length)) {
                data.setHitBox(BoundingBoxUtils
                                       .formBoundingBox(data.getBlockList(), data.getMinX(), maxX, data.getMinZ(),
                                                        maxZ));
            }
        }
    }

    private void detectBlock(int x, int y, int z) {

        BlockVec workingLocation = new BlockVec(x, y, z);

        if (notVisited(workingLocation, visited)) {

            Block testBlock;
            Material testMaterial;
            int testID;
            byte testData;
            try {
                testBlock = data.getWorld().getBlockAt(x, y, z);
                testMaterial = testBlock.getType();
                testID = testBlock.getTypeId();
                testData = testBlock.getData();
            } catch (Exception e) {
                fail(String.format(i18n.get("Detection - Craft too large"), sizeRange.max));
                return;
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
                     String.format("\nInvalid Block: %s at (%d, %d, %d)", BlockNames.itemName(testMaterial, testData),
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
                }

                addToBlockList(workingLocation);
                for (MaterialDataPredicate flyBlockDef : dFlyBlocks.keySet()) {
                    if (flyBlockDef.checkBlock(testBlock)) {
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

    private boolean isAllowedBlock(int test, byte testData) {
        return data.getAllowedBlocks().check(new MaterialData(test, testData));
    }

    private boolean isForbiddenBlock(int test, byte testData) {
        return data.getForbiddenBlocks().check(new MaterialData(test, testData));
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

    private void addToBlockCount(MaterialDataPredicate id) {
        int count = Optional.fromNullable(blockTypeCount.get(id)).or(0);
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

    private boolean confirmStructureRequirements(Map<MaterialDataPredicate, List<CraftType.Constraint>> flyBlocks,
                                                 Map<MaterialDataPredicate, Integer> countData, int total)
    {
        if (getCraft().getType().getRequireWaterContact()) {
            if (!data.getWaterContact()) {
                fail(i18n.get("Detection - Failed - Water contact required but not found"));
                return false;
            }
        }

        for (Map.Entry<MaterialDataPredicate, List<CraftType.Constraint>> entry : flyBlocks.entrySet()) {
            MaterialDataPredicate predicate = entry.getKey();
            List<CraftType.Constraint> constraints = entry.getValue();
            int count = Optional.fromNullable(countData.get(predicate)).or(0);
            double percentage = ((double) count / total) * 100;
            String name = Joiner.on(' ').join(BlockNames.materialDataPredicateNames(entry.getKey()));

            for (CraftType.Constraint constraint : constraints) {
                int exactBound = constraint.bound.asExact(total);
                double ratioBound = constraint.bound.asRatio(total);

                if (!constraint.isSatisfiedBy(count, total)) {
                    if (constraint.isUpper) {
                        if (constraint.bound.isExact()) {
                            fail(String.format(i18n.get("Not enough flyblock") + " (%d < %d) : %s", count, exactBound,
                                               name));
                        } else {
                            fail(String.format(i18n.get("Not enough flyblock") + " (%.2f%% < %.2f%%) : %s", percentage,
                                               ratioBound * 100.0));
                        }
                    } else {
                        if (constraint.bound.isExact()) {
                            fail(String.format(i18n.get("Too much flyblock") + " (%d > %d) : %s", count, exactBound,
                                               name));
                        } else {
                            fail(String.format(i18n.get("Too much flyblock") + " (%.2f%% > %.2f%%) : %s", percentage,
                                               ratioBound * 100.0));
                        }
                    }
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
