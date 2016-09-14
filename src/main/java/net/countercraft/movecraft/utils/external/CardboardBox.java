/*
 * This code is not part of the Movecraft project but belongs to NuclearW
 *
 *  Attribtuion : http://forums.bukkit.org/threads/cardboard-serializable-itemstack-with-enchantments.75768/
 */

package net.countercraft.movecraft.utils.external;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * A serializable ItemStack
 */
public class CardboardBox implements Serializable {
    private static final long serialVersionUID = 729890133797629668L;

    private final int type, amount;
    private final short damage;
    private final byte data;

    private final Map<CardboardEnchantment, Integer> enchants;

    public CardboardBox(ItemStack item) {
        this.type = item.getTypeId();
        this.amount = item.getAmount();
        this.damage = item.getDurability();
        this.data = item.getData().getData();

        Map<CardboardEnchantment, Integer> map = new HashMap<>();

        Map<Enchantment, Integer> enchantments = item.getEnchantments();

        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            map.put(new CardboardEnchantment(entry.getKey()), entry.getValue());
        }

        this.enchants = map;
    }

    public ItemStack unbox() {
        ItemStack item = new ItemStack(type, amount, damage, data);

        Map<Enchantment, Integer> map = new HashMap<>();

        for (Map.Entry<CardboardEnchantment, Integer> entry : enchants.entrySet()) {
            map.put(entry.getKey().unbox(), entry.getValue());
        }

        item.addUnsafeEnchantments(map);

        return item;
    }
}