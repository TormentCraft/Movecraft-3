package net.countercraft.movecraft.api;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.material.MaterialData;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class MaterialDataPredicate {
    private MaterialDataPredicate() {
    }

    public abstract boolean isTrivial();

    public abstract boolean check(MaterialData materialData);
    public boolean check(Material material) {
        return check(new MaterialData(material));
    }
    public boolean check(Material material, byte data) {
        return check(new MaterialData(material, data));
    }
    public boolean checkBlock(Block block) {
        return check(block.getState().getData());
    }

    public abstract Iterable<Material> allMaterials();
    public abstract Iterable<MaterialData> allMaterialDataPairs();

    private static final None NONE = new None();
    private static final AllBlocks ALL_BLOCKS = new AllBlocks();

    public static MaterialDataPredicate none() {
        return NONE;
    }

    public static MaterialDataPredicate all() {
        return ALL_BLOCKS;
    }

    public static MaterialDataPredicate single(Material material) {
        return new SingleMaterial(material);
    }

    public static MaterialDataPredicate single(MaterialData materialData) {
        return new SingleMaterialData(materialData);
    }

    public static MaterialDataPredicate single(Material material, byte data) {
        return new SingleMaterialData(new MaterialData(material, data));
    }

    public static MaterialDataPredicate many(Set<Material> materials, Set<MaterialData> materialDataPairs) {
        if (materials.isEmpty() && materialDataPairs.isEmpty()) {
            return NONE;
        } else if (materials.size() == 1 && materialDataPairs.isEmpty()) {
            return new SingleMaterial(materials.toArray(new Material[1])[0]);
        } else if (materialDataPairs.size() == 1 && materials.isEmpty()) {
            return new SingleMaterialData(materialDataPairs.toArray(new MaterialData[1])[0]);
        } else {
            return new Many(materials, materialDataPairs);
        }
    }

    private static final class None extends MaterialDataPredicate {
        @Override public boolean isTrivial() {
            return true;
        }

        @Override public boolean check(MaterialData materialData) {
            return false;
        }

        @Override public Iterable<Material> allMaterials() {
            return Collections.emptyList();
        }

        @Override public Iterable<MaterialData> allMaterialDataPairs() {
            return Collections.emptyList();
        }

        @Override public String toString() {
            return "()";
        }

        @Override public int hashCode() {
            return NONE.hashCode();
        }

        @Override public boolean equals(Object o) {
            return NONE.equals(o);
        }
    }

    private static final class AllBlocks extends MaterialDataPredicate {
        private static final ImmutableSet<Material> NON_AIR_BLOCK_MATERIALS;
        static {
            ImmutableSet.Builder<Material> builder = ImmutableSet.builder();
            for (Material material : EnumSet.allOf(Material.class)) {
                if (material != Material.AIR && material.isBlock()) {
                    builder.add(material);
                }
            }
            NON_AIR_BLOCK_MATERIALS = builder.build();
        }

        @Override public boolean isTrivial() {
            return !NON_AIR_BLOCK_MATERIALS.isEmpty();
        }

        @Override public boolean check(MaterialData materialData) {
            return materialData.getItemType() != Material.AIR && materialData.getItemType().isBlock();
        }

        @Override public Iterable<Material> allMaterials() {
            return NON_AIR_BLOCK_MATERIALS;
        }

        @Override public Iterable<MaterialData> allMaterialDataPairs() {
            return Collections.emptyList();
        }

        @Override public String toString() {
            return "(*)";
        }

        @Override public int hashCode() {
            return ALL_BLOCKS.hashCode();
        }

        @Override public boolean equals(Object o) {
            return ALL_BLOCKS.equals(o);
        }
    }

    private static final class SingleMaterial extends MaterialDataPredicate {
        public final Material material;

        public SingleMaterial(Material material) {
            Preconditions.checkNotNull(material);
            this.material = material;
        }

        @Override public boolean isTrivial() {
            return false;
        }

        @Override public boolean check(MaterialData materialData) {
            return material == materialData.getItemType();
        }

        @Override public Iterable<Material> allMaterials() {
            return ImmutableList.of(material);
        }

        @Override public Iterable<MaterialData> allMaterialDataPairs() {
            return Collections.emptyList();
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

        @Override public boolean isTrivial() {
            return false;
        }

        @Override public boolean check(MaterialData materialData) {
            return Objects.equals(this.materialData, materialData);
        }

        @Override public Iterable<Material> allMaterials() {
            return Collections.emptyList();
        }

        @Override public Iterable<MaterialData> allMaterialDataPairs() {
            return ImmutableList.of(materialData);
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

        @Override public boolean isTrivial() {
            return false;
        }

        @Override public boolean check(MaterialData materialData) {
            return materials.contains(materialData.getItemType()) || materialDataPairs.contains(materialData);
        }

        @Override public Iterable<Material> allMaterials() {
            return materials;
        }

        @Override public Iterable<MaterialData> allMaterialDataPairs() {
            return materialDataPairs;
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
                } else if (predicate instanceof AllBlocks) {
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