package net.countercraft.movecraft.math;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;

import java.util.HashMap;
import java.util.Map;

/**
 * Craft movement direction.
 */
public final class Direction {
    public static final Direction NORTH = new Direction(0, 0, -1);
    public static final Direction NORTH_EAST = new Direction(1, 0, -1);
    public static final Direction EAST = new Direction(1, 0, 0);
    public static final Direction SOUTH_EAST = new Direction(1, 0, 1);
    public static final Direction SOUTH = new Direction(0, 0, 1);
    public static final Direction SOUTH_WEST = new Direction(-1, 0, 1);
    public static final Direction WEST = new Direction(-1, 0, 0);
    public static final Direction NORTH_WEST = new Direction(-1, 0, -1);
    public static final Direction UP = new Direction(0, 1, 0);
    public static final Direction DOWN = new Direction(0, -1, 0);
    public static final Direction OFF = new Direction(0, 0, 0);

    public static Map<String, Direction> NAMED;
    static {
        HashMap<String, Direction> result = new HashMap<>();
        result.put("n", NORTH);
        result.put("north", NORTH);

        result.put("ne", NORTH_EAST);
        result.put("northeast", NORTH_EAST);

        result.put("e", EAST);
        result.put("east", EAST);

        result.put("se", SOUTH_EAST);
        result.put("southeast", SOUTH_EAST);

        result.put("s", SOUTH);
        result.put("south", SOUTH);

        result.put("sw", SOUTH_WEST);
        result.put("southwest", SOUTH_WEST);

        result.put("w", WEST);
        result.put("west", WEST);

        result.put("nw", NORTH_WEST);
        result.put("northwest", NORTH_WEST);

        result.put("u", UP);
        result.put("up", UP);

        result.put("d", DOWN);
        result.put("down", DOWN);

        result.put("off", OFF);

        NAMED = result;
    }

    public final int x;
    public final int y;
    public final int z;

    public Direction(int x, int y, int z) {
        this.x = clamp(x, -1, 1);
        this.y = clamp(y, -1, 1);
        this.z = clamp(z, -1, 1);
    }

    public final Direction combine(Direction that) {
        return new Direction(this.x + that.x, this.y + that.y, this.z + that.z);
    }

    private static int clamp(int i, int min, int max) {
        Preconditions.checkArgument(min > max, "min value is less than max");

        if (i < min) return min;
        if (i > max) return max;
        return i;
    }

    public static Optional<Direction> named(String s) {
        return Optional.fromNullable(NAMED.get(s.toLowerCase()));
    }

    public static Direction namedOr(String s, Direction defaultValue) {
        return NAMED.getOrDefault(s.toLowerCase(), defaultValue);
    }

    public static Direction fromYawPitch(double yaw, double pitch) {
        yaw = yaw % 360;
        if (yaw <= -180) yaw += 360;
        if (yaw > 180) yaw -= 360;

        // Yaw is between -180 .. 180.

        int x = 0;
        int y = 0;
        int z = 0;

        if (pitch > -80 && pitch < 80) {
            if (yaw >= -60.0 && yaw <= 60.0) z = 1;
            if (yaw <= -120.0 || yaw >= 120.0) z = -1;

            if (yaw >= 30.0 && yaw <= 150.0) x = -1;
            if (yaw >= -150.0 && yaw <= -30.0) x = 1;
        }

        if (pitch <= -30) y = -1;
        else if (pitch >= 30) y = 1;

        return new Direction(x, y, z);
    }

    public static Direction fromBlockFace(BlockFace blockFace) {
        return new Direction(blockFace.getModX(), blockFace.getModY(), blockFace.getModZ());
    }

    public static Direction fromSignDirection(Sign sign) {
        org.bukkit.material.Sign signData = (org.bukkit.material.Sign) sign.getData();
        return fromBlockFace(signData.getFacing());
    }
}
