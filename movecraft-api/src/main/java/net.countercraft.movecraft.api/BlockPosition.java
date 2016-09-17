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

package net.countercraft.movecraft.api;

public final class BlockPosition {
    public final int x, y, z;

    public BlockPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Returns a BlockPosition that has undergone the given translation.
     * <p>
     * This does not change the BlockPosition that it is called upon and that should be accounted for in terms of
     * Garbage Collection.
     *
     * @param dx - X translation
     * @param dy - Y translation
     * @param dz - Z translation
     * @return New BlockPosition shifted by specified amount
     */
    public BlockPosition translate(int dx, int dy, int dz) {
        return new BlockPosition(x + dx, y + dy, z + dz);
    }

    @Override public boolean equals(Object o) {
        if (o instanceof BlockPosition) {
            BlockPosition location = (BlockPosition) o;
            if (location.x == x && location.y == y && location.z == z) {
                return true;
            }
        }

        return false;
    }

    @Override public int hashCode() {
        return Integer.valueOf(x).hashCode() >> 13 ^ Integer.valueOf(y).hashCode() >> 7 ^ Integer.valueOf(z).hashCode();
    }

    public BlockPosition add(BlockPosition l) {
        return new BlockPosition(x + l.x, y + l.y, z + l.z);
    }

    public BlockPosition subtract(BlockPosition l) {
        return new BlockPosition(x - l.x, y - l.y, z - l.z);
    }
}
