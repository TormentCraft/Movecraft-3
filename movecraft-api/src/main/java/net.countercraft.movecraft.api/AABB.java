package net.countercraft.movecraft.api;

import com.google.common.base.Preconditions;

import java.util.Collection;

public final class AABB {
    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;

    public AABB(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public AABB translate(BlockVec vec) {
        return new AABB(
                minX + vec.x,
                minY + vec.y,
                minZ + vec.z,
                maxX + vec.x,
                maxY + vec.y,
                maxZ + vec.z);
    }

    public IntRange xRange() {
        return new IntRange(minX, maxX);
    }
    public IntRange yRange() {
        return new IntRange(minY, maxY);
    }
    public IntRange zRange() {
        return new IntRange(minZ, maxZ);
    }

    public int xSize() {
        return maxX - minX + 1;
    }
    public int ySize() {
        return maxY - minY + 1;
    }
    public int zSize() {
        return maxZ - minZ + 1;
    }

    public BlockVec getCenterBlock() {
        return new BlockVec(
                minX + (maxX - minX) / 2,
                minY + (maxY - minY) / 2,
                minZ + (maxZ - minZ) / 2
        );
    }

    public BlockVec getMinBlock() {
        return new BlockVec(minX, minY, minZ);
    }

    public boolean contains(BlockVec that) {
        return this.minX <= that.x && that.x <= this.maxX &&
               this.minY <= that.x && that.x <= this.maxY &&
               this.minZ <= that.x && that.x <= this.maxZ;
    }

    public boolean contains(AABB that) {
        return this.minX <= that.minX && that.maxX <= this.maxX &&
               this.minY <= that.minY && that.maxY <= this.maxY &&
               this.minZ <= that.minZ && that.maxZ <= this.maxZ;
    }

    public boolean intersects(AABB that) {
        return (Math.abs(this.minX - that.minX) * 2 < (this.xSize() + that.xSize())) &&
               (Math.abs(this.minY - that.minY) * 2 < (this.ySize() + that.ySize())) &&
               (Math.abs(this.minZ - that.minZ) * 2 < (this.zSize() + that.zSize()));
    }

    public static AABB fromTwoCorners(BlockVec c1, BlockVec c2) {
        Preconditions.checkNotNull(c1);
        Preconditions.checkNotNull(c2);

        return new AABB(
                Math.min(c1.x, c2.x), Math.min(c1.y, c2.y), Math.min(c1.z, c2.z),
                Math.max(c1.x, c2.x), Math.max(c1.y, c2.y), Math.max(c1.z, c2.z));
    }

    public static AABB from(Collection<BlockVec> vecs) {
        return new Builder().addAll(vecs).result();
    }

    @Override
    public String toString() {
        return "AABB{" +
               "x=[" + minX + ", " + maxX + ']' +
               ", y=[" + minY + ", " + maxY + ']' +
               ", z=[" + minZ + ", " + maxZ + ']' + '}';
    }

    public AABB rotate(BlockVec around, Rotation rotation) {
        BlockVec c1 = new BlockVec(minX, minY, minZ);
        BlockVec c2 = new BlockVec(maxX, maxY, maxZ);

        return fromTwoCorners(
                c1.subtract(around).rotate(rotation).add(around),
                c2.subtract(around).rotate(rotation).add(around));
    }

    public static final class Builder {
        private int count;
        private int minX;
        private int minY;
        private int minZ;
        private int maxX;
        private int maxY;
        private int maxZ;

        public Builder() {
            count = 0;
        }

        public Builder add(BlockVec p) {
            Preconditions.checkNotNull(p);

            if (count == 0) {
                minX = maxX = p.x;
                minY = maxY = p.y;
                minZ = maxZ = p.z;
            } else {
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
                minZ = Math.min(minZ, p.z);
                maxX = Math.max(maxX, p.x);
                maxY = Math.max(maxY, p.y);
                maxZ = Math.max(maxZ, p.z);
            }

            count += 1;

            return this;
        }

        public Builder addAll(BlockVec[] blockPositions) {
            Preconditions.checkNotNull(blockPositions);

            for (BlockVec p : blockPositions) {
                add(p);
            }
            return this;
        }

        public Builder addAll(Collection<BlockVec> blockPositions) {
            Preconditions.checkNotNull(blockPositions);

            for (BlockVec p : blockPositions) {
                add(p);
            }
            return this;
        }

        public AABB result() {
            Preconditions.checkState(count > 0);
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public int getCount() {
            return count;
        }

        public int getMinX() {
            return minX;
        }

        public int getMinY() {
            return minY;
        }

        public int getMinZ() {
            return minZ;
        }

        public int getMaxX() {
            return maxX;
        }

        public int getMaxY() {
            return maxY;
        }

        public int getMaxZ() {
            return maxZ;
        }
    }
}