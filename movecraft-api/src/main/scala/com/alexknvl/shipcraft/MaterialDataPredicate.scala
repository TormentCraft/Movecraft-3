package com.alexknvl.shipcraft

import com.google.common.base.Preconditions
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.material.MaterialData

sealed abstract class MaterialDataPredicate {
  def isTrivial: Boolean

  def check(materialData: MaterialData): Boolean
  def check(material: Material): Boolean = check(new MaterialData(material))
  def check(material: Material, data: Byte): Boolean = check(new MaterialData(material, data))
  def checkBlock(block: Block): Boolean = check(block.getState.getData)

  def allMaterials: Set[Material]
  def allMaterialDataPairs: Set[MaterialData]
}

object MaterialDataPredicate {
  def none: MaterialDataPredicate = None

  def all: MaterialDataPredicate = AllBlocks

  def single(material: Material): MaterialDataPredicate =
    SingleMaterial(material)

  def single(materialData: MaterialData): MaterialDataPredicate =
    SingleMaterialData(materialData)

  def single(material: Material, data: Byte): MaterialDataPredicate =
    SingleMaterialData(new MaterialData(material, data))

  def many(materials: Set[Material], materialDataPairs: Set[MaterialData]): MaterialDataPredicate =
    if (materials.isEmpty && materialDataPairs.isEmpty) None
    else if (materials.size == 1 && materialDataPairs.isEmpty) SingleMaterial(materials.head)
    else if (materialDataPairs.size == 1 && materials.isEmpty) SingleMaterialData(materialDataPairs.head)
    else Many(materials, materialDataPairs)

  final private case object None extends MaterialDataPredicate {
    def isTrivial = true

    def check(materialData: MaterialData) = false

    def allMaterials: Set[Material] = Set.empty

    def allMaterialDataPairs: Set[MaterialData] = Set.empty

    override def toString = "()"
  }

  final private case object AllBlocks extends MaterialDataPredicate {
    import scala.collection.JavaConverters._
    private val NON_AIR_BLOCK_MATERIALS = for {
      material <- java.util.EnumSet.allOf(classOf[Material]).asScala.toSet
      if material.isBlock
      if material ne Material.AIR
    } yield material

    def isTrivial: Boolean = NON_AIR_BLOCK_MATERIALS.nonEmpty

    def check(materialData: MaterialData): Boolean = (materialData.getItemType ne Material.AIR) && materialData.getItemType.isBlock

    def allMaterials = NON_AIR_BLOCK_MATERIALS

    def allMaterialDataPairs: Set[MaterialData] = Set.empty

    override def toString = "(*)"
  }

  final private case class SingleMaterial(material: Material) extends MaterialDataPredicate {
    Preconditions.checkNotNull(material)

    def isTrivial = false

    def check(materialData: MaterialData): Boolean = material eq materialData.getItemType

    def allMaterials: Set[Material] = Set(material)

    def allMaterialDataPairs: Set[MaterialData] = Set.empty

    override def toString: String = "(" + material + ')'
  }

  final private case class SingleMaterialData(materialData: MaterialData) extends MaterialDataPredicate {
    Preconditions.checkNotNull(materialData)

    def isTrivial = false

    def check(materialData: MaterialData): Boolean = this.materialData == materialData

    def allMaterials: Set[Material] = Set.empty

    def allMaterialDataPairs: Set[MaterialData] = Set(materialData)

    override def toString: String = "(" + materialData + ')'
  }

  final private case class Many(materials: Set[Material], materialDataPairs: Set[MaterialData]) extends MaterialDataPredicate {
    require(materials.size + materialDataPairs.size > 1)

    def isTrivial = false

    def check(materialData: MaterialData): Boolean = materials.contains(materialData.getItemType) || materialDataPairs.contains(materialData)

    def allMaterials: Set[Material] = materials

    def allMaterialDataPairs: Set[MaterialData] = materialDataPairs

    override def toString: String =
      "(" + (materials.map(_.toString) ++ materialDataPairs.map(_.toString)).mkString(" | ") + ")"
  }

  class Builder() {
    final private val materials = Set.newBuilder[Material]
    final private val materialDataPairs = Set.newBuilder[MaterialData]
    private var all = false

    def add(material: Material): Unit = if (!all) materials.+=(material)

    def add(materialDataPair: MaterialData): Unit = if (!all) materialDataPairs.+=(materialDataPair)

    def add(predicate: MaterialDataPredicate): Unit =
      if (!all) predicate match {
        case MaterialDataPredicate.None => ()
        case MaterialDataPredicate.SingleMaterial(x) => materials.+=(x)
        case MaterialDataPredicate.SingleMaterialData(x) => materialDataPairs.+=(x)
        case MaterialDataPredicate.Many(a, b) =>
          a.foreach(x => materials.+=(x))
          b.foreach(x => materialDataPairs.+=(x))
        case AllBlocks =>
          all = true
          materials.clear()
          materialDataPairs.clear()
      }

    def result: MaterialDataPredicate =
      if (all) AllBlocks
      else many(materials.result(), materialDataPairs.result())
  }

}