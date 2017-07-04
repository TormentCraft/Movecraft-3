package com.alexknvl.shipcraft.data

import scala.annotation.tailrec
import scala.language.higherKinds
import scala.reflect.ClassTag

trait EnumSet_ {
  type T[E <: Enum[E]]

  def of[E <: Enum[E]](e: E*)(implicit E: ClassTag[E]): T[E]
  def apply[E <: Enum[E]](set: T[E], e: E): Boolean
}
object EnumSet_ extends EnumSet_ {
  type T[E <: Enum[E]] = Any // Long | Array[Long]

  def of[E <: Enum[E]](e: E*)(implicit E: ClassTag[E]): T[E] = {
    val args = e.asInstanceOf[Array[E]]
    val length = E.runtimeClass.getEnumConstants.length

    if (length <= 64) {
      @tailrec def go(i: Int, acc: Long): T[E] =
        if (i < args.length) {
          val ordinal = args(i).ordinal()
          go(i + 1, acc | (1L << ordinal))
        } else acc
      go(0, 0L)
    } else {
      @tailrec def go(i: Int, acc: Array[Long]): T[E] =
        if (i < args.length) {
          val ordinal = args(i).ordinal()
          acc(ordinal / 64) |= 1L << (ordinal % 64)
          go(i + 1, acc)
        } else acc
      go(0, Array((length + 63) / 64))
    }
  }

  def apply[E <: Enum[E]](set: T[E], e: E): Boolean = set match {
    case v: Long =>
      (v & (1L << e.ordinal())) != 0L
    case v: Array[Long] =>
      (v(e.ordinal() / 64) & (1L << (e.ordinal() % 64))) != 0L
  }
}