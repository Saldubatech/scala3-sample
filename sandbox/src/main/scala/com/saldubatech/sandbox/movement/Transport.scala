package com.saldubatech.sandbox.movement

import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.ddes.types.DomainMessage
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.elements.SimActorBehavior
import com.saldubatech.ddes.elements.{SimActor, SimActorBehavior}

class UnlimitedTransportWithDelay0[C]
(val target: SimActor[Load[C]], val delay: LongRVar)
  extends AbstractTransport[C]:
  class Induct(private val binding: SimActorBehavior[?]) extends BaseInduct:
    override def induct(l: Load[C]): Unit =
      binding.env.schedule(target)(binding.currentTime+delay(), l)

  class Discharge(override val discharge: Intake[C]) extends BaseDischarge



object Tst
