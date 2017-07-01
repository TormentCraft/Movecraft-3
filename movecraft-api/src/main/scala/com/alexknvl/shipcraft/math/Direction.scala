package com.alexknvl.shipcraft.math

import org.bukkit.block.Sign

import ExtraMath._

/**
  * Craft movement direction.
  */
object Direction {
  val North     = new Direction(0, 0, -1)
  val NorthEast = new Direction(1, 0, -1)
  val East      = new Direction(1, 0, 0)
  val SouthEast = new Direction(1, 0, 1)
  val South     = new Direction(0, 0, 1)
  val SouthWest = new Direction(-1, 0, 1)
  val West      = new Direction(-1, 0, 0)
  val NorthWest = new Direction(-1, 0, -1)
  val Up        = new Direction(0, 1, 0)
  val Down      = new Direction(0, -1, 0)
  val Off       = new Direction(0, 0, 0)


  val NameMap = Map(
    "n" -> North, "north" -> North,
    "ne" -> NorthEast, "northeast" -> NorthEast,
    "e" -> East, "east" -> East,
    "se" -> SouthEast, "southeast" -> SouthEast,
    "s" -> South, "south" -> South,
    "sw" -> SouthWest, "southwest" -> SouthWest,
    "w" -> West, "west" -> West,
    "nw" -> NorthWest, "northwest" -> NorthWest,
    "u" -> Up, "up" -> Up,
    "d" -> Down, "down" -> Down,
    "off" -> Off)

  def named(s: String): Option[Direction] = NameMap.get(s.toLowerCase)

  def namedOr(s: String, defaultValue: Direction): Direction =
    named(s).getOrElse(defaultValue)

  def fromYawPitch(yaw: Double, pitch: Double): Direction = {
    var yaw1 = yaw % 360
    if (yaw1 <= -180) yaw1 += 360
    if (yaw1 > 180) yaw1 -= 360
    // Yaw is between -180 .. 180.

    var x = 0
    var z = 0
    if (pitch > -80 && pitch < 80) {
      if (yaw >= -60.0 && yaw <= 60.0) z = 1
      if (yaw <= -120.0 || yaw >= 120.0) z = -1
      if (yaw >= 30.0 && yaw <= 150.0) x = -1
      if (yaw >= -150.0 && yaw <= -30.0) x = 1
    }
    var y = 0
    if (pitch <= -30) y = 1 //up
    else if (pitch >= 30) y = -1 //down
    new Direction(x, y, z)
  }

  def fromSignDirection(sign: Sign): Direction = {
    val rawData = sign.getRawData
    if (rawData == 0x3.toByte) return North //north
    if (rawData == 0x4.toByte) return East //east
    if (rawData == 0x2.toByte) return South //south
    if (rawData == 0x5.toByte) return West //west
    Off
  }
}

final case class Direction(x: Int, y: Int, z: Int) {
  def rotateXZ(rotation: RotationXZ): Direction =
    if (rotation == RotationXZ.None) this
    else {
      val cos = 0
      val sin = if (rotation == RotationXZ.CW) 1
      else -1
      val x = (this.x * cos) + (this.z * -sin)
      val z = (this.x * sin) + (this.z * cos)
      new Direction(x, y, z)
    }

  def combine(that: Direction) =
    new Direction(
      (this.x + that.x).clamp(-1, 1),
      (this.y + that.y).clamp(-1, 1),
      (this.z + that.z).clamp(-1, 1))
}