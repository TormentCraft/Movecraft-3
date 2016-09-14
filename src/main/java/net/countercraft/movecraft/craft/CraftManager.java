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

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.MovecraftLocation;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CraftManager {
    private static final CraftManager ourInstance = new CraftManager();
    private CraftType[] craftTypes;
    private final Map<World, Set<Craft>> craftList = new ConcurrentHashMap<>();
    private final Map<Player, Craft> craftPlayerIndex = new HashMap<>();
    private final Map<Player, BukkitTask> releaseEvents = new HashMap<>();

    public static CraftManager getInstance() {
        return ourInstance;
    }

    private CraftManager() {
        initCraftTypes();
    }

    public CraftType[] getCraftTypes() {
        return craftTypes;
    }

    public void initCraftTypes() {
        File craftsFile = new File(Movecraft.getInstance().getDataFolder().getAbsolutePath() + "/types");

        if (!craftsFile.exists()) {
            craftsFile.mkdirs();
            Movecraft.getInstance().saveResource("types/airship.craft", false);
            Movecraft.getInstance().saveResource("types/airskiff.craft", false);
            Movecraft.getInstance().saveResource("types/BigAirship.craft", false);
            Movecraft.getInstance().saveResource("types/BigSubAirship.craft", false);
            Movecraft.getInstance().saveResource("types/elevator.craft", false);
            Movecraft.getInstance().saveResource("types/LaunchTorpedo.craft", false);
            Movecraft.getInstance().saveResource("types/Ship.craft", false);
            Movecraft.getInstance().saveResource("types/SubAirship.craft", false);
            Movecraft.getInstance().saveResource("types/Submarine.craft", false);
            Movecraft.getInstance().saveResource("types/Turret.craft", false);
        }

        HashSet<CraftType> craftTypesSet = new HashSet<>();

        boolean foundCraft = false;
        for (File file : craftsFile.listFiles()) {
            if (file.isFile() && (file.getName().endsWith(".craft") || file.getName().endsWith(".yaml"))) {
                if (file.getName().endsWith(".craft")) {
                    String name = file.getName();
                    String newName = name.substring(0, name.length() - ".craft".length()) + ".yaml";
                    Movecraft.getInstance().getLogger()
                             .warning("\"craft\" extension is deprecated, please rename " + name + " to " + newName);
                }

                CraftType type = new CraftType(file);
                craftTypesSet.add(type);
                foundCraft = true;
            }
        }
        if (!foundCraft) {
            Movecraft.getInstance().getLogger().log(Level.SEVERE, "ERROR: NO CRAFTS FOUND!!!");
        }
        craftTypes = craftTypesSet.toArray(new CraftType[1]);
        Movecraft.getInstance().getLogger().log(Level.INFO, String.format(
                I18nSupport.getInternationalisedString("Startup - Number of craft files loaded"), craftTypes.length));
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
            pilot.sendMessage(I18nSupport.getInternationalisedString("Release - Craft has been released message"));
            Movecraft.getInstance().getLogger().log(Level.INFO, String.format(
                    I18nSupport.getInternationalisedString("Release - Player has released a craft console"),
                    c.getNotificationPlayer().getName(), c.getType().getCraftName(), c.getBlockList().length,
                    c.getMinX(), c.getMinZ()));
        } else {
            Movecraft.getInstance().getLogger().log(Level.INFO, String.format(I18nSupport.getInternationalisedString(
                    "NULL Player has released a craft of type %s with size %d at coordinates : %d x , %d z"),
                                                                              c.getType().getCraftName(),
                                                                              c.getBlockList().length, c.getMinX(),
                                                                              c.getMinZ()));
        }
        craftPlayerIndex.remove(pilot);

        destroyBindingBlocks(pilot, c);
    }

    private boolean canPilotBreakBlock(Player pilot, Block block) {
        if (pilot == null || !pilot.isOnline()) {
            return false;
        }
        BlockBreakEvent be = new BlockBreakEvent(block, pilot);
        Movecraft.getInstance().getServer().getPluginManager().callEvent(be);
        if (be.isCancelled()) {
            return false;
        }
        return true;
    }

    private void destroySnowOnPilot(Player pilot, Craft craft) {
        if (pilot == null || !pilot.isOnline()) return;

        Set<MovecraftLocation> craftBlocks = new HashSet<>(Arrays.asList(craft.getBlockList()));
        for (MovecraftLocation block : craftBlocks) {
            MovecraftLocation test = new MovecraftLocation(block.x, block.y + 1, block.z);
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

        Set<MovecraftLocation> craftBlocks = new HashSet<>(Arrays.asList(craft.getBlockList()));
        int blockedBroken = 0;
        for (MovecraftLocation block : craftBlocks) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        //No diagonals
                        if ((z != 0 && x != 0) || (x == 0 && y == 0 && z == 0)) continue;

                        MovecraftLocation test = new MovecraftLocation(block.x + x, block.y + y, block.z + z);
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
        Set<Player> players = craftPlayerIndex.keySet();
        for (Player player : players) {
            if (player != null) {
                if (player.getName().equals(name)) {
                    return craftPlayerIndex.get(player);
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
            getPlayerFromCraft(c).sendMessage(
                    I18nSupport.getInternationalisedString("Release - Craft has been released message"));
            Movecraft.getInstance().getLogger().log(Level.INFO, String.format(
                    I18nSupport.getInternationalisedString("Release - Player has released a craft console"),
                    c.getNotificationPlayer().getName(), c.getType().getCraftName(), c.getBlockList().length,
                    c.getMinX(), c.getMinZ()));
            Player p = getPlayerFromCraft(c);
            craftPlayerIndex.put(null, c);
            craftPlayerIndex.remove(p);
        }
    }

    public Map<Player, BukkitTask> getReleaseEvents() {
        return releaseEvents;
    }

    public final void addReleaseTask(final Craft c) {
        Player p = getPlayerFromCraft(c);
        if (!getReleaseEvents().containsKey(p)) {
            p.sendMessage(I18nSupport.getInternationalisedString("Release - Player has left craft"));
            BukkitTask releaseTask = new BukkitRunnable() {
                @Override public void run() {
                    removeCraft(c);
                }
            }.runTaskLater(Movecraft.getInstance(), (20 * 15));
            CraftManager.getInstance().getReleaseEvents().put(p, releaseTask);
        }
    }

    public final void removeReleaseTask(final Craft c) {
        Player p = getPlayerFromCraft(c);
        if (p != null) {
            if (releaseEvents.containsKey(p)) {
                releaseEvents.get(p).cancel();
                releaseEvents.remove(p);
            }
        }
    }
}
