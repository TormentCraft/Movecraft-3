package com.alexknvl.shipcraft.math

object ExtraMath {
  def positiveMod(mod: Int, divisor: Int): Int = {
    val r = mod % divisor
    if (r < 0) r + divisor else r
  }

  implicit class extraMathIntSyntax(val value: Int) extends AnyVal {
    def %%(other: Int): Int = positiveMod(value, other)
    def clamp(min: Int, max: Int): Int = Math.max(min, Math.min(value, max))
  }
}
