package net.countercraft.movecraft.listener;

import java.util.*;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.CraftType;
import net.minecraft.server.v1_10_R1.Item;
import net.minecraft.server.v1_10_R1.ItemStack;

public class CraftHelpListener implements CommandExecutor {

	private CraftType getCraftByName(String string) {
		CraftType[] crafts = CraftManager.getInstance().getCraftTypes();
		if (crafts == null || string == null || string.length() == 0) {
			return null;
		}

		List<CraftType> found = new ArrayList<CraftType>();
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

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (args.length == 0 || args[0].length() == 0) {
			args = new String[] { "list" };
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
		if (args[0].equalsIgnoreCase("reload")) {
			CraftManager.getInstance().initCraftTypes();
			sender.sendMessage(ChatColor.GOLD + "Craft configuration reloaded.");
			return true;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("&6==========[ &f" + c.getCraftName() + "&6 ]==========\n");
		sb.append("\n&6Size: &7" + c.getMinSize() + " to " + c.getMaxSize());
		sb.append("\n&6Speed: &7" + Math.round(20.0 / c.getTickCooldown()) + " to " + (Math.round(20.0 / c.getCruiseTickCooldown()) * c.getCruiseSkipBlocks()));
		sb.append("\n&6Altitude: &7" + c.getMinHeightLimit() + " to " + c.getMaxHeightLimit());
		if (c.getFuelBurnRate() > 0) {
			sb.append("\n&6Fuel Use: &7" + c.getFuelBurnRate());
		}
		if (c.getRequireWaterContact()) {
			sb.append("\n&6Requires Water: &7YES");
		}
		StringBuilder req = new StringBuilder(), limit = new StringBuilder();
		appendFlyBlocks(req, limit, c.getFlyBlocks());
		if (req.length() > 0) {
			sb.append("\n&6Requirements: &7" + req.toString());
		}
		if (limit.length() > 0) {
			sb.append("\n&6Constraints: &7" + limit.toString());
		}
		
		sb.append("\n&6Allowed Blocks: &7");
		Integer[] blockIds = c.getAllowedBlocks();
		Set<String> blockList = new HashSet<String>();
		for (int ix = 0; ix < blockIds.length; ix++) {
			itemNames(blockIds[ix], blockList);
		}
		String[] names = blockList.toArray(new String[blockList.size()]);
		Arrays.sort(names);
		sb.append(String.join(", ", names));
		
		sender.sendMessage(ChatColor.translateAlternateColorCodes('&', sb.toString()));
		return true;
	}

	private String properCase(String text) {
		char[] chars = text.toCharArray();
		boolean makeUpper = true;
		for (int ix = 0; ix < chars.length; ix++) {
			char ch = makeUpper ? Character.toUpperCase(chars[ix]) : Character.toLowerCase(chars[ix]);
			makeUpper = !Character.isLetter(ch);
			chars[ix] = makeUpper ? ' ' : ch;
		}

		return new String(chars).replaceAll("\\s\\s+", " ").trim();
	}

	private String itemName(int mvcftId) {
		int blockData = 0, blockId = mvcftId;
		boolean hasData = false;
		if (blockId > 10000) {
			blockId -= 10000;
			blockData = blockId & 0x0F;
			blockId = blockId >> 4;
			hasData = true;
		}
		String tmp = null;
		try {
			ItemStack stck = new ItemStack(Item.getById(blockId), 0, blockData);
			tmp = stck == null ? null : stck.getName();
		}
		catch (Exception e) {}
		if (tmp == null || tmp.length() == 0) {
			tmp = Material.getMaterial(blockId).name();
		}
		tmp = properCase(tmp);
		tmp = tmp.replaceAll("\\s(On|Off)$", "");
		if (!hasData && tmp.startsWith("White "))
			tmp = tmp.substring(6);
		return tmp;
	}

	private void itemNames(int blk, Set<String> blockList) {
		if (blk < 10000) {
			// Wool, Carpet, Stained Glass, Glass Pane, Clay
			if (blk == 35 || blk == 95 || blk == 159 || blk == 160 || blk == 171) {
				blockList.add(itemName(blk));
				return;
			}
			for (int ix = 0; ix < 16; ix++) {
				int shiftedID = (blk<<4) + ix + 10000;
				blockList.add(itemName(shiftedID));
			}
		}
		else {
			blockList.add(itemName(blk));
		}
	}

	private void appendFlyBlocks(StringBuilder sbReq, StringBuilder sbLimit,
			HashMap<ArrayList<Integer>, ArrayList<Double>> flyBlocks) {
		for (ArrayList<Integer> i : flyBlocks.keySet()) {
			String blockName = itemName(i.get(0));
			Double minPercentage = flyBlocks.get(i).get(0);
			Double maxPercentage = flyBlocks.get(i).get(1);

			if (minPercentage > 0.01) {
				if (sbReq.length() > 0)
					sbReq.append(", ");
				if (minPercentage < 10000.0) {
					sbReq.append(String.format("%.2f%% %s", minPercentage, blockName));
				} else {
					minPercentage -= 10000.0;
					sbReq.append(String.format("%d %s", minPercentage.intValue(), blockName));
				}
			}
			if (maxPercentage.intValue() < 100 || (maxPercentage.intValue() >= 10000 && maxPercentage.intValue() < 10100)) {
				if (sbLimit.length() > 0)
					sbLimit.append(", ");
				if (maxPercentage < 10000.0) {
					sbLimit.append(String.format("%s <= %.2f%%", blockName, maxPercentage));
				} else {
					maxPercentage -= 10000.0;
					sbLimit.append(String.format("%s <= %d", blockName, maxPercentage.intValue()));
				}
			}
		}
	}

	private void doCraftList(CommandSender sender) {
		CraftType[] crafts = CraftManager.getInstance().getCraftTypes();
		if (crafts == null)
			crafts = new CraftType[0];
		String[] names = new String[crafts.length];
		for (int ix = 0; ix < crafts.length; ix++) {
			names[ix] = crafts[ix].getCraftName();
		}

		sender.sendMessage(ChatColor.GOLD + "Craft Types: " + ChatColor.GRAY + String.join(", ", names));
	}

}
