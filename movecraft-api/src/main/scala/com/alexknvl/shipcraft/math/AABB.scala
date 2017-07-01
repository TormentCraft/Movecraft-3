package com.alexknvl.shipcraft.math

import java.util

import com.google.common.base.Preconditions

object AABB {
  def fromTwoCorners(c1: BlockVec, c2: BlockVec): AABB = {
    Preconditions.checkNotNull(c1)
    Preconditions.checkNotNull(c2)
    new AABB(Math.min(c1.x, c2.x), Math.min(c1.y, c2.y), Math.min(c1.z, c2.z), Math.max(c1.x, c2.x), Math.max(c1.y, c2.y), Math.max(c1.z, c2.z))
  }

  def from(vecs: util.Collection[BlockVec]): AABB =
    new AABB.Builder().addAll(vecs).result

  final class Builder {
    private var count = 0
    private var minX = 0
    private var minY = 0
    private var minZ = 0
    private var maxX = 0
    private var maxY = 0
    private var maxZ = 0

    def add(p: BlockVec): AABB.Builder = {
      Preconditions.checkNotNull(p)
      if (count == 0) {
        maxX = p.x
        maxY = p.y
        maxZ = p.z
      } else {
        minX = Math.min(minX, p.x)
        minY = Math.min(minY, p.y)
        minZ = Math.min(minZ, p.z)
        maxX = Math.max(maxX, p.x)
        maxY = Math.max(maxY, p.y)
        maxZ = Math.max(maxZ, p.z)
      }
      count += 1
      this
    }

    def addAll(blockPositions: Array[BlockVec]): AABB.Builder = {
      Preconditions.checkNotNull(blockPositions)
      for (p <- blockPositions) {
        add(p)
      }
      this
    }

    def addAll(blockPositions: util.Collection[BlockVec]): AABB.Builder = {
      Preconditions.checkNotNull(blockPositions)
      import scala.collection.JavaConversions._
      for (p <- blockPositions) {
        add(p)
      }
      this
    }

    def result: AABB = {
      Preconditions.checkState(count > 0)
      new AABB(minX, minY, minZ, maxX, maxY, maxZ)
    }

    def getCount: Int = count
    def getMinX: Int = minX
    def getMinY: Int = minY
    def getMinZ: Int = minZ
    def getMaxX: Int = maxX
    def getMaxY: Int = maxY
    def getMaxZ: Int = maxZ
  }

}

final case class AABB(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) {
  def translate(vec: BlockVec) =
    new AABB(minX + vec.x, minY + vec.y, minZ + vec.z, maxX + vec.x, maxY + vec.y, maxZ + vec.z)

  def xRange = new IntRange(minX, maxX)
  def yRange = new IntRange(minY, maxY)
  def zRange = new IntRange(minZ, maxZ)

  def xSize: Int = maxX - minX + 1
  def ySize: Int = maxY - minY + 1
  def zSize: Int = maxZ - minZ + 1

  def getCenterBlock = BlockVec(minX + (maxX - minX) / 2, minY + (maxY - minY) / 2, minZ + (maxZ - minZ) / 2)

  def getMinBlock = BlockVec(minX, minY, minZ)

  def contains(that: BlockVec): Boolean =
    this.minX <= that.x && that.x <= this.maxX &&
      this.minY <= that.x && that.x <= this.maxY &&
      this.minZ <= that.x && that.x <= this.maxZ

  def contains(that: AABB): Boolean =
    this.minX <= that.minX && that.maxX <= this.maxX &&
      this.minY <= that.minY && that.maxY <= this.maxY &&
      this.minZ <= that.minZ && that.maxZ <= this.maxZ

  def intersects(that: AABB): Boolean =
    (Math.abs(this.minX - that.minX) * 2 < (this.xSize + that.xSize)) &&
      (Math.abs(this.minY - that.minY) * 2 < (this.ySize + that.ySize)) &&
      (Math.abs(this.minZ - that.minZ) * 2 < (this.zSize + that.zSize))

  def rotate(around: BlockVec, rotation: RotationXZ): AABB = {
    val c1 = BlockVec(minX, minY, minZ)
    val c2 = BlockVec(maxX, maxY, maxZ)

    AABB.fromTwoCorners(
      (c1 - around).rotate(rotation) + around,
      (c2 - around).rotate(rotation) + around)
  }
}