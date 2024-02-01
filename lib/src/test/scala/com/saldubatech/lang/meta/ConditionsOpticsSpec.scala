package com.saldubatech.lang.meta

import zio.ZIO
import zio.optics._ // {Traversal, Optic, OpticFailure}
import zio.schema.Schema.Sequence
import zio.schema.{DeriveSchema, Schema}
import zio.schema.optics.ZioOpticsBuilder
import zio.{Chunk, Scope, ZIO}
import zio.test.junit.JUnitRunnableSpec
import zio.test.{Gen, Spec, TestEnvironment, assertTrue, check}

object ConditionsOpticsSpec extends JUnitRunnableSpec {
  val probe = Probe("asdf", 33, tValue = (44, "qwert"), pValue = ProductProbe("zxcv", 55), Left("uiop"), cValue = Chunk("asdf", "zxcv"))
  val otherProbe = OtherProbe("asdfasdf", 333)
  val listProbe = List("qwer", "asdf", "zxcv")

  def spec= suite("ConditionsSpec")(
    test("Prism"){
      for {
        _ <- ZIO.logInfo(s"Starting ProbeSum Check")
        updatedProbe: SumProbe <- ZIO.fromEither(SumProbe.otherProbePrism.setOptic(otherProbe)(probe))
      } yield assertTrue(updatedProbe.name == "asdfasdf")
    },
    test("Lens") {
      for {
        _ <- ZIO.logInfo(s"Starting Probe Check")
        updatedProbe = Probe.nameLens.setOptic("ASDF")(probe)
      } yield assertTrue(updatedProbe.map(_.name == "ASDF").getOrElse(false))
    },
    test("Composite Lens") {
      for {
        _ <- ZIO.logInfo(s"Starting Composite Lens Check")
        updatedProbe <- ZIO.fromEither(Probe.aOptic.setOptic("ASDF")(probe))
      } yield assertTrue(updatedProbe.pValue.a == "ASDF")
    },
    test("Traversal") {
      val mapped = SeqProbe.seqTraversal.set(Chunk.fromIterable(listProbe.map(_.toUpperCase)))(listProbe)
      val listOptic = listProbe.optic
      val wkw = listProbe.optic.update(_.map(_.toUpperCase))
      for {
        _ <- ZIO.logInfo(s"Starting Traversal Check")
        updatedList <- ZIO.fromEither(SeqProbe.seqTraversal.set(Chunk("poiu", "lkjh"))(listProbe))
        updatedList3 <- ZIO.fromEither(SeqProbe.seqTraversal.set(Chunk("poiu", "lkjh"))(listProbe))
        _ <- ZIO.logInfo(s"Updated List: $updatedList")
        updatedList2 <- ZIO.fromEither(wkw)
        _ <- ZIO.logInfo(s"Updated List 2: $updatedList2")
      } yield assertTrue(updatedList.size == 3, updatedList.head == "poiu")
        && assertTrue(updatedList2.size == 3, updatedList2.head == listProbe.head.toUpperCase, updatedList2.tail.head == listProbe.tail.head.toUpperCase)
    }
  )
}
