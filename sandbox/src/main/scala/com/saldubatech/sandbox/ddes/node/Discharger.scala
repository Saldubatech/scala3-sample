package com.saldubatech.sandbox.ddes.node

import com.saldubatech.ddes.types.{DomainMessage, Tick, OAMMessage}
import com.saldubatech.ddes.runtime.Clock
import com.saldubatech.ddes.elements.{DomainProcessor, SimActor, SimActorBehavior, SimEnvironment, DomainEvent}
import com.saldubatech.lang.Id
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.math.randomvariables.Distributions
import com.saldubatech.lang.types.{AppResult, UnitResult, AppSuccess, AppError, AppFail}

object Discharger

trait Discharger[FINISHED <: DomainMessage, OUTBOUND <: DomainMessage]:
  /**
    * Whether there are any materials within the Discharger that have not been sent
    * out yet.
    *
    * @return
    */
  def isIdle: Boolean
  /**
    * Invoked  when the station processing finishes and "delivers" a `FINISHED` item to
    * the Discharger
    *
    * @param at The time at which packing starts
    * @param job The id of the job that is just finished
    * @param finished The product of the processing activity
    * @return Success with the expected delay of "packing" until ready for discharge or Failure
    */
  def pack(at: Tick, job: Id, finished: FINISHED): AppResult[Tick]
  /**
    * Invoked when the packing "time" has completed (whatever the packing process needs to do)
    *
    * @param at The time at which "Packing" completes
    * @param job The identifier of the job that completed packing
    * @return Success or Failure
    */
  def dischargeReady(at: Tick, job: Id): UnitResult
  /**
    * Invoked as part of the book keeping activities that happen every time the station gets activated,
    * including after receiving a "discharge ready" signal
    *
    * @param at: The time at which the discharge is to happen
    * @return The list of `OUTBOUND` Items that is ready for discharge. They will all be discharged at this time.
    */
  def doDischarge(at: Tick): Iterator[OUTBOUND]

