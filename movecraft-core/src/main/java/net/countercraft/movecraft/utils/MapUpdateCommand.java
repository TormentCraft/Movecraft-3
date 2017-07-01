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

package net.countercraft.movecraft.utils;

import com.alexknvl.shipcraft.math.BlockVec;
import com.alexknvl.shipcraft.math.RotationXZ;
import net.countercraft.movecraft.craft.Craft;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Class that stores the data about a single blocks changes to the map in an unspecified world. The world is
 * retrieved contextually from the submitting craft.
 */
@Immutable
public class MapUpdateCommand {
    @Nullable public final BlockVec blockLocation;
    public final BlockVec newBlockLocation;
    public final int typeID;
    public final byte dataID;
    @Nullable public final Object worldEditBaseBlock;
    public final RotationXZ rotation;
    public final Craft craft;
    public final int smoke;

    public MapUpdateCommand(final BlockVec blockLocation, final BlockVec newBlockLocation,
                            final int typeID, final byte dataID,
                            final RotationXZ rotation, final Craft craft)
    {
        this.blockLocation = blockLocation;
        this.newBlockLocation = newBlockLocation;
        this.typeID = typeID;
        this.dataID = dataID;
        this.worldEditBaseBlock = null;
        this.rotation = rotation;
        this.craft = craft;
        this.smoke = 0;
    }

    public MapUpdateCommand(final BlockVec blockLocation, final BlockVec newBlockLocation,
                            final int typeID, final byte dataID, final Craft craft)
    {
        this.blockLocation = blockLocation;
        this.newBlockLocation = newBlockLocation;
        this.typeID = typeID;
        this.dataID = dataID;
        this.worldEditBaseBlock = null;
        this.rotation = RotationXZ.None$.MODULE$;
        this.craft = craft;
        this.smoke = 0;
    }

    public MapUpdateCommand(final BlockVec newBlockLocation, final int typeID, final byte dataID, final Craft craft) {
        this.blockLocation = null;
        this.newBlockLocation = newBlockLocation;
        this.typeID = typeID;
        this.dataID = dataID;
        this.worldEditBaseBlock = null;
        this.rotation = RotationXZ.None$.MODULE$;
        this.craft = craft;
        this.smoke = 0;
    }

    public MapUpdateCommand(final BlockVec newBlockLocation, final int typeID, final byte dataID,
                            final Object worldEditBaseBlock, final Craft craft)
    {
        this.blockLocation = null;
        this.newBlockLocation = newBlockLocation;
        this.typeID = typeID;
        this.dataID = dataID;
        this.worldEditBaseBlock = worldEditBaseBlock;
        this.rotation = RotationXZ.None$.MODULE$;
        this.craft = craft;
        this.smoke = 0;
    }

    public MapUpdateCommand(final BlockVec newBlockLocation, final int typeID, final byte dataID,
                            final Craft craft, final int smoke) {
        this.blockLocation = null;
        this.newBlockLocation = newBlockLocation;
        this.typeID = typeID;
        this.dataID = dataID;
        this.worldEditBaseBlock = null;
        this.rotation = RotationXZ.None$.MODULE$;
        this.craft = craft;
        this.smoke = smoke;
    }
}

