package com.saldubatech.sandbox.movement

import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.ddes.{Clock, DomainMessage, SimActor}

import scala.reflect.ClassTag

class UnlimitedTransportWithDelay0[C, TGT >: SimActor[Load[C]] <: SimActor[?]]
(val target: TGT, val delay: LongRVar)
(using evidence: Load[C] <:< target.PROTOCOL) 
  extends AbstractTransport[C]:
  class Induct(private val binding: SimActor[?]) extends BaseInduct:
    override def accept(l: Load[C]): Unit =
      binding.schedule(target)(binding.at+delay(), l)

  class Discharge(override val destination: Intake[C]) extends BaseDischarge




object Tst:
  abstract class LoadDestination[PROTOCOL <: DomainMessage : ClassTag]
  (override val name: String)
  (using clock: Clock)
    extends SimActor[PROTOCOL]
