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

import com.alexknvl.shipcraft.math.BlockVec;
import com.alexknvl.shipcraft.math.RotationXZ;
import com.sk89q.worldedit.blocks.BaseBlock;
import net.countercraft.movecraft.craft.Craft;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Class that stores the data about a single blocks changes to the map in an unspecified world. The world is
 * retrieved contextually from the submitting craft.
 */
@Immutable
public class MapUpdateCommand {
    private MapUpdateCommand() { }

    @Immutable
    public static final class MoveBlock extends MapUpdateCommand {
        @Nullable public final BlockVec blockLocation;
        public final BlockVec newBlockLocation;
        public final MaterialData data;
        @Nullable public final Object worldEditBaseBlock;
        public final RotationXZ rotation;
        public final Craft craft;

        public MoveBlock(final BlockVec blockLocation, final BlockVec newBlockLocation,
                         final MaterialData data, final Craft craft) {
            this.blockLocation = blockLocation;
            this.newBlockLocation = newBlockLocation;
            this.data = data;
            this.craft = craft;
            this.rotation = RotationXZ.none();
            this.worldEditBaseBlock = null;
        }

        public MoveBlock(final BlockVec newBlockLocation, final Material material, final Craft craft) {
            this.newBlockLocation = newBlockLocation;
            this.data = new MaterialData(material);
            this.craft = craft;
            this.blockLocation = null;
            this.rotation = RotationXZ.none();
            this.worldEditBaseBlock = null;
        }

        public MoveBlock(final BlockVec newBlockLocation, final Material material, final byte data, final Craft craft) {
            this.newBlockLocation = newBlockLocation;
            this.data = new MaterialData(material, data);
            this.craft = craft;
            this.blockLocation = null;
            this.rotation = RotationXZ.none();
            this.worldEditBaseBlock = null;
        }

        public MoveBlock(final BlockVec blockLocation, final BlockVec newBlockLocation, final MaterialData data,
                         final RotationXZ rotation, final Craft craft) {
            this.blockLocation = blockLocation;
            this.newBlockLocation = newBlockLocation;
            this.data = data;
            this.worldEditBaseBlock = null;
            this.rotation = rotation;
            this.craft = craft;
        }

        public MoveBlock(BlockVec blockLocation, MaterialData data, BaseBlock bb, Craft craft) {
            this.blockLocation = null;
            this.newBlockLocation = blockLocation;
            this.data = data;
            this.craft = craft;
            this.rotation = RotationXZ.none();
            this.worldEditBaseBlock = bb;
        }
    }

    @Immutable
    public static final class SpawnExplosion extends MapUpdateCommand {
        public final int explosion;
        public final BlockVec newBlockLocation;

        public SpawnExplosion(final int explosion, final BlockVec newBlockLocation) {
            this.explosion = explosion;
            this.newBlockLocation = newBlockLocation;
        }
    }

    @Immutable
    public static final class SpawnSmoke extends MapUpdateCommand {
        public final int smoke;
        public final BlockVec newBlockLocation;

        public SpawnSmoke(final int smoke, final BlockVec newBlockLocation) {
            this.smoke = smoke;
            this.newBlockLocation = newBlockLocation;
        }
    }

    /**
     * Class that stores the data about a item drops to the map in an unspecified world. The world is retrieved
     * contextually from the submitting craft.
     */
    @Immutable
    public static final class DropItem extends MapUpdateCommand {
        public final Location location;
        public final ItemStack itemStack;

        public DropItem(final Location location, final ItemStack itemStack) {
            this.location = location;
            this.itemStack = itemStack;
        }
    }

    /**
     * Class that stores the data about a single blocks changes to the map in an unspecified world. The world is
     * retrieved contextually from the submitting craft.
     */
    @Immutable
    public static final class MoveEntity extends MapUpdateCommand {
        @Nullable
        public final Location location;
        public final Location newLocation;
        public final Entity entity;

        public MoveEntity(final Location blockLocation, final Location newLocation, final Entity entity) {
            this.location = blockLocation;
            this.newLocation = newLocation;
            this.entity = entity;
        }
    }
}

