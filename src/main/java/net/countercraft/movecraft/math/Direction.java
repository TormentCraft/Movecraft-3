package net.countercraft.movecraft.math;

import com.google.common.base.Optional;
import org.bukkit.block.Sign;

import java.util.Collections;
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

    private static final Map<String, Direction> NAME_MAP;
    static {
        Map<String, Direction> result = new HashMap<>();
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
        NAME_MAP = Collections.unmodifiableMap(result);
    }

    public final int x;
    public final int y;
    public final int z;

    public Direction(int x, int y, int z) {
        this.x = Math.min(Math.max(x, -1), 1);
        this.y = Math.min(Math.max(y, -1), 1);
        this.z = Math.min(Math.max(z, -1), 1);
    }

    public Direction combine(Direction that) {
        return new Direction(this.x + that.x, this.y + that.y, this.z + that.z);
    }

    public static Optional<Direction> named(String s) {
        return Optional.fromNullable(NAME_MAP.get(s.toLowerCase()));
    }

    public static Direction namedOr(String s, Direction defaultValue) {
        return NAME_MAP.getOrDefault(s.toLowerCase(), defaultValue);
    }

    public static Direction fromYawPitch(double yaw, double pitch) {
        yaw = yaw % 360;
        if (yaw <= -180) yaw += 360;
        if (yaw > 180) yaw -= 360;

        // Yaw is between -180 .. 180.

        int x = 0;
        int z = 0;

        if (pitch > -80 && pitch < 80) {
            if (yaw >= -60.0 && yaw <= 60.0) z = 1;
            if (yaw <= -120.0 || yaw >= 120.0) z = -1;

            if (yaw >= 30.0 && yaw <= 150.0) x = -1;
            if (yaw >= -150.0 && yaw <= -30.0) x = 1;
        }

        int y = 0;
        if (pitch <= -30) y = 1;//up
        else if (pitch >= 30) y = -1;//down

        return new Direction(x, y, z);
    }

    public static Direction fromSignDirection(Sign sign) {
        byte rawData = sign.getRawData();
        if (rawData == ((byte) 0x3)) return NORTH;//north
        if (rawData == ((byte) 0x4)) return EAST;//east
        if (rawData == ((byte) 0x2)) return SOUTH;//south
        if (rawData == ((byte) 0x5)) return WEST;//west
        return OFF;
    }
}
