package com.saldubatech.sandbox.ddes

import com.saldubatech.lang.types.{MAP, OR, SUB_TUPLE}
import com.saldubatech.sandbox.ddes.DomainEvent
import org.apache.pekko.actor.typed.scaladsl.ActorContext

import scala.reflect.Typeable

object Composite:

  type DOMAIN_MESSAGE[DOMAIN_TUPLE] = OR[DOMAIN_TUPLE] & DomainMessage

  def wkw[GEN_DM <: DomainMessage, DE <: DomainEvent[GEN_DM] : Typeable]
  (ev: DomainEvent[GEN_DM]): ActionResult =
    ev match
      case dmEv : DE => Right(())
      case _ => Left(SimulationError("asdf"))

  abstract class Component[DM <: DomainMessage, DE <: DomainEvent[DM] : Typeable]:
    final def maybeAccept(at: Tick)(using env: SimEnvironment)
    : PartialFunction[DomainEvent[DM], ActionResult] = { case dmEv: DE => accept(at, dmEv) }

    def accept(at: Tick, dmEv: DE)(using env: SimEnvironment): ActionResult

  class DP[DOMAIN_TUPLE](components: Seq[Component[DOMAIN_MESSAGE[DOMAIN_TUPLE], ?]], env: SimEnvironment)
  (using Typeable[DOMAIN_MESSAGE[DOMAIN_TUPLE]])
  extends DomainProcessor[DOMAIN_MESSAGE[DOMAIN_TUPLE]]:
    type PF = PartialFunction[DomainEvent[DOMAIN_MESSAGE[DOMAIN_TUPLE]], ActionResult]
    private val fallThrough: PF =
      { case other => Left(SimulationError(s"No Component to handle the message $other"))}

    val resolver: (Tick, SimEnvironment) => PF =
      components.map[(Tick, SimEnvironment) => PF](cmp => (tick, env) => cmp.maybeAccept(tick)(using env))
        .foldRight[(Tick, SimEnvironment) => PF]((t, env) => fallThrough)( (cmp, rs) => (t, env) => cmp(t, env) orElse rs(t, env))

    override def accept(at: Tick, ev: DomainEvent[DOMAIN_MESSAGE[DOMAIN_TUPLE]])
    : ActionResult = resolver(at, env)(ev)


trait Composite[DOMAIN_TUPLE <: Tuple : Typeable]
(using Typeable[Composite.DOMAIN_MESSAGE[DOMAIN_TUPLE]], SUB_TUPLE[DOMAIN_TUPLE, DomainMessage])
(
  val components: Seq[Composite.Component[Composite.DOMAIN_MESSAGE[DOMAIN_TUPLE], ?]]
)
  extends SimActorBehavior[Composite.DOMAIN_MESSAGE[DOMAIN_TUPLE]]:
  import Composite.*

  override val domainProcessor: DP[DOMAIN_TUPLE] = DP(components, this.env)

