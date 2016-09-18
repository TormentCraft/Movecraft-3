package net.countercraft.movecraft.api;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.material.MaterialData;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class MaterialDataPredicate {
    private MaterialDataPredicate() {
    }

    public abstract boolean check(MaterialData materialData);

    public abstract boolean isTrivial();

    public boolean checkBlock(Block block) {
        return check(block.getState().getData());
    }

    private static final None noneValue = new None();
    private static final All allValue = new All();

    public static MaterialDataPredicate none() {
        return noneValue;
    }

    public static MaterialDataPredicate all() {
        return allValue;
    }

    public static MaterialDataPredicate single(Material material) {
        return new SingleMaterial(material);
    }

    public static MaterialDataPredicate single(MaterialData materialData) {
        return new SingleMaterialData(materialData);
    }

    public static MaterialDataPredicate many(Set<Material> materials, Set<MaterialData> materialDataPairs) {
        if (materials.isEmpty() && materialDataPairs.isEmpty()) {
            return noneValue;
        } else if (materials.size() == 1 && materialDataPairs.isEmpty()) {
            return new SingleMaterial(materials.toArray(new Material[1])[0]);
        } else if (materialDataPairs.size() == 1 && materials.isEmpty()) {
            return new SingleMaterialData(materialDataPairs.toArray(new MaterialData[1])[0]);
        } else {
            return new Many(materials, materialDataPairs);
        }
    }

    private static final class None extends MaterialDataPredicate {
        public None() {
        }

        @Override public boolean check(MaterialData materialData) {
            return false;
        }

        @Override public boolean isTrivial() {
            return true;
        }

        @Override public String toString() {
            return "()";
        }
    }

    private static final class All extends MaterialDataPredicate {
        public All() {
        }

        @Override public boolean check(MaterialData materialData) {
            return materialData.getItemType() != Material.AIR;
        }

        @Override public boolean isTrivial() {
            return false;
        }

        @Override public String toString() {
            return "(*)";
        }
    }

    private static final class SingleMaterial extends MaterialDataPredicate {
        public final Material material;

        public SingleMaterial(Material material) {
            Preconditions.checkNotNull(material);
            this.material = material;
        }

        @Override public boolean check(MaterialData materialData) {
            return material == materialData.getItemType();
        }

        @Override public boolean isTrivial() {
            return false;
        }

        @Override public int hashCode() {
            return material.hashCode();
        }

        @Override public String toString() {
            return "(" + material + ')';
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SingleMaterial that = (SingleMaterial) o;
            return material == that.material;
        }
    }

    private static final class SingleMaterialData extends MaterialDataPredicate {
        public final MaterialData materialData;

        public SingleMaterialData(MaterialData materialData) {
            Preconditions.checkNotNull(materialData);
            this.materialData = materialData;
        }

        @Override public boolean check(MaterialData materialData) {
            return Objects.equals(this.materialData, materialData);
        }

        @Override public boolean isTrivial() {
            return false;
        }

        @Override public int hashCode() {
            return materialData.hashCode();
        }

        @Override public String toString() {
            return "(" + materialData + ')';
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SingleMaterialData that = (SingleMaterialData) o;

            return materialData.equals(that.materialData);
        }
    }

    private static final class Many extends MaterialDataPredicate {
        public final ImmutableSet<Material> materials;
        public final ImmutableSet<MaterialData> materialDataPairs;

        public Many(Set<Material> materials, Set<MaterialData> materialDataPairs) {
            Preconditions.checkNotNull(materials);
            Preconditions.checkNotNull(materialDataPairs);
            Preconditions.checkArgument(materials.size() + materialDataPairs.size() > 1,
                                        "Use SingleMaterial or SingleMaterialData for predicates with a single " +
                                        "element.");
            this.materials = ImmutableSet.copyOf(materials);
            this.materialDataPairs = ImmutableSet.copyOf(materialDataPairs);
        }

        @Override public boolean check(MaterialData materialData) {
            return materials.contains(materialData.getItemType()) || materialDataPairs.contains(materialData);
        }

        @Override public boolean isTrivial() {
            return false;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Many that = (Many) o;

            return materials.equals(that.materials) && materialDataPairs.equals(that.materialDataPairs);
        }

        @Override public int hashCode() {
            int result = materials.hashCode();
            result = 31 * result + materialDataPairs.hashCode();
            return result;
        }

        private static final int AVERAGE_NAME_LENGTH = 10;
        private static final Joiner joiner = Joiner.on(" | ");

        @Override public String toString() {
            StringBuilder result = new StringBuilder(
                    AVERAGE_NAME_LENGTH * (materials.size() + materialDataPairs.size()));

            result.append('(');
            joiner.appendTo(result, Iterables.concat(materials, materialDataPairs));
            result.append(')');

            return result.toString();
        }
    }

    public static class Builder {
        private final EnumSet<Material> materials;
        private final HashSet<MaterialData> materialDataPairs;
        private boolean all;

        public Builder() {
            this.materials = EnumSet.noneOf(Material.class);
            this.materialDataPairs = new HashSet<>();
            this.all = false;
        }

        public void add(Material material) {
            if (!all) materials.add(material);
        }

        public void add(MaterialData materialDataPair) {
            if (!all) materialDataPairs.add(materialDataPair);
        }

        public void add(MaterialDataPredicate predicate) {
            if (!all) {
                if (predicate instanceof SingleMaterial) {
                    materials.add(((SingleMaterial) predicate).material);
                } else if (predicate instanceof SingleMaterialData) {
                    materialDataPairs.add(((SingleMaterialData) predicate).materialData);
                } else if (predicate instanceof Many) {
                    materials.addAll(((Many) predicate).materials);
                    materialDataPairs.addAll(((Many) predicate).materialDataPairs);
                } else if (predicate instanceof All) {
                    all = true;
                    materials.clear();
                    materialDataPairs.clear();
                }
            }
        }

        public MaterialDataPredicate result() {
            if (all) return all();
            else return many(materials, materialDataPairs);
        }
    }
}