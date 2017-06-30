package net.countercraft.movecraft.utils;

import com.google.common.collect.ImmutableSet;
import net.countercraft.movecraft.api.MaterialDataPredicate;
import net.minecraft.server.v1_12_R1.Item;
import net.minecraft.server.v1_12_R1.ItemStack;
import org.bukkit.Material;
import org.bukkit.material.MaterialData;

public final class BlockNames {
    public static String properCase(final String text) {
        final char[] chars = text.toCharArray();
        boolean makeUpper = true;
        for (int ix = 0; ix < chars.length; ix++) {
            final char ch = makeUpper ? Character.toUpperCase(chars[ix]) : Character.toLowerCase(chars[ix]);
            makeUpper = !Character.isLetter(ch);
            chars[ix] = makeUpper ? ' ' : ch;
        }

        return new String(chars).replaceAll("\\s\\s+", " ").trim();
    }

    public static ImmutableSet<String> materialDataPredicateNames(final MaterialDataPredicate predicate) {
        final ImmutableSet.Builder<String> builder = ImmutableSet.builder();

        for (final Material material : predicate.allMaterials()) {
            builder.add(itemName(material));
        }
        for (final MaterialData materialDataPair : predicate.allMaterialDataPairs()) {
            builder.add(itemName(materialDataPair));
        }

        return builder.build();
    }

    private static String itemName(final Material material, final byte data, final boolean hasData) {
        String tmp = null;

        try {
            tmp = new ItemStack(Item.getById(material.getId()), 1, data).getName();
        } catch (final Exception ignored) { }

        if (tmp == null || tmp.isEmpty()) {
            tmp = material.name();
        }

        tmp = properCase(tmp);
        tmp = tmp.replaceAll("\\s(On|Off)$", "");
        if (!hasData && tmp.startsWith("White ")) tmp = tmp.substring(6);
        return tmp;
    }

    public static String itemName(final MaterialData materialData) {
        return itemName(materialData.getItemType(), materialData.getData(), true);
    }

    public static String itemName(final Material material, final byte data) {
        return itemName(material, data, true);
    }

    public static String itemName(final Material material) {
        return itemName(material, (byte) 0, false);
    }

    private static final ImmutableSet<Material> COLORED_MATERIALS = ImmutableSet
            .of(Material.WOOL, Material.CARPET, Material.STAINED_GLASS, Material.STAINED_GLASS_PANE,
                Material.STAINED_CLAY);

    public static ImmutableSet<String> itemNames(final Material blk) {
        // Wool, Carpet, Stained Glass, Glass Pane, Clay
        if (COLORED_MATERIALS.contains(blk)) {
            return ImmutableSet.of(itemName(blk));
        }

        final ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (byte data = 0; data < 16; data++) {
            builder.add(itemName(blk, data, true));
        }
        return builder.build();
    }

    public static ImmutableSet<String> itemNames(final MaterialData blk) {
        return ImmutableSet.of(itemName(blk));
    }
}
