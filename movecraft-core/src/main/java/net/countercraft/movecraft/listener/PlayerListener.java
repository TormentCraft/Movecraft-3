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

import net.countercraft.movecraft.api.BlockVec;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.BlockNames;
import net.countercraft.movecraft.utils.MathUtils;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;


public class PlayerListener implements Listener {
    private final Plugin plugin;
    private final Settings settings;
    private final I18nSupport i18n;
    private final CraftManager craftManager;

    public PlayerListener(final Plugin plugin, final Settings settings, final I18nSupport i18n, final CraftManager craftManager) {
        this.plugin = plugin;
        this.settings = settings;
        this.i18n = i18n;
        this.craftManager = craftManager;
    }

    private static String checkCraftBorders(final Craft craft) {
        final Set<BlockVec> craftBlocks = new HashSet<>(Arrays.asList(craft.getBlockList()));
        for (final BlockVec block : craftBlocks) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        //No diagonals
                        if (x == 0 && y == 0 && z == 0) continue;
                        if (z != 0 && x != 0) continue;

                        final BlockVec test = new BlockVec(block.x + x, block.y + y, block.z + z);
                        if (!craftBlocks.contains(test)) {
                            final Block testBlock = craft.getWorld().getBlockAt(block.x + x, block.y + y, block.z + z);
                            if (craft.getType().isAllowedBlock(testBlock.getTypeId(), testBlock.getData()) ||
                                craft.getType().isForbiddenBlock(testBlock.getTypeId(), testBlock.getData())) {

                                return String
                                        .format("%s at (%d,%d,%d)", BlockNames.itemName(testBlock.getState().getData()),
                                                test.x, test.y, test.z);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    @EventHandler public void onPLayerLogout(final PlayerQuitEvent event) {
        final Craft c = this.craftManager.getCraftByPlayer(event.getPlayer());

        if (c != null) {
            this.craftManager.removeCraft(c);
        }
    }

/*	public void onPlayerDamaged( EntityDamageByEntityEvent e ) {
        if ( e instanceof Player ) {
			Player p = ( Player ) e;
			CraftManager.getInstance().removeCraft( CraftManager.getInstance().getCraftByPlayer( p ) );
		}
	}*/

    @SuppressWarnings("unused")
    @EventHandler public void onPlayerDeath(final EntityDamageByEntityEvent event)
    {  // changed to death so when you shoot up an airship and hit the pilot, it still sinks
        if (event instanceof Player) {
            final Player p = (Player) event;
            this.craftManager.removeCraft(this.craftManager.getCraftByPlayer(p));
        }
    }

    @SuppressWarnings("unused")
    @EventHandler public void onPlayerMove(final PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final Craft craft = this.craftManager.getCraftByPlayer(player);

        if (craft != null) {
            if (craft.isNotProcessing() && (!MathUtils
                    .playerIsWithinBoundingPolygon(craft.getHitBox(), craft.getMinX(), craft.getMinZ(),
                                                   MathUtils.bukkit2MovecraftLoc(player.getLocation())))) {

                if (!this.craftManager.getReleaseEvents().containsKey(player) && craft.getType().getMoveEntities()) {
                    if (this.settings.ManOverBoardTimeout == 0)
                        player.sendMessage(this.i18n.get("Release - Player has left craft"));
                    else player.sendMessage(this.i18n.get(
                            "You have left your craft. You may return to your craft by typing /manoverboard any time " +
                            "before the timeout expires"));
                    if (craft.getBlockList().length > 11000) {
                        player.sendMessage(this.i18n.get(
                                "Craft is too big to check its borders. Make sure this area is safe to release your " +
                                "craft in."));
                    } else {
                        final String ret = checkCraftBorders(craft);
                        if (ret != null) {
                            player.sendMessage(ChatColor.RED +
                                               this.i18n.get("WARNING! There are blocks near your craft, part of" +
                                                             " your craft may be damaged!") +
                                               ChatColor.RESET + "\n" + ret);
                        }
                    }

                    final BukkitTask releaseTask = new BukkitRunnable() {

                        @Override public void run() {
                            PlayerListener.this.craftManager.removeCraft(craft);
                        }
                    }.runTaskLater(this.plugin, (20 * 30));

                    this.craftManager.getReleaseEvents().put(player, releaseTask);
                }
            } else {
                if (this.craftManager.getReleaseEvents().containsKey(player) && craft.getType().getMoveEntities()) {
                    this.craftManager.removeReleaseTask(craft);
                }
            }
        }
    }

/*	@EventHandler
    public void onPlayerHit( EntityDamageByEntityEvent event ) {
		if ( event.getEntity() instanceof Player && CraftManager.getInstance().getCraftByPlayer( ( Player ) event
		.getEntity() ) != null ) {
			CraftManager.getInstance().removeCraft( CraftManager.getInstance().getCraftByPlayer( ( Player ) event
			.getEntity() ) );
		}
	}   */
}
