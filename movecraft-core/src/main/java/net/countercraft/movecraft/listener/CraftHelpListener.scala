package net.countercraft.movecraft.listener

import com.google.common.base.Joiner
import com.alexknvl.shipcraft.MaterialDataPredicate
import net.countercraft.movecraft.craft.CraftManager
import net.countercraft.movecraft.craft.CraftType
import net.countercraft.movecraft.utils.BlockNames
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.material.MaterialData
import java.util

import scala.collection.JavaConverters._

class CraftHelpListener(val craftManager: CraftManager) extends CommandExecutor {
  private def getCraftByName(name: String): Option[CraftType] = {
    val prefix = name.toLowerCase
    val matches = craftManager.getCraftTypes
      .filter(t => t.getCraftName.toLowerCase.startsWith(prefix))

    if (matches.length == 1) Some(matches(0)) else None
  }

  @SuppressWarnings(Array("unused")) override def onCommand(sender: CommandSender, cmd: Command, label: String, args: Array[String]): Boolean = args match {
    case Array() | Array("") | Array("list") =>
      doCraftList(sender)
      true
    case Array("reload") =>
      if (sender.hasPermission("movecraftadmin.reload")) {
        this.craftManager.initCraftTypes()
        sender.sendMessage(ChatColor.GOLD + "Craft configuration reloaded.")
        true
      } else {
        sender.sendMessage(ChatColor.RED + "Insufficient permissions.")
        true
      }
    case Array(name) =>
      getCraftByName(name) match {
        case None =>
          sender.sendMessage(ChatColor.RED + "Unable to locate a craft by that name.")
          true
        case Some(craftType) =>
          val sb = new StringBuilder
          sb.append("&6==========[ &f")
          sb.append(craftType.getCraftName).append("&6 ]==========\n")
          sb.append("\n&6Size: &7").append(craftType.getSizeRange.min).append(" to ").append(craftType.getSizeRange.max)
          sb.append("\n&6Speed: &7").append(20.0 / craftType.getTickCooldown.round).append(" to ").append(20.0 / craftType.getCruiseTickCooldown.round * craftType.getCruiseSkipBlocks)
          sb.append("\n&6Altitude: &7").append(craftType.getHeightRange.min).append(" to ").append(craftType.getHeightRange.max)
          if (craftType.getFuelBurnRate > 0) sb.append("\n&6Fuel Use: &7").append(craftType.getFuelBurnRate)
          if (craftType.getRequireWaterContact) sb.append("\n&6Requires Water: &7YES")
          val req = new util.ArrayList[String]
          val limit = new util.ArrayList[String]
          appendFlyBlocks(req, limit, craftType.getFlyBlocks)
          if (!req.isEmpty) sb.append("\n&6Requirements: &7").append(Joiner.on(", ").join(req))
          if (!limit.isEmpty) sb.append("\n&6Constraints: &7").append(Joiner.on(", ").join(limit))
          sb.append("\n&6Allowed Blocks: &7")
          val blockIds = craftType.getAllowedBlocks
          val blockList =
            blockIds.allMaterials.flatMap(x => BlockNames.itemNames(x)) ++
              blockIds.allMaterialDataPairs.flatMap(x => BlockNames.itemNames(x))
          sb.append(blockList.toList.sorted.mkString(", "))
          sender.sendMessage(ChatColor.translateAlternateColorCodes('&', sb.toString))
          true
      }
  }

  private def appendFlyBlocks(sbReq: util.List[String], sbLimit: util.List[String], flyBlocks: util.Map[MaterialDataPredicate, util.List[CraftType.Constraint]]) = {
    for (entry <- flyBlocks.entrySet.asScala) {
      val predicate = entry.getKey
      val constraints = entry.getValue
      val name = BlockNames.materialDataPredicateNames(predicate).mkString(" or ")

      for (constraint <- constraints.asScala) {
        if (!constraint.isTrivial) {
          if (constraint.isUpper) {
            if (constraint.bound.isExact)
              sbLimit.add(f"$name%s <= ${constraint.bound.asExact(0)}%d")
            else
              sbLimit.add(f"$name%s <= ${constraint.bound.asRatio(1) * 100.0}%.2f%%")
          } else {
            if (constraint.bound.isExact)
              sbReq.add(f"${constraint.bound.asExact(0)}%d $name%s")
            else
              sbReq.add(f"${constraint.bound.asRatio(1) * 100.0}%.2f%% $name%s")
          }
        }
      }
    }
  }

  private def doCraftList(sender: CommandSender): Unit = {
    val crafts = this.craftManager.getCraftTypes.map(_.getCraftName).mkString(", ")
    sender.sendMessage(ChatColor.GOLD + "Craft Types: " + ChatColor.GRAY + crafts)
  }
}