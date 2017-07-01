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

import com.alexknvl.shipcraft.math.BlockVec;
import com.alexknvl.shipcraft.math.IntRange;
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

    public TranslationTaskData(final int dx, final int dz, final int dy, final BlockVec[] blockList, final int[][][] hitbox, final int minZ, final int minX,
                               final IntRange heightRange)
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

        return this.dx;
    }

    public int getDy() {
        return this.dy;
    }

    public int getDz() {
        return this.dz;
    }

    public void setDy(final int dY) {
        this.dy = dY;
    }

    public boolean failed() {
        return this.failed;
    }

    public void setFailed(final boolean failed) {
        this.failed = failed;
    }

    public boolean collisionExplosion() {
        return this.collisionExplosion;
    }

    public void setCollisionExplosion(final boolean collisionExplosion) {
        this.collisionExplosion = collisionExplosion;
    }

    public String getFailMessage() {
        return this.failMessage;
    }

    public void setFailMessage(final String failMessage) {
        this.failMessage = failMessage;
    }

    public BlockVec[] getBlockList() {
        return this.blockList;
    }

    public void setBlockList(final BlockVec[] blockList) {
        this.blockList = blockList;
    }

    public MapUpdateCommand[] getUpdates() {
        return this.updates;
    }

    public void setUpdates(final MapUpdateCommand[] updates) {
        this.updates = updates;
    }

    public EntityUpdateCommand[] getEntityUpdates() {
        return this.entityUpdates;
    }

    public void setEntityUpdates(final EntityUpdateCommand[] entityUpdates) {
        this.entityUpdates = entityUpdates;
    }

    public int[][][] getHitbox() {
        return this.hitbox;
    }

    public void setHitbox(final int[][][] hitbox) {
        this.hitbox = hitbox;
    }

    public int getMinX() {
        return this.minX;
    }

    public void setMinX(final int minX) {
        this.minX = minX;
    }

    public int getMinZ() {
        return this.minZ;
    }

    public void setMinZ(final int minZ) {
        this.minZ = minZ;
    }

    public ItemDropUpdateCommand[] getItemDropUpdateCommands() {
        return this.itemDropUpdates;
    }

    public void setItemDropUpdates(final ItemDropUpdateCommand[] itemDropUpdate) {
        this.itemDropUpdates = itemDropUpdate;
    }
}
