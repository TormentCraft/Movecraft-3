/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.countercraft.movecraft.detail;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import javax.annotation.concurrent.Immutable;

/**
 * Class that stores the data about a item drops to the map in an unspecified world. The world is retrieved
 * contextually from the submitting craft.
 */
@Immutable
public class ItemDropUpdateCommand {
    public final Location location;
    public final ItemStack itemStack;

    public ItemDropUpdateCommand(final Location location, final ItemStack itemStack) {
        this.location = location;
        this.itemStack = itemStack;
    }
}