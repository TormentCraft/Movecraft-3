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
import com.alexknvl.shipcraft.math.Direction;
import com.google.common.base.Preconditions;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Craft implements net.countercraft.movecraft.api.Craft {
    private int[][][] hitBox;
    @Nonnull public final CraftType type;
    private BlockVec[] blockList;
    @Nonnull public final World world;
    private final AtomicBoolean processing = new AtomicBoolean();
    private int minX;
    private int minZ;
    private boolean cruising;
    private boolean sinking;
    @Nonnull private Direction cruiseDirection;
    private long lastCruiseUpdate;
    private long lastBlockCheck;
    private long lastRightClick;
    private int lastDX, lastDY, lastDZ;
    private boolean keepMoving;
    private double burningFuel;
    private boolean pilotLocked;
    private double pilotLockedX;
    private double pilotLockedY;
    private int origBlockCount;
    private double pilotLockedZ;
    private Player notificationPlayer;
    private final Map<Player, Long> movedPlayers = new HashMap<>();

    public Craft(final CraftType type, final World world) {
        this.type = type;
        this.world = world;
        this.blockList = new BlockVec[1];
        this.pilotLocked = false;
        this.pilotLockedX = 0.0;
        this.pilotLockedY = 0.0;
        this.pilotLockedZ = 0.0;
        this.keepMoving = false;
        this.cruiseDirection = Direction.Off();
    }

    public boolean isNotProcessing() {
        return !this.processing.get();
    }

    public void setProcessing(final boolean processing) {
        this.processing.set(processing);
    }

    public BlockVec[] getBlockList() {
        synchronized (this.blockList) {
            return this.blockList.clone();
        }
    }

    public void setBlockList(final BlockVec[] blockList) {
        synchronized (this.blockList) {
            this.blockList = blockList;
        }
    }

    public CraftType getType() {
        return this.type;
    }

    public World getWorld() {
        return this.world;
    }

    public int[][][] getHitBox() {
        return this.hitBox;
    }

    public void setHitBox(final int[][][] hitBox) {
        this.hitBox = hitBox;
    }

    public void resetSigns(final boolean resetCruise, final boolean resetAscend, final boolean resetDescend) {
        for (final BlockVec location : this.blockList) {
            final int blockID = this.world.getBlockAt(location.x(), location.y(), location.z()).getTypeId();
            if (blockID == 63 || blockID == 68) {
                final Sign s = (Sign) this.world.getBlockAt(location.x(), location.y(), location.z()).getState();
                if (resetCruise) if (ChatColor.stripColor(s.getLine(0)).equals("Cruise: ON")) {
                    s.setLine(0, "Cruise: OFF");
                    s.update(true);
                }
                if (resetAscend) if (ChatColor.stripColor(s.getLine(0)).equals("Ascend: ON")) {
                    s.setLine(0, "Ascend: OFF");
                    s.update(true);
                }
                if (resetDescend) if (ChatColor.stripColor(s.getLine(0)).equals("Descend: ON")) {
                    s.setLine(0, "Descend: OFF");
                    s.update(true);
                }
            }
        }
    }

    public int getMaxX() {
        return this.minX + this.hitBox.length;
    }

    public int getMaxZ() {
        return this.minZ + this.hitBox[0].length;
    }

    public int getMinY() {
        int minY = 65535;
        int maxY = -65535;
        for (final int[][] i1 : this.hitBox) {
            if (i1 != null) for (final int[] i2 : i1) {
                if (i2 != null) {
                    if (i2[0] < minY) {
                        minY = i2[0];
                    }
                    if (i2[1] > maxY) {
                        maxY = i2[1];
                    }
                }
            }
        }
        return minY;
    }

    public int getMaxY() {
        int minY = 65535;
        int maxY = -65535;
        for (final int[][] i1 : this.hitBox) {
            for (final int[] i2 : i1) {
                if (i2 != null) {
                    if (i2[0] < minY) {
                        minY = i2[0];
                    }
                    if (i2[1] > maxY) {
                        maxY = i2[1];
                    }
                }
            }
        }
        return maxY;
    }

    public int getMinZ() {
        return this.minZ;
    }

    public int getMinX() {
        return this.minX;
    }

    public void setMinX(final int minX) {
        this.minX = minX;
    }

    public void setMinZ(final int minZ) {
        this.minZ = minZ;
    }

    public boolean isCraftBlock(final BlockVec mloc) {

        if (mloc.x() < getMinX() || mloc.x() > getMaxX()) return false;
        if (mloc.z() < getMinZ() || mloc.z() > getMaxZ()) return false;
        if (mloc.y() < getMinY() || mloc.y() > getMaxY()) return false;

        for (final BlockVec loc : getBlockList()) {
            if (loc.equals(mloc)) return true;
        }
        return false;
    }

    public boolean getCruising() {
        return this.cruising;
    }

    public boolean getSinking() {
        return this.sinking;
    }

    public void setCruiseDirection(final Direction cruiseDirection) {
        Preconditions.checkNotNull(cruiseDirection);
        this.cruiseDirection = cruiseDirection;
    }

    public Direction getCruiseDirection() {
        return this.cruiseDirection;
    }

    public void setCruising(final boolean cruising) {
        this.cruising = cruising;
    }

    public void setSinking(final boolean sinking) {
        this.sinking = sinking;
    }

    public void setLastCruiseUpdate(final long update) {
        this.lastCruiseUpdate = update;
    }

    public long getLastCruiseUpdate() {
        return this.lastCruiseUpdate;
    }

    public void setLastBlockCheck(final long update) {
        this.lastBlockCheck = update;
    }

    public long getLastBlockCheck() {
        return this.lastBlockCheck;
    }

    public void setLastRightClick(final long update) {
        this.lastRightClick = update;
    }

    public long getLastRightClick() {
        return this.lastRightClick;
    }

    public void setKeepMoving(final boolean keepMoving) {
        this.keepMoving = keepMoving;
    }

    public boolean getKeepMoving() {
        return this.keepMoving;
    }

    public int getLastDX() {
        return this.lastDX;
    }

    public void setLastDX(final int dX) {
        this.lastDX = dX;
    }

    public int getLastDY() {
        return this.lastDY;
    }

    public void setLastDY(final int dY) {
        this.lastDY = dY;
    }

    public int getLastDZ() {
        return this.lastDZ;
    }

    public void setLastDZ(final int dZ) {
        this.lastDZ = dZ;
    }

    public boolean getPilotLocked() {
        return this.pilotLocked;
    }

    public Map<Player, Long> getMovedPlayers() {
        return this.movedPlayers;
    }

    public void setPilotLocked(final boolean pilotLocked) {
        this.pilotLocked = pilotLocked;
    }

    public double getPilotLockedX() {
        return this.pilotLockedX;
    }

    public void setPilotLockedX(final double pilotLockedX) {
        this.pilotLockedX = pilotLockedX;
    }

    public double getPilotLockedY() {
        return this.pilotLockedY;
    }

    public void setPilotLockedY(final double pilotLockedY) {
        this.pilotLockedY = pilotLockedY;
    }

    public double getPilotLockedZ() {
        return this.pilotLockedZ;
    }

    public void setPilotLockedZ(final double pilotLockedZ) {
        this.pilotLockedZ = pilotLockedZ;
    }

    public void setBurningFuel(final double burningFuel) {
        this.burningFuel = burningFuel;
    }

    public double getBurningFuel() {
        return this.burningFuel;
    }

    public void setOrigBlockCount(final int origBlockCount) {
        this.origBlockCount = origBlockCount;
    }

    public int getOrigBlockCount() {
        return this.origBlockCount;
    }

    public void setNotificationPlayer(final Player notificationPlayer) {
        this.notificationPlayer = notificationPlayer;
    }

    public Player getNotificationPlayer() {
        return this.notificationPlayer;
    }
}
