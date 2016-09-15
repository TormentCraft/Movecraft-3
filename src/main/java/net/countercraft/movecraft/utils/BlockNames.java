package net.countercraft.movecraft.utils;

import net.minecraft.server.v1_10_R1.Item;
import net.minecraft.server.v1_10_R1.ItemStack;
import org.bukkit.Material;

import java.util.Set;

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

    public static String itemName(int blockId, int blockData, boolean hasData) {
        String tmp = null;
        try {
            ItemStack stack = new ItemStack(Item.getById(blockId), 0, blockData);
            tmp = stack == null ? null : stack.getName();
        } catch (Exception e) {
        }
        if (tmp == null || tmp.isEmpty()) {
            tmp = Material.getMaterial(blockId).name();
        }
        tmp = properCase(tmp);
        tmp = tmp.replaceAll("\\s(On|Off)$", "");
        if (!hasData && tmp.startsWith("White ")) tmp = tmp.substring(6);
        return tmp;
    }

    public static String itemName(int movecraftId) {
        int blockData = 0, blockId = movecraftId;
        boolean hasData = false;
        if (blockId > 10000) {
            blockId -= 10000;
            blockData = blockId & 0x0F;
            blockId = blockId >> 4;
            hasData = true;
        }
        return itemName(blockId, blockData, hasData);
    }

    public static void itemNames(int blk, Set<String> blockList) {
        if (blk < 10000) {
            // Wool, Carpet, Stained Glass, Glass Pane, Clay
            if (blk == 35 || blk == 95 || blk == 159 || blk == 160 || blk == 171) {
                blockList.add(itemName(blk));
                return;
            }
            for (int ix = 0; ix < 16; ix++) {
                int shiftedID = (blk << 4) + ix + 10000;
                blockList.add(itemName(shiftedID));
            }
        } else {
            blockList.add(itemName(blk));
        }
    }
}
