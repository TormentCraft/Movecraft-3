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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.alexknvl.shipcraft.math.IntRange;
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
import java.util.Optional;

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

        private ParseException(final Throwable cause, final String fmt, final Object... args) {
            super(MessageFormat.format(fmt, args), cause);
            this.format = fmt;
            this.arguments = ImmutableList.copyOf(args);
        }

        public static ParseException of(final String fmt, final Object... args) {
            return new ParseException(null, fmt, args);
        }

        public ParseException causedBy(final Throwable cause) {
            return new ParseException(cause, this.format, this.arguments);
        }
    }

    public enum Ordering {
        LT(-1), EQ(0), GT(1);

        public final int intValue;

        Ordering(final int value) {
            this.intValue = value;
        }

        public static Ordering of(final int value) {
            if (value < 0) return Ordering.LT;
            if (value > 0) return Ordering.GT;
            return Ordering.EQ;
        }
    }

    public abstract static class Bound {
        private Bound() {
        }

        public abstract boolean isExact();

        public abstract Ordering compare(int count, int total);

        public abstract double asRatio(int total);

        public abstract int asExact(int total);

        public static Bound exact(final int count) {
            return new Exact(count);
        }

        public static Bound ratio(final double value) {
            return new Ratio(value);
        }

        public static final class Exact extends Bound {
            public final int value;

            public Exact(final int value) {
                this.value = value;
            }

            @Override public boolean isExact() {
                return true;
            }

            @Override public Ordering compare(final int count, final int total) {
                if (count > this.value) return Ordering.GT;
                if (count == this.value) return Ordering.EQ;
                return Ordering.LT;
            }

            @Override public double asRatio(final int total) {
                return this.value / (double) total;
            }

            @Override public int asExact(final int total) {
                return this.value;
            }

            @Override public boolean equals(final Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                final Exact exact = (Exact) o;

                return this.value == exact.value;
            }

            @Override public int hashCode() {
                return this.value;
            }

            @Override public String toString() {
                return String.format("Exact{value=%d}", this.value);
            }
        }

        public static final class Ratio extends Bound {
            public final double value;

            public Ratio(final double value) {
                this.value = value;
            }

            @Override public boolean isExact() {
                return false;
            }

            @Override public Ordering compare(final int count, final int total) {
                final double ratio = count / (double) total;
                if (ratio > this.value) return Ordering.GT;
                if (ratio == this.value) return Ordering.EQ;
                return Ordering.LT;
            }

            @Override public double asRatio(final int total) {
                return this.value;
            }

            @Override public int asExact(final int total) {
                return (int) (this.value * total);
            }

            @Override public boolean equals(final Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                final Ratio ratio = (Ratio) o;

                return Double.compare(ratio.value, this.value) == 0;
            }

            @Override public int hashCode() {
                final long temp = Double.doubleToLongBits(this.value);
                return (int) (temp ^ (temp >>> 32));
            }

            @Override public String toString() {
                return String.format("Ratio{value=%f}", this.value);
            }
        }
    }

    public static final class Constraint {
        public final Bound bound;
        public final boolean isUpper;

        private Constraint(final Bound bound, final boolean isUpper) {
            Preconditions.checkNotNull(bound);
            this.bound = bound;
            this.isUpper = isUpper;
        }

        public boolean isTrivial() {
            if (this.isUpper) {
                return !this.bound.isExact() && this.bound.asRatio(1) >= 1.0;
            } else {
                if (this.bound.isExact()) {
                    return this.bound.asExact(1) == 0;
                } else {
                    return this.bound.asRatio(1) == 0;
                }
            }
        }

        public boolean isSatisfiedBy(final int count, final int total) {
            final Ordering ordering = this.bound.compare(count, total);
            if (this.isUpper) {
                return ordering == Ordering.EQ || ordering == Ordering.LT;
            } else {
                return ordering == Ordering.EQ || ordering == Ordering.GT;
            }
        }

        public static Constraint lower(final Bound bound) {
            return new Constraint(bound, false);
        }

        public static Constraint upper(final Bound bound) {
            return new Constraint(bound, true);
        }

        @Override public String toString() {
            // if (this.isUpper) return String.format("<= %s", this.bound);
            // return String.format(">= %s", this.bound);
            return String.format("Constraint{bound=%s,isUpper=%b}", this.bound.toString(), this.isUpper);
        }
    }

    private static int parseInteger(final String string) throws ParseException {
        try {
            return Integer.valueOf(string);
        } catch (final NumberFormatException nfe) {
            throw ParseException.of("Expected an integer, got {0}.", string).causedBy(nfe);
        }
    }

    private static byte parseByte(final String string) throws ParseException {
        try {
            return Byte.valueOf(string);
        } catch (final NumberFormatException nfe) {
            throw ParseException.of("Expected a byte value, got {0}.", string).causedBy(nfe);
        }
    }

    private static double parseDouble(final String string) throws ParseException {
        try {
            return Double.valueOf(string);
        } catch (final NumberFormatException nfe) {
            throw ParseException.of("Expected a byte value, got {0}.", string).causedBy(nfe);
        }
    }

    private static int asInteger(final Object obj) throws ParseException {
        if (obj instanceof Integer) {
            return (Integer) obj;
        } else {
            throw ParseException.of("Expected an integer, got {0}.", obj);
        }
    }

    private static double asDouble(final Object obj) throws ParseException {
        if (obj instanceof Integer) {
            return ((Integer) obj).doubleValue();
        } else if (obj instanceof Double) {
            return (Double) obj;
        } else {
            throw ParseException.of("Expected a floating point number, got {0}.", obj);
        }
    }

    private static boolean asBoolean(final Object obj) throws ParseException {
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else {
            throw ParseException.of("Expected a boolean, got {0}.", obj);
        }
    }

    private static Material asMaterial(final Object obj) throws ParseException {
        Optional<Material> material;
        if (obj instanceof String) {
            final String s = (String) obj;
            try {
                material = Optional.ofNullable(Material.getMaterial(Integer.valueOf(s)));
            } catch (final NumberFormatException ignored) {
                material = Optional.ofNullable(Material.getMaterial(s));
            }
        } else if (obj instanceof Integer) {
            material = Optional.ofNullable(Material.getMaterial((Integer) obj));
        } else {
            material = Optional.empty();
        }

        if (material.isPresent()) {
            return material.get();
        } else {
            throw ParseException.of("Expected a material name, got {0}.", obj);
        }
    }

    private static MaterialDataPredicate asSingleMaterialDataPredicate(final Object obj) throws ParseException {
        if (obj instanceof String) {
            final String str = (String) obj;

            if (str.contains(":")) {
                final String[] parts = str.split(":");

                if (parts.length != 2) {
                    throw ParseException.of("Expected a material data pair, got {0}.", str);
                }

                final Material typeID = asMaterial(parts[0]);
                final byte data = parseByte(parts[1]);

                return MaterialDataPredicate.single(typeID, data);
            } else {
                return MaterialDataPredicate.single(asMaterial(str));
            }
        } else {
            return MaterialDataPredicate.single(asMaterial(obj));
        }
    }

    private static MaterialDataPredicate asMaterialDataPredicateList(final Object obj) throws ParseException {
        if (obj instanceof Iterable) {
            final MaterialDataPredicate.Builder builder = new MaterialDataPredicate.Builder();
            final Iterable<?> objList = (Iterable<?>) obj;
            for (final Object element : objList) {
                builder.add(asSingleMaterialDataPredicate(element));
            }
            return builder.result();
        } else {
            throw ParseException.of("Expected a list of material data pairs, got {0}.", obj);
        }
    }

    private static MaterialDataPredicate asMaterialDataPredicate(final Object obj) throws ParseException {
        if (obj instanceof Iterable) {
            return asMaterialDataPredicateList(obj);
        } else {
            return asSingleMaterialDataPredicate(obj);
        }
    }

    private static Bound asBound(final Object object) throws ParseException {
        if (object instanceof String) {
            final String string = (String) object;
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

    private static List<Constraint> asConstrantList(final Object object) throws ParseException {
        if (!(object instanceof List)) {
            throw ParseException.of("Expected a list of constraints, got {0}", object);
        }

        final List<?> list = (List<?>) object;

        if (list.size() != 2) {
            throw ParseException.of("Expected a list of two constraints, got {0}", object);
        }

        return ImmutableList.of(Constraint.lower(asBound(list.get(0))), Constraint.upper(asBound(list.get(1))));
    }

    private static Map<MaterialDataPredicate, List<Constraint>> asRequirementList(final Object obj) throws ParseException {
        final Map<MaterialDataPredicate, List<Constraint>> returnMap = new HashMap<>();

        if (obj instanceof Map) {
            final Map<?, ?> objMap = (Map<?, ?>) obj;
            for (final Map.Entry<?, ?> entry : objMap.entrySet()) {
                // First read in the list of the blocks that type of flyblock.
                // It could be a single string (with or without a ":") or integer,
                // or it could be multiple of them.
                final MaterialDataPredicate predicate = asMaterialDataPredicate(entry.getKey());

                // Then read in the limitation values, low and high.
                final List<Constraint> list = asConstrantList(entry.getValue());

                returnMap.put(predicate, list);
            }
            return ImmutableMap.copyOf(returnMap);
        } else {
            throw ParseException.of("Expected a requirement list, got {0}.", obj);
        }
    }

    private static boolean getBooleanOr(final Map<?, ?> data, final String name, final boolean defaultValue) throws ParseException {
        if (data.containsKey(name)) {
            return asBoolean(data.get(name));
        } else {
            return defaultValue;
        }
    }

    private static double getDoubleOr(final Map<?, ?> data, final String name, final double defaultValue) throws ParseException {
        if (data.containsKey(name)) {
            return asDouble(data.get(name));
        } else {
            return defaultValue;
        }
    }

    private static int getIntegerOr(final Map<?, ?> data, final String name, final int defaultValue) throws ParseException {
        if (data.containsKey(name)) {
            return asInteger(data.get(name));
        } else {
            return defaultValue;
        }
    }

    public void parseCraftDataFromFile(final File file, final int defaultSinkRateTicks) throws FileNotFoundException, ParseException
    {
        final InputStream input = new FileInputStream(file);
        final Yaml yaml = new Yaml();
        final Map<?, ?> data = (Map<?, ?>) yaml.load(input);
        this.craftName = (String) data.get("name");

        final int maxSize = asInteger(data.get("maxSize"));
        final int minSize = asInteger(data.get("minSize"));
        this.sizeRange = new IntRange(minSize, maxSize);

        this.allowedBlocks = asMaterialDataPredicateList(data.get("allowedBlocks"));
        this.forbiddenBlocks = asMaterialDataPredicateList(data.get("forbiddenBlocks"));
        this.blockedByWater = getBooleanOr(data, "canFly", getBooleanOr(data, "blockedByWater", true));
        this.requireWaterContact = getBooleanOr(data, "requireWaterContact", false);
        this.tryNudge = getBooleanOr(data, "tryNudge", false);

        this.tickCooldown = (int) Math.ceil(20 / (asDouble(data.get("speed"))));
        if (data.containsKey("cruiseSpeed")) {
            this.cruiseTickCooldown = (int) Math.ceil(20 / (asDouble(data.get("cruiseSpeed"))));
        } else {
            this.cruiseTickCooldown = this.tickCooldown;
        }

        this.flyBlocks = asRequirementList(data.get("flyblocks"));
        this.canCruise = getBooleanOr(data, "canCruise", false);
        this.canTeleport = getBooleanOr(data, "canTeleport", false);
        this.cruiseOnPilot = getBooleanOr(data, "cruiseOnPilot", false);
        this.cruiseOnPilotVertMove = getIntegerOr(data, "cruiseOnPilotVertMove", 0);
        this.allowVerticalMovement = getBooleanOr(data, "allowVerticalMovement", true);
        this.rotateAtMidpoint = getBooleanOr(data, "rotateAtMidpoint", false);
        this.allowHorizontalMovement = getBooleanOr(data, "allowHorizontalMovement", true);
        this.allowRemoteSign = getBooleanOr(data, "allowRemoteSign", true);
        this.canStaticMove = getBooleanOr(data, "canStaticMove", false);
        this.maxStaticMove = getIntegerOr(data, "maxStaticMove", 10000);
        this.cruiseSkipBlocks = getIntegerOr(data, "cruiseSkipBlocks", 0);
        this.vertCruiseSkipBlocks = getIntegerOr(data, "vertCruiseSkipBlocks", this.cruiseSkipBlocks);
        this.halfSpeedUnderwater = getBooleanOr(data, "halfSpeedUnderwater", false);
        this.staticWaterLevel = getIntegerOr(data, "staticWaterLevel", 0);
        this.fuelBurnRate = getDoubleOr(data, "fuelBurnRate", 0.0);
        this.sinkPercent = getDoubleOr(data, "sinkPercent", 0.0);
        this.overallSinkPercent = getDoubleOr(data, "overallSinkPercent", 0.0);
        this.detectionMultiplier = getDoubleOr(data, "detectionMultiplier", 0.0);
        this.underwaterDetectionMultiplier = getDoubleOr(data, "underwaterDetectionMultiplier", this.detectionMultiplier);

        if (data.containsKey("sinkSpeed")) {
            this.sinkRateTicks = (int) Math.ceil(20 / (asDouble(data.get("sinkSpeed"))));
        } else {
            this.sinkRateTicks = defaultSinkRateTicks;
        }

        this.keepMovingOnSink = getBooleanOr(data, "keepMovingOnSink", false);
        this.smokeOnSink = getIntegerOr(data, "smokeOnSink", 0);
        this.explodeOnCrash = getDoubleOr(data, "explodeOnCrash", 0.0);
        this.collisionExplosion = getDoubleOr(data, "collisionExplosion", 0.0);

        final int minHeightLimit = getIntegerOr(data, "minHeightLimit", 0);
        final int maxHeightLimit = getIntegerOr(data, "maxHeightLimit", 255);
        this.heightRange = new IntRange(minHeightLimit, maxHeightLimit);

        this.maxHeightAboveGround = getIntegerOr(data, "maxHeightAboveGround", -1);
        this.canDirectControl = getBooleanOr(data, "canDirectControl", true);
        this.canHover = getBooleanOr(data, "canHover", false);
        this.canHoverOverWater = getBooleanOr(data, "canHoverOverWater", true);
        this.moveEntities = getBooleanOr(data, "moveEntities", true);
        this.useGravity = getBooleanOr(data, "useGravity", false);
        this.hoverLimit = getIntegerOr(data, "hoverLimit", 0);

        if (data.containsKey("harvestBlocks")) {
            this.harvestBlocks = asMaterialDataPredicateList(data.get("harvestBlocks"));
        } else {
            this.harvestBlocks = MaterialDataPredicate.none();
        }

        if (data.containsKey("harvesterBladeBlocks")) {
            this.harvesterBladeBlocks = asMaterialDataPredicateList(data.get("harvesterBladeBlocks"));
        } else {
            this.harvesterBladeBlocks = MaterialDataPredicate.none();
        }

        this.allowVerticalTakeoffAndLanding = getBooleanOr(data, "allowVerticalTakeoffAndLanding", true);
    }

    public String getCraftName() {
        return this.craftName;
    }

    public IntRange getSizeRange() {
        return this.sizeRange;
    }

    public MaterialDataPredicate getAllowedBlocks() {
        return this.allowedBlocks;
    }

    public MaterialDataPredicate getForbiddenBlocks() {
        return this.forbiddenBlocks;
    }

    public boolean blockedByWater() {
        return this.blockedByWater;
    }

    public boolean getRequireWaterContact() {
        return this.requireWaterContact;
    }

    public boolean getCanCruise() {
        return this.canCruise;
    }

    public int getCruiseSkipBlocks() {
        return this.cruiseSkipBlocks;
    }

    public int getVertCruiseSkipBlocks() {
        return this.vertCruiseSkipBlocks;
    }

    public int maxStaticMove() {
        return this.maxStaticMove;
    }

    public int getStaticWaterLevel() {
        return this.staticWaterLevel;
    }

    public boolean getCanTeleport() {
        return this.canTeleport;
    }

    public boolean getCanStaticMove() {
        return this.canStaticMove;
    }

    public boolean getCruiseOnPilot() {
        return this.cruiseOnPilot;
    }

    public int getCruiseOnPilotVertMove() {
        return this.cruiseOnPilotVertMove;
    }

    public boolean allowVerticalMovement() {
        return this.allowVerticalMovement;
    }

    public boolean rotateAtMidpoint() {
        return this.rotateAtMidpoint;
    }

    public boolean allowHorizontalMovement() {
        return this.allowHorizontalMovement;
    }

    public boolean allowRemoteSign() {
        return this.allowRemoteSign;
    }

    public double getFuelBurnRate() {
        return this.fuelBurnRate;
    }

    public double getSinkPercent() {
        return this.sinkPercent;
    }

    public double getOverallSinkPercent() {
        return this.overallSinkPercent;
    }

    public double getDetectionMultiplier() {
        return this.detectionMultiplier;
    }

    public double getUnderwaterDetectionMultiplier() {
        return this.underwaterDetectionMultiplier;
    }

    public int getSinkRateTicks() {
        return this.sinkRateTicks;
    }

    public boolean getKeepMovingOnSink() {
        return this.keepMovingOnSink;
    }

    public double getExplodeOnCrash() {
        return this.explodeOnCrash;
    }

    public int getSmokeOnSink() {
        return this.smokeOnSink;
    }

    public double getCollisionExplosion() {
        return this.collisionExplosion;
    }

    public int getTickCooldown() {
        return this.tickCooldown;
    }

    public int getCruiseTickCooldown() {
        return this.cruiseTickCooldown;
    }

    public boolean getHalfSpeedUnderwater() {
        return this.halfSpeedUnderwater;
    }

    public boolean isTryNudge() {
        return this.tryNudge;
    }

    public Map<MaterialDataPredicate, List<Constraint>> getFlyBlocks() {
        return this.flyBlocks;
    }

    public IntRange getHeightRange() {
        return this.heightRange;
    }

    public int getMaxHeightAboveGround() {
        return this.maxHeightAboveGround;
    }

    public boolean getCanHover() {
        return this.canHover;
    }

    public boolean getCanDirectControl() {
        return this.canDirectControl;
    }

    public int getHoverLimit() {
        return this.hoverLimit;
    }

    public MaterialDataPredicate getHarvestBlocks() {
        return this.harvestBlocks;
    }

    public MaterialDataPredicate getHarvesterBladeBlocks() {
        return this.harvesterBladeBlocks;
    }

    public boolean getCanHoverOverWater() {
        return this.canHoverOverWater;
    }

    public boolean getMoveEntities() {
        return this.moveEntities;
    }

    public boolean getUseGravity() {
        return this.useGravity;
    }

    public boolean allowVerticalTakeoffAndLanding() {
        return this.allowVerticalTakeoffAndLanding;
    }

    public boolean isAllowedBlock(final int blockId, final byte data) {
        return this.allowedBlocks.check(new MaterialData(blockId, data));
    }

    public boolean isForbiddenBlock(final int blockId, final byte data) {
        return this.forbiddenBlocks.check(new MaterialData(blockId, data));
    }
}
