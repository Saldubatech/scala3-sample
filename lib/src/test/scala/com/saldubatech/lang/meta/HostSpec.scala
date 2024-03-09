package com.saldubatech.lang.meta

import zio.{Chunk, UIO, Task, Scope, ZIO, ZLayer}
import zio.optics.*
import zio.test.TestAspect.sequential
import zio.test.{Gen, Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}

given inMemoryContext : InMemoryContext with {}

class IntType extends ComparableElementType[Int, inMemoryContext.type], ElementType[Int, inMemoryContext.type](inMemoryContext):
  override val ordering: ctx.Sorter[Int] = ctx.inMemorySorter[Int]

given intType: IntType()

class ProbeType extends ProductElementType[inMemoryContext.type, Probe](inMemoryContext):
  override type LIFTED_PRODUCT = LIFTED_TYPE
  case class LLocator[V, VT <: ElementType[V, ctx.type]](lens: Optional[Probe, V])(using override val  vt: VT) extends Locator[V, VT]:
    override val projection: LIFTED_PRODUCT => Task[vt.LIFTED_TYPE] = lifted => ZIO.fromEither(lens.get(lifted))

  override val elements: Map[String, Locator[_, _]] = Map[String, Locator[_, _]](
    "value" -> new LLocator[Int, IntType](Probe.valueLens),
  )
  val value: Locator[Int, intType.type] = new LLocator[Int, intType.type](Probe.valueLens)
  val valueProjector: intType.Comprehension => Comprehension = iprj => project[Int, intType.type](value, iprj)

given probeType : ProbeType with {}

object probeRepo extends ListBasedSet[inMemoryContext.type, Probe, probeType.type]

object HostSpec extends ZIOSpecDefault {
  val probe = Probe("asdf", 11, tValue = (111, "qwert"), pValue = ProductProbe("zxcv", 111), Left("uiop"), cValue = Chunk("asdf", "zxcv"))
  val probe2 = Probe("asdf", 22, tValue = (222, "qwert"), pValue = ProductProbe("zxcv", 222), Left("uiop"), cValue = Chunk("asdf", "zxcv"))
  val probe3 = Probe("asdf", 33, tValue = (333, "qwert"), pValue = ProductProbe("zxcv", 333), Left("uiop"), cValue = Chunk("asdf", "zxcv"))
  val otherProbe = OtherProbe("asdfasdf", 333)
  val listProbe = List("qwer", "asdf", "zxcv")
  val vCondition = probeType.project[Int, intType.type](probeType.value, intType.eq(11))
  val vConditionAlt = probeType.valueProjector(intType.eq(11))
//  val vCondition2 = intType.eq(11)

  def spec= suite("ConditionsSpec")(
    test("Filter First one") {
      val firstCondition = probeType.eq(probe)
      val thirdCondition = probeType.eq(probe3)
      for {
        _ <- probeRepo.add(probe)
        _ <- probeRepo.add(probe2)
        _ <- probeRepo.add(probe3)
        r : List[Probe] <- probeRepo.find(firstCondition + thirdCondition)
      } yield assertTrue(r.size == 2) && assertTrue(r.head == probe, r.tail.head == probe3)
    },
    test("Based on Field") {
      for {
        r: List[Probe] <- probeRepo.find(vConditionAlt)
      } yield assertTrue(r.size == 1, r.head == probe)
    }
  ) @@ sequential
}
