package com.saldubatech.ddes.elements

import com.saldubatech.lang.types._
import com.saldubatech.ddes.types.{DomainMessage, SimulationError, Tick}
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.Typeable
import com.saldubatech.ddes.elements.{SimActorBehavior, DomainProcessor}

object Composite:

  type DOMAIN_MESSAGE[DOMAIN_TUPLE] = OR[DOMAIN_TUPLE] & DomainMessage

  def wkw[GEN_DM <: DomainMessage, DE <: DomainEvent[GEN_DM] : Typeable]
  (ev: DomainEvent[GEN_DM]): UnitResult =
    ev match
      case dmEv : DE => Right(())
      case _ => Left(SimulationError("asdf"))

  abstract class Component[DM <: DomainMessage, DE <: DomainEvent[DM] : Typeable]:
    final def maybeAccept(at: Tick)(using env: SimEnvironment[DM])
    : PartialFunction[DomainEvent[DM], UnitResult] = { case dmEv: DE => accept(at, dmEv) }

    def accept(at: Tick, dmEv: DE)(using env: SimEnvironment[DM]): UnitResult

  class DP[DOMAIN_TUPLE](components: Seq[Component[DOMAIN_MESSAGE[DOMAIN_TUPLE], ?]], env: SimEnvironment[DOMAIN_MESSAGE[DOMAIN_TUPLE]])
  (using Typeable[DOMAIN_MESSAGE[DOMAIN_TUPLE]])
  extends DomainProcessor[DOMAIN_MESSAGE[DOMAIN_TUPLE]]:
    type PF = PartialFunction[DomainEvent[DOMAIN_MESSAGE[DOMAIN_TUPLE]], UnitResult]
    private val fallThrough: PF =
      { case other => Left(SimulationError(s"No Component to handle the message $other"))}

    val resolver: (Tick, SimEnvironment[DOMAIN_MESSAGE[DOMAIN_TUPLE]]) => PF =
      components.map[(Tick, SimEnvironment[DOMAIN_MESSAGE[DOMAIN_TUPLE]]) => PF](cmp => (tick, env) => cmp.maybeAccept(tick)(using env))
        .foldRight[(Tick, SimEnvironment[DOMAIN_MESSAGE[DOMAIN_TUPLE]]) => PF]((t, env) => fallThrough)( (cmp, rs) => (t, env) => cmp(t, env) orElse rs(t, env))

    override def accept(at: Tick, ev: DomainEvent[DOMAIN_MESSAGE[DOMAIN_TUPLE]])
    : UnitResult = resolver(at, env)(ev)


trait Composite[DOMAIN_TUPLE <: Tuple : Typeable]
(using Typeable[Composite.DOMAIN_MESSAGE[DOMAIN_TUPLE]], SUB_TUPLE[DOMAIN_TUPLE, DomainMessage])
(
  val components: Seq[Composite.Component[Composite.DOMAIN_MESSAGE[DOMAIN_TUPLE], ?]]
)
  extends SimActorBehavior[Composite.DOMAIN_MESSAGE[DOMAIN_TUPLE]]:
  import Composite.*

  override val domainProcessor: DP[DOMAIN_TUPLE] = DP(components, this.env)

