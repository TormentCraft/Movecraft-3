package com.alexknvl.shipcraft.math

sealed abstract class RotationXZ {

}
object RotationXZ {
  case object None extends RotationXZ
  case object CW extends RotationXZ
  case object CCW extends RotationXZ
  case object TurnAround extends RotationXZ

  val none: RotationXZ = None
  val cw: RotationXZ = CW
  val ccw: RotationXZ = CCW
  val flip: RotationXZ = TurnAround
}
