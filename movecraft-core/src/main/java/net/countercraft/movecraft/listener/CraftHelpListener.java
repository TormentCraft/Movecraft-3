package net.countercraft.movecraft.listener;

import com.google.common.base.Joiner;
import net.countercraft.movecraft.api.MaterialDataPredicate;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.CraftType;
import net.countercraft.movecraft.utils.BlockNames;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.material.MaterialData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CraftHelpListener implements CommandExecutor {
    private final CraftManager craftManager;

    public CraftHelpListener(CraftManager craftManager) {
        this.craftManager = craftManager;
    }

    private CraftType getCraftByName(String string) {
        CraftType[] crafts = craftManager.getCraftTypes();
        if (crafts == null || string == null || string.isEmpty()) {
            return null;
        }

        List<CraftType> found = new ArrayList<>();
        String prefix = string.toLowerCase();
        for (CraftType tcraft : crafts) {
            if (tcraft.getCraftName().toLowerCase().startsWith(prefix)) {
                found.add(tcraft);
            }
        }

        if (found.size() == 1) {
            return found.get(0);
        }

        return null;
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].isEmpty()) {
            args = new String[]{"list"};
        }

        if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("movecraftadmin.reload")) {
            craftManager.initCraftTypes();
            sender.sendMessage(ChatColor.GOLD + "Craft configuration reloaded.");
            return true;
        }

        boolean doList = args[0].equalsIgnoreCase("list");
        CraftType c = null;
        if (!doList) {
            c = getCraftByName(args[0]);
            if (c == null) {
                sender.sendMessage(ChatColor.RED + "Unable to locate a craft by that name.");
            }
        }
        if (doList || c == null) {
            doCraftList(sender);
            return true;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("&6==========[ &f" + c.getCraftName() + "&6 ]==========\n");
        sb.append("\n&6Size: &7" + c.getSizeRange().min + " to " + c.getSizeRange().max);
        sb.append("\n&6Speed: &7" + Math.round(20.0 / c.getTickCooldown()) + " to " +
                  (Math.round(20.0 / c.getCruiseTickCooldown()) * c.getCruiseSkipBlocks()));
        sb.append("\n&6Altitude: &7" + c.getHeightRange().min + " to " + c.getHeightRange().max);
        if (c.getFuelBurnRate() > 0) {
            sb.append("\n&6Fuel Use: &7" + c.getFuelBurnRate());
        }
        if (c.getRequireWaterContact()) {
            sb.append("\n&6Requires Water: &7YES");
        }
        List<String> req = new ArrayList<>();
        List<String> limit = new ArrayList<>();
        appendFlyBlocks(req, limit, c.getFlyBlocks());
        if (!req.isEmpty()) {
            sb.append("\n&6Requirements: &7" + Joiner.on(", ").join(req));
        }
        if (!limit.isEmpty()) {
            sb.append("\n&6Constraints: &7" + Joiner.on(", ").join(limit));
        }

        sb.append("\n&6Allowed Blocks: &7");
        MaterialDataPredicate blockIds = c.getAllowedBlocks();
        Set<String> blockList = new HashSet<>();
        for (Material material : blockIds.allMaterials()) {
            blockList.addAll(BlockNames.itemNames(material));
        }
        for (MaterialData materialDataPair : blockIds.allMaterialDataPairs()) {
            blockList.addAll(BlockNames.itemNames(materialDataPair));
        }

        String[] names = blockList.toArray(new String[blockList.size()]);
        Arrays.sort(names);
        sb.append(String.join(", ", names));

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', sb.toString()));
        return true;
    }

    private void appendFlyBlocks(List<String> sbReq, List<String> sbLimit,
                                 Map<MaterialDataPredicate, List<CraftType.Constraint>> flyBlocks)
    {
        for (Map.Entry<MaterialDataPredicate, List<CraftType.Constraint>> entry : flyBlocks.entrySet()) {
            MaterialDataPredicate predicate = entry.getKey();
            List<CraftType.Constraint> constraints = entry.getValue();
            String name = Joiner.on(" or ").join(BlockNames.materialDataPredicateNames(predicate));

            for (CraftType.Constraint constraint : constraints) {
                if (constraint.isTrivial()) continue;

                if (constraint.isUpper) {
                    if (constraint.bound.isExact()) {
                        sbLimit.add(String.format("%s <= %d", name, constraint.bound.asExact(0)));
                    } else {
                        sbLimit.add(String.format("%s <= %.2f%%", name, constraint.bound.asRatio(1) * 100.0));
                    }
                } else {
                    if (constraint.bound.isExact()) {
                        sbReq.add(String.format("%d %s", constraint.bound.asExact(0), name));
                    } else {
                        sbReq.add(String.format("%.2f%% %s", constraint.bound.asRatio(1) * 100.0, name));
                    }
                }
            }
        }
    }

    private void doCraftList(CommandSender sender) {
        CraftType[] crafts = craftManager.getCraftTypes();
        if (crafts == null) crafts = new CraftType[0];
        String[] names = new String[crafts.length];
        for (int ix = 0; ix < crafts.length; ix++) {
            names[ix] = crafts[ix].getCraftName();
        }

        sender.sendMessage(ChatColor.GOLD + "Craft Types: " + ChatColor.GRAY + String.join(", ", names));
    }
}
