package com.saldubatech.sandbox.movement

import com.saldubatech.infrastructure.storage.rdbms.Id
import com.saldubatech.sandbox.ddes.{DomainEvent, DomainMessage, EventAction, NodeType}

import scala.reflect.ClassTag

case class Load[+C] private (c: C, id: Id) extends DomainMessage

object Load:
  def apply[C](c: C): Load[C] = Load(c, Id())

class Intake[-C](private val process: Load[C] => Unit):
   def arrival(c: Load[C]): Unit = process(c)

object AbstractTransport:
  class Types[C : ClassTag] extends NodeType[Load[C]]:
    override type DOMAIN_MESSAGE = Load[C]
    override type DOMAIN_EVENT = DomainEvent[DOMAIN_MESSAGE, DOMAIN_MESSAGE]
    override type EVENT_ACTION = EventAction[DOMAIN_MESSAGE, DOMAIN_MESSAGE, DOMAIN_EVENT]
    override final val dmCt = summon[ClassTag[DOMAIN_MESSAGE]]

trait AbstractTransport[C]:
  trait BaseInduct:
    def accept(l: Load[C]): Unit
  trait BaseDischarge:
    val destination: Intake[C]
    
object _Hidden:


  class DirectTransport[C](destination: Intake[C]) extends AbstractTransport[C]:
    val induct: Induct = Induct()
    val discharge: Discharge = Discharge(destination)
    class Induct extends BaseInduct:
      def accept(l: Load[C]): Unit = discharge.destination.arrival(l)
    class Discharge(override val destination: Intake[C]) extends BaseDischarge
      
  case class Cargo(weight: Double)

  def doIt(): Unit =
    val c = Cargo((10.0))
    val l = Load(c)
    
    val i: Intake[Cargo] = Intake{l => print(s"Cargo arrived: $l")}
    
    val transport = DirectTransport[Cargo](i)
    
    transport.induct.accept(l)
    
    
    class MockNode:
      val intake1: Intake[Cargo] = new Intake[Cargo](
        l =>  print(s"Cargo arrived to intake 1 $l")
      )

      val intake2: Intake[Cargo] = new Intake[Cargo](
        l => print(s"Cargo arrived to intake 2 $l")
      )
