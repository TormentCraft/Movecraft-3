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
import com.google.common.collect.Sets;
import com.sk89q.worldguard.LocalPlayer;
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
import java.util.Optional;
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

    public DetectionTask(final Craft craft, final BlockVec startLocation, final IntRange sizeRange, final MaterialDataPredicate allowedBlocks,
                         final MaterialDataPredicate forbiddenBlocks, final Player player, final Player notificationPlayer, final World w,
                         final Movecraft plugin, final Settings settings, final I18nSupport i18n)
    {
        super(craft);
        this.startLocation = startLocation;
        this.sizeRange = sizeRange;
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.data = new DetectionTaskData(w, player, notificationPlayer, allowedBlocks, forbiddenBlocks);
    }

    @Override public void execute() {
        final Map<MaterialDataPredicate, List<CraftType.Constraint>> flyBlocks = this.getCraft().getType().getFlyBlocks();
        this.dFlyBlocks = flyBlocks;

        this.blockStack.push(this.startLocation);

        do {
            this.detectSurrounding(this.blockStack.pop());
        } while (!this.blockStack.isEmpty());

        if (this.data.failed()) {
            return;
        }

        if (this.isWithinLimit(this.blockList.size(), this.sizeRange, false)) {

            this.data.setBlockList(this.finaliseBlockList(this.blockList));

            if (this.confirmStructureRequirements(flyBlocks, this.blockTypeCount, this.data.getBlockList().length)) {
                this.data.setHitBox(BoundingBoxUtils
                                       .formBoundingBox(this.data.getBlockList(), this.data.getMinX(), this.maxX, this.data.getMinZ(),
                                                        this.maxZ));
            }
        }
    }

    private void detectBlock(final int x, final int y, final int z) {
        final BlockVec workingLocation = new BlockVec(x, y, z);

        if (this.notVisited(workingLocation, this.visited)) {
            final Block testBlock;
            final Material testMaterial;
            final MaterialData materialData;
            try {
                testBlock = this.data.getWorld().getBlockAt(x, y, z);
                materialData = testBlock.getState().getData();
                testMaterial = materialData.getItemType();
            } catch (final Exception e) {
                this.fail(String.format(this.i18n.get("Detection - Craft too large"), this.sizeRange.max));
                return;
            }

            if ((testMaterial == Material.WATER) || (testMaterial == Material.STATIONARY_WATER)) {
                this.data.setWaterContact(true);
            }
            if (testMaterial == Material.WALL_SIGN || testMaterial == Material.SIGN_POST) {
                final BlockState state = this.data.getWorld().getBlockAt(x, y, z).getState();
                if (state instanceof Sign) {
                    final Sign s = (Sign) state;
                    if (s.getLine(0).equalsIgnoreCase("Pilot:") && this.data.getPlayer() != null) {
                        final String playerName = this.data.getPlayer().getName();
                        boolean foundPilot = false;
                        if (s.getLine(1).equalsIgnoreCase(playerName) || s.getLine(2).equalsIgnoreCase(playerName) ||
                            s.getLine(3).equalsIgnoreCase(playerName)) {
                            foundPilot = true;
                        }
                        if (!foundPilot && (!this.data.getPlayer().hasPermission("movecraft.bypasslock"))) {
                            this.fail(this.i18n.get("Not one of the registered pilots on this craft"));
                        }
                    }
                }
            }

            if (this.isForbiddenBlock(materialData)) {
                this.fail(this.i18n.get("Detection - Forbidden block found") +
                          String.format("\nInvalid Block: %s at (%d, %d, %d)", BlockNames.itemName(materialData),
                                   x, y, z));
            } else if (this.isAllowedBlock(materialData)) {
                // Check for double chests.
                final Set<Material> chestTypes = Sets.immutableEnumSet(Material.CHEST, Material.TRAPPED_CHEST);
                if (chestTypes.contains(testMaterial)) {
                    final World w = this.data.getWorld();
                    final boolean foundDoubleChest =
                            (w.getBlockAt(x - 1, y, z).getType() == testMaterial) ||
                            (w.getBlockAt(x + 1, y, z).getType() == testMaterial) ||
                            (w.getBlockAt(x, y, z - 1).getType() == testMaterial) ||
                            (w.getBlockAt(x, y, z + 1).getType() == testMaterial);
                    if (foundDoubleChest) {
                        this.fail(this.i18n.get("Detection - ERROR: Double chest found"));
                    }
                }

                final Location loc = new Location(this.data.getWorld(), x, y, z);
                final Player p;
                if (this.data.getPlayer() == null) {
                    p = this.data.getNotificationPlayer();
                } else {
                    p = this.data.getPlayer();
                }
                if (p != null) {
                    if (this.plugin.getWorldGuardPlugin() != null && this.plugin.getWGCustomFlagsPlugin() != null && this.settings.WGCustomFlagsUsePilotFlag) {
                        final LocalPlayer lp = this.plugin.getWorldGuardPlugin().wrapPlayer(p);
                        if (!WGCustomFlagsUtils
                                .validateFlag(this.plugin.getWorldGuardPlugin(), loc, this.plugin.FLAG_PILOT, lp)) {
                            this.fail(String.format(this.i18n.get("WGCustomFlags - Detection Failed") + " @ %d,%d,%d", x, y, z));
                        }
                    }
                }

                this.addToBlockList(workingLocation);
                for (final MaterialDataPredicate flyBlockDef : this.dFlyBlocks.keySet()) {
                    if (flyBlockDef.checkBlock(testBlock)) {
                        this.addToBlockCount(flyBlockDef);
                    } else {
                        this.addToBlockCount(null);
                    }
                }

                if (this.isWithinLimit(this.blockList.size(), new IntRange(0, this.sizeRange.max), true)) {

                    this.addToDetectionStack(workingLocation);

                    this.calculateBounds(workingLocation);
                }
            }
        }
    }

    private boolean isAllowedBlock(final MaterialData test) {
        return this.data.getAllowedBlocks().check(test);
    }

    private boolean isForbiddenBlock(final MaterialData test) {
        return this.data.getForbiddenBlocks().check(test);
    }

    public DetectionTaskData getData() {
        return this.data;
    }

    private boolean notVisited(final BlockVec vec, final Set<BlockVec> locations) {
        if (locations.contains(vec)) {
            return false;
        } else {
            locations.add(vec);
            return true;
        }
    }

    private void addToBlockList(final BlockVec l) {
        this.blockList.add(l);
    }

    private void addToDetectionStack(final BlockVec l) {
        this.blockStack.push(l);
    }

    private void addToBlockCount(final MaterialDataPredicate id) {
        final int count = Optional.ofNullable(this.blockTypeCount.get(id)).orElse(0);
        this.blockTypeCount.put(id, count + 1);
    }

    private void detectSurrounding(final BlockVec l) {
        final int x = l.x;
        final int y = l.y;
        final int z = l.z;

        for (int xMod = -1; xMod < 2; xMod += 2) {
            for (int yMod = -1; yMod < 2; yMod++) {
                this.detectBlock(x + xMod, y + yMod, z);
            }
        }

        for (int zMod = -1; zMod < 2; zMod += 2) {
            for (int yMod = -1; yMod < 2; yMod++) {
                this.detectBlock(x, y + yMod, z + zMod);
            }
        }

        for (int yMod = -1; yMod < 2; yMod += 2) {
            this.detectBlock(x, y + yMod, z);
        }
    }

    private void calculateBounds(final BlockVec l) {
        if (this.maxX == null || l.x > this.maxX) {
            this.maxX = l.x;
        }
        if (this.maxY == null || l.y > this.maxY) {
            this.maxY = l.y;
        }
        if (this.maxZ == null || l.z > this.maxZ) {
            this.maxZ = l.z;
        }
        if (this.data.getMinX() == null || l.x < this.data.getMinX()) {
            this.data.setMinX(l.x);
        }
        if (this.minY == null || l.y < this.minY) {
            this.minY = l.y;
        }
        if (this.data.getMinZ() == null || l.z < this.data.getMinZ()) {
            this.data.setMinZ(l.z);
        }
    }

    private boolean isWithinLimit(final int size, final IntRange sizeRange, final boolean continueOver) {
        if (size < sizeRange.min) {
            this.fail(String.format(this.i18n.get("Detection - Craft too small"), sizeRange.min) +
                      String.format("\nBlocks found: %d", size));
            return false;
        } else if ((!continueOver && size > sizeRange.max) || (continueOver && size > (sizeRange.max + 1000))) {
            this.fail(String.format(this.i18n.get("Detection - Craft too large"), sizeRange.max) +
                      String.format("\nBlocks found: %d", size));
            return false;
        } else {
            return true;
        }
    }

    private BlockVec[] finaliseBlockList(final Set<BlockVec> blockSet) {
        //BlockVec[] finalList=blockSet.toArray( new BlockVec[1] );
        final ArrayList<BlockVec> finalList = new ArrayList<>();

        // Sort the blocks from the bottom up to minimize lower altitude block updates
        for (int posx = this.data.getMinX(); posx <= this.maxX; posx++) {
            for (int posz = this.data.getMinZ(); posz <= this.maxZ; posz++) {
                for (int posy = this.minY; posy <= this.maxY; posy++) {
                    final BlockVec test = new BlockVec(posx, posy, posz);
                    if (blockSet.contains(test)) finalList.add(test);
                }
            }
        }
        return finalList.toArray(new BlockVec[1]);
    }

    private boolean confirmStructureRequirements(final Map<MaterialDataPredicate, List<CraftType.Constraint>> flyBlocks,
                                                 final Map<MaterialDataPredicate, Integer> countData, final int total)
    {
        if (this.getCraft().getType().getRequireWaterContact()) {
            if (!this.data.getWaterContact()) {
                this.fail(this.i18n.get("Detection - Failed - Water contact required but not found"));
                return false;
            }
        }

        for (final Map.Entry<MaterialDataPredicate, List<CraftType.Constraint>> entry : flyBlocks.entrySet()) {
            final MaterialDataPredicate predicate = entry.getKey();
            final List<CraftType.Constraint> constraints = entry.getValue();
            final int count = Optional.ofNullable(countData.get(predicate)).orElse(0);
            final double percentage = ((double) count / total) * 100;
            final String name = Joiner.on(' ').join(BlockNames.materialDataPredicateNames(entry.getKey()));

            for (final CraftType.Constraint constraint : constraints) {
                final int exactBound = constraint.bound.asExact(total);
                final double ratioBound = constraint.bound.asRatio(total);

                if (!constraint.isSatisfiedBy(count, total)) {
                    if (constraint.isUpper) {
                        if (constraint.bound.isExact()) {
                            this.fail(String.format(this.i18n.get("Not enough flyblock") + " (%d < %d) : %s", count, exactBound,
                                                    name));
                        } else {
                            this.fail(String.format(this.i18n.get("Not enough flyblock") + " (%.2f%% < %.2f%%) : %s", percentage,
                                               ratioBound * 100.0));
                        }
                    } else {
                        if (constraint.bound.isExact()) {
                            this.fail(String.format(this.i18n.get("Too much flyblock") + " (%d > %d) : %s", count, exactBound,
                                                    name));
                        } else {
                            this.fail(String.format(this.i18n.get("Too much flyblock") + " (%.2f%% > %.2f%%) : %s", percentage,
                                               ratioBound * 100.0));
                        }
                    }
                    return false;
                }
            }
        }

        return true;
    }

    private void fail(final String message) {
        this.data.setFailed(true);
        this.data.setFailMessage(message);
    }
}
