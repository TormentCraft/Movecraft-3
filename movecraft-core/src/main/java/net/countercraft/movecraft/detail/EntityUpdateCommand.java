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

package net.countercraft.movecraft.detail;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import javax.annotation.Nullable;

/**
 * Class that stores the data about a single blocks changes to the map in an unspecified world. The world is
 * retrieved contextually from the submitting craft.
 */
public class EntityUpdateCommand {
    @Nullable
    public final Location location;
    public final Location newLocation;
    public final Entity entity;

    public EntityUpdateCommand(final Location blockLocation, final Location newLocation, final Entity entity) {
        this.location = blockLocation;
        this.newLocation = newLocation;
        this.entity = entity;
    }
}

