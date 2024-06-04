package com.saldubatech.lang.meta

import zio.ZIO
import zio.optics.*
import zio.schema.Schema.{CaseClass6, Sequence}
import zio.schema.{DeriveSchema, Schema}
import zio.schema.optics.ZioOpticsBuilder
import zio.{Chunk, Scope, ZIO}
import zio.test.junit.JUnitRunnableSpec
import zio.test.{Gen, Spec, TestEnvironment, assertTrue, check}

case class ProductProbe(a: String, b: Int)

sealed trait SumProbe:
  def name: String
case class Probe(name: String, value: Int, tValue: (Int, String), pValue: ProductProbe, sValue: Either[String, Int], cValue: Chunk[String]) extends SumProbe
case class OtherProbe(name: String, nothingElse: Int) extends SumProbe

object Probe:
  // If explicitly typed, it breaks. Let type inference do its magic.
  val schema = DeriveSchema.gen[Probe]
  val (nameLens, valueLens, tValueLens, pValueLens, sValueLens, cValueLens) = schema.makeAccessors(ZioOpticsBuilder)

  val pProbeSchema = DeriveSchema.gen[ProductProbe]
  val (pA, pB) = pProbeSchema.makeAccessors(ZioOpticsBuilder)

  val aOptic = pValueLens >>> pA

object SumProbe:
  val schema = DeriveSchema.gen[SumProbe]
  val (probePrism, otherProbePrism) = schema.makeAccessors(ZioOpticsBuilder)

object SeqProbe:
  val schema = Sequence[List[String], String, String] (
    elementSchema = Schema[String],
    fromChunk = _.toList,
    toChunk = i => Chunk.fromIterable(i),
    annotations = Chunk.empty,
    identity = "List"
  )
  // List[String], List[String], Chunk[String], OpticFailure, OpticFailure, Chunk[String], List[String]
  // -GetWhole, -SetWholeBefore, -SetPiece, +GetError, +SetError, +GetPiece, +SetWholeAfter
  // S, S, Chunk[B], OpticFailure, OpticFailure, Chunk[A], T
  // -S, +T, +A, -B
  // S, A
  val seqTraversal: Traversal[List[String], String] = schema.makeAccessors(ZioOpticsBuilder)
given probeSchema: Schema[Probe] = DeriveSchema.gen[Probe]

object memHost extends Host[ProductProbe] {
  
  
}
