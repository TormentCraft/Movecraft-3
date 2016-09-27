package net.countercraft.movecraft.utils;

import com.google.common.collect.ImmutableSet;
import net.countercraft.movecraft.api.MaterialDataPredicate;
import net.minecraft.server.v1_10_R1.Item;
import net.minecraft.server.v1_10_R1.ItemStack;
import org.bukkit.Material;
import org.bukkit.material.MaterialData;

public final class BlockNames {

    public static String properCase(String text) {
        char[] chars = text.toCharArray();
        boolean makeUpper = true;
        for (int ix = 0; ix < chars.length; ix++) {
            char ch = makeUpper ? Character.toUpperCase(chars[ix]) : Character.toLowerCase(chars[ix]);
            makeUpper = !Character.isLetter(ch);
            chars[ix] = makeUpper ? ' ' : ch;
        }

        return new String(chars).replaceAll("\\s\\s+", " ").trim();
    }

    public static ImmutableSet<String> materialDataPredicateNames(MaterialDataPredicate predicate) {
        ImmutableSet.Builder<String> builder = ImmutableSet.builder();

        for (Material material : predicate.allMaterials()) {
            builder.add(itemName(material));
        }
        for (MaterialData materialDataPair : predicate.allMaterialDataPairs()) {
            builder.add(itemName(materialDataPair));
        }

        return builder.build();
    }

    private static String itemName(Material material, byte data, boolean hasData) {
        String tmp = null;

        try {
            tmp = new ItemStack(Item.getById(material.getId()), 0, data).getName();
        } catch (Exception e) {
        }

        if (tmp == null || tmp.isEmpty()) {
            tmp = material.name();
        }

        tmp = properCase(tmp);
        tmp = tmp.replaceAll("\\s(On|Off)$", "");
        if (!hasData && tmp.startsWith("White ")) tmp = tmp.substring(6);
        return tmp;
    }

    public static String itemName(MaterialData materialData) {
        return itemName(materialData.getItemType(), materialData.getData(), true);
    }

    public static String itemName(Material material, byte data) {
        return itemName(material, data, true);
    }

    public static String itemName(Material material) {
        return itemName(material, (byte) 0, false);
    }

    private static final ImmutableSet<Material> COLORED_MATERIALS = ImmutableSet
            .of(Material.WOOL, Material.CARPET, Material.STAINED_GLASS, Material.STAINED_GLASS_PANE,
                Material.STAINED_CLAY);

    public static ImmutableSet<String> itemNames(Material blk) {
        // Wool, Carpet, Stained Glass, Glass Pane, Clay
        if (COLORED_MATERIALS.contains(blk)) {
            return ImmutableSet.of(itemName(blk));
        }

        ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        for (byte data = 0; data < 16; data++) {
            builder.add(itemName(blk, data, true));
        }
        return builder.build();
    }

    public static ImmutableSet<String> itemNames(MaterialData blk) {
        return ImmutableSet.of(itemName(blk));
    }
}
