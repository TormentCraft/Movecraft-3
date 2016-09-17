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

import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.SignBlock;
import com.sk89q.worldedit.schematic.SchematicFormat;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.api.BlockPosition;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.MapUpdateCommand;
import net.countercraft.movecraft.utils.MapUpdateManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//import com.sk89q.worldedit.blocks.BaseBlock;
//import com.sk89q.worldedit.world.DataException;

//import com.sk89q.worldedit.blocks.BaseBlock;
//import com.sk89q.worldedit.world.DataException;

public class WorldEditInteractListener implements Listener {
    private final Movecraft plugin;
    private final Settings settings;
    private final I18nSupport i18n;
    private final MapUpdateManager mapUpdateManager;
    private final CraftManager craftManager;
    private final Map<Player, Long> timeMap = new HashMap<>();
    private final Map<Player, Long> repairRightClickTimeMap = new HashMap<>();

    public WorldEditInteractListener(Movecraft plugin, Settings settings, I18nSupport i18n,
                                     MapUpdateManager mapUpdateManager, CraftManager craftManager)
    {
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.mapUpdateManager = mapUpdateManager;
        this.craftManager = craftManager;
    }

    @EventHandler public void WEOnPlayerInteract(PlayerInteractEvent event) {

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Material m = event.getClickedBlock().getType();
            if (m == Material.SIGN_POST || m == Material.WALL_SIGN) {
                Sign sign = (Sign) event.getClickedBlock().getState();
                String signText = org.bukkit.ChatColor.stripColor(sign.getLine(0));

                if (signText == null) {
                    return;
                }
            }
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Material m = event.getClickedBlock().getType();
            if (m == Material.SIGN_POST || m == Material.WALL_SIGN) {
                WEOnSignRightClick(event);
            }
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Material m = event.getClickedBlock().getType();
            if (m == Material.SIGN_POST || m == Material.WALL_SIGN) {
                if (event.getClickedBlock() == null) {
                    return;
                }
                Sign sign = (Sign) event.getClickedBlock().getState();
                String signText = org.bukkit.ChatColor.stripColor(sign.getLine(0));

                if (signText == null) {
                    return;
                }

                if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase(
                        "Repair:")) { // left click the Repair sign, and it saves the state
                    final Player player = event.getPlayer();
                    if (settings.RepairTicksPerBlock == 0) {
                        player.sendMessage(i18n.get("Repair functionality is disabled or WorldEdit was not detected"));
                        return;
                    }
                    Craft pCraft = craftManager.getCraftByPlayer(player);
                    if (pCraft == null) {
                        player.sendMessage(i18n.get("You must be piloting a craft"));
                        return;
                    }

                    String repairStateName = plugin.getDataFolder().getAbsolutePath() + "/RepairStates";
                    File file = new File(repairStateName);
                    if (!file.exists()) {
                        file.mkdirs();
                    }
                    repairStateName += "/";
                    repairStateName += player.getName();
                    repairStateName += sign.getLine(1);
                    file = new File(repairStateName);

                    Vector size = new Vector(pCraft.getMaxX() - pCraft.getMinX(),
                                             (pCraft.getMaxY() - pCraft.getMinY()) + 1,
                                             pCraft.getMaxZ() - pCraft.getMinZ());
                    Vector origin = new Vector(sign.getX(), sign.getY(), sign.getZ());
                    Vector offset = new Vector(pCraft.getMinX() - sign.getX(), pCraft.getMinY() - sign.getY(),
                                               pCraft.getMinZ() - sign.getZ());
                    CuboidClipboard cc = new CuboidClipboard(size, origin, offset);
                    final int[] ignoredBlocks = new int[]{
                            26, 34, 64, 71, 140, 144, 176, 177, 193, 194, 195, 196,
                            197};  // BLOCKS THAT CAN'T BE PARTIALLY RECONSTRUCTED

                    for (BlockPosition loc : pCraft.getBlockList()) {
                        Vector ccpos = new Vector(loc.x - pCraft.getMinX(), loc.y - pCraft.getMinY(),
                                                  loc.z - pCraft.getMinZ());
                        Block b = sign.getWorld().getBlockAt(loc.x, loc.y, loc.z);
                        boolean isIgnored = (Arrays.binarySearch(ignoredBlocks, b.getTypeId()) >= 0);
                        if (!isIgnored) {
                            com.sk89q.worldedit.blocks.BaseBlock bb;
                            BlockState state = b.getState();
                            if (state instanceof Sign) {
                                Sign s = (Sign) state;
                                bb = new SignBlock(b.getTypeId(), b.getData(), s.getLines());
                            } else {
                                bb = new com.sk89q.worldedit.blocks.BaseBlock(b.getTypeId(), b.getData());
                            }
                            cc.setBlock(ccpos, bb);
                        }
                    }
                    try {
                        cc.saveSchematic(file);
                    } catch (Exception e) {
                        player.sendMessage(i18n.get("Could not save file"));
                        e.printStackTrace();
                        return;
                    }
                    player.sendMessage(i18n.get("State saved"));
                    event.setCancelled(true);
                }
            }
        }
    }

    private void WEOnSignRightClick(PlayerInteractEvent event) {
        Sign sign = (Sign) event.getClickedBlock().getState();
        String signText = org.bukkit.ChatColor.stripColor(sign.getLine(0));

        if (signText == null) {
            return;
        }

        // don't process commands if this is a pilot tool click
        final Player player = event.getPlayer();
        final Craft craft = craftManager.getCraftByPlayer(player);
        if (event.getItem() != null && event.getItem().getTypeId() == settings.PilotTool) {
            if (craft != null) return;
        }

        if (org.bukkit.ChatColor.stripColor(sign.getLine(0)).equalsIgnoreCase("Repair:")) {
            if (settings.RepairTicksPerBlock == 0) {
                player.sendMessage(i18n.get("Repair functionality is disabled or WorldEdit was not detected"));
                return;
            }
            if (craft == null) {
                player.sendMessage(i18n.get("You must be piloting a craft"));
                return;
            }
            if (!player.hasPermission("movecraft." + craft.getType().getCraftName() + ".repair")) {
                player.sendMessage(i18n.get("Insufficient Permissions"));
                return;
            }
            // load up the repair state

            String repairStateName = plugin.getDataFolder().getAbsolutePath() + "/RepairStates";
            repairStateName += "/";
            repairStateName += player.getName();
            repairStateName += sign.getLine(1);
            File file = new File(repairStateName);
            if (!file.exists()) {
                player.sendMessage(i18n.get("REPAIR STATE NOT FOUND"));
                return;
            }
            SchematicFormat sf = SchematicFormat.getFormat(file);
            CuboidClipboard cc;
            try {
                cc = sf.load(file);
            } catch (com.sk89q.worldedit.data.DataException e) {
                player.sendMessage(i18n.get("REPAIR STATE NOT FOUND"));
                e.printStackTrace();
                return;
            } catch (IOException e) {
                player.sendMessage(i18n.get("REPAIR STATE NOT FOUND"));
                e.printStackTrace();
                return;
            }

            // calculate how many and where the blocks need to be replaced
            Location worldLoc = new Location(sign.getWorld(), sign.getX(), sign.getY(), sign.getZ());
            int numdiffblocks = 0;
            HashMap<Integer, Integer> numMissingItems = new HashMap<>(); // block type, number missing
            Set<Vector> locMissingBlocks = new HashSet<>();
            for (int x = 0; x < cc.getWidth(); x++) {
                for (int y = 0; y < cc.getHeight(); y++) {
                    for (int z = 0; z < cc.getLength(); z++) {
                        Vector ccLoc = new Vector(x, y, z);
                        worldLoc.setX(sign.getX() + cc.getOffset().getBlockX() + x);
                        worldLoc.setY(sign.getY() + cc.getOffset().getBlockY() + y);
                        worldLoc.setZ(sign.getZ() + cc.getOffset().getBlockZ() + z);
                        Boolean isImportant = true;
                        if (!craft.getType().blockedByWater())
                            if (cc.getBlock(ccLoc).getId() == 8 || cc.getBlock(ccLoc).getId() == 9) isImportant = false;
                        if (cc.getBlock(ccLoc).getId() == 0) isImportant = false;
                        if (isImportant &&
                            worldLoc.getWorld().getBlockAt(worldLoc).getTypeId() != cc.getBlock(ccLoc).getId()) {
                            numdiffblocks++;
                            int itemToConsume = cc.getBlock(ccLoc).getId();
                            int qtyToConsume = 1;
                            //some blocks aren't represented by items with the same number as the block
                            if (itemToConsume == 63 || itemToConsume == 68) // signs
                                itemToConsume = 323;
                            if (itemToConsume == 93 || itemToConsume == 94) // repeaters
                                itemToConsume = 356;
                            if (itemToConsume == 149 || itemToConsume == 150) // comparators
                                itemToConsume = 404;
                            if (itemToConsume == 55) // redstone
                                itemToConsume = 331;
                            if (itemToConsume == 118) // cauldron
                                itemToConsume = 380;
                            if (itemToConsume == 124) // lit redstone lamp
                                itemToConsume = 123;
                            if (itemToConsume == 75) // lit redstone torch
                                itemToConsume = 76;
                            if (itemToConsume == 8 || itemToConsume == 9) { // don't require water to be in the chest
                                itemToConsume = 0;
                                qtyToConsume = 0;
                            }
                            if (itemToConsume == 10 ||
                                itemToConsume == 11) { // don't require lava either, yeah you could exploit
                                // this for free lava, so make sure you set a price per block
                                itemToConsume = 0;
                                qtyToConsume = 0;
                            }
                            if (itemToConsume == 43) { // for double slabs, require 2 slabs
                                itemToConsume = 44;
                                qtyToConsume = 2;
                            }
                            if (itemToConsume == 125) { // for double wood slabs, require 2 wood slabs
                                itemToConsume = 126;
                                qtyToConsume = 2;
                            }
                            if (itemToConsume == 181) { // for double red sandstone slabs, require 2 red sandstone slabs
                                itemToConsume = 182;
                                qtyToConsume = 2;
                            }

                            if (itemToConsume != 0) {
                                if (numMissingItems.containsKey(itemToConsume)) {
                                    Integer num = numMissingItems.get(itemToConsume);
                                    num += qtyToConsume;
                                    numMissingItems.put(itemToConsume, num);
                                } else {
                                    numMissingItems.put(itemToConsume, qtyToConsume);
                                }
                            }
                            locMissingBlocks.add(ccLoc);
                        }
                    }
                }
            }

            // if this is the second click in the last 5 seconds, start the repair, otherwise give them the info on
            // the repair
            Boolean secondClick = false;
            Long time = repairRightClickTimeMap.get(player);
            if (time != null) {
                long ticksElapsed = (System.currentTimeMillis() - time) / 50;
                if (ticksElapsed < 100) {
                    secondClick = true;
                }
            }
            if (secondClick) {
                // check all the chests for materials for the repair
                Map<Integer, ArrayList<InventoryHolder>> chestsToTakeFrom = new HashMap<>(); // typeid, list of chest
                // inventories
                boolean enoughMaterial = true;
                for (Map.Entry<Integer, Integer> entry : numMissingItems.entrySet()) {
                    int remainingQty = entry.getValue();
                    final int itemTypeId = entry.getKey();
                    ArrayList<InventoryHolder> chests = new ArrayList<>();

                    for (BlockPosition loc : craft.getBlockList()) {
                        Block b = craft.getW().getBlockAt(loc.x, loc.y, loc.z);
                        if ((b.getTypeId() == 54) || (b.getTypeId() == 146)) {
                            InventoryHolder inventoryHolder = (InventoryHolder) b.getState();
                            if (inventoryHolder.getInventory().contains(itemTypeId) && remainingQty > 0) {
                                Map<Integer, ? extends ItemStack> foundItems = inventoryHolder.getInventory()
                                                                                              .all(itemTypeId);
                                // count how many were in the chest
                                int numfound = 0;
                                for (ItemStack istack : foundItems.values()) {
                                    numfound += istack.getAmount();
                                }
                                remainingQty -= numfound;
                                chests.add(inventoryHolder);
                            }
                        }
                    }
                    if (remainingQty > 0) {
                        player.sendMessage(String.format(i18n.get("Need more of material") + ": %s - %d",
                                                         Material.getMaterial(itemTypeId).name().toLowerCase()
                                                                 .replace("_", " "), remainingQty));
                        enoughMaterial = false;
                    } else {
                        chestsToTakeFrom.put(itemTypeId, chests);
                    }
                }
                if (plugin.getEconomy() != null && enoughMaterial) {
                    double moneyCost = numdiffblocks * settings.RepairMoneyPerBlock;
                    if (plugin.getEconomy().has(player, moneyCost)) {
                        plugin.getEconomy().withdrawPlayer(player, moneyCost);
                    } else {
                        player.sendMessage(i18n.get("You do not have enough money"));
                        enoughMaterial = false;
                    }
                }
                if (enoughMaterial) {
                    // we know we have enough materials to make the repairs, so remove the materials from the chests
                    for (Map.Entry<Integer, Integer> entry : numMissingItems.entrySet()) {
                        int remainingQty = entry.getValue();
                        final Integer itemType = entry.getKey();
                        for (InventoryHolder inventoryHolder : chestsToTakeFrom.get(itemType)) {
                            Map<Integer, ? extends ItemStack> foundItems = inventoryHolder.getInventory().all(itemType);
                            for (ItemStack istack : foundItems.values()) {
                                if (istack.getAmount() <= remainingQty) {
                                    remainingQty -= istack.getAmount();
                                    inventoryHolder.getInventory().removeItem(istack);
                                } else {
                                    istack.setAmount(istack.getAmount() - remainingQty);
                                    remainingQty = 0;
                                }
                            }
                        }
                    }
                    ArrayList<MapUpdateCommand> updateCommands = new ArrayList<>();
                    for (Vector ccloc : locMissingBlocks) {
                        com.sk89q.worldedit.blocks.BaseBlock bb = cc.getBlock(ccloc);
                        if (bb.getId() == 68 ||
                            bb.getId() == 63) { // I don't know why this is necessary. I'm pretty sure WE
                            // should be loading signs as signblocks, but it doesn't seem to
                            SignBlock sb = new SignBlock(bb.getId(), bb.getData());
                            sb.setNbtData(bb.getNbtData());
                            bb = sb;
                        }
                        BlockPosition moveloc = new BlockPosition(
                                sign.getX() + cc.getOffset().getBlockX() + ccloc.getBlockX(),
                                sign.getY() + cc.getOffset().getBlockY() + ccloc.getBlockY(),
                                sign.getZ() + cc.getOffset().getBlockZ() + ccloc.getBlockZ());
                        MapUpdateCommand updateCom = new MapUpdateCommand(moveloc, bb.getType(), (byte) bb.getData(),
                                                                          bb, craft);
                        updateCommands.add(updateCom);
                    }
                    if (!updateCommands.isEmpty()) {
                        final MapUpdateCommand[] fUpdateCommands = updateCommands.toArray(new MapUpdateCommand[1]);
                        int durationInTicks = numdiffblocks * settings.RepairTicksPerBlock;

                        // send out status updates every minute
                        for (int ticsFromStart = 0; ticsFromStart < durationInTicks; ticsFromStart += 1200) {
                            final int fTics = ticsFromStart / 20;
                            final int fDur = durationInTicks / 20;
                            BukkitTask statusTask = new BukkitRunnable() {
                                @Override public void run() {
                                    player.sendMessage(
                                            String.format(i18n.get("Repairs underway") + ": %d / %d", fTics, fDur));
                                }
                            }.runTaskLater(plugin, (ticsFromStart));
                        }

                        // keep craft piloted during the repair process so player can not move it
                        craftManager.removePlayerFromCraft(craft);
                        BukkitTask releaseTask = new BukkitRunnable() {
                            @Override public void run() {
                                craftManager.removeCraft(craft);
                                player.sendMessage(i18n.get("Repairs complete. You may now pilot the craft"));
                            }
                        }.runTaskLater(plugin, (durationInTicks + 20));

                        //do the actual repair
                        BukkitTask repairTask = new BukkitRunnable() {
                            @Override public void run() {
                                mapUpdateManager.addWorldUpdate(craft.getW(), fUpdateCommands, null, null);
                            }
                        }.runTaskLater(plugin, (durationInTicks));
                    }
                }
            } else {
                // if this is the first time they have clicked the sign, show the summary of repair costs and
                // requirements
                player.sendMessage(String.format(i18n.get("Total damaged blocks") + ": %d", numdiffblocks));
                float percent = (numdiffblocks * 100) / craft.getOrigBlockCount();
                player.sendMessage(String.format(i18n.get("Percentage of craft") + ": %.2f%%", percent));
                if (percent > 50) {
                    player.sendMessage(i18n.get("This craft is too damaged and can not be repaired"));
                    return;
                }
                if (numdiffblocks != 0) {
                    player.sendMessage(i18n.get("SUPPLIES NEEDED"));
                    for (Map.Entry<Integer, Integer> entry : numMissingItems.entrySet()) {
                        final Integer itemType = entry.getKey();
                        final Integer amount = entry.getValue();
                        player.sendMessage(String.format("%s : %d", Material.getMaterial(itemType).name().toLowerCase()
                                                                            .replace("_", " "), amount));
                    }
                    int durationInSeconds = numdiffblocks * settings.RepairTicksPerBlock / 20;
                    player.sendMessage(
                            String.format(i18n.get("Seconds to complete repair") + ": %d", durationInSeconds));
                    int moneyCost = (int) (numdiffblocks * settings.RepairMoneyPerBlock);
                    player.sendMessage(String.format(i18n.get("Money to complete repair") + ": %d", moneyCost));
                    repairRightClickTimeMap.put(player, System.currentTimeMillis());
                }
            }
        }
    }
}
