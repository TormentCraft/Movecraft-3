package com.alexknvl.shipcraft.math

import org.bukkit.{Location, World}

final case class BlockVec(x: Int, y: Int, z: Int) {
  def toBukkitLocation(world: World): Location =
    new Location(world, x, y, z)

  def +(vec: BlockVec): BlockVec =
    BlockVec(x + vec.x, y + vec.y, z + vec.z)

  def -(vec: BlockVec): BlockVec =
    BlockVec(x - vec.x, y - vec.y, z - vec.z)

  def add(vec: BlockVec): BlockVec = this + vec
  def subtract(vec: BlockVec): BlockVec = this - vec

  def translate(dx: Int, dy: Int, dz: Int): BlockVec =
    BlockVec(x + dx, y + dy, z + dz)

  def rotate(r: RotationXZ): BlockVec = r match {
    case RotationXZ.None       => this
    case RotationXZ.TurnAround => BlockVec(-x, y, -z)
    case RotationXZ.CW         =>
      val cos: Int = 0
      val sin: Int = 1
      val x: Int = this.x * cos + this.z * -sin
      val z: Int = this.x * sin + this.z * cos
      BlockVec(x, y, z)
    case RotationXZ.CCW         =>
      val cos: Int = 0
      val sin: Int = -1
      val x: Int = this.x * cos + this.z * -sin
      val z: Int = this.x * sin + this.z * cos
      BlockVec(x, y, z)
  }
}
object BlockVec {
  def from(location: Location): BlockVec =
    BlockVec(location.getBlockX, location.getBlockY, location.getBlockZ)
}