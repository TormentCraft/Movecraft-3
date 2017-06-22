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

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.countercraft.movecraft.api.IntRange;
import net.countercraft.movecraft.api.MaterialDataPredicate;
import org.bukkit.Material;
import org.bukkit.material.MaterialData;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CraftType {
    private String craftName;
    private IntRange sizeRange;
    private IntRange heightRange;
    private int maxHeightAboveGround;
    private MaterialDataPredicate allowedBlocks;
    private MaterialDataPredicate forbiddenBlocks;
    private boolean blockedByWater;
    private boolean requireWaterContact;
    private boolean tryNudge;
    private boolean canCruise;
    private boolean canTeleport;
    private boolean canStaticMove;
    private boolean canHover;
    private boolean canDirectControl;
    private boolean useGravity;
    private boolean canHoverOverWater;
    private boolean moveEntities;
    private boolean allowHorizontalMovement;
    private boolean allowVerticalMovement;
    private boolean allowRemoteSign;
    private boolean cruiseOnPilot;
    private boolean allowVerticalTakeoffAndLanding;
    private boolean rotateAtMidpoint;
    private int cruiseOnPilotVertMove;
    private int maxStaticMove;
    private int cruiseSkipBlocks;
    private int vertCruiseSkipBlocks;
    private int cruiseTickCooldown;
    private boolean halfSpeedUnderwater;
    private int staticWaterLevel;
    private double fuelBurnRate;
    private double sinkPercent;
    private double overallSinkPercent;
    private double detectionMultiplier;
    private double underwaterDetectionMultiplier;
    private int sinkRateTicks;
    private boolean keepMovingOnSink;
    private int smokeOnSink;
    private double explodeOnCrash;
    private double collisionExplosion;
    private int tickCooldown;
    private Map<MaterialDataPredicate, List<Constraint>> flyBlocks = new HashMap<>();
    private int hoverLimit;
    private MaterialDataPredicate harvestBlocks;
    private MaterialDataPredicate harvesterBladeBlocks;

    public static final class ParseException extends Exception {
        private static final long serialVersionUID = 2203721495116081165L;

        public final String format;
        public final ImmutableList<Object> arguments;

        private ParseException(Throwable cause, String fmt, Object... args) {
            super(MessageFormat.format(fmt, args), cause);
            this.format = fmt;
            this.arguments = ImmutableList.copyOf(args);
        }

        public static ParseException of(String fmt, Object... args) {
            return new ParseException(null, fmt, args);
        }

        public ParseException causedBy(Throwable cause) {
            return new ParseException(cause, format, arguments);
        }
    }

    public enum Ordering {
        LT(-1), EQ(0), GT(1);

        public final int intValue;

        Ordering(int value) {
            this.intValue = value;
        }

        public static Ordering of(int value) {
            if (value < 0) return LT;
            if (value > 0) return GT;
            return EQ;
        }
    }

    public abstract static class Bound {
        private Bound() {
        }

        public abstract boolean isExact();

        public abstract Ordering compare(int count, int total);

        public abstract double asRatio(int total);

        public abstract int asExact(int total);

        public static Bound exact(int count) {
            return new Exact(count);
        }

        public static Bound ratio(double value) {
            return new Ratio(value);
        }

        public static final class Exact extends Bound {
            public final int value;

            public Exact(int value) {
                this.value = value;
            }

            @Override public boolean isExact() {
                return true;
            }

            @Override public Ordering compare(int count, int total) {
                if (count > value) return Ordering.GT;
                if (count == value) return Ordering.EQ;
                return Ordering.LT;
            }

            @Override public double asRatio(int total) {
                return value / (double) total;
            }

            @Override public int asExact(int total) {
                return value;
            }

            @Override public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Exact exact = (Exact) o;

                return value == exact.value;
            }

            @Override public int hashCode() {
                return value;
            }

            @Override public String toString() {
                return String.valueOf(value);
            }
        }

        public static final class Ratio extends Bound {
            public final double value;

            public Ratio(double value) {
                this.value = value;
            }

            @Override public boolean isExact() {
                return false;
            }

            @Override public Ordering compare(int count, int total) {
                double ratio = count / (double) total;
                if (ratio > value) return Ordering.GT;
                if (ratio == value) return Ordering.EQ;
                return Ordering.LT;
            }

            @Override public double asRatio(int total) {
                return value;
            }

            @Override public int asExact(int total) {
                return (int) (value * total);
            }

            @Override public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Ratio ratio = (Ratio) o;

                return Double.compare(ratio.value, value) == 0;
            }

            @Override public int hashCode() {
                long temp = Double.doubleToLongBits(value);
                return (int) (temp ^ (temp >>> 32));
            }

            @Override public String toString() {
                return String.format("%.2f%%", value);
            }
        }
    }

    public static final class Constraint {
        public final Bound bound;
        public final boolean isUpper;

        private Constraint(Bound bound, boolean isUpper) {
            Preconditions.checkNotNull(bound);
            this.bound = bound;
            this.isUpper = isUpper;
        }

        public boolean isTrivial() {
            if (isUpper) {
                return !bound.isExact() && bound.asRatio(1) >= 1.0;
            } else {
                if (bound.isExact()) {
                    return bound.asExact(1) == 0;
                } else {
                    return bound.asRatio(1) == 0;
                }
            }
        }

        public boolean isSatisfiedBy(int count, int total) {
            Ordering ordering = bound.compare(count, total);
            if (isUpper) {
                return ordering == Ordering.EQ || ordering == Ordering.LT;
            } else {
                return ordering == Ordering.EQ || ordering == Ordering.GT;
            }
        }

        public static Constraint lower(Bound bound) {
            return new Constraint(bound, false);
        }

        public static Constraint upper(Bound bound) {
            return new Constraint(bound, true);
        }

        @Override public String toString() {
            if (isUpper) return String.format("<= %s", bound);
            return String.format(">= %s", bound);
        }
    }

    private static int parseInteger(String string) throws ParseException {
        try {
            return Integer.valueOf(string);
        } catch (NumberFormatException nfe) {
            throw ParseException.of("Expected an integer, got {0}.", string).causedBy(nfe);
        }
    }

    private static byte parseByte(String string) throws ParseException {
        try {
            return Byte.valueOf(string);
        } catch (NumberFormatException nfe) {
            throw ParseException.of("Expected a byte value, got {0}.", string).causedBy(nfe);
        }
    }

    private static double parseDouble(String string) throws ParseException {
        try {
            return Double.valueOf(string);
        } catch (NumberFormatException nfe) {
            throw ParseException.of("Expected a byte value, got {0}.", string).causedBy(nfe);
        }
    }

    private static int asInteger(Object obj) throws ParseException {
        if (obj instanceof Integer) {
            return (Integer) obj;
        } else {
            throw ParseException.of("Expected an integer, got {0}.", obj);
        }
    }

    private static double asDouble(Object obj) throws ParseException {
        if (obj instanceof Integer) {
            return ((Integer) obj).doubleValue();
        } else if (obj instanceof Double) {
            return (Double) obj;
        } else {
            throw ParseException.of("Expected a floating point number, got {0}.", obj);
        }
    }

    private static boolean asBoolean(Object obj) throws ParseException {
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else {
            throw ParseException.of("Expected a boolean, got {0}.", obj);
        }
    }

    private static Material asMaterial(Object obj) throws ParseException {
        Optional<Material> material;
        if (obj instanceof String) {
            String s = (String) obj;
            try {
                material = Optional.fromNullable(Material.getMaterial(Integer.valueOf(s)));
            } catch (NumberFormatException ignored) {
                material = Optional.fromNullable(Material.getMaterial(s));
            }
        } else if (obj instanceof Integer) {
            material = Optional.fromNullable(Material.getMaterial((Integer) obj));
        } else {
            material = Optional.absent();
        }

        if (material.isPresent()) {
            return material.get();
        } else {
            throw ParseException.of("Expected a material name, got {0}.", obj);
        }
    }

    private static MaterialDataPredicate asSingleMaterialDataPredicate(Object obj) throws ParseException {
        if (obj instanceof String) {
            String str = (String) obj;

            if (str.contains(":")) {
                String[] parts = str.split(":");

                if (parts.length != 2) {
                    throw ParseException.of("Expected a material data pair, got {0}.", str);
                }

                Material typeID = asMaterial(parts[0]);
                byte data = parseByte(parts[1]);

                return MaterialDataPredicate.single(typeID, data);
            } else {
                return MaterialDataPredicate.single(asMaterial(str));
            }
        } else {
            return MaterialDataPredicate.single(asMaterial(obj));
        }
    }

    private static MaterialDataPredicate asMaterialDataPredicateList(Object obj) throws ParseException {
        if (obj instanceof Iterable) {
            MaterialDataPredicate.Builder builder = new MaterialDataPredicate.Builder();
            Iterable<?> objList = (Iterable<?>) obj;
            for (Object element : objList) {
                builder.add(asSingleMaterialDataPredicate(element));
            }
            return builder.result();
        } else {
            throw ParseException.of("Expected a list of material data pairs, got {0}.", obj);
        }
    }

    private static MaterialDataPredicate asMaterialDataPredicate(Object obj) throws ParseException {
        if (obj instanceof Iterable) {
            return asMaterialDataPredicateList(obj);
        } else {
            return asSingleMaterialDataPredicate(obj);
        }
    }

    private static Bound asBound(Object object) throws ParseException {
        if (object instanceof String) {
            String string = (String) object;
            // An 'N' in front of the value indicates a specific quantity, i.e. "N2" for exactly 2 of the block.
            if (string.startsWith("N")) {
                return Bound.exact(parseInteger(string.substring(1)));
            } else {
                return Bound.ratio(parseDouble(string) / 100.0);
            }
        } else {
            return Bound.ratio(asDouble(object) / 100.0);
        }
    }

    private static List<Constraint> asConstrantList(Object object) throws ParseException {
        if (!(object instanceof List)) {
            throw ParseException.of("Expected a list of constraints, got {0}", object);
        }

        List<?> list = (List<?>) object;

        if (list.size() != 2) {
            throw ParseException.of("Expected a list of two constraints, got {0}", object);
        }

        return ImmutableList.of(Constraint.lower(asBound(list.get(0))), Constraint.upper(asBound(list.get(1))));
    }

    private static Map<MaterialDataPredicate, List<Constraint>> asRequirementList(Object obj) throws ParseException {
        Map<MaterialDataPredicate, List<Constraint>> returnMap = new HashMap<>();

        if (obj instanceof Map) {
            Map<?, ?> objMap = (Map<?, ?>) obj;
            for (Map.Entry<?, ?> entry : objMap.entrySet()) {
                // First read in the list of the blocks that type of flyblock.
                // It could be a single string (with or without a ":") or integer,
                // or it could be multiple of them.
                MaterialDataPredicate predicate = asMaterialDataPredicate(entry.getKey());

                // Then read in the limitation values, low and high.
                List<Constraint> list = asConstrantList(entry.getValue());

                returnMap.put(predicate, list);
            }
            return ImmutableMap.copyOf(returnMap);
        } else {
            throw ParseException.of("Expected a requirement list, got {0}.", obj);
        }
    }

    private static boolean getBooleanOr(Map<?, ?> data, String name, boolean defaultValue) throws ParseException {
        if (data.containsKey(name)) {
            return asBoolean(data.get(name));
        } else {
            return defaultValue;
        }
    }

    private static double getDoubleOr(Map<?, ?> data, String name, double defaultValue) throws ParseException {
        if (data.containsKey(name)) {
            return asDouble(data.get(name));
        } else {
            return defaultValue;
        }
    }

    private static int getIntegerOr(Map<?, ?> data, String name, int defaultValue) throws ParseException {
        if (data.containsKey(name)) {
            return asInteger(data.get(name));
        } else {
            return defaultValue;
        }
    }

    public void parseCraftDataFromFile(File file, int defaultSinkRateTicks) throws FileNotFoundException, ParseException
    {
        InputStream input = new FileInputStream(file);
        Yaml yaml = new Yaml();
        Map<?, ?> data = (Map<?, ?>) yaml.load(input);
        craftName = (String) data.get("name");

        int maxSize = asInteger(data.get("maxSize"));
        int minSize = asInteger(data.get("minSize"));
        sizeRange = new IntRange(minSize, maxSize);

        allowedBlocks = asMaterialDataPredicateList(data.get("allowedBlocks"));
        forbiddenBlocks = asMaterialDataPredicateList(data.get("forbiddenBlocks"));
        blockedByWater = getBooleanOr(data, "canFly", getBooleanOr(data, "blockedByWater", true));
        requireWaterContact = getBooleanOr(data, "requireWaterContact", false);
        tryNudge = getBooleanOr(data, "tryNudge", false);

        tickCooldown = (int) Math.ceil(20 / (asDouble(data.get("speed"))));
        if (data.containsKey("cruiseSpeed")) {
            cruiseTickCooldown = (int) Math.ceil(20 / (asDouble(data.get("cruiseSpeed"))));
        } else {
            cruiseTickCooldown = tickCooldown;
        }

        flyBlocks = asRequirementList(data.get("flyblocks"));
        canCruise = getBooleanOr(data, "canCruise", false);
        canTeleport = getBooleanOr(data, "canTeleport", false);
        cruiseOnPilot = getBooleanOr(data, "cruiseOnPilot", false);
        cruiseOnPilotVertMove = getIntegerOr(data, "cruiseOnPilotVertMove", 0);
        allowVerticalMovement = getBooleanOr(data, "allowVerticalMovement", true);
        rotateAtMidpoint = getBooleanOr(data, "rotateAtMidpoint", false);
        allowHorizontalMovement = getBooleanOr(data, "allowHorizontalMovement", true);
        allowRemoteSign = getBooleanOr(data, "allowRemoteSign", true);
        canStaticMove = getBooleanOr(data, "canStaticMove", false);
        maxStaticMove = getIntegerOr(data, "maxStaticMove", 10000);
        cruiseSkipBlocks = getIntegerOr(data, "cruiseSkipBlocks", 0);
        vertCruiseSkipBlocks = getIntegerOr(data, "vertCruiseSkipBlocks", cruiseSkipBlocks);
        halfSpeedUnderwater = getBooleanOr(data, "halfSpeedUnderwater", false);
        staticWaterLevel = getIntegerOr(data, "staticWaterLevel", 0);
        fuelBurnRate = getDoubleOr(data, "fuelBurnRate", 0.0);
        sinkPercent = getDoubleOr(data, "sinkPercent", 0.0);
        overallSinkPercent = getDoubleOr(data, "overallSinkPercent", 0.0);
        detectionMultiplier = getDoubleOr(data, "detectionMultiplier", 0.0);
        underwaterDetectionMultiplier = getDoubleOr(data, "underwaterDetectionMultiplier", detectionMultiplier);

        if (data.containsKey("sinkSpeed")) {
            sinkRateTicks = (int) Math.ceil(20 / (asDouble(data.get("sinkSpeed"))));
        } else {
            sinkRateTicks = defaultSinkRateTicks;
        }

        keepMovingOnSink = getBooleanOr(data, "keepMovingOnSink", false);
        smokeOnSink = getIntegerOr(data, "smokeOnSink", 0);
        explodeOnCrash = getDoubleOr(data, "explodeOnCrash", 0.0);
        collisionExplosion = getDoubleOr(data, "collisionExplosion", 0.0);

        int minHeightLimit = getIntegerOr(data, "minHeightLimit", 0);
        int maxHeightLimit = getIntegerOr(data, "maxHeightLimit", 255);
        heightRange = new IntRange(minHeightLimit, maxHeightLimit);

        maxHeightAboveGround = getIntegerOr(data, "maxHeightAboveGround", -1);
        canDirectControl = getBooleanOr(data, "canDirectControl", true);
        canHover = getBooleanOr(data, "canHover", false);
        canHoverOverWater = getBooleanOr(data, "canHoverOverWater", true);
        moveEntities = getBooleanOr(data, "moveEntities", true);
        useGravity = getBooleanOr(data, "useGravity", false);
        hoverLimit = getIntegerOr(data, "hoverLimit", 0);

        if (data.containsKey("harvestBlocks")) {
            harvestBlocks = asMaterialDataPredicateList(data.get("harvestBlocks"));
        } else {
            harvestBlocks = MaterialDataPredicate.none();
        }

        if (data.containsKey("harvesterBladeBlocks")) {
            harvesterBladeBlocks = asMaterialDataPredicateList(data.get("harvesterBladeBlocks"));
        } else {
            harvesterBladeBlocks = MaterialDataPredicate.none();
        }

        allowVerticalTakeoffAndLanding = getBooleanOr(data, "allowVerticalTakeoffAndLanding", true);
    }

    public String getCraftName() {
        return craftName;
    }

    public IntRange getSizeRange() {
        return sizeRange;
    }

    public MaterialDataPredicate getAllowedBlocks() {
        return allowedBlocks;
    }

    public MaterialDataPredicate getForbiddenBlocks() {
        return forbiddenBlocks;
    }

    public boolean blockedByWater() {
        return blockedByWater;
    }

    public boolean getRequireWaterContact() {
        return requireWaterContact;
    }

    public boolean getCanCruise() {
        return canCruise;
    }

    public int getCruiseSkipBlocks() {
        return cruiseSkipBlocks;
    }

    public int getVertCruiseSkipBlocks() {
        return vertCruiseSkipBlocks;
    }

    public int maxStaticMove() {
        return maxStaticMove;
    }

    public int getStaticWaterLevel() {
        return staticWaterLevel;
    }

    public boolean getCanTeleport() {
        return canTeleport;
    }

    public boolean getCanStaticMove() {
        return canStaticMove;
    }

    public boolean getCruiseOnPilot() {
        return cruiseOnPilot;
    }

    public int getCruiseOnPilotVertMove() {
        return cruiseOnPilotVertMove;
    }

    public boolean allowVerticalMovement() {
        return allowVerticalMovement;
    }

    public boolean rotateAtMidpoint() {
        return rotateAtMidpoint;
    }

    public boolean allowHorizontalMovement() {
        return allowHorizontalMovement;
    }

    public boolean allowRemoteSign() {
        return allowRemoteSign;
    }

    public double getFuelBurnRate() {
        return fuelBurnRate;
    }

    public double getSinkPercent() {
        return sinkPercent;
    }

    public double getOverallSinkPercent() {
        return overallSinkPercent;
    }

    public double getDetectionMultiplier() {
        return detectionMultiplier;
    }

    public double getUnderwaterDetectionMultiplier() {
        return underwaterDetectionMultiplier;
    }

    public int getSinkRateTicks() {
        return sinkRateTicks;
    }

    public boolean getKeepMovingOnSink() {
        return keepMovingOnSink;
    }

    public double getExplodeOnCrash() {
        return explodeOnCrash;
    }

    public int getSmokeOnSink() {
        return smokeOnSink;
    }

    public double getCollisionExplosion() {
        return collisionExplosion;
    }

    public int getTickCooldown() {
        return tickCooldown;
    }

    public int getCruiseTickCooldown() {
        return cruiseTickCooldown;
    }

    public boolean getHalfSpeedUnderwater() {
        return halfSpeedUnderwater;
    }

    public boolean isTryNudge() {
        return tryNudge;
    }

    public Map<MaterialDataPredicate, List<Constraint>> getFlyBlocks() {
        return flyBlocks;
    }

    public IntRange getHeightRange() {
        return heightRange;
    }

    public int getMaxHeightAboveGround() {
        return maxHeightAboveGround;
    }

    public boolean getCanHover() {
        return canHover;
    }

    public boolean getCanDirectControl() {
        return canDirectControl;
    }

    public int getHoverLimit() {
        return hoverLimit;
    }

    public MaterialDataPredicate getHarvestBlocks() {
        return harvestBlocks;
    }

    public MaterialDataPredicate getHarvesterBladeBlocks() {
        return harvesterBladeBlocks;
    }

    public boolean getCanHoverOverWater() {
        return canHoverOverWater;
    }

    public boolean getMoveEntities() {
        return moveEntities;
    }

    public boolean getUseGravity() {
        return useGravity;
    }

    public boolean allowVerticalTakeoffAndLanding() {
        return allowVerticalTakeoffAndLanding;
    }

    public boolean isAllowedBlock(int blockId, byte data) {
        return this.allowedBlocks.check(new MaterialData(blockId, data));
    }

    public boolean isForbiddenBlock(int blockId, byte data) {
        return this.forbiddenBlocks.check(new MaterialData(blockId, data));
    }
}
