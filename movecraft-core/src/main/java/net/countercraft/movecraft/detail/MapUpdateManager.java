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
import com.google.common.collect.Sets;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.SignBlock;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.utils.BlockUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

import static org.bukkit.Material.*;

public final class MapUpdateManager extends BukkitRunnable {
    private static final boolean DEBUG = true;
    private static final Logger LOGGER = Logger.getLogger(MapUpdateManager.class.getSimpleName());

    private final Map<World, ArrayList<MapUpdateCommand.MoveBlock>> updates = new HashMap<>();
    private final Map<World, ArrayList<MapUpdateCommand.MoveEntity>> entityUpdates = new HashMap<>();
    private final Map<World, ArrayList<MapUpdateCommand.DropItem>> itemDropUpdates = new HashMap<>();

    private final Plugin plugin;
    private final boolean compatibilityMode;
    private final int queueChunkSize;

    private static final Set<Material> LIGHT_SOURCES = Sets.immutableEnumSet(
            TORCH, GLOWSTONE, SEA_LANTERN, REDSTONE_LAMP_ON);
    private static final Set<Material> BLOCKS_TO_BLANK_OUT = Sets.immutableEnumSet(
            CHEST, FURNACE, BURNING_FURNACE, SIGN_POST, WALL_SIGN, ENCHANTMENT_TABLE, BREWING_STAND,
            ANVIL, TRAPPED_CHEST, REDSTONE_COMPARATOR_OFF, REDSTONE_COMPARATOR_ON, HOPPER, DROPPER);


    public MapUpdateManager(final Plugin plugin, final boolean compatibilityMode, final int queueChunkSize) {
        this.plugin = plugin;
        this.compatibilityMode = compatibilityMode;
        this.queueChunkSize = queueChunkSize;
    }

    private void updateBlock(final MapUpdateCommand.MoveBlock command, final World world,
                             final Map<BlockVec, TransferData> dataMap,
                             final Set<net.minecraft.server.v1_12_R1.Chunk> nativeChunks,
                             final Set<Chunk> cmChunks,
                             final HashMap<BlockVec, Byte> origLightMap,
                             final boolean placeDispensers)
    {
        Material newTypeID = command.data.getItemType();

        if ((newTypeID == Material.REDSTONE_BLOCK || newTypeID == Material.BED_BLOCK) && !placeDispensers) {
            return;
        }

        final BlockVec vec = command.newBlockLocation;
        final int x = vec.x();
        final int y = vec.y();
        final int z = vec.z();

        final Block block = world.getBlockAt(x, y, z);
        final Chunk chunk = block.getChunk();
        final net.minecraft.server.v1_12_R1.Chunk nativeChunk =
                this.compatibilityMode ? null : ((CraftChunk) chunk).getHandle();

        byte data = command.data.getData();

        if (newTypeID == Material.DISPENSER && !placeDispensers) {
            newTypeID = Material.STEP;
            data = 8;
        }

        final Material origType = block.getType();
        final byte origData = block.getData();

        if (this.compatibilityMode) {
            if (origType != newTypeID || origData != data) {
                final boolean doBlankOut = BLOCKS_TO_BLANK_OUT.contains(newTypeID);
                if (doBlankOut) {
                    block.setType(org.bukkit.Material.AIR);
                }

                if (origType == Material.REDSTONE_COMPARATOR_OFF || origType == Material.REDSTONE_COMPARATOR_ON) {
                    // Necessary because bukkit does not handle comparators correctly.
                    // This code does not prevent console spam, but it does prevent chunk corruption.
                    block.setType(org.bukkit.Material.SIGN_POST);
                    final BlockState state = block.getState();
                    if (state instanceof Sign) {
                        // For some bizarre reason the block is sometimes not a sign,
                        // which crashes unless I do this.
                        final Sign sign = (Sign) state;
                        sign.setLine(0, "PLACEHOLDER");
                        // sign.update();
                    }
                    block.setType(org.bukkit.Material.AIR);
                }

                if ((newTypeID == Material.REDSTONE_COMPARATOR_OFF || newTypeID == Material.REDSTONE_COMPARATOR_ON) && command.worldEditBaseBlock == null) {
                    //noinspection deprecation
                    block.setTypeIdAndData(newTypeID.getId(), data, false);
                } else {
                    if (command.worldEditBaseBlock == null) {
                        //noinspection deprecation
                        block.setTypeIdAndData(newTypeID.getId(), data, false);
                    } else {
                        //noinspection deprecation
                        block.setTypeIdAndData(((BaseBlock) command.worldEditBaseBlock).getType(),
                                                                   (byte) ((BaseBlock) command.worldEditBaseBlock).getData(),
                                                                   false);
                        if (command.worldEditBaseBlock instanceof SignBlock) {
                            final Sign sign = (Sign) block.getState();
                            for (int i = 0; i < ((SignBlock) command.worldEditBaseBlock).getText().length; i++) {
                                sign.setLine(i, ((SignBlock) command.worldEditBaseBlock).getText()[i]);
                            }
                            sign.update();
                        }
                    }
                }
            }
            if (!cmChunks.contains(chunk)) {
                cmChunks.add(chunk);
            }
        } else {
            final net.minecraft.server.v1_12_R1.BlockPosition position = new net.minecraft.server.v1_12_R1.BlockPosition(x, y, z);

            boolean success = false;
            if ((origType == REDSTONE_COMPARATOR_OFF || origType == REDSTONE_COMPARATOR_ON) && command.worldEditBaseBlock == null) {
                // bukkit can't remove comparators safely, it screws
                // up the NBT data. So turn it to a sign, then remove it.
                nativeChunk.a(position, CraftMagicNumbers.getBlock(org.bukkit.Material.AIR).fromLegacyData(0));
                nativeChunk.a(position, CraftMagicNumbers.getBlock(org.bukkit.Material.SIGN_POST).fromLegacyData(0));

                final BlockState state = block.getState();
                final Sign s = (Sign) state;
                s.setLine(0, "PLACEHOLDER");
                s.update();
                nativeChunk.a(position, CraftMagicNumbers.getBlock(org.bukkit.Material.SIGN_POST).fromLegacyData(0));
                success = nativeChunk.a(position, CraftMagicNumbers.getBlock(newTypeID).fromLegacyData(data)) != null;
                if (!success) {
                    //noinspection deprecation
                    block.setTypeIdAndData(newTypeID.getId(), data, false);
                }
                if (!nativeChunks.contains(nativeChunk)) {
                    nativeChunks.add(nativeChunk);
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
                    final boolean doBlankOut = BLOCKS_TO_BLANK_OUT.contains(newTypeID);
                    if (doBlankOut) {
                        nativeChunk.a(position, CraftMagicNumbers.getBlock(0).fromLegacyData(0));
                        block.setType(org.bukkit.Material.AIR);
                    }



                    if (LIGHT_SOURCES.contains(newTypeID)) {
                        // Don't use native code for lights.
                        //noinspection deprecation
                        block.setTypeIdAndData(newTypeID.getId(), data, false);
                    } else {
                        if (command.worldEditBaseBlock == null) {
                            success = nativeChunk.a(position, CraftMagicNumbers.getBlock(newTypeID).fromLegacyData(data)) != null;
                        } else {
                            success = nativeChunk.a(position, CraftMagicNumbers.getBlock(newTypeID).fromLegacyData(data)) != null;
                            if (command.worldEditBaseBlock instanceof SignBlock) {
                                final BlockState state = block.getState();
                                final Sign s = (Sign) state;
                                for (int i = 0; i < ((SignBlock) command.worldEditBaseBlock).getText().length; i++) {
                                    s.setLine(i, ((SignBlock) command.worldEditBaseBlock).getText()[i]);
                                }
                                s.update();
                            }
                        }
                    }
                } else {
                    success = true;
                }
                if (!success) {
                    if (command.worldEditBaseBlock == null) {
                        //noinspection deprecation
                        block.setTypeIdAndData(newTypeID.getId(), data, false);
                    } else {
                        block.setTypeIdAndData(((BaseBlock) command.worldEditBaseBlock).getType(),
                                                                   (byte) ((BaseBlock) command.worldEditBaseBlock).getData(),
                                                                   false);
                        if (command.worldEditBaseBlock instanceof SignBlock) {
                            final BlockState state = block.getState();
                            final Sign sign = (Sign) state;
                            for (int i = 0; i < ((SignBlock) command.worldEditBaseBlock).getText().length; i++) {
                                sign.setLine(i, ((SignBlock) command.worldEditBaseBlock).getText()[i]);
                            }
                            sign.update();
                        }
                    }
                }

                if (!nativeChunks.contains(nativeChunk)) {
                    nativeChunks.add(nativeChunk);
                }
            }
        }
    }

    private void updateData(final Map<BlockVec, TransferData> dataMap, final World wo) {
        // Restore block specific information
        for (final Map.Entry<BlockVec, TransferData> entry : dataMap.entrySet()) {
            try {
                final TransferData transferData = entry.getValue();

                if (transferData instanceof SignTransferHolder) {
                    final SignTransferHolder signData = (SignTransferHolder) transferData;
                    final BlockState bs = wo.getBlockAt(entry.getKey().x(), entry.getKey().y(), entry.getKey().z()).getState();
                    if (bs instanceof Sign) {
                        final Sign sign = (Sign) bs;
                        for (int i = 0; i < signData.lines.length; i++) {
                            sign.setLine(i, signData.lines[i]);
                        }

                        for (final Player p : wo.getPlayers()) {
                            // This is necessary because signs do not get updated client side
                            // correctly without refreshing the chunks, which causes a memory
                            // leak in the clients.
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
                    final InventoryHolder inventoryHolder = (InventoryHolder) wo
                            .getBlockAt(entry.getKey().x(), entry.getKey().y(), entry.getKey().z()).getState();
                    inventoryHolder.getInventory().setContents(invData.inventory);
                } else if (transferData instanceof CommandBlockTransferHolder) {
                    final CommandBlockTransferHolder cbData = (CommandBlockTransferHolder) transferData;
                    final CommandBlock cblock = (CommandBlock) wo
                            .getBlockAt(entry.getKey().x(), entry.getKey().y(), entry.getKey().z()).getState();
                    cblock.setCommand(cbData.commandText);
                    cblock.setName(cbData.commandName);
                    cblock.update();
                }
                wo.getBlockAt(entry.getKey().x(), entry.getKey().y(), entry.getKey().z()).setData(transferData.data);
            } catch (final IndexOutOfBoundsException | IllegalArgumentException e) {
                LOGGER.log(Level.SEVERE, "Severe error in map updater");
            }
        }
    }

    private void runQueue(final ArrayList<MapUpdateCommand.MoveBlock> queuedMapUpdateCommands,
                          final ArrayList<Boolean> queuedPlaceDispensers, final World world,
                          final Set<net.minecraft.server.v1_12_R1.Chunk> chunks, final Set<Chunk> cmChunks,
                          final HashMap<BlockVec, Byte> origLightMap, final Map<BlockVec, TransferData> dataMap,
                          final List<MapUpdateCommand.MoveBlock> updatesInWorld,
                          final Map<BlockVec, List<MapUpdateCommand.MoveEntity>> entityMap)
    {
        int numToRun = queuedMapUpdateCommands.size();
        if (numToRun > this.queueChunkSize) numToRun = this.queueChunkSize;

        final long start = System.currentTimeMillis();
        for (int i = 0; i < numToRun; i++) {
            final MapUpdateCommand.MoveBlock command = queuedMapUpdateCommands.get(0);
            this.updateBlock(command, world, dataMap, chunks, cmChunks, origLightMap, queuedPlaceDispensers.get(0));
            queuedMapUpdateCommands.remove(0);
            queuedPlaceDispensers.remove(0);
        }
        final long end = System.currentTimeMillis();

        if (!queuedMapUpdateCommands.isEmpty()) {
            final BukkitTask nextQueueRun = new BukkitRunnable() {
                @Override public void run() {
                    try {
                        MapUpdateManager.this
                                .runQueue(queuedMapUpdateCommands, queuedPlaceDispensers, world, chunks, cmChunks, origLightMap,
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
            this.updateData(dataMap, world);

            // and set all crafts that were updated to not processing
            for (final MapUpdateCommand.MoveBlock command : updatesInWorld) {
                if (command != null) {
                    final Craft craft = command.craft;
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
                // Send updates to client.
                for (final MapUpdateCommand.MoveBlock command : updatesInWorld) {
                    final Location loc = command.newBlockLocation.toBukkitLocation(world);
                    world.getBlockAt(loc).getState().update();
                }

                // for (net.minecraft.server.v1_8_R3.Chunk c : chunks) {
                //    c.initLighting();
                // }

                /*for(BlockVec mloc : origLightMap.keySet()) {
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

			
			// move all players one final time
            /*for(List<EntityUpdateCommand> listE : entityMap.values()) {
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

        for (final Map.Entry<World, ArrayList<MapUpdateCommand.MoveBlock>> entry : this.updates.entrySet()) {
            final World world = entry.getKey();
            if (world != null) {
                final List<MapUpdateCommand.MoveBlock> updatesInWorld = entry.getValue();
                final List<MapUpdateCommand.MoveEntity> entityUpdatesInWorld = this.entityUpdates.get(world);
                final List<MapUpdateCommand.DropItem> itemDropUpdatesInWorld = this.itemDropUpdates.get(world);
                final Map<BlockVec, List<MapUpdateCommand.DropItem>> itemMap = new HashMap<>();

                Set<net.minecraft.server.v1_12_R1.Chunk> nativeChunks = null;
                Set<Chunk> chunks = null;

                if (this.compatibilityMode) {
                    chunks = new HashSet<>();
                } else {
                    nativeChunks = new HashSet<>();
                }

                // Preprocessing
                final Map<BlockVec, TransferData> dataMap = new HashMap<>();
                final HashMap<BlockVec, Byte> origLightMap = new HashMap<>();
                final ArrayList<MapUpdateCommand.MoveBlock> queuedMapUpdateCommands = new ArrayList<>();
                final ArrayList<Boolean> queuedPlaceDispensers = new ArrayList<>();
                for (final MapUpdateCommand.MoveBlock command : updatesInWorld) {
                    final BlockVec vec = command.blockLocation;

                    if (vec != null) {
                        // keep track of the light levels that were present before moving the craft
                        origLightMap.put(vec, world.getBlockAt(vec.x(), vec.y(), vec.z()).getLightLevel());

                        // keep track of block data for later reconstruction
                        final TransferData blockDataPacket = this.getBlockDataPacket(
                                world.getBlockAt(vec.x(), vec.y(), vec.z()).getState(), command.rotation);
                        if (blockDataPacket != null) {
                            dataMap.put(command.newBlockLocation, blockDataPacket);
                        }

                        // Remove dispensers and replace them with half slabs to prevent them firing
                        // during reconstruction.
                        if (world.getBlockAt(vec.x(), vec.y(), vec.z()).getType() == Material.DISPENSER) {
                            final MapUpdateCommand.MoveBlock blankCommand =
                                    new MapUpdateCommand.MoveBlock(command.blockLocation, Material.DISPENSER, command.data.getData(), command.craft);
                            // if(compatibilityMode) {
                            queuedMapUpdateCommands.add(blankCommand);
                            queuedPlaceDispensers.add(false);
                            // } else updateBlock(blankCommand, world, dataMap, nativeChunks, chunks, origLightMap, false);
                        }

                        // Remove redstone blocks and replace them with stone to prevent redstone activation
                        // during reconstruction.
                        if (world.getBlockAt(vec.x(), vec.y(), vec.z()).getType() == Material.REDSTONE_BLOCK) {
                            final MapUpdateCommand.MoveBlock blankCommand =
                                    new MapUpdateCommand.MoveBlock(command.blockLocation, Material.STONE, command.craft);
                            // if(compatibilityMode) {
                            queuedMapUpdateCommands.add(blankCommand);
                            queuedPlaceDispensers.add(false);
                            // } else updateBlock(blankCommand, world, dataMap, nativeChunks, chunks, origLightMap, false);
                        }

                        // Remove water and lava blocks and replace them with stone to prevent spillage
                        // during reconstruction.
                        if (world.getBlockAt(vec.x(), vec.y(), vec.z()).getTypeId() >= 8 &&
                            world.getBlockAt(vec.x(), vec.y(), vec.z()).getTypeId() <= 11) {
                            final MapUpdateCommand.MoveBlock blankCommand =
                                    new MapUpdateCommand.MoveBlock(command.blockLocation, Material.AIR, command.craft);
                            this.updateBlock(blankCommand, world, dataMap, nativeChunks, chunks, origLightMap, false);
                        }
                    }
                }

                // move entities
                final Map<BlockVec, List<MapUpdateCommand.MoveEntity>> entityMap = new HashMap<>();
                if (entityUpdatesInWorld != null) {
                    for (final MapUpdateCommand.MoveEntity command : entityUpdatesInWorld) {
                        if (command != null) {
                            final Location newLocation = command.newLocation;
                            final BlockVec entityLoc = new BlockVec(newLocation.getBlockX(),
                                                                    newLocation.getBlockY() - 1,
                                                                    newLocation.getBlockZ());
                            if (entityMap.containsKey(entityLoc)) {
                                final List<MapUpdateCommand.MoveEntity> entUpdateList = entityMap.get(entityLoc);
                                entUpdateList.add(command);
                            } else {
                                final List<MapUpdateCommand.MoveEntity> entUpdateList = new ArrayList<>();
                                entUpdateList.add(command);
                                entityMap.put(entityLoc, entUpdateList);
                            }
                            if (command.entity instanceof Player) {
                                // send the blocks around the player first
                                final Player p = (Player) command.entity;
                                for (final MapUpdateCommand.MoveBlock muc : updatesInWorld) {
                                    final int dy = Math.abs(muc.newBlockLocation.y() - newLocation.getBlockY());
                                    final int dx = Math.abs(muc.newBlockLocation.x() - newLocation.getBlockX());
                                    final int dz = Math.abs(muc.newBlockLocation.z() - newLocation.getBlockZ());
                                    if (dy < 2 && dx < 2 && dz < 2) {
                                        this.updateBlock(muc, world, dataMap, nativeChunks, chunks, origLightMap,
                                                         false);
                                        final Location nloc = muc.newBlockLocation.toBukkitLocation(world);
                                        p.sendBlockChange(nloc, muc.data.getItemType(), muc.data.getData());
                                    }
                                }
                            }
                            command.entity.teleport(newLocation);
                        }
                    }
                }

                // Place any blocks that replace "fragiles", other than other fragiles
                for (final MapUpdateCommand.MoveBlock command : updatesInWorld) {
                    if (true) { // command.typeID >= 0
                        final Material prevType =
                                world.getBlockAt(command.newBlockLocation.x(), command.newBlockLocation.y(),
                                                 command.newBlockLocation.z()).getType();
                        final boolean prevIsFragile = BlockUtils.FRAGILE_BLOCKS.contains(prevType);
                        final boolean isFragile = BlockUtils.FRAGILE_BLOCKS.contains(command.data.getItemType());

                        if (prevIsFragile && (!isFragile)) {
                            // if(compatibilityMode) {
                            queuedMapUpdateCommands.add(command);
                            queuedPlaceDispensers.add(false);
                            // } else updateBlock(command, world, dataMap, nativeChunks, chunks, origLightMap, false);
                        }
                        if (prevIsFragile && isFragile) {
                            final MapUpdateCommand.MoveBlock blankCommand = new MapUpdateCommand.MoveBlock(command.newBlockLocation, Material.AIR, command.craft);
                            // if(compatibilityMode) {
                            queuedMapUpdateCommands.add(blankCommand);
                            queuedPlaceDispensers.add(false);
                            // } else updateBlock(blankCommand, world, dataMap, nativeChunks, chunks, origLightMap, false);
                        }
                    }
                }

                // Perform core block updates, don't do "fragiles" yet. Don't do Dispensers or air yet either
                for (final MapUpdateCommand.MoveBlock command : updatesInWorld) {
                    final boolean isFragile = BlockUtils.FRAGILE_BLOCKS.contains(command.data.getItemType());

                    if (!isFragile) {
                        // a TypeID less than 0 indicates an explosion
                        if (false) {
                            /*if (command.typeID < -10) { // don't bother with tiny explosions
                                float explosionPower = command.typeID;
                                explosionPower = 0.0F - explosionPower / 100.0F;
                                final Location loc = new Location(world, command.newBlockLocation.x() + 0.5,
                                                                  command.newBlockLocation.y() + 0.5,
                                                                  command.newBlockLocation.z());
                                this.createExplosion(loc, explosionPower);
                            }*/
                        } else {
                            //							if(compatibilityMode) {
                            queuedMapUpdateCommands.add(command);
                            queuedPlaceDispensers.add(false);
                            //							} else
                            //								updateBlock(command, world, dataMap, nativeChunks, chunks,
                            // origLightMap, false);
                        }
                    }

                    // If the block you just updated had any entities on it, move them.
                    // If they are moving, add in their motion to the craft motion.
                    if (entityMap.containsKey(command.newBlockLocation) && !this.compatibilityMode) {
                        final List<MapUpdateCommand.MoveEntity> mapUpdateList = entityMap.get(command.newBlockLocation);
                        for (final MapUpdateCommand.MoveEntity entityUpdate : mapUpdateList) {
                            final Entity entity = entityUpdate.entity;

                            entity.teleport(entityUpdate.newLocation);
                        }
                        entityMap.remove(command.newBlockLocation);
                    }
                }

                // Fix redstone and other "fragiles"
                for (final MapUpdateCommand.MoveBlock command : updatesInWorld) {
                    final boolean isFragile = BlockUtils.FRAGILE_BLOCKS.contains(command.data.getItemType());
                    if (isFragile) {
                        // if(compatibilityMode) {
                        queuedMapUpdateCommands.add(command);
                        queuedPlaceDispensers.add(false);
                        // } else updateBlock(command, world, dataMap, nativeChunks, chunks, origLightMap, false);
                    }
                }

                for (final MapUpdateCommand.MoveBlock command : updatesInWorld) {
                    // Put Dispensers back in now that the ship is reconstructed
                    final Material type = command.data.getItemType();
                    if (type == Material.DISPENSER || type == Material.REDSTONE_BLOCK) {
                        // if(compatibilityMode) {
                        queuedMapUpdateCommands.add(command);
                        queuedPlaceDispensers.add(true);
                        // } else updateBlock(command, world, dataMap, nativeChunks, chunks, origLightMap, true);
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
								updateBlock(i, world, dataMap, nativeChunks, chunks, origLightMap, true);
						}
						
					}
				}*/

                for (final MapUpdateCommand.MoveBlock command : updatesInWorld) {
                    // Place beds
                    if (command.data.getItemType() == Material.BED_BLOCK) {
                        // if(compatibilityMode) {
                        queuedMapUpdateCommands.add(command);
                        queuedPlaceDispensers.add(true);
                        // } else updateBlock(command, world, dataMap, nativeChunks, chunks, origLightMap, true);
                    }
                }

                for (final MapUpdateCommand.MoveBlock command : updatesInWorld) {
                    // Place fragiles again, in case they got screwed up the first time
                    final boolean isFragile = BlockUtils.FRAGILE_BLOCKS.contains(command.data.getItemType());
                    if (isFragile) {
                        // if(compatibilityMode) {
                        queuedMapUpdateCommands.add(command);
                        queuedPlaceDispensers.add(true);
                        // } else updateBlock(command, world, dataMap, nativeChunks, chunks, origLightMap, true);
                    }
                }

				// move entities again
                /*if(!compatibilityMode)
                    for(BlockVec i : entityMap.keySet()) {
                        List<EntityUpdateCommand> mapUpdateList=entityMap.get(i);
                            for(EntityUpdateCommand entityUpdate : mapUpdateList) {
                                Entity entity=entityUpdate.getEntity();
                                entity.teleport(entityUpdate.getNewLocation());
                            }
                    }*/

                // put in smoke or effects
                /*for (final MapUpdateCommand.MoveBlock command : updatesInWorld) {
                    if (command != null) {
                        if (command.smoke == 1) {
                            final Location loc = command.newBlockLocation.toBukkitLocation(world);
                            world.playEffect(loc, Effect.SMOKE, 4);
                        }
                    }
                }*/

                // if(compatibilityMode) {
                final long endTime = System.currentTimeMillis();
                if (DEBUG) {
                    this.plugin.getServer().broadcastMessage(
                            String.format("Map update setup took (ms): %d", endTime - startTime));
                }
                try {
                    this.runQueue(queuedMapUpdateCommands, queuedPlaceDispensers, world, nativeChunks, chunks,
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
					
					for ( net.minecraft.server.v1_8_R3.Chunk c : nativeChunks ) {
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
                    for (final MapUpdateCommand.DropItem i : itemDropUpdatesInWorld) {
                        if (i != null) {
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

    public boolean addWorldUpdate(final World world,
                                  final MapUpdateCommand.MoveBlock[] mapUpdates,
                                  final MapUpdateCommand.MoveEntity[] eUpdates,
                                  final MapUpdateCommand.DropItem[] iUpdates)
    {
        ArrayList<MapUpdateCommand.MoveBlock> get = this.updates.get(world);
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
            for (final MapUpdateCommand.MoveBlock command : mapUpdates) {
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

        List<MapUpdateCommand.MoveBlock> tempSet = null;
        if (mapUpdates != null) {
            tempSet = new ArrayList<>();//(Arrays.asList(mapUpdates));
            // Sort the blocks from the bottom up to minimize lower altitude block updates
            for (int posy = maxy; posy >= miny; posy--) {
                for (final MapUpdateCommand.MoveBlock test : mapUpdates) {
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
            ArrayList<MapUpdateCommand.MoveEntity> eGet = this.entityUpdates.get(world);
            if (eGet != null) {
                this.entityUpdates.remove(world);
            } else {
                eGet = new ArrayList<>();
            }

            final List<MapUpdateCommand.MoveEntity> tempEUpdates = new ArrayList<>();
            tempEUpdates.addAll(Arrays.asList(eUpdates));
            eGet.addAll(tempEUpdates);
            this.entityUpdates.put(world, eGet);
        }

        //now do item drop updates
        if (iUpdates != null) {
            ArrayList<MapUpdateCommand.DropItem> iGet = this.itemDropUpdates.get(world);
            if (iGet != null) {
                this.entityUpdates.remove(world);
            } else {
                iGet = new ArrayList<>();
            }

            final List<MapUpdateCommand.DropItem> tempIDUpdates = new ArrayList<>();
            tempIDUpdates.addAll(Arrays.asList(iUpdates));
            iGet.addAll(tempIDUpdates);
            this.itemDropUpdates.put(world, iGet);
        }

        return false;
    }

    private static boolean areIntersecting(final Iterable<MapUpdateCommand.MoveBlock> set, final MapUpdateCommand.MoveBlock c) {
        for (final MapUpdateCommand.MoveBlock command : set) {
            if (command != null && c != null) if (command.newBlockLocation.equals(c.newBlockLocation)) {
                return true;
            }
        }

        return false;
    }

    private TransferData getBlockDataPacket(final BlockState state, final RotationXZ rotation) {
        if (BlockUtils.blockHasNoData(state.getType())) {
            return null;
        }

        byte data = state.getRawData();

        if (BlockUtils.blockRequiresRotation(state.getTypeId()) && !rotation.equals(RotationXZ.none())) {
            data = BlockUtils.rotate(data, state.getTypeId(), rotation);
        }

        switch (state.getType()) {
            case DISPENSER:
            case CHEST:
            case FURNACE:
            case BURNING_FURNACE:
            case BREWING_STAND:
            case TRAPPED_CHEST:
            case HOPPER:
            case DROPPER:
                final ItemStack[] contents = ((InventoryHolder) state).getInventory().getContents().clone();
                ((InventoryHolder) state).getInventory().clear();
                return new InventoryTransferHolder(data, contents);

            case SIGN_POST:
            case WALL_SIGN:
                // Data and sign lines
                return new SignTransferHolder(data, ((Sign) state).getLines());

            case PISTON_BASE:
                return new TransferData(data);

            case COMMAND:
                final CommandBlock cblock = (CommandBlock) state;
                return new CommandBlockTransferHolder(data, cblock.getCommand(), cblock.getName());

            default:
                return null;
        }
    }

    private void createExplosion(final Location loc, final float explosionPower) {
        loc.getWorld().createExplosion(loc.getX() + 0.5, loc.getY() + 0.5, loc.getZ() + 0.5, explosionPower);
    }
}
