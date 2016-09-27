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

package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.api.BlockVec;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.localisation.I18nSupport;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class CraftManager implements net.countercraft.movecraft.api.CraftManager {
    private final Settings settings;
    private final I18nSupport i18nSupport;
    private final Plugin plugin;
    private CraftType[] craftTypes;

    private final Map<World, Set<Craft>> craftList = new ConcurrentHashMap<>();
    private final Map<Player, Craft> craftPlayerIndex = new HashMap<>();
    private final Map<Player, BukkitTask> releaseEvents = new HashMap<>();

    public CraftManager(Settings settings, I18nSupport i18nSupport, Plugin plugin) {
        this.settings = settings;
        this.i18nSupport = i18nSupport;
        this.plugin = plugin;
    }

    public CraftType[] getCraftTypes() {
        return craftTypes;
    }

    public void initCraftTypes() {
        File craftsFile = new File(plugin.getDataFolder().getAbsolutePath() + "/types");

        if (!craftsFile.exists()) {
            craftsFile.mkdirs();
            plugin.saveResource("types/airship.craft", false);
            plugin.saveResource("types/airskiff.craft", false);
            plugin.saveResource("types/BigAirship.craft", false);
            plugin.saveResource("types/BigSubAirship.craft", false);
            plugin.saveResource("types/elevator.craft", false);
            plugin.saveResource("types/LaunchTorpedo.craft", false);
            plugin.saveResource("types/Ship.craft", false);
            plugin.saveResource("types/SubAirship.craft", false);
            plugin.saveResource("types/Submarine.craft", false);
            plugin.saveResource("types/Turret.craft", false);
        }

        HashSet<CraftType> craftTypesSet = new HashSet<>();

        boolean foundCraft = false;
        for (File file : craftsFile.listFiles()) {
            if (file.isFile() && (file.getName().endsWith(".craft") || file.getName().endsWith(".yaml"))) {
                if (file.getName().endsWith(".craft")) {
                    String name = file.getName();
                    String newName = name.substring(0, name.length() - ".craft".length()) + ".yaml";
                    plugin.getLogger()
                          .warning("\"craft\" extension is deprecated, please rename " + name + " to " + newName);
                }

                try {
                    CraftType type = new CraftType();
                    type.parseCraftDataFromFile(file, settings.SinkRateTicks);
                    craftTypesSet.add(type);
                    foundCraft = true;
                } catch (FileNotFoundException e) {
                    plugin.getLogger().log(Level.SEVERE,
                                           String.format(i18nSupport.get("Startup - Error parsing CraftType file"),
                                                         file.getAbsolutePath()));
                } catch (CraftType.ParseException e) {
                    plugin.getLogger().log(Level.SEVERE,
                                           String.format(i18nSupport.get("Startup - Error parsing CraftType file"),
                                                         file.getAbsolutePath()));
                }
            }
        }
        if (!foundCraft) {
            plugin.getLogger().log(Level.SEVERE, "ERROR: NO CRAFTS FOUND!!!");
        }
        craftTypes = craftTypesSet.toArray(new CraftType[1]);
        plugin.getLogger().log(Level.INFO, String.format(i18nSupport.get("Startup - Number of craft files loaded"),
                                                         craftTypes.length));
    }

    public void addCraft(Craft c, Player p) {
        Set<Craft> crafts = craftList.get(c.getW());
        if (crafts == null) {
            craftList.put(c.getW(), new HashSet<Craft>());
        }
        craftList.get(c.getW()).add(c);
        craftPlayerIndex.put(p, c);
        destroySnowOnPilot(p, c);
    }

    public void removeCraft(Craft c) {
        removeReleaseTask(c);
        // if its sinking, just remove the craft without notifying or checking
        if (c.getSinking()) {
            craftList.get(c.getW()).remove(c);
            craftPlayerIndex.remove(getPlayerFromCraft(c));
        }
        // don't just release torpedoes, make them sink so they don't clutter up the place
        if (c.getType().getCruiseOnPilot()) {
            c.setCruising(false);
            c.setSinking(true);
            c.setNotificationPlayer(null);
            return;
        }
        craftList.get(c.getW()).remove(c);
        Player pilot = getPlayerFromCraft(c);
        if (pilot != null) {
            pilot.sendMessage(i18nSupport.get("Release - Craft has been released message"));
            plugin.getLogger().log(Level.INFO,
                                   String.format(i18nSupport.get("Release - Player has released a craft console"),
                                                 c.getNotificationPlayer().getName(), c.getType().getCraftName(),
                                                 c.getBlockList().length, c.getMinX(), c.getMinZ()));
        } else {
            plugin.getLogger().log(Level.INFO, String.format(i18nSupport
                                                                     .get("NULL Player has released a craft of type " +
                                                                          "%s with size %d at coordinates : %d x , %d" +
                                                                          " z"),
                                                             c.getType().getCraftName(), c.getBlockList().length,
                                                             c.getMinX(), c.getMinZ()));
        }
        craftPlayerIndex.remove(pilot);

        destroyBindingBlocks(pilot, c);
    }

    private boolean canPilotBreakBlock(Player pilot, Block block) {
        if (pilot == null || !pilot.isOnline()) {
            return false;
        }
        BlockBreakEvent be = new BlockBreakEvent(block, pilot);
        plugin.getServer().getPluginManager().callEvent(be);
        return !be.isCancelled();
    }

    private void destroySnowOnPilot(Player pilot, Craft craft) {
        if (pilot == null || !pilot.isOnline()) return;

        Set<BlockVec> craftBlocks = new HashSet<>(Arrays.asList(craft.getBlockList()));
        for (BlockVec block : craftBlocks) {
            BlockVec test = new BlockVec(block.x, block.y + 1, block.z);
            if (!craftBlocks.contains(test)) {
                Block testBlock = craft.getW().getBlockAt(test.x, test.y, test.z);

                if (testBlock.getType() == Material.SNOW && testBlock.getData() == 0) {
                    Collection<ItemStack> drops = testBlock.getDrops(new ItemStack(Material.WOOD_SPADE, 1, (short) 5));
                    testBlock.setType(Material.AIR);
                    for (ItemStack drop : drops) {
                        testBlock.getWorld().dropItemNaturally(testBlock.getLocation(), drop);
                    }
                }
            }
        }
    }

    private void destroyBindingBlocks(Player pilot, Craft craft) {
        if (pilot != null && (pilot.getGameMode() == GameMode.CREATIVE || pilot.hasPermission("*"))) {
            return;
        }

        Set<BlockVec> craftBlocks = new HashSet<>(Arrays.asList(craft.getBlockList()));
        int blockedBroken = 0;
        for (BlockVec block : craftBlocks) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        //No diagonals
                        if ((z != 0 && x != 0) || (x == 0 && y == 0 && z == 0)) continue;

                        BlockVec test = new BlockVec(block.x + x, block.y + y, block.z + z);
                        if (!craftBlocks.contains(test)) {
                            Block testBlock = craft.getW().getBlockAt(test.x, test.y, test.z);
                            if (craft.getType().isAllowedBlock(testBlock.getTypeId(), testBlock.getData()) ||
                                craft.getType().isForbiddenBlock(testBlock.getTypeId(), testBlock.getData())) {

                                testBlock = craft.getW().getBlockAt(block.x, block.y, block.z);
                                if (!canPilotBreakBlock(pilot, testBlock)) {
                                    blockedBroken++;
                                    Collection<ItemStack> drops = testBlock.getDrops();
                                    testBlock.setType(Material.AIR);
                                    for (ItemStack drop : drops) {
                                        testBlock.getWorld().dropItemNaturally(testBlock.getLocation(), drop);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (blockedBroken > 0 && pilot != null && pilot.isOnline()) {
            pilot.sendMessage(ChatColor.RED + "WARNING: Some of your craft has been destroyed.");
        }
    }

    public List<net.countercraft.movecraft.api.Craft> getCrafts() {
        List<net.countercraft.movecraft.api.Craft> result = new ArrayList<>();
        for (Map.Entry<World, Set<Craft>> entry : craftList.entrySet()) {
            result.addAll(entry.getValue());
        }
        return Collections.unmodifiableList(result);
    }

    public Craft[] getCraftsInWorld(World w) {
        Set<Craft> crafts = craftList.get(w);
        if (crafts == null || crafts.isEmpty()) {
            return null;
        } else {
            return craftList.get(w).toArray(new Craft[1]);
        }
    }

    public Craft getCraftByPlayer(Player p) {
        return craftPlayerIndex.get(p);
    }

    public Craft getCraftByPlayerName(String name) {
        for (Map.Entry<Player, Craft> entry : craftPlayerIndex.entrySet()) {
            if (entry.getKey() != null) {
                if (entry.getKey().getName().equals(name)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    public Player getPlayerFromCraft(Craft c) {
        for (Map.Entry<Player, Craft> playerCraftEntry : craftPlayerIndex.entrySet()) {

            if (playerCraftEntry.getValue() == c) {
                return playerCraftEntry.getKey();
            }
        }

        return null;
    }

    public void removePlayerFromCraft(Craft c) {
        if (getPlayerFromCraft(c) != null) {
            removeReleaseTask(c);
            getPlayerFromCraft(c).sendMessage(i18nSupport.get("Release - Craft has been released message"));
            plugin.getLogger().log(Level.INFO,
                                   String.format(i18nSupport.get("Release - Player has released a craft console"),
                                                 c.getNotificationPlayer().getName(), c.getType().getCraftName(),
                                                 c.getBlockList().length, c.getMinX(), c.getMinZ()));
            Player p = getPlayerFromCraft(c);
            craftPlayerIndex.put(null, c);
            craftPlayerIndex.remove(p);
        }
    }

    public Map<Player, BukkitTask> getReleaseEvents() {
        return releaseEvents;
    }

    public void addReleaseTask(final Craft c) {
        Player p = getPlayerFromCraft(c);
        if (!getReleaseEvents().containsKey(p)) {
            p.sendMessage(i18nSupport.get("Release - Player has left craft"));
            BukkitTask releaseTask = new BukkitRunnable() {
                @Override public void run() {
                    removeCraft(c);
                }
            }.runTaskLater(plugin, (20 * 15));
            getReleaseEvents().put(p, releaseTask);
        }
    }

    public void removeReleaseTask(final Craft c) {
        Player p = getPlayerFromCraft(c);
        if (p != null) {
            if (releaseEvents.containsKey(p)) {
                releaseEvents.get(p).cancel();
                releaseEvents.remove(p);
            }
        }
    }
}
