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

package net.countercraft.movecraft.utils;

import net.countercraft.movecraft.api.BlockVec;
import net.countercraft.movecraft.api.Rotation;
import org.bukkit.Location;

public final class MathUtils {

    public static boolean playerIsWithinBoundingPolygon(final int[][][] box, final int minX, final int minZ, final BlockVec l) {

        if (l.x >= minX && l.x < (minX + box.length)) {
            // PLayer is within correct X boundary
            if (l.z >= minZ && l.z < (minZ + box[l.x - minX].length)) {
                // Player is within valid Z boundary
                final int minY;
                final int maxY;

                try {
                    minY = box[l.x - minX][l.z - minZ][0];
                    maxY = box[l.x - minX][l.z - minZ][1];
                } catch (final NullPointerException e) {
                    return false;
                }

                if (l.y >= minY && l.y <= (maxY + 2)) {
                    // Player is on board the vessel
                    return true;
                }
            }
        }

        return false;
    }

    public static BlockVec bukkit2MovecraftLoc(final Location l) {
        return new BlockVec(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public static BlockVec rotateVec(final Rotation r, final BlockVec l) {
        final double theta;
        if (r == Rotation.CLOCKWISE) {
            theta = 0.5 * Math.PI;
        } else {
            theta = -1 * 0.5 * Math.PI;
        }

        final int x = (int) Math.round((l.x * Math.cos(theta)) + (l.z * (-1 * Math.sin(theta))));
        final int z = (int) Math.round((l.x * Math.sin(theta)) + (l.z * Math.cos(theta)));

        return new BlockVec(x, l.y, z);
    }

    public static double[] rotateVec(final Rotation r, final double x, final double z) {
        final double theta;
        if (r == Rotation.CLOCKWISE) {
            theta = 0.5 * Math.PI;
        } else {
            theta = -1 * 0.5 * Math.PI;
        }

        final double newX = Math.round((x * Math.cos(theta)) + (z * (-1 * Math.sin(theta))));
        final double newZ = Math.round((x * Math.sin(theta)) + (z * Math.cos(theta)));

        return new double[]{newX, newZ};
    }

    public static double[] rotateVecNoRound(final Rotation r, final double x, final double z) {
        final double theta;
        if (r == Rotation.CLOCKWISE) {
            theta = 0.5 * Math.PI;
        } else {
            theta = -1 * 0.5 * Math.PI;
        }

        final double newX = (x * Math.cos(theta)) + (z * (-1 * Math.sin(theta)));
        final double newZ = (x * Math.sin(theta)) + (z * Math.cos(theta));

        return new double[]{newX, newZ};
    }

    public static int positiveMod(int mod, final int divisor) {
        if (mod < 0) {
            mod += divisor;
        }

        return mod;
    }
}
