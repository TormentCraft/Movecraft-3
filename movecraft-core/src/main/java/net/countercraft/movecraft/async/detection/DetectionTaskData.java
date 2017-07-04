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

package net.countercraft.movecraft.async.detection;

import com.alexknvl.shipcraft.math.BlockVec;
import com.alexknvl.shipcraft.MaterialDataPredicate;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class DetectionTaskData {
    private World world;
    private boolean failed;
    private boolean waterContact;
    private String failMessage;
    private BlockVec[] blockList;
    private Player player;
    private Player notificationPlayer;
    private int[][][] hitBox;
    private Integer minX, minZ;
    private final MaterialDataPredicate allowedBlocks;
    private final MaterialDataPredicate forbiddenBlocks;

    public DetectionTaskData(final World world, final Player player, final Player notificationPlayer, final MaterialDataPredicate allowedBlocks,
                             final MaterialDataPredicate forbiddenBlocks)
    {
        this.world = world;
        this.player = player;
        this.notificationPlayer = notificationPlayer;
        this.allowedBlocks = allowedBlocks;
        this.forbiddenBlocks = forbiddenBlocks;
        this.waterContact = false;
    }

    public MaterialDataPredicate getAllowedBlocks() {
        return this.allowedBlocks;
    }

    public MaterialDataPredicate getForbiddenBlocks() {
        return this.forbiddenBlocks;
    }

    public World getWorld() {
        return this.world;
    }

    void setWorld(final World w) {
        this.world = w;
    }

    public boolean failed() {
        return this.failed;
    }

    public boolean getWaterContact() {
        return this.waterContact;
    }

    public String getFailMessage() {
        return this.failMessage;
    }

    void setFailMessage(final String failMessage) {
        this.failMessage = failMessage;
    }

    public BlockVec[] getBlockList() {
        return this.blockList;
    }

    void setBlockList(final BlockVec[] blockList) {
        this.blockList = blockList;
    }

    public Player getPlayer() {
        return this.player;
    }

    public Player getNotificationPlayer() {
        return this.notificationPlayer;
    }

    public int[][][] getHitBox() {
        return this.hitBox;
    }

    void setHitBox(final int[][][] hitBox) {
        this.hitBox = hitBox;
    }

    public Integer getMinX() {
        return this.minX;
    }

    void setMinX(final Integer minX) {
        this.minX = minX;
    }

    public Integer getMinZ() {
        return this.minZ;
    }

    void setMinZ(final Integer minZ) {
        this.minZ = minZ;
    }

    void setFailed(final boolean failed) {
        this.failed = failed;
    }

    void setWaterContact(final boolean waterContact) {
        this.waterContact = waterContact;
    }

    void setPlayer(final Player player) {
        this.player = player;
    }

    void setNotificationPlayer(final Player notificationPlayer) {
        this.notificationPlayer = notificationPlayer;
    }
}
