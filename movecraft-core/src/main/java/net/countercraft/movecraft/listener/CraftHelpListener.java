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

    public CraftHelpListener(final CraftManager craftManager) {
        this.craftManager = craftManager;
    }

    private CraftType getCraftByName(final String name) {
        final CraftType[] crafts = this.craftManager.getCraftTypes();
        if (crafts == null || name == null || name.isEmpty()) {
            return null;
        }

        final List<CraftType> found = new ArrayList<>();
        final String prefix = name.toLowerCase();
        for (final CraftType tcraft : crafts) {
            if (tcraft.getCraftName().toLowerCase().startsWith(prefix)) {
                found.add(tcraft);
            }
        }

        if (found.size() == 1) {
            return found.get(0);
        }

        return null;
    }

    @SuppressWarnings("unused")
    @Override public boolean onCommand(final CommandSender sender, final Command cmd, final String label, String[] args) {
        if (args.length == 0 || args[0].isEmpty()) {
            args = new String[]{"list"};
        }

        if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("movecraftadmin.reload")) {
            this.craftManager.initCraftTypes();
            sender.sendMessage(ChatColor.GOLD + "Craft configuration reloaded.");
            return true;
        }

        final boolean doList = args[0].equalsIgnoreCase("list");
        CraftType craftType = null;
        if (!doList) {
            craftType = getCraftByName(args[0]);
            if (craftType == null) {
                sender.sendMessage(ChatColor.RED + "Unable to locate a craft by that name.");
            }
        }
        if (doList || craftType == null) {
            doCraftList(sender);
            return true;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("&6==========[ &f").append(craftType.getCraftName()).append("&6 ]==========\n");
        sb.append("\n&6Size: &7").append(craftType.getSizeRange().min).append(" to ").append(craftType.getSizeRange().max);
        sb.append("\n&6Speed: &7").append(Math.round(20.0 / craftType.getTickCooldown())).append(" to ")
          .append(Math.round(20.0 / craftType.getCruiseTickCooldown()) * craftType.getCruiseSkipBlocks());
        sb.append("\n&6Altitude: &7").append(craftType.getHeightRange().min).append(" to ").append(craftType.getHeightRange().max);
        if (craftType.getFuelBurnRate() > 0) {
            sb.append("\n&6Fuel Use: &7").append(craftType.getFuelBurnRate());
        }
        if (craftType.getRequireWaterContact()) {
            sb.append("\n&6Requires Water: &7YES");
        }
        final List<String> req = new ArrayList<>();
        final List<String> limit = new ArrayList<>();
        appendFlyBlocks(req, limit, craftType.getFlyBlocks());
        if (!req.isEmpty()) {
            sb.append("\n&6Requirements: &7").append(Joiner.on(", ").join(req));
        }
        if (!limit.isEmpty()) {
            sb.append("\n&6Constraints: &7").append(Joiner.on(", ").join(limit));
        }

        sb.append("\n&6Allowed Blocks: &7");
        final MaterialDataPredicate blockIds = craftType.getAllowedBlocks();
        final Set<String> blockList = new HashSet<>();
        for (final Material material : blockIds.allMaterials()) {
            blockList.addAll(BlockNames.itemNames(material));
        }
        for (final MaterialData materialDataPair : blockIds.allMaterialDataPairs()) {
            blockList.addAll(BlockNames.itemNames(materialDataPair));
        }

        final String[] names = blockList.toArray(new String[blockList.size()]);
        Arrays.sort(names);
        sb.append(String.join(", ", names));

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', sb.toString()));
        return true;
    }

    private void appendFlyBlocks(final List<String> sbReq, final List<String> sbLimit,
                                 final Map<MaterialDataPredicate, List<CraftType.Constraint>> flyBlocks)
    {
        for (final Map.Entry<MaterialDataPredicate, List<CraftType.Constraint>> entry : flyBlocks.entrySet()) {
            final MaterialDataPredicate predicate = entry.getKey();
            final List<CraftType.Constraint> constraints = entry.getValue();
            final String name = Joiner.on(" or ").join(BlockNames.materialDataPredicateNames(predicate));

            for (final CraftType.Constraint constraint : constraints) {
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

    private void doCraftList(final CommandSender sender) {
        CraftType[] crafts = this.craftManager.getCraftTypes();
        if (crafts == null) crafts = new CraftType[0];
        final String[] names = new String[crafts.length];
        for (int ix = 0; ix < crafts.length; ix++) {
            names[ix] = crafts[ix].getCraftName();
        }

        sender.sendMessage(ChatColor.GOLD + "Craft Types: " + ChatColor.GRAY + String.join(", ", names));
    }
}
