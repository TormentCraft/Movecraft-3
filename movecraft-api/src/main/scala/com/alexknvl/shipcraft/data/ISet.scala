package com.alexknvl.shipcraft.data

sealed abstract class ISet[-A] {
  import ISet._

  def apply(a: A): Boolean

  def ||[B <: A](that: ISet[B]): ISet[B] = (this, that) match {
    case (None, b)        => b
    case (All, _)         => All
    case (a, None)        => a
    case (_, All)         => All
    case (Or(as), Or(bs)) => Or(as ++ bs)
    case (Or(as), b)      => Or(b :: as)
    case (a, Or(bs))      => Or(a :: bs)
    case (a, b)           => Or(a :: b :: Nil)
  }

  def &&[B <: A](that: ISet[B]): ISet[B] = (this, that) match {
    case (None, _)          => None
    case (All, b)           => b
    case (_, None)          => None
    case (a, All)           => a
    case (And(as), And(bs)) => And(as ++ bs)
    case (And(as), b)       => And(b :: as)
    case (a, And(bs))       => And(a :: bs)
    case (a, b)             => And(a :: b :: Nil)
  }

  def negate: ISet[A] = this match {
    case Not(x) => x
    case x => Not(x)
  }
}
object ISet {
  private final case object None extends ISet[Any] {
    def apply(a: Any): Boolean = false
  }
  private final case object All extends ISet[Any] {
    def apply(a: Any): Boolean = true
  }

  private final case class And[A](args: List[ISet[A]]) extends ISet[A] {
    def apply(a: A): Boolean = args.map(_.apply(a)).forall(identity)
  }
  private final case class Or[A](args: List[ISet[A]]) extends ISet[A] {
    def apply(a: A): Boolean = args.map(_.apply(a)).exists(identity)
  }
  private final case class Not[A](arg: ISet[A]) extends ISet[A] {
    def apply(a: A): Boolean = !arg.apply(a)
  }
}