package net.countercraft.movecraft.api;

public class IntRange {
    public final int min;
    public final int max;

    public IntRange(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public IntRange translate(int offset) {
        return new IntRange(min + offset, max + offset);
    }

    public boolean isInside(int value) {
        return min <= value && value <= max;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IntRange intRange = (IntRange) o;

        return min == intRange.min && max == intRange.max;
    }

    @Override public int hashCode() {
        int result = min;
        result = 31 * result + max;
        return result;
    }

    @Override public String toString() {
        return "IntRange{" +
               "min=" + min +
               ", max=" + max +
               '}';
    }
}
