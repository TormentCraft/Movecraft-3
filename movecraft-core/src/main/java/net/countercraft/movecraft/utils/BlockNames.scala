package net.countercraft.movecraft.utils

import com.google.common.collect.ImmutableSet
import com.alexknvl.shipcraft.MaterialDataPredicate
import net.minecraft.server.v1_12_R1.Item
import net.minecraft.server.v1_12_R1.ItemStack
import org.bukkit.Material
import org.bukkit.material.MaterialData

import scala.annotation.tailrec

object BlockNames {
  def properCase(text: String): String = {
    @tailrec def go(ix: Int, makeUpper: Boolean, chars: Array[Char]): Array[Char] =
      if (ix < chars.length) {
        val ch =
          if (makeUpper) Character.toUpperCase(chars(ix))
          else Character.toLowerCase(chars(ix))
        val isLetter = Character.isLetter(ch)
        chars(ix) = if (isLetter) ch else ' '
        go(ix + 1, !isLetter, chars)
      } else chars

    new String(go(0, makeUpper = true, text.toCharArray)).replaceAll("\\s\\s+", " ").trim
  }

  def materialDataPredicateNames(predicate: MaterialDataPredicate): Set[String] =
    predicate.allMaterials.map(itemName) ++ predicate.allMaterialDataPairs.map(itemName)

  private def itemName(material: Material, data: Byte, hasData: Boolean) = {
    var tmp =
      try new ItemStack(Item.getById(material.getId), 1, data).getName
      catch { case ignored: Exception => null }
    if (tmp == null || tmp.isEmpty) tmp = material.name
    tmp = properCase(tmp).replaceAll("\\s(On|Off)$", "")
    if (!hasData && tmp.startsWith("White "))
      tmp = tmp.substring(6)
    tmp
  }

  def itemName(materialData: MaterialData): String =
    itemName(materialData.getItemType, materialData.getData, hasData = true)
  def itemName(material: Material, data: Byte): String =
    itemName(material, data, hasData = true)
  def itemName(material: Material): String =
    itemName(material, 0.toByte, hasData = false)

  private val COLORED_MATERIALS = Set(
    Material.WOOL, Material.CARPET, Material.STAINED_GLASS,
    Material.STAINED_GLASS_PANE, Material.STAINED_CLAY)

  def itemNames(blk: Material): Set[String] = {
    // Wool, Carpet, Stained Glass, Glass Pane, Clay
    if (COLORED_MATERIALS.contains(blk)) Set(itemName(blk))
    else {
      val builder = Set.newBuilder[String]
      for (data <- 0 until 16) {
        builder.+=(itemName(blk, data.toByte, true))
      }
      builder.result()
    }
  }

  def itemNames(blk: MaterialData): Set[String] = Set(itemName(blk))
}