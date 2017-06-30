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
import net.countercraft.movecraft.api.Direction;
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
    @Nonnull public final World w;
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

    public Craft(CraftType type, World world) {
        this.type = type;
        this.w = world;
        this.blockList = new BlockVec[1];
        this.pilotLocked = false;
        this.pilotLockedX = 0.0;
        this.pilotLockedY = 0.0;
        this.pilotLockedZ = 0.0;
        this.keepMoving = false;
    }

    public boolean isNotProcessing() {
        return !processing.get();
    }

    public void setProcessing(boolean processing) {
        this.processing.set(processing);
    }

    public BlockVec[] getBlockList() {
        synchronized (blockList) {
            return blockList.clone();
        }
    }

    public void setBlockList(BlockVec[] blockList) {
        synchronized (this.blockList) {
            this.blockList = blockList;
        }
    }

    public CraftType getType() {
        return type;
    }

    public World getW() {
        return w;
    }

    public int[][][] getHitBox() {
        return hitBox;
    }

    public void setHitBox(int[][][] hitBox) {
        this.hitBox = hitBox;
    }

    public void resetSigns(boolean resetCruise, boolean resetAscend, boolean resetDescend) {
        for (BlockVec location : blockList) {
            int blockID = w.getBlockAt(location.x, location.y, location.z).getTypeId();
            if (blockID == 63 || blockID == 68) {
                Sign s = (Sign) w.getBlockAt(location.x, location.y, location.z).getState();
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
        return minX + hitBox.length;
    }

    public int getMaxZ() {
        return minZ + hitBox[0].length;
    }

    public int getMinY() {
        int minY = 65535;
        int maxY = -65535;
        for (int[][] i1 : hitBox) {
            if (i1 != null) for (int[] i2 : i1) {
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
        for (int[][] i1 : hitBox) {
            for (int[] i2 : i1) {
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
        return minZ;
    }

    public int getMinX() {
        return minX;
    }

    public void setMinX(int minX) {
        this.minX = minX;
    }

    public void setMinZ(int minZ) {
        this.minZ = minZ;
    }

    public boolean isCraftBlock(BlockVec mloc) {

        if (mloc.x < getMinX() || mloc.x > getMaxX()) return false;
        if (mloc.z < getMinZ() || mloc.z > getMaxZ()) return false;
        if (mloc.y < getMinY() || mloc.y > getMaxY()) return false;

        for (BlockVec loc : getBlockList()) {
            if (loc.x == mloc.x && loc.y == mloc.y && loc.z == mloc.z) return true;
        }
        return false;
    }

    public boolean getCruising() {
        return cruising;
    }

    public boolean getSinking() {
        return sinking;
    }

    public void setCruiseDirection(Direction cruiseDirection) {
        this.cruiseDirection = cruiseDirection;
    }

    public Direction getCruiseDirection() {
        return cruiseDirection;
    }

    public void setCruising(boolean cruising) {
        this.cruising = cruising;
    }

    public void setSinking(boolean sinking) {
        this.sinking = sinking;
    }

    public void setLastCruiseUpdate(long update) {
        this.lastCruiseUpdate = update;
    }

    public long getLastCruiseUpdate() {
        return lastCruiseUpdate;
    }

    public void setLastBlockCheck(long update) {
        this.lastBlockCheck = update;
    }

    public long getLastBlockCheck() {
        return lastBlockCheck;
    }

    public void setLastRightClick(long update) {
        this.lastRightClick = update;
    }

    public long getLastRightClick() {
        return lastRightClick;
    }

    public void setKeepMoving(boolean keepMoving) {
        this.keepMoving = keepMoving;
    }

    public boolean getKeepMoving() {
        return keepMoving;
    }

    public int getLastDX() {
        return lastDX;
    }

    public void setLastDX(int dX) {
        this.lastDX = dX;
    }

    public int getLastDY() {
        return lastDY;
    }

    public void setLastDY(int dY) {
        this.lastDY = dY;
    }

    public int getLastDZ() {
        return lastDZ;
    }

    public void setLastDZ(int dZ) {
        this.lastDZ = dZ;
    }

    public boolean getPilotLocked() {
        return pilotLocked;
    }

    public Map<Player, Long> getMovedPlayers() {
        return movedPlayers;
    }

    public void setPilotLocked(boolean pilotLocked) {
        this.pilotLocked = pilotLocked;
    }

    public double getPilotLockedX() {
        return pilotLockedX;
    }

    public void setPilotLockedX(double pilotLockedX) {
        this.pilotLockedX = pilotLockedX;
    }

    public double getPilotLockedY() {
        return pilotLockedY;
    }

    public void setPilotLockedY(double pilotLockedY) {
        this.pilotLockedY = pilotLockedY;
    }

    public double getPilotLockedZ() {
        return pilotLockedZ;
    }

    public void setPilotLockedZ(double pilotLockedZ) {
        this.pilotLockedZ = pilotLockedZ;
    }

    public void setBurningFuel(double burningFuel) {
        this.burningFuel = burningFuel;
    }

    public double getBurningFuel() {
        return burningFuel;
    }

    public void setOrigBlockCount(int origBlockCount) {
        this.origBlockCount = origBlockCount;
    }

    public int getOrigBlockCount() {
        return origBlockCount;
    }

    public void setNotificationPlayer(Player notificationPlayer) {
        this.notificationPlayer = notificationPlayer;
    }

    public Player getNotificationPlayer() {
        return notificationPlayer;
    }
}
