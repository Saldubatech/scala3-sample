package com.saldubatech.sandbox.types

import org.scalatest.wordspec.AnyWordSpec

import scala.reflect.{ClassTag, TypeTest}


object MockTypes:
  sealed class T1

  class T11 extends T1
  class T12 extends T1
  class T2
  class T3
  class T4
  class T5

  val t1: T1 = T1()
  val t2: T2 = T2()
  val t3: T3 = T3()
  val t4: T4 = T4()
  val t5: T5 = T5()

  trait Invariant[T]
  type ID[T] = T

object Meta:
  import MockTypes._



  infix type OR[L, R] = L | R

  type ORT[T] =
    T match
      case x *: EmptyTuple => x
      case x *: xs => x | ORT[xs]
      case _ => T


  type LIFTER[T, C[_, _], F[_]] =
    T match
      case C[l, r] => String
      case _ => Int

  //summon[Invariant[T1] =:= LIFTER[T1, Either, Invariant]]
  //summon[Either[Invariant[T1], Invariant[T2]] =:= LIFTER[Either[T1,T2], Either, Invariant]]
  summon[String =:= LIFTER[T1 | T2, |, Invariant]]
  summon[String =:= LIFTER[T1, |, Invariant]]
  summon[Int =:= LIFTER[T1, Either, Invariant]]
  summon[String =:= LIFTER[Either[T1,T2], Either, Invariant]]

  type TUPLIFY[TPL, ELEM] =
    TPL match
      case x *: xs => ELEM *: TUPLIFY[xs, ELEM]
      case EmptyTuple => EmptyTuple
      case _ => ELEM

  type TUPLE_CHECK[TPL, ELEM] = TUPLIFY[TPL, ELEM] =:= TPL
  class MT[P]
  (using evidence: P =:= String *: String *: EmptyTuple)

  class MT2[P](p: P)
              (using evidence: P =:= TUPLIFY[P, String])

  class MT3[P](p: P)
              (using evidence: TUPLE_CHECK[P, String])


class MetaTypeSpec extends AnyWordSpec:
  import MockTypes._
  import Meta._

  def summonCT[T](using ct: ClassTag[T]): ClassTag[T] = ct

  "A CHECK" when {
    "it" should {
      "Tuple Extensions" in {
        type PROBE = T11 *: T12 *: EmptyTuple
        type REF = T11 *: T12 *: EmptyTuple
        summon[PROBE <:< REF]
      }
      "do this" in {
        summon[ORT[T1 *: T2 *: EmptyTuple] =:= (T1 | T2)]
        summon[ORT[T1 *: T2 *: EmptyTuple] =:= (T1 | T2)]
        summon[ORT[T1 *: T2 *: EmptyTuple] =:= ORT[T1 *: T2 *: EmptyTuple]]
        summon[ORT[T1] =:= T1]
        summon[ORT[T1] =:= ORT[T1]]
      }
      "summon =:=" in {
        summon[T1 =:= (T1 | Nothing)]
      }
      "Tuple Check" in {
        type Probe = String *: String *: EmptyTuple
        val probe = "asdf" *: "asdf" *: EmptyTuple
        type ProbeInt = Int *: Int *: EmptyTuple
        summon[Probe =:= String *: String *: EmptyTuple]
        summon[Probe =:= TUPLIFY[ProbeInt, String]]
        val t: MT[Probe] = MT()
        val t2: MT2[Probe] = MT2[Probe](probe)
        val t2b: MT2[Probe] = MT2(probe)
        val t3: MT3[Probe] = MT3[Probe](probe)
        val t3b: MT3[Probe] = MT3(probe)
      }
    }
  }

