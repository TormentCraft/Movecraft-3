package net.countercraft.movecraft;

import org.bukkit.permissions.Permission;

public final class Permissions {
    public static final String CREATE_CRUISE_SIGN = "movecraft.cruisesign";
    public static final String COMMANDS        = "movecraft.commands";
    public static final String COMMAND_RELEASE = "movecraft.commands.release";
    public static final String COMMAND_PILOT   = "movecraft.commands.pilot";


    public static String CREATE(final String name) {
        return String.format("movecraft.%s.create", name);
    }
    public static String PILOT(final String name) {

        return String.format("movecraft.%s.pilot", name);
    }
}
