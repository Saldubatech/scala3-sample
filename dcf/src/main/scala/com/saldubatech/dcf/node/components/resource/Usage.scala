package com.saldubatech.dcf.node.components.resource

import com.saldubatech.dcf.material.Eaches

object Usage:
  sealed trait Sign
  object Sign:
    case object + extends Sign
    case object zero extends Sign
    case object - extends Sign
  end Sign // object

  sealed trait Increment:
    val sign: Sign
  end Increment
  object Increment:
    case class Defined(q: Eaches) extends Increment:
      override val sign =
        q match
          case 0 => Sign.zero
          case p if p > 0 => Sign.+
          case _ => Sign.-

    case object AcquireAll extends Increment:
      override val sign = Sign.+
    case object ReleaseAll extends Increment:
      override val sign = Sign.-
  end Increment // object

  private def usageEquals(left: Usage, right: Any): Boolean =
    if !right.isInstanceOf[Usage] then false
    else
      val r = right.asInstanceOf[Usage]
      if  left eq r then true
      else if (left eq Idle) && r.isInstanceOf[Usage.FromIdle] && r.asInstanceOf[Usage.FromIdle].q == 0 then true
      else if (r eq Idle) && r.isInstanceOf[Usage.FromIdle] && left.asInstanceOf[Usage.FromIdle].q == 0 then true
      else if (left.isInstanceOf[FromIdle]) && r.isInstanceOf[FromIdle] && left.asInstanceOf[FromIdle].q == r.asInstanceOf[FromIdle].q then true
      else if (left eq Busy) && right.isInstanceOf[Usage.FromBusy] && r.asInstanceOf[Usage.FromBusy].q == 0 then true
      else if (r eq Busy) && left.isInstanceOf[Usage.FromBusy] && left.asInstanceOf[Usage.FromBusy].q == 0 then true
      else if (left.isInstanceOf[FromBusy]) && r.isInstanceOf[FromBusy] && left.asInstanceOf[FromBusy].q == r.asInstanceOf[FromBusy].q then true
      else false

  case class FromIdle(q: Eaches) extends Usage

  case class FromBusy(q: Eaches) extends Usage

  case object Idle extends Usage

  case object Busy extends Usage

end Usage // object


sealed trait Usage:
  import Usage._
  def +(increment: Increment): Usage =
    import Increment._
    (this, increment) match
      case (_, AcquireAll) => Busy
      case (_, ReleaseAll) => Idle
      case (u, Defined(0)) => u // no change
      case (Idle, Defined(i)) =>
        if i > 0 then FromIdle(i)
        else Idle
      case (Busy, Defined(i)) =>
        if i < 0 then FromBusy(-i)
        else Busy
      case (FromIdle(u), Defined(i)) =>
        if -i >= u then Idle
        else FromIdle(u + i)
      case (FromBusy(u), Defined(i)) =>
        if i >= u then Busy
        else FromBusy(u-i)

  def normalize(cap: Option[Eaches]): Usage =
    (cap, this) match
      case (_, Idle) => FromIdle(0)
      case (None, other) => other
      case (Some(c), u: FromIdle) => u
      case (Some(c), FromBusy(q)) => FromIdle(c - q)
      case (Some(c), Busy) => FromIdle(c)

  def normalize(cap: Eaches): Usage =
    this match
      case Idle => Idle
      case u: FromIdle =>
        if u.q == 0 then Idle
        else if u.q < cap then u
        else Busy
      case FromBusy(q) =>
        if q == 0 then Busy
        else if q < cap then FromIdle(cap - q)
        else Idle
      case Busy => Busy

  def available(cap: Eaches): Eaches =
    normalize(cap) match
      case Busy => 0
      case Idle => cap
      case FromIdle(q) => q
      case FromBusy(q) => cap - q

  def compare(other: Usage, withCapacity: Eaches): Boolean =
    available(withCapacity) == other.available(withCapacity)

  override def equals(that: Any): Boolean = Usage.usageEquals(this, that)

end Usage // trait
