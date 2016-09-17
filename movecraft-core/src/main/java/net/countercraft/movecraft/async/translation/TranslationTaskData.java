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

package net.countercraft.movecraft.async.translation;

import net.countercraft.movecraft.api.BlockVec;
import net.countercraft.movecraft.api.IntRange;
import net.countercraft.movecraft.utils.EntityUpdateCommand;
import net.countercraft.movecraft.utils.ItemDropUpdateCommand;
import net.countercraft.movecraft.utils.MapUpdateCommand;

public class TranslationTaskData {
    private final int dx;
    private int dy;
    private final int dz;
    private boolean failed = false;
    private String failMessage;
    private BlockVec[] blockList;
    private MapUpdateCommand[] updates;
    private EntityUpdateCommand[] entityUpdates;
    private ItemDropUpdateCommand[] itemDropUpdates;
    private int[][][] hitbox;
    private int minX, minZ;
    public final IntRange heightRange;
    private boolean collisionExplosion;

    public TranslationTaskData(int dx, int dz, int dy, BlockVec[] blockList, int[][][] hitbox, int minZ, int minX,
                               IntRange heightRange)
    {
        this.dx = dx;
        this.dz = dz;
        this.dy = dy;
        this.blockList = blockList;
        this.hitbox = hitbox;
        this.minZ = minZ;
        this.minX = minX;
        this.heightRange = heightRange;
    }

    public int getDx() {

        return dx;
    }

    public int getDy() {
        return dy;
    }

    public int getDz() {
        return dz;
    }

    public void setDy(int dY) {
        this.dy = dY;
    }

    public boolean failed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public boolean collisionExplosion() {
        return collisionExplosion;
    }

    public void setCollisionExplosion(boolean collisionExplosion) {
        this.collisionExplosion = collisionExplosion;
    }

    public String getFailMessage() {
        return failMessage;
    }

    public void setFailMessage(String failMessage) {
        this.failMessage = failMessage;
    }

    public BlockVec[] getBlockList() {
        return blockList;
    }

    public void setBlockList(BlockVec[] blockList) {
        this.blockList = blockList;
    }

    public MapUpdateCommand[] getUpdates() {
        return updates;
    }

    public void setUpdates(MapUpdateCommand[] updates) {
        this.updates = updates;
    }

    public EntityUpdateCommand[] getEntityUpdates() {
        return entityUpdates;
    }

    public void setEntityUpdates(EntityUpdateCommand[] entityUpdates) {
        this.entityUpdates = entityUpdates;
    }

    public int[][][] getHitbox() {
        return hitbox;
    }

    public void setHitbox(int[][][] hitbox) {
        this.hitbox = hitbox;
    }

    public int getMinX() {
        return minX;
    }

    public void setMinX(int minX) {
        this.minX = minX;
    }

    public int getMinZ() {
        return minZ;
    }

    public void setMinZ(int minZ) {
        this.minZ = minZ;
    }

    public ItemDropUpdateCommand[] getItemDropUpdateCommands() {
        return this.itemDropUpdates;
    }

    public void setItemDropUpdates(ItemDropUpdateCommand[] itemDropUpdate) {
        this.itemDropUpdates = itemDropUpdate;
    }
}
