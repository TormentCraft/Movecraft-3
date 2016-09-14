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

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.utils.BlockNames;
import net.countercraft.movecraft.utils.MathUtils;
import net.countercraft.movecraft.utils.MovecraftLocation;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PlayerListener implements Listener {

    private String checkCraftBorders(Craft craft) {
        Set<MovecraftLocation> craftBlocks = new HashSet<>(Arrays.asList(craft.getBlockList()));
        for (MovecraftLocation block : craftBlocks) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        //No diagonals
                        if ((z != 0 && x != 0) || (x == 0 && y == 0 && z == 0)) continue;

                        MovecraftLocation test = new MovecraftLocation(block.x + x, block.y + y, block.z + z);
                        if (!craftBlocks.contains(test)) {
                            Block testBlock = craft.getW().getBlockAt(block.x + x, block.y + y, block.z + z);
                            if (craft.getType().isAllowedBlock(testBlock.getTypeId(), testBlock.getData()) ||
                                craft.getType().isForbiddenBlock(testBlock.getTypeId(), testBlock.getData())) {

                                return String.format("%s at (%d,%d,%d)", BlockNames
                                                             .itemName(testBlock.getTypeId(), testBlock.getData(),
                                                                       true), test.x, test.y,
                                                     test.z);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @EventHandler public void onPLayerLogout(PlayerQuitEvent e) {
        Craft c = CraftManager.getInstance().getCraftByPlayer(e.getPlayer());

        if (c != null) {
            CraftManager.getInstance().removeCraft(c);
        }
    }

/*	public void onPlayerDamaged( EntityDamageByEntityEvent e ) {
        if ( e instanceof Player ) {
			Player p = ( Player ) e;
			CraftManager.getInstance().removeCraft( CraftManager.getInstance().getCraftByPlayer( p ) );
		}
	}*/

    @EventHandler public void onPlayerDeath(EntityDamageByEntityEvent e)
    {  // changed to death so when you shoot up an airship and hit the pilot, it still sinks
        if (e instanceof Player) {
            Player p = (Player) e;
            CraftManager.getInstance().removeCraft(CraftManager.getInstance().getCraftByPlayer(p));
        }
    }

    @EventHandler public void onPlayerMove(PlayerMoveEvent event) {
        final Craft c = CraftManager.getInstance().getCraftByPlayer(event.getPlayer());
        if (c != null) {
            if (c.isNotProcessing() && (!MathUtils
                    .playerIsWithinBoundingPolygon(c.getHitBox(), c.getMinX(), c.getMinZ(),
                                                   MathUtils.bukkit2MovecraftLoc(event.getPlayer().getLocation())))) {

                if (!CraftManager.getInstance().getReleaseEvents().containsKey(event.getPlayer()) &&
                    c.getType().getMoveEntities()) {
                    if (Settings.ManOverBoardTimeout != 0) event.getPlayer().sendMessage(I18nSupport
                                                                                                               .getInternationalisedString(
                                                                                                                       "You have left your craft. You may return to your craft by typing /manoverboard any time before the timeout expires"));
                    else event.getPlayer().sendMessage(
                            I18nSupport.getInternationalisedString("Release - Player has left craft"));
                    if (c.getBlockList().length > 11000) {
                        event.getPlayer().sendMessage(I18nSupport.getInternationalisedString(
                                "Craft is too big to check its borders. Make sure this area is safe to release your " +
                                "craft in."));
                    } else {
                        String ret = checkCraftBorders(c);
                        if (ret != null) {
                            event.getPlayer().sendMessage(ChatColor.RED +
                                                          I18nSupport.getInternationalisedString(
                                                                  "WARNING! There are blocks near your craft, part of" +
                                                                  " your craft may be damaged!") +
                                                          ChatColor.RESET + "\n" + ret);
                        }
                    }

                    BukkitTask releaseTask = new BukkitRunnable() {

                        @Override public void run() {
                            CraftManager.getInstance().removeCraft(c);
                        }
                    }.runTaskLater(Movecraft.getInstance(), (20 * 30));

                    CraftManager.getInstance().getReleaseEvents().put(event.getPlayer(), releaseTask);
                }
            } else {
                if (CraftManager.getInstance().getReleaseEvents().containsKey(event.getPlayer()) &&
                    c.getType().getMoveEntities()) {
                    CraftManager.getInstance().removeReleaseTask(c);
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
