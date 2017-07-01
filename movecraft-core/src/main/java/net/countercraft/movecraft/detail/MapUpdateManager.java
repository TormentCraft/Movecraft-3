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

package net.countercraft.movecraft.detail;

import com.alexknvl.shipcraft.math.BlockVec;
import com.alexknvl.shipcraft.math.RotationXZ;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.SignBlock;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
//import net.countercraft.movecraft.Movecraft;
//import net.countercraft.movecraft.config.Settings;
//import net.countercraft.movecraft.craft.Craft;
//import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.utils.BlockUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.Sign;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MapUpdateManager extends BukkitRunnable {
    private static final boolean DEBUG = false;
    private static final Logger LOGGER = Logger.getLogger(MapUpdateManager.class.getSimpleName());

    private final Map<World, ArrayList<MapUpdateCommand>> updates = new HashMap<>();
    private final Map<World, ArrayList<EntityUpdateCommand>> entityUpdates = new HashMap<>();
    private final Map<World, ArrayList<ItemDropUpdateCommand>> itemDropUpdates = new HashMap<>();

    private final Plugin plugin;
    private final boolean compatibilityMode;
    private final int queueChunkSize;

    public MapUpdateManager(final Plugin plugin, final boolean compatibilityMode, final int queueChunkSize) {
        this.plugin = plugin;
        this.compatibilityMode = compatibilityMode;
        this.queueChunkSize = queueChunkSize;
    }

    private void updateBlock(final MapUpdateCommand m, final World w, final Map<BlockVec, TransferData> dataMap,
                             final Set<net.minecraft.server.v1_12_R1.Chunk> chunks, final Set<Chunk> cmChunks,
                             final HashMap<BlockVec, Byte> origLightMap, final boolean placeDispensers)
    {
        final BlockVec workingL = m.newBlockLocation;
        final int[] blocksToBlankOut = {54, 61, 62, 63, 68, 116, 117, 146, 149, 150, 154, 158, 145};

        final int x = workingL.x();
        final int y = workingL.y();
        final int z = workingL.z();

        int newTypeID = m.typeID;

        if ((newTypeID == 152 || newTypeID == 26) && !placeDispensers) {
            return;
        }

        final Chunk chunk = w.getBlockAt(x, y, z).getChunk();

        net.minecraft.server.v1_12_R1.Chunk c = null;
        Chunk cmC = null;
        if (this.compatibilityMode) {
            cmC = chunk;
        } else {
            c = ((CraftChunk) chunk).getHandle();
        }

        byte data = m.dataID;

        if (newTypeID == 23 && !placeDispensers) {
            newTypeID = 44;
            data = 8;
        }

        final int origType = w.getBlockAt(x, y, z).getTypeId();
        final byte origData = w.getBlockAt(x, y, z).getData();

        if (this.compatibilityMode) {

            if (origType != newTypeID || origData != data) {
                final boolean doBlankOut = (Arrays.binarySearch(blocksToBlankOut, newTypeID) >= 0);
                if (doBlankOut) {
                    w.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR);
                }

                if (origType == 149 ||
                    origType == 150) { // necessary because bukkit does not handle comparators correctly. This
                    // code does not prevent console spam, but it does prevent chunk corruption
                    w.getBlockAt(x, y, z).setType(org.bukkit.Material.SIGN_POST);
                    final BlockState state = w.getBlockAt(x, y, z).getState();
                    if (state instanceof Sign) { // for some bizarre reason the block is sometimes not a sign, which
                        // crashes unless I do this
                        final Sign s = (Sign) state;
                        s.setLine(0, "PLACEHOLDER");
//						s.update();   FROGGG
                    }
                    w.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR);
                }
                if ((newTypeID == 149 || newTypeID == 150) && m.worldEditBaseBlock == null) {
                    w.getBlockAt(x, y, z).setTypeIdAndData(newTypeID, data, false);
                } else {
                    if (m.worldEditBaseBlock == null) {
                        w.getBlockAt(x, y, z).setTypeIdAndData(newTypeID, data, false);
                    } else {
                        w.getBlockAt(x, y, z).setTypeIdAndData(((BaseBlock) m.worldEditBaseBlock).getType(),
                                                               (byte) ((BaseBlock) m.worldEditBaseBlock).getData(),
                                                               false);
                        final BaseBlock bb = (BaseBlock) m.worldEditBaseBlock;
                        if (m.worldEditBaseBlock instanceof SignBlock) {
                            final BlockState state = w.getBlockAt(x, y, z).getState();
                            final Sign s = (Sign) state;
                            for (int i = 0; i < ((SignBlock) m.worldEditBaseBlock).getText().length; i++) {
                                s.setLine(i, ((SignBlock) m.worldEditBaseBlock).getText()[i]);
                            }
                            s.update();
                        }
                    }
                }
            }
            if (!cmChunks.contains(cmC)) {
                cmChunks.add(cmC);
            }
        } else {
            final net.minecraft.server.v1_12_R1.BlockPosition position = new net.minecraft.server.v1_12_R1.BlockPosition(x, y,
                                                                                                                         z);

            boolean success = false;
            if ((origType == 149 || origType == 150) &&
                m.worldEditBaseBlock == null) { // bukkit can't remove comparators safely, it screws
                // up the NBT data. So turn it to a sign, then remove it.

                c.a(position, CraftMagicNumbers.getBlock(org.bukkit.Material.AIR).fromLegacyData(0));
                c.a(position, CraftMagicNumbers.getBlock(org.bukkit.Material.SIGN_POST).fromLegacyData(0));

                final BlockState state = w.getBlockAt(x, y, z).getState();
                final Sign s = (Sign) state;
                s.setLine(0, "PLACEHOLDER");
                s.update();
                c.a(position, CraftMagicNumbers.getBlock(org.bukkit.Material.SIGN_POST).fromLegacyData(0));
                success = c.a(position, CraftMagicNumbers.getBlock(newTypeID).fromLegacyData(data)) != null;
                if (!success) {
                    w.getBlockAt(x, y, z).setTypeIdAndData(newTypeID, data, false);
                }
                if (!chunks.contains(c)) {
                    chunks.add(c);
                }
            } else {
/*				if(origType==50 || origType==89 || origType==124 || origType==169) {
                    // if removing a light source, remove lighting from nearby terrain to avoid light pollution
					int centerX=x;
					int centerY=y;
					int centerZ=z;
					for(int posx=centerX-14;posx<=centerX+14;posx++) {
						for(int posy=centerY-14;posy<=centerY+14;posy++) {
							if(posy>0 && posy<=255)
								for(int posz=centerZ-14;posz<=centerZ+14;posz++) {
									int linearDist=Math.abs(posx-centerX);
									linearDist+=Math.abs(posy-centerY);
									linearDist+=Math.abs(posz-centerZ);
									if(linearDist<=15) {
//										((CraftWorld) world).getHandle().b(EnumSkyBlock.BLOCK, x, y, z, lightLevel);
Changed for 1.8, and quite possibly wrong:
										BlockVec positioni = new BlockVec(posx, posy, posz);
										((CraftWorld) world).getHandle().b(EnumSkyBlock.BLOCK, positioni);
									}
								}
						}
					}
				}*/

                if (origType != newTypeID || origData != data) {
                    final boolean doBlankOut = (Arrays.binarySearch(blocksToBlankOut, newTypeID) >= 0);
                    if (doBlankOut) {
                        c.a(position, CraftMagicNumbers.getBlock(0).fromLegacyData(0));
                        w.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR);
                    }

                    if (newTypeID == 50 || newTypeID == 89 || newTypeID == 169 || newTypeID == 124 || origType == 50 ||
                        origType == 89 || origType == 169 || origType == 124) // don't use native code for lights
                        w.getBlockAt(x, y, z).setTypeIdAndData(newTypeID, data, false);
                    else {
                        if (m.worldEditBaseBlock == null) {
                            success = c.a(position, CraftMagicNumbers.getBlock(newTypeID).fromLegacyData(data)) != null;
                        } else {
                            success = c.a(position, CraftMagicNumbers.getBlock(newTypeID).fromLegacyData(data)) != null;
                            if (m.worldEditBaseBlock instanceof SignBlock) {
                                final BlockState state = w.getBlockAt(x, y, z).getState();
                                final Sign s = (Sign) state;
                                for (int i = 0; i < ((SignBlock) m.worldEditBaseBlock).getText().length; i++) {
                                    s.setLine(i, ((SignBlock) m.worldEditBaseBlock).getText()[i]);
                                }
                                s.update();
                            }
                        }
                    }
                } else {
                    success = true;
                }
                if (!success) {
                    if (m.worldEditBaseBlock == null) {
                        w.getBlockAt(x, y, z).setTypeIdAndData(newTypeID, data, false);
                    } else {
                        w.getBlockAt(x, y, z).setTypeIdAndData(((BaseBlock) m.worldEditBaseBlock).getType(),
                                                               (byte) ((BaseBlock) m.worldEditBaseBlock).getData(),
                                                               false);
                        if (m.worldEditBaseBlock instanceof SignBlock) {
                            final BlockState state = w.getBlockAt(x, y, z).getState();
                            final Sign sign = (Sign) state;
                            for (int i = 0; i < ((SignBlock) m.worldEditBaseBlock).getText().length; i++) {
                                sign.setLine(i, ((SignBlock) m.worldEditBaseBlock).getText()[i]);
                            }
                            sign.update();
                        }
                    }
                }

                if (!chunks.contains(c)) {
                    chunks.add(c);
                }
            }
        }
    }

    private void updateData(final Map<BlockVec, TransferData> dataMap, final World w) {
        // Restore block specific information
        for (final Map.Entry<BlockVec, TransferData> entry : dataMap.entrySet()) {
            try {
                final TransferData transferData = entry.getValue();

                if (transferData instanceof SignTransferHolder) {

                    final SignTransferHolder signData = (SignTransferHolder) transferData;
                    final BlockState bs = w.getBlockAt(entry.getKey().x(), entry.getKey().y(), entry.getKey().z()).getState();
                    if (bs instanceof Sign) {
                        final Sign sign = (Sign) bs;
                        for (int i = 0; i < signData.lines.length; i++) {
                            sign.setLine(i, signData.lines[i]);
                        }

                        for (final Player p : w
                                .getPlayers()) { // this is necessary because signs do not get updated client side
                            // correctly without refreshing the chunks, which causes a memory leak in the clients
                            final int playerChunkX = p.getLocation().getBlockX() >> 4;
                            final int playerChunkZ = p.getLocation().getBlockZ() >> 4;
                            if (Math.abs(playerChunkX - sign.getChunk().getX()) < Bukkit.getServer().getViewDistance())
                                if (Math.abs(playerChunkZ - sign.getChunk().getZ()) <
                                    Bukkit.getServer().getViewDistance()) {
                                    p.sendBlockChange(sign.getLocation(), 63, (byte) 0);
                                    p.sendBlockChange(sign.getLocation(), sign.getTypeId(), sign.getRawData());
                                }
                        }
                        sign.update(true, false);
                    }
                } else if (transferData instanceof InventoryTransferHolder) {
                    final InventoryTransferHolder invData = (InventoryTransferHolder) transferData;
                    final InventoryHolder inventoryHolder = (InventoryHolder) w
                            .getBlockAt(entry.getKey().x(), entry.getKey().y(), entry.getKey().z()).getState();
                    inventoryHolder.getInventory().setContents(invData.inventory);
                } else if (transferData instanceof CommandBlockTransferHolder) {
                    final CommandBlockTransferHolder cbData = (CommandBlockTransferHolder) transferData;
                    final CommandBlock cblock = (CommandBlock) w
                            .getBlockAt(entry.getKey().x(), entry.getKey().y(), entry.getKey().z()).getState();
                    cblock.setCommand(cbData.commandText);
                    cblock.setName(cbData.commandName);
                    cblock.update();
                }
                w.getBlockAt(entry.getKey().x(), entry.getKey().y(), entry.getKey().z()).setData(transferData.data);
            } catch (final IndexOutOfBoundsException | IllegalArgumentException e) {
                LOGGER.log(Level.SEVERE, "Severe error in map updater");
            }
        }
    }

    private void runQueue(final ArrayList<MapUpdateCommand> queuedMapUpdateCommands,
                          final ArrayList<Boolean> queuedPlaceDispensers, final World w,
                          final Set<net.minecraft.server.v1_12_R1.Chunk> chunks, final Set<Chunk> cmChunks,
                          final HashMap<BlockVec, Byte> origLightMap, final Map<BlockVec, TransferData> dataMap,
                          final List<MapUpdateCommand> updatesInWorld,
                          final Map<BlockVec, List<EntityUpdateCommand>> entityMap)
    {
        int numToRun = queuedMapUpdateCommands.size();
        if (numToRun > this.queueChunkSize) numToRun = this.queueChunkSize;

        final long start = System.currentTimeMillis();
        for (int i = 0; i < numToRun; i++) {
            final MapUpdateCommand command = queuedMapUpdateCommands.get(0);
            this.updateBlock(command, w, dataMap, chunks, cmChunks, origLightMap, queuedPlaceDispensers.get(0));
            queuedMapUpdateCommands.remove(0);
            queuedPlaceDispensers.remove(0);
        }
        final long end = System.currentTimeMillis();

        if (!queuedMapUpdateCommands.isEmpty()) {
            final BukkitTask nextQueueRun = new BukkitRunnable() {
                @Override public void run() {
                    try {
                        MapUpdateManager.this
                                .runQueue(queuedMapUpdateCommands, queuedPlaceDispensers, w, chunks, cmChunks, origLightMap,
                                          dataMap, updatesInWorld, entityMap);
                    } catch (final Exception e) {
                        final StringWriter sw = new StringWriter();
                        final PrintWriter pw = new PrintWriter(sw);
                        e.printStackTrace(pw);
                        LOGGER.log(Level.SEVERE, sw.toString());
                    }
                }
            }.runTaskLater(this.plugin, ((end - start) / 50));
        } else {
            // all done, do final cleanup with sign data, inventories, etc
            this.updateData(dataMap, w);

            // and set all crafts that were updated to not processing
            for (final MapUpdateCommand c : updatesInWorld) {
                if (c != null) {
                    final Craft craft = c.craft;
                    if (craft != null) {
                        if (!craft.isNotProcessing()) {
                            craft.setProcessing(false);
                            if (DEBUG) {
                                final long finish = System.currentTimeMillis();
                                this.plugin.getServer().broadcastMessage("Time from last cruise to update (ms): " +
                                                                         (finish - craft.getLastCruiseUpdate()));
                            }
                        }
                    }
                }
            }

            if (!this.compatibilityMode) {
                // send updates to client
                for (final MapUpdateCommand command : updatesInWorld) {
                    final Location loc = command.newBlockLocation.toBukkitLocation(w);
                    w.getBlockAt(loc).getState().update();
                }
//				for ( net.minecraft.server.v1_8_R3.Chunk c : chunks ) {
//					c.initLighting();
//				}
/*				for(BlockVec mloc : origLightMap.keySet()) {
                    Location loc=new Location(world, mloc.getX(), mloc.getY(), mloc.getZ());
					for ( Player p : world.getPlayers() ) {
						Chunk c=p.getLocation().getChunk();
						int playerChunkX=p.getLocation().getBlockX()>>4;
						int playerChunkZ=p.getLocation().getBlockZ()>>4;

						if(Math.abs(playerChunkX-c.getX())<Bukkit.getServer().getViewDistance())
							if(Math.abs(playerChunkZ-c.getZ())<Bukkit.getServer().getViewDistance())
								p.sendBlockChange(loc, world.getBlockTypeIdAt(loc), world.getBlockAt(loc).getData());
					}
					world.getBlockAt(loc).getState().update();
				}*/
            }

			
/*			// move all players one final time
            for(List<EntityUpdateCommand> listE : entityMap.values()) {
				for(EntityUpdateCommand e : listE) {
					if(e.getEntity() instanceof Player) {
						e.getEntity().teleport(e.getNewLocation());
					}
				}
			}*/
        }
    }

    @Override public void run() {
        if (this.updates.isEmpty()) return;

        final long startTime = System.currentTimeMillis();

        final int[] fragileBlocks = {
                26, 34, 50, 55, 63, 64, 65, 68, 69, 70, 71, 72, 75, 76, 77, 93, 94, 96, 131, 132, 143, 147, 148, 149,
                150, 151, 171, 323, 324, 330, 331, 356, 404};
        Arrays.sort(fragileBlocks);

        for (final Map.Entry<World, ArrayList<MapUpdateCommand>> entry : this.updates.entrySet()) {
            if (entry.getKey() != null) {
                final List<MapUpdateCommand> updatesInWorld = entry.getValue();
                final List<EntityUpdateCommand> entityUpdatesInWorld = this.entityUpdates.get(entry.getKey());
                final List<ItemDropUpdateCommand> itemDropUpdatesInWorld = this.itemDropUpdates.get(entry.getKey());
                final Map<BlockVec, List<ItemDropUpdateCommand>> itemMap = new HashMap<>();

                Set<net.minecraft.server.v1_12_R1.Chunk> chunks = null;
                Set<Chunk> cmChunks = null;

                if (this.compatibilityMode) {
                    cmChunks = new HashSet<>();
                } else {
                    chunks = new HashSet<>();
                }

                // Preprocessing
                final Map<BlockVec, TransferData> dataMap = new HashMap<>();
                final HashMap<BlockVec, Byte> origLightMap = new HashMap<>();
                final ArrayList<MapUpdateCommand> queuedMapUpdateCommands = new ArrayList<>();
                final ArrayList<Boolean> queuedPlaceDispensers = new ArrayList<>();
                for (final MapUpdateCommand c : updatesInWorld) {
                    final BlockVec l;
                    if (c != null) l = c.blockLocation;
                    else l = null;

                    if (l != null) {
                        // keep track of the light levels that were present before moving the craft
                        origLightMap.put(l, entry.getKey().getBlockAt(l.x(), l.y(), l.z()).getLightLevel());

                        // keep track of block data for later reconstruction
                        final TransferData blockDataPacket = this.getBlockDataPacket(
                                entry.getKey().getBlockAt(l.x(), l.y(), l.z()).getState(), c.rotation);
                        if (blockDataPacket != null) {
                            dataMap.put(c.newBlockLocation, blockDataPacket);
                        }

                        //remove dispensers and replace them with half slabs to prevent them firing during
                        // reconstruction
                        if (entry.getKey().getBlockAt(l.x(), l.y(), l.z()).getTypeId() == 23) {
                            final MapUpdateCommand blankCommand = new MapUpdateCommand(c.blockLocation, 23, c
                                    .dataID, c.craft);
//							if(compatibilityMode) {
                            queuedMapUpdateCommands.add(blankCommand);
                            queuedPlaceDispensers.add(false);
//							} else 
//								updateBlock(blankCommand, world, dataMap, chunks, cmChunks, origLightMap, false);
                        }
                        //remove redstone blocks and replace them with stone to prevent redstone activation during
                        // reconstruction
                        if (entry.getKey().getBlockAt(l.x(), l.y(), l.z()).getTypeId() == 152) {
                            final MapUpdateCommand blankCommand = new MapUpdateCommand(c.blockLocation, 1, (byte) 0, c.craft);
//							if(compatibilityMode) {
                            queuedMapUpdateCommands.add(blankCommand);
                            queuedPlaceDispensers.add(false);
//							} else 
//								updateBlock(blankCommand, world, dataMap, chunks, cmChunks, origLightMap, false);
                        }
                        //remove water and lava blocks and replace them with stone to prevent spillage during
                        // reconstruction
                        if (entry.getKey().getBlockAt(l.x(), l.y(), l.z()).getTypeId() >= 8 &&
                            entry.getKey().getBlockAt(l.x(), l.y(), l.z()).getTypeId() <= 11) {
                            final MapUpdateCommand blankCommand = new MapUpdateCommand(c.blockLocation, 0, (byte) 0, c.craft);
                            this.updateBlock(blankCommand, entry.getKey(), dataMap, chunks, cmChunks, origLightMap, false);
                        }
                    }
                }

                // move entities
                final Map<BlockVec, List<EntityUpdateCommand>> entityMap = new HashMap<>();
                if (entityUpdatesInWorld != null) {
                    for (final EntityUpdateCommand command : entityUpdatesInWorld) {
                        if (command != null) {
                            final BlockVec entityLoc = new BlockVec(command.newLocation.getBlockX(),
                                                                    command.newLocation.getBlockY() - 1,
                                                                    command.newLocation.getBlockZ());
                            if (entityMap.containsKey(entityLoc)) {
                                final List<EntityUpdateCommand> entUpdateList = entityMap.get(entityLoc);
                                entUpdateList.add(command);
                            } else {
                                final List<EntityUpdateCommand> entUpdateList = new ArrayList<>();
                                entUpdateList.add(command);
                                entityMap.put(entityLoc, entUpdateList);
                            }
                            if (command.entity instanceof Player) {
                                // send the blocks around the player first
                                final Player p = (Player) command.entity;
                                for (final MapUpdateCommand muc : updatesInWorld) {
                                    final int disty = Math.abs(muc.newBlockLocation.y() - command.newLocation.getBlockY());

                                    final int distx = Math.abs(muc.newBlockLocation.x() - command.newLocation.getBlockX
                                            ());
                                    final int distz = Math.abs(muc.newBlockLocation.z() - command.newLocation.getBlockZ());
                                    if (disty < 2 && distx < 2 && distz < 2) {
                                        this.updateBlock(muc, entry.getKey(), dataMap, chunks, cmChunks, origLightMap,
                                                         false);
                                        final Location nloc = muc.newBlockLocation.toBukkitLocation(entry.getKey());
                                        p.sendBlockChange(nloc, muc.typeID, muc.dataID);
                                    }
                                }
                            }
                            command.entity.teleport(command.newLocation);
                        }
                    }
                }

                // Place any blocks that replace "fragiles", other than other fragiles
                for (final MapUpdateCommand i : updatesInWorld) {
                    if (i != null) {
                        if (i.typeID >= 0) {
                            final int prevType = entry.getKey()
                                                      .getBlockAt(i.newBlockLocation.x(), i.newBlockLocation.y(),
                                                            i.newBlockLocation.z()).getTypeId();
                            final boolean prevIsFragile = (Arrays.binarySearch(fragileBlocks, prevType) >= 0);
                            final boolean isFragile = (Arrays.binarySearch(fragileBlocks, i.typeID) >= 0);
                            if (prevIsFragile && (!isFragile)) {
//								if(compatibilityMode) {
                                queuedMapUpdateCommands.add(i);
                                queuedPlaceDispensers.add(false);
//								} else 
//									updateBlock(i, world, dataMap, chunks, cmChunks, origLightMap, false);
                            }
                            if (prevIsFragile && isFragile) {
                                final MapUpdateCommand blankCommand = new MapUpdateCommand(i.newBlockLocation, 0,
                                                                                           (byte) 0, i.craft);
//								if(compatibilityMode) {
                                queuedMapUpdateCommands.add(blankCommand);
                                queuedPlaceDispensers.add(false);
//								} else 
//									updateBlock(blankCommand, world, dataMap, chunks, cmChunks, origLightMap, false);
                            }
                        }
                    }
                }

                // Perform core block updates, don't do "fragiles" yet. Don't do Dispensers or air yet either
                for (final MapUpdateCommand command : updatesInWorld) {
                    if (command != null) {
                        final boolean isFragile = (Arrays.binarySearch(fragileBlocks, command.typeID) >= 0);

                        if (!isFragile) {
                            // a TypeID less than 0 indicates an explosion
                            if (command.typeID < 0) {
                                if (command.typeID < -10) { // don't bother with tiny explosions
                                    float explosionPower = command.typeID;
                                    explosionPower = 0.0F - explosionPower / 100.0F;
                                    final Location loc = new Location(entry.getKey(), command.newBlockLocation.x() + 0.5,
                                                                      command.newBlockLocation.y() + 0.5,
                                                                      command.newBlockLocation.z());
                                    this.createExplosion(loc, explosionPower);
                                    //world.createExplosion(command.getNewBlockLocation().getX()+0.5, command.getNewBlockLocation()
                                    // .getY()+0.5, command.getNewBlockLocation().getZ()+0.5, explosionPower);
                                }
                            } else {
                                //							if(compatibilityMode) {
                                queuedMapUpdateCommands.add(command);
                                queuedPlaceDispensers.add(false);
                                //							} else
                                //								updateBlock(command, world, dataMap, chunks, cmChunks,
                                // origLightMap, false);
                            }
                        }

                        // if the block you just updated had any entities on it, move them. If they are moving, add
                        // in their motion to the craft motion
                        if (entityMap.containsKey(command.newBlockLocation) && !this.compatibilityMode) {
                            final List<EntityUpdateCommand> mapUpdateList = entityMap.get(command.newBlockLocation);
                            for (final EntityUpdateCommand entityUpdate : mapUpdateList) {
                                final Entity entity = entityUpdate.entity;

                                entity.teleport(entityUpdate.newLocation);
                            }
                            entityMap.remove(command.newBlockLocation);
                        }
                    }
                }

                // Fix redstone and other "fragiles"
                for (final MapUpdateCommand command : updatesInWorld) {
                    if (command != null) {
                        final boolean isFragile = (Arrays.binarySearch(fragileBlocks, command.typeID) >= 0);
                        if (isFragile) {
                            //					if(compatibilityMode) {
                            queuedMapUpdateCommands.add(command);
                            queuedPlaceDispensers.add(false);
                            //					} else
                            //						updateBlock(command, world, dataMap, chunks, cmChunks, origLightMap, false);
                        }
                    }
                }

                for (final MapUpdateCommand command : updatesInWorld) {
                    if (command != null) {
                        // Put Dispensers back in now that the ship is reconstructed
                        if (command.typeID == 23 || command.typeID == 152) {
                            //					if(compatibilityMode) {
                            queuedMapUpdateCommands.add(command);
                            queuedPlaceDispensers.add(true);
                            //					} else
                            //						updateBlock(command, world, dataMap, chunks, cmChunks, origLightMap, true);
                        }
                    }
                }

				/*for ( MapUpdateCommand i : updatesInWorld ) {
                    if(i!=null) {
						// Place air
						if(i.getTypeID()==0) {
							if(compatibilityMode) {
								queuedMapUpdateCommands.add(i);
								queuedPlaceDispensers.add(true);
							} else 
								updateBlock(i, world, dataMap, chunks, cmChunks, origLightMap, true);
						}
						
					}
				}*/

                for (final MapUpdateCommand command : updatesInWorld) {
                    if (command != null) {
                        // Place beds
                        if (command.typeID == 26) {
                            //					if(compatibilityMode) {
                            queuedMapUpdateCommands.add(command);
                            queuedPlaceDispensers.add(true);
                            //					} else
                            //						updateBlock(command, world, dataMap, chunks, cmChunks, origLightMap, true);
                        }
                    }
                }

                for (final MapUpdateCommand command : updatesInWorld) {
                    if (command != null) {
                        // Place fragiles again, in case they got screwed up the first time
                        final boolean isFragile = (Arrays.binarySearch(fragileBlocks, command.typeID) >= 0);
                        if (isFragile) {
                            //					if(compatibilityMode) {
                            queuedMapUpdateCommands.add(command);
                            queuedPlaceDispensers.add(true);
                            //					} else
                            //						updateBlock(command, world, dataMap, chunks, cmChunks, origLightMap, true);
                        }
                    }
                }

/*				// move entities again
                if(!compatibilityMode)
					for(BlockVec i : entityMap.keySet()) {
						List<EntityUpdateCommand> mapUpdateList=entityMap.get(i);
							for(EntityUpdateCommand entityUpdate : mapUpdateList) {
								Entity entity=entityUpdate.getEntity();
								entity.teleport(entityUpdate.getNewLocation());
							}
					}*/

                // put in smoke or effects
                for (final MapUpdateCommand command : updatesInWorld) {
                    if (command != null) {
                        if (command.smoke == 1) {
                            final Location loc = command.newBlockLocation.toBukkitLocation(entry.getKey());
                            entry.getKey().playEffect(loc, Effect.SMOKE, 4);
                        }
                    }
                }

//				if(compatibilityMode) {
                final long endTime = System.currentTimeMillis();
                if (DEBUG) {
                    this.plugin.getServer().broadcastMessage(
                            String.format("Map update setup took (ms): %d", endTime - startTime));
                }
                try {
                    this.runQueue(queuedMapUpdateCommands, queuedPlaceDispensers, entry.getKey(), chunks, cmChunks,
                                  origLightMap, dataMap, updatesInWorld, entityMap);
                } catch (final Exception e) {
                    final StringWriter sw = new StringWriter();
                    final PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);
                    this.plugin.getLogger().log(Level.SEVERE, sw.toString());
                }
/*				} else {
					// update signs, inventories, other special data
					updateData(dataMap, world);
					
					for ( net.minecraft.server.v1_8_R3.Chunk c : chunks ) {
//						c.initLighting();
						ChunkCoordIntPair ccip = new ChunkCoordIntPair( c.locX, c.locZ ); // changed from c.x to c
						.locX and c.locZ

						for ( Player p : world.getPlayers() ) {
							List<ChunkCoordIntPair> chunkCoordIntPairQueue = ( List<ChunkCoordIntPair> ) ( (
							CraftPlayer ) p ).getHandle().chunkCoordIntPairQueue;
							int playerChunkX=p.getLocation().getBlockX()>>4;
							int playerChunkZ=p.getLocation().getBlockZ()>>4;
							
							// only send the chunk if the player is near enough to see it and it's not still in the
							queue, but always send the chunk if the player is standing in it
							if(playerChunkX==c.locX && playerChunkZ==c.locZ) {
								chunkCoordIntPairQueue.add( 0, ccip );
							} else {
								if(Math.abs(playerChunkX-c.locX)<Bukkit.getServer().getViewDistance())
									if(Math.abs(playerChunkZ-c.locZ)<Bukkit.getServer().getViewDistance())
										if ( !chunkCoordIntPairQueue.contains( ccip ) )
											chunkCoordIntPairQueue.add( ccip );
							}
						}
					}
					
					if(CraftManager.getInstance().getCraftsInWorld(world)!=null) {
						
						// and set all crafts that were updated to not processing
						for ( MapUpdateCommand c : updatesInWorld ) {
							if(c!=null) {
								Craft craft=c.getCraft();
								if(craft!=null) {
									if(!craft.isNotProcessing()) {
										craft.setProcessing(false);
									}
								}

							}						
						}
					}
					long endTime=System.currentTimeMillis();
					if (DEBUG) {
						Movecraft.getInstance().getServer().broadcastMessage("Map update took (ms): "+
						(endTime-startTime));
					}
				}*/

                //drop harvested yield
                if (itemDropUpdatesInWorld != null) {
                    for (final ItemDropUpdateCommand i : itemDropUpdatesInWorld) {
                        if (i != null) {
                            final World world = entry.getKey();
                            final Location loc = i.location;
                            final ItemStack stack = i.itemStack;
                            if (i.itemStack != null) {
                                // drop Item
                                final BukkitTask dropTask = new BukkitRunnable() {
                                    @Override public void run() {
                                        world.dropItemNaturally(loc, stack);
                                    }
                                }.runTaskLater(this.plugin, (20 * 1));
                            }
                        }
                    }
                }
            }
        }

        this.updates.clear();
        this.entityUpdates.clear();
        this.itemDropUpdates.clear();
    }

    public boolean addWorldUpdate(final World world, final MapUpdateCommand[] mapUpdates, final EntityUpdateCommand[] eUpdates,
                                  final ItemDropUpdateCommand[] iUpdates)
    {
        ArrayList<MapUpdateCommand> get = this.updates.get(world);
        if (get != null) {
            this.updates.remove(world);
        } else {
            get = new ArrayList<>();
        }

        Integer miny = Integer.MAX_VALUE;
        Integer maxy = Integer.MIN_VALUE;
        if (mapUpdates != null) {
            Integer minx = Integer.MAX_VALUE;
            Integer maxx = Integer.MIN_VALUE;
            Integer minz = Integer.MAX_VALUE;
            Integer maxz = Integer.MIN_VALUE;
            //final Map<BlockVec, MapUpdateCommand> sortRef = new HashMap<>();
            for (final MapUpdateCommand command : mapUpdates) {
                if (MapUpdateManager.areIntersecting(get, command)) {
                    return true;
                }
                if (command != null) {
                    if (command.newBlockLocation.x() < minx) minx = command.newBlockLocation.x();
                    if (command.newBlockLocation.y() < miny) miny = command.newBlockLocation.y();
                    if (command.newBlockLocation.z() < minz) minz = command.newBlockLocation.z();
                    if (command.newBlockLocation.x() > maxx) maxx = command.newBlockLocation.x();
                    if (command.newBlockLocation.y() > maxy) maxy = command.newBlockLocation.y();
                    if (command.newBlockLocation.z() > maxz) maxz = command.newBlockLocation.z();
                    //sortRef.put(command.newBlockLocation, command);
                }
            }
        }

        List<MapUpdateCommand> tempSet = null;
        if (mapUpdates != null) {
            tempSet = new ArrayList<>();//(Arrays.asList(mapUpdates));
            // Sort the blocks from the bottom up to minimize lower altitude block updates
            for (int posy = maxy; posy >= miny; posy--) {
                for (final MapUpdateCommand test : mapUpdates) {
                    if (test.newBlockLocation.y() == posy) {
                        tempSet.add(test);
                    }
                }
            }
        } else {
            tempSet = new ArrayList<>();
        }

        get.addAll(tempSet);
        this.updates.put(world, get);

        //now do entity updates
        if (eUpdates != null) {
            ArrayList<EntityUpdateCommand> eGet = this.entityUpdates.get(world);
            if (eGet != null) {
                this.entityUpdates.remove(world);
            } else {
                eGet = new ArrayList<>();
            }

            final List<EntityUpdateCommand> tempEUpdates = new ArrayList<>();
            tempEUpdates.addAll(Arrays.asList(eUpdates));
            eGet.addAll(tempEUpdates);
            this.entityUpdates.put(world, eGet);
        }

        //now do item drop updates
        if (iUpdates != null) {
            ArrayList<ItemDropUpdateCommand> iGet = this.itemDropUpdates.get(world);
            if (iGet != null) {
                this.entityUpdates.remove(world);
            } else {
                iGet = new ArrayList<>();
            }

            final List<ItemDropUpdateCommand> tempIDUpdates = new ArrayList<>();
            tempIDUpdates.addAll(Arrays.asList(iUpdates));
            iGet.addAll(tempIDUpdates);
            this.itemDropUpdates.put(world, iGet);
        }

        return false;
    }

    private static boolean areIntersecting(final Iterable<MapUpdateCommand> set, final MapUpdateCommand c) {
        for (final MapUpdateCommand command : set) {
            if (command != null && c != null) if (command.newBlockLocation.equals(c.newBlockLocation)) {
                return true;
            }
        }

        return false;
    }

    private TransferData getBlockDataPacket(final BlockState s, final RotationXZ r) {
        if (BlockUtils.blockHasNoData(s.getTypeId())) {
            return null;
        }

        byte data = s.getRawData();

        if (BlockUtils.blockRequiresRotation(s.getTypeId()) && !r.equals(RotationXZ.none())) {
            data = BlockUtils.rotate(data, s.getTypeId(), r);
        }

        switch (s.getTypeId()) {
            case 23:
            case 54:
            case 61:
            case 62:
            case 117:
            case 146:
            case 158:
            case 154:
                // Data and Inventory
                if (((InventoryHolder) s).getInventory().getSize() == 54) {
                    this.plugin.getLogger().log(Level.SEVERE, "ERROR: Double chest detected. This is not supported.");
                    throw new IllegalArgumentException("INVALID BLOCK");
                }
                final ItemStack[] contents = ((InventoryHolder) s).getInventory().getContents().clone();
                ((InventoryHolder) s).getInventory().clear();
                return new InventoryTransferHolder(data, contents);

            case 68:
            case 63:
                // Data and sign lines
                return new SignTransferHolder(data, ((Sign) s).getLines());

            case 33:
                return new TransferData(data);

            case 137:
                final CommandBlock cblock = (CommandBlock) s;
                return new CommandBlockTransferHolder(data, cblock.getCommand(), cblock.getName());

            default:
                return null;
        }
    }

    private void createExplosion(final Location loc, final float explosionPower) {
        loc.getWorld().createExplosion(loc.getX() + 0.5, loc.getY() + 0.5, loc.getZ() + 0.5, explosionPower);
    }
}
