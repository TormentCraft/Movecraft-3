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

import com.alexknvl.shipcraft.math.BlockVec;
import com.google.common.collect.Sets;
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

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public CraftManager(final Settings settings, final I18nSupport i18nSupport, final Plugin plugin) {
        this.settings = settings;
        this.i18nSupport = i18nSupport;
        this.plugin = plugin;
    }

    @Nonnull public Optional<CraftType> getCraftTypeFromString(final String s) {
        for (final CraftType t : this.getCraftTypes()) {
            if (s.equalsIgnoreCase(t.getCraftName())) {
                return Optional.of(t);
            }
        }

        return Optional.empty();
    }

    public CraftType[] getCraftTypes() {
        return this.craftTypes;
    }

    public void initCraftTypes() {
        final File craftsFile = new File(this.plugin.getDataFolder().getAbsolutePath() + "/types");

        if (!craftsFile.exists()) {
            craftsFile.mkdirs();
            this.plugin.saveResource("types/airship.yaml", false);
            this.plugin.saveResource("types/airskiff.yaml", false);
            this.plugin.saveResource("types/BigAirship.yaml", false);
            this.plugin.saveResource("types/BigSubAirship.yaml", false);
            this.plugin.saveResource("types/elevator.yaml", false);
            this.plugin.saveResource("types/LaunchTorpedo.yaml", false);
            this.plugin.saveResource("types/Ship.yaml", false);
            this.plugin.saveResource("types/SubAirship.yaml", false);
            this.plugin.saveResource("types/Submarine.yaml", false);
            this.plugin.saveResource("types/Turret.yaml", false);
        }

        final HashSet<CraftType> craftTypesSet = new HashSet<>();

        boolean foundCraft = false;
        for (final File file : craftsFile.listFiles()) {
            if (file.isFile() && (file.getName().endsWith(".craft") || file.getName().endsWith(".yaml"))) {
                if (file.getName().endsWith(".craft")) {
                    final String name = file.getName();
                    final String newName = name.substring(0, name.length() - ".craft".length()) + ".yaml";
                    this.plugin.getLogger()
                               .warning("\"craft\" extension is deprecated, please rename " + name + " to " + newName);
                }

                try {
                    final CraftType type = new CraftType();
                    type.parseCraftDataFromFile(file, this.settings.SinkRateTicks);
                    craftTypesSet.add(type);
                    foundCraft = true;
                } catch (FileNotFoundException | CraftType.ParseException e) {
                    this.plugin.getLogger().log(Level.SEVERE,
                                                String.format(this.i18nSupport.get("Startup - Error parsing CraftType file"),
                                                         file.getAbsolutePath()));
                    e.printStackTrace();
                }
            }
        }
        if (!foundCraft) {
            this.plugin.getLogger().log(Level.SEVERE, "ERROR: NO CRAFTS FOUND!!!");
        }
        this.craftTypes = craftTypesSet.toArray(new CraftType[1]);
        this.plugin.getLogger().log(Level.INFO, String.format(this.i18nSupport.get("Startup - Number of craft files loaded"),
                                                              this.craftTypes.length));
    }

    public void addCraft(final Craft craft, final Player player) {
        this.craftList.computeIfAbsent(craft.getWorld(), k -> new HashSet<>()).add(craft);
        this.craftPlayerIndex.put(player, craft);
        this.destroySnowOnPilot(player, craft);
    }

    public void removeCraft(final Craft craft) {
        this.removeReleaseTask(craft);
        // if its sinking, just remove the craft without notifying or checking
        if (craft.getSinking()) {
            this.craftList.get(craft.getWorld()).remove(craft);
            this.craftPlayerIndex.remove(this.getPlayerFromCraft(craft));
        }
        // don't just release torpedoes, make them sink so they don't clutter up the place
        if (craft.getType().getCruiseOnPilot()) {
            craft.setCruising(false);
            craft.setSinking(true);
            craft.setNotificationPlayer(null);
            return;
        }
        this.craftList.get(craft.getWorld()).remove(craft);
        final Player pilot = this.getPlayerFromCraft(craft);
        if (pilot != null) {
            pilot.sendMessage(this.i18nSupport.get("Release - Craft has been released message"));
            this.plugin.getLogger().log(Level.INFO,
                                        String.format(this.i18nSupport.get("Release - Player has released a craft console"),
                                                      craft.getNotificationPlayer().getName(), craft.getType().getCraftName(),
                                                      craft.getBlockList().length, craft.getMinX(), craft.getMinZ()));
        } else {
            this.plugin.getLogger().log(Level.INFO, String.format(this.i18nSupport
                                                                     .get("NULL Player has released a craft of type " +
                                                                          "%s with size %d at coordinates : %d x , %d" +
                                                                          " z"),
                                                                  craft.getType().getCraftName(), craft.getBlockList().length,
                                                                  craft.getMinX(), craft.getMinZ()));
        }
        this.craftPlayerIndex.remove(pilot);

        this.destroyBindingBlocks(pilot, craft);
    }

    private boolean canPilotBreakBlock(final Player pilot, final Block block) {
        if (pilot == null || !pilot.isOnline()) {
            return false;
        }
        final BlockBreakEvent be = new BlockBreakEvent(block, pilot);
        this.plugin.getServer().getPluginManager().callEvent(be);
        return !be.isCancelled();
    }

    private void destroySnowOnPilot(final Player pilot, final Craft craft) {
        if (pilot == null || !pilot.isOnline()) return;

        final Set<BlockVec> craftBlocks = Sets.newHashSet(craft.getBlockList());
        for (final BlockVec block : craftBlocks) {
            final BlockVec test = block.translate(0, 1, 0);
            if (!craftBlocks.contains(test)) {
                final Block testBlock = craft.getWorld().getBlockAt(test.x(), test.y(), test.z());

                if (testBlock.getType() == Material.SNOW && testBlock.getData() == 0) {
                    final Collection<ItemStack> drops = testBlock.getDrops(new ItemStack(Material.WOOD_SPADE, 1, (short) 5));
                    testBlock.setType(Material.AIR);
                    for (final ItemStack drop : drops) {
                        testBlock.getWorld().dropItemNaturally(testBlock.getLocation(), drop);
                    }
                }
            }
        }
    }

    private void destroyBindingBlocks(final Player pilot, final Craft craft) {
        if (pilot != null && (pilot.getGameMode() == GameMode.CREATIVE || pilot.hasPermission("*"))) {
            return;
        }

        final Set<BlockVec> craftBlocks = Sets.newHashSet(craft.getBlockList());
        int blockedBroken = 0;
        for (final BlockVec block : craftBlocks) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        //No diagonals
                        if (z != 0 && x != 0) continue;
                        if (x == 0 && y == 0 && z == 0) continue;

                        final BlockVec test = block.translate(x, y, z);
                        if (!craftBlocks.contains(test)) {
                            Block testBlock = craft.getWorld().getBlockAt(test.x(), test.y(), test.z());
                            if (craft.getType().isAllowedBlock(testBlock.getTypeId(), testBlock.getData()) ||
                                craft.getType().isForbiddenBlock(testBlock.getTypeId(), testBlock.getData())) {

                                testBlock = craft.getWorld().getBlockAt(block.x(), block.y(), block.z());
                                if (!this.canPilotBreakBlock(pilot, testBlock)) {
                                    blockedBroken++;
                                    final Collection<ItemStack> drops = testBlock.getDrops();
                                    testBlock.setType(Material.AIR);
                                    for (final ItemStack drop : drops) {
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

    @Nonnull public List<net.countercraft.movecraft.api.Craft> getCrafts() {
        final List<net.countercraft.movecraft.api.Craft> result = new ArrayList<>();
        for (final Map.Entry<World, Set<Craft>> entry : this.craftList.entrySet()) {
            result.addAll(entry.getValue());
        }
        return Collections.unmodifiableList(result);
    }

    @Nonnull public Set<Craft> getCraftsInWorld(final World world) {
        final Set<Craft> result = this.craftList.get(world);
        if (result == null) return Collections.emptySet();
        return Collections.unmodifiableSet(result);
    }

    public Craft getCraftByPlayer(final Player player) {
        return this.craftPlayerIndex.get(player);
    }

    public Player getPlayerFromCraft(final Craft craft) {
        for (final Map.Entry<Player, Craft> playerCraftEntry : this.craftPlayerIndex.entrySet()) {

            if (playerCraftEntry.getValue() == craft) {
                return playerCraftEntry.getKey();
            }
        }

        return null;
    }

    public void removePlayerFromCraft(final Craft craft) {
        if (this.getPlayerFromCraft(craft) != null) {
            this.removeReleaseTask(craft);
            this.getPlayerFromCraft(craft).sendMessage(this.i18nSupport.get("Release - Craft has been released message"));
            this.plugin.getLogger().log(Level.INFO,
                                        String.format(this.i18nSupport.get("Release - Player has released a craft console"),
                                                      craft.getNotificationPlayer().getName(), craft.getType().getCraftName(),
                                                      craft.getBlockList().length, craft.getMinX(), craft.getMinZ()));
            final Player p = this.getPlayerFromCraft(craft);
            this.craftPlayerIndex.put(null, craft);
            this.craftPlayerIndex.remove(p);
        }
    }

    public Map<Player, BukkitTask> getReleaseEvents() {
        return this.releaseEvents;
    }

    public void addReleaseTask(final Craft craft) {
        final Player p = this.getPlayerFromCraft(craft);
        if (!this.getReleaseEvents().containsKey(p)) {
            p.sendMessage(this.i18nSupport.get("Release - Player has left craft"));
            final BukkitTask releaseTask = new BukkitRunnable() {
                @Override public void run() {
                    CraftManager.this.removeCraft(craft);
                }
            }.runTaskLater(this.plugin, (20 * 15));
            this.getReleaseEvents().put(p, releaseTask);
        }
    }

    public void removeReleaseTask(final Craft c) {
        final Player p = this.getPlayerFromCraft(c);
        if (p != null) {
            if (this.releaseEvents.containsKey(p)) {
                this.releaseEvents.get(p).cancel();
                this.releaseEvents.remove(p);
            }
        }
    }
}
