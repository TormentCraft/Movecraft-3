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

import org.bukkit.Location;
import org.bukkit.World;

public final class BlockVec {
    public static final BlockVec ZERO = new BlockVec(0, 0, 0);

    public final int x;
    public final int y;
    public final int z;

    public BlockVec(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockVec from(Location location) {
        return new BlockVec(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Location toBukkitLocation(World world) {
        return new Location(world, x, y, z);
    }

    public BlockVec add(BlockVec vec) {
        return new BlockVec(x + vec.x, y + vec.y, z + vec.z);
    }

    public BlockVec subtract(BlockVec vec) {
        return new BlockVec(x - vec.x, y - vec.y, z - vec.z);
    }

    /**
     * Returns a BlockVec that has undergone the given translation.
     * <p/>
     * This does not change the BlockVec that it is called upon and that should be accounted for in terms of Garbage Collection.
     *
     * @param dx - X translation
     * @param dy - Y translation
     * @param dz - Z translation
     * @return New BlockVec shifted by specified amount
     */
    public BlockVec translate(int dx, int dy, int dz) {
        return new BlockVec(x + dx, y + dy, z + dz);
    }

    public BlockVec rotate(Rotation r) {
        if (r == Rotation.NONE) return this;

        int cos = 0;
        int sin = (r == Rotation.CLOCKWISE) ? 1 : -1;

        int x = this.x * cos + this.z * -sin;
        int z = this.x * sin + this.z * cos;

        return new BlockVec(x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof BlockVec) {
            BlockVec location = (BlockVec) o;
            if (location.x == x && location.y == y && location.z == z) {
                return true;
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Integer.valueOf(x).hashCode() >> 13
               ^ Integer.valueOf(y).hashCode() >> 7
               ^ Integer.valueOf(z).hashCode();
    }

    @Override
    public String toString() {
        return "BlockVec{" +
               "x=" + x +
               ", y=" + y +
               ", z=" + z +
               '}';
    }
}
