package com.alexknvl.shipcraft.math

import com.google.common.base.Preconditions
import java.util

import scala.annotation.tailrec

object HitBox {
  def from(blocks: Array[BlockVec]): HitBox = {
    Preconditions.checkNotNull(blocks)
    from(util.Arrays.asList(blocks).asInstanceOf[util.Collection[BlockVec]])
  }

  def from(blocks: util.Collection[BlockVec]): HitBox = {
    Preconditions.checkNotNull(blocks)
    Preconditions.checkArgument(blocks.size >= 1)

    val aabb = AABB.from(blocks)
    val ybox = Array.fill[IntRange](aabb.xSize, aabb.zSize)(null)

    @tailrec def go(it: util.Iterator[BlockVec]): HitBox =
      if (it.hasNext) {
        val l = it.next()
        val x = l.x - aabb.minX
        val z = l.z - aabb.minZ
        val y = l.y - aabb.minY

        if (ybox(x)(z) == null) ybox(x)(z) = IntRange(y, y)
        else ybox(x)(z) = IntRange(Math.min(ybox(x)(z).min, y), Math.max(ybox(x)(z).min, y))

        go(it)
      } else HitBox(aabb, ybox)

    go(blocks.iterator())
  }
}

final case class HitBox private(aabb: AABB, ybox: Array[Array[IntRange]]) {
  Preconditions.checkNotNull(aabb)
  Preconditions.checkNotNull(ybox)
  Preconditions.checkArgument(aabb.xSize == ybox.length)
  Preconditions.checkArgument(aabb.zSize == ybox(0).length)

  def xRange: IntRange = aabb.xRange
  def yRange: IntRange = aabb.yRange
  def zRange: IntRange = aabb.zRange

  def xSize: Int = aabb.xSize
  def ySize: Int = aabb.ySize
  def zSize: Int = aabb.zSize

  def minX: Int = aabb.minX
  def minY: Int = aabb.minY
  def minZ: Int = aabb.minZ

  def maxX: Int = aabb.maxX
  def maxY: Int = aabb.maxY
  def maxZ: Int = aabb.maxZ

  def getCenterBlock: BlockVec = aabb.getCenterBlock

  def translate(vec: BlockVec) = HitBox(aabb.translate(vec), ybox)

  def isInside(vec: BlockVec): Boolean = {
    Preconditions.checkNotNull(vec)
    if (aabb.xRange.isInside(vec.x) && aabb.zRange.isInside(vec.z)) {
      val rel = vec.subtract(aabb.getMinBlock)
      val yRange = ybox(rel.x)(rel.z)
      yRange != null && rel.y >= yRange.min && rel.y <= yRange.max
    } else false
  }

  def isInside(x: Int, y: Int, z: Int): Boolean =
    if (aabb.xRange.isInside(x) && aabb.zRange.isInside(z)) {
      val yRange = ybox(x - minX)(z - minZ)
      yRange != null && yRange.isInside(y)
    } else false

  def isPlayerWithin(vec: BlockVec): Boolean =
    if (aabb.xRange.isInside(vec.x) && aabb.zRange.isInside(vec.z)) {
      val rel = vec.subtract(aabb.getMinBlock)
      val yRange = ybox(rel.x)(rel.z)
      yRange != null && rel.y >= yRange.min && rel.y <= (yRange.max + 2)
    } else false

  def get(x: Int, z: Int): IntRange = {
    Preconditions.checkElementIndex(x - aabb.minX, aabb.xSize)
    Preconditions.checkElementIndex(z - aabb.minZ, aabb.zSize)
    ybox(x - aabb.minX)(z - aabb.minZ).translate(aabb.minY)
  }
}
