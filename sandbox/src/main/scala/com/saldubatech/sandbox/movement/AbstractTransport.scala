package com.saldubatech.sandbox.movement

import com.saldubatech.infrastructure.storage.rdbms.Id
import com.saldubatech.sandbox.ddes.{DomainEvent, DomainMessage, EventAction}

import scala.reflect.ClassTag

case class Load[+C] private (c: C, override val id: Id) extends DomainMessage

object Load:
  def apply[C](c: C): Load[C] = Load(c, Id())

class Intake[-C](private val process: Load[C] => Unit):
   def arrival(c: Load[C]): Unit = process(c)

object AbstractTransport


trait AbstractTransport[C]:
  trait BaseInduct:
    def induct(l: Load[C]): Unit

  trait BaseDischarge:
    val discharge: Intake[C]

object _Hidden:


  class DirectTransport[C](destination: Intake[C]) extends AbstractTransport[C]:
    val induct: Induct = Induct()
    val discharge: Discharge = Discharge(destination)
    class Induct extends BaseInduct:
      def induct(l: Load[C]): Unit = discharge.discharge.arrival(l)
    class Discharge(override val discharge: Intake[C]) extends BaseDischarge

  case class Cargo(weight: Double)

  def doIt(): Unit =
    val c = Cargo((10.0))
    val l = Load(c)

    val i: Intake[Cargo] = Intake{l => print(s"###### Cargo arrived: $l")}

    val transport = DirectTransport[Cargo](i)

    transport.induct.induct(l)


    class MockNode:
      val intake1: Intake[Cargo] = new Intake[Cargo](
        l =>  print(s"###### Cargo arrived to intake 1 $l")
      )

      val intake2: Intake[Cargo] = new Intake[Cargo](
        l => print(s"###### Cargo arrived to intake 2 $l")
      )
