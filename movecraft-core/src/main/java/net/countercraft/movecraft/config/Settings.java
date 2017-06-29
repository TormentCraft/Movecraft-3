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

package net.countercraft.movecraft.config;

public final class Settings {
    public boolean IGNORE_RESET = false;
    public boolean Debug = false;
    public String LOCALE;
    public int PilotTool = 280;
    public boolean CompatibilityMode = false;
    public int SinkRateTicks = 20;
    public double SinkCheckTicks = 100.0;
    public double TracerRateTicks = 5.0;
    public boolean WorldGuardBlockMoveOnBuildPerm = false;
    public boolean WorldGuardBlockSinkOnPVPPerm = false;
    public boolean ProtectPilotedCrafts = false;
    public boolean DisableSpillProtection = false;
    public boolean RequireCreatePerm = false;
    public boolean TNTContactExplosives = true;
    public int FadeWrecksAfter = 0;
    public int ManOverBoardTimeout = 60;
    public int FireballLifespan = 6;
    public int RepairTicksPerBlock = 0;
    public int BlockQueueChunkSize = 1000;
    public double RepairMoneyPerBlock = 0.0;
    public boolean FireballPenetration = true;
    public boolean AllowCrewSigns = true;
    public boolean SetHomeToCrewSign = true;
    public boolean WGCustomFlagsUsePilotFlag = false;
    public boolean WGCustomFlagsUseMoveFlag = false;
    public boolean WGCustomFlagsUseRotateFlag = false;
    public boolean WGCustomFlagsUseSinkFlag = false;
}
