package com.alexknvl.shipcraft.math

final case class IntRange(min: Int, max: Int) {
  def translate(offset: Int) =
    IntRange(min + offset, max + offset)

  def isInside(value: Int): Boolean =
    min <= value && value <= max
}