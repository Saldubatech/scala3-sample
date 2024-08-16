package com.saldubatech.dcf.node

import com.saldubatech.dcf.material.Material
import com.saldubatech.lang.Id
import com.saldubatech.sandbox.ddes.Tick
import com.saldubatech.lang.types.{AppResult, UnitResult, AppFail, AppError, AppSuccess, collectResults, unit}
import io.netty.channel.unix.Buffer
import com.saldubatech.sandbox.ddes.{DomainMessage}
import com.saldubatech.lang.types.CollectedError

import scala.reflect.Typeable
import com.saldubatech.math.randomvariables.Distributions.LongRVar
import com.saldubatech.sandbox.ddes.SimActor


object Buffer:
  // Errors
  case class UnableToRelease(at: Tick, stockId: Id, bufferId: Id, reason: Option[String]) extends AppError(
    s"Unable to release stock with Id[$stockId] from buffer[$bufferId]" + (reason match{
      case None => ""
      case Some(r) => r
    }) + s" at $at"
    )
  case class StockNotAvailable(at: Tick, bufferId: Id, stockIds: List[Id] = List()) extends AppError(
    (stockIds match {
      case Nil => s"No stock"
      case other => s"At least one stock Item of $other is not"
    }) + s" available in buffer[$bufferId] at time $at"
  )
  object UnableToRelease:
    def fail[R](at: Tick, stockId: Id, bufferId: Id, reason: Option[String]): AppFail[UnableToRelease, R] = AppFail(UnableToRelease(at, stockId, bufferId, reason))
  object StockNotAvailable:
    def fail[R](at: Tick, bufferId: Id, stockIds: List[Id] = List()): AppFail[StockNotAvailable, R] = AppFail(StockNotAvailable(at, bufferId, stockIds))


  // Actor Protocols

  sealed trait MaterialSignal extends DomainMessage
  case class MaterialArrival[M <: Material](override val id: Id, override val job: Id, bufferId: Id, material: M) extends MaterialSignal
  case class MaterialPack[M <: Material](override val id: Id, override val job: Id, bufferId: Id, inbounds: List[Id]) extends MaterialSignal
  case class MaterialRelease[M <: Material](override val id: Id, override val job: Id, bufferId: Id, stockId: Option[Id]) extends MaterialSignal

  type PROTOCOL = MaterialSignal

  trait Behavior[INBOUND <: Material, OUTBOUND <: Material]:
    /**
      * Return all the Inbound materials that are available to pack, depending on the
      * buffer policy (e.g. a FIFO will only return one or none if empty)
      *
      * @param at
      * @return The list of available stock items if any
      */
    def peekInbound(at: Tick): AppResult[List[WipStock[INBOUND]]]
    /**
      * Return all the outbound materials that are available to release, depending on the buffer
      * policy (e.g. a FIFO will only return one or none if empty)
      *
      * @param at
      * @return
      */
    def peekOutbound(at: Tick): AppResult[List[WipStock[OUTBOUND]]]

    /**
      * Take the identified inbound materials if available, and pack them into an outbound item
      * that is ready for release. If not all materials are available, return a failure.
      *
      * @param at
      * @param inbounds
      * @return The outbound item resulting from the packing.
      */
    def pack(at: Tick, inbounds: List[Id]): AppResult[WipStock[OUTBOUND]]

    /**
    * Release the materials that are ready
    *
    * If stockId is None, release all materials that are ready for release in the order the buffer determines
    * If stockId is not none, release the identified material if it is ready.
    *
    * @param at
    * @param stockId
    * @return
    */
    def release(at: Tick, stockId: Option[Id] = None): AppResult[List[WipStock[OUTBOUND]]]


  // Listener for any kind of Stock, must sort out internally what to do if not known type
  trait OutboundListener:
    val id: Id
    // Must be implemented Asynchronously
    def stockReady(at: Tick, stock: WipStock[?]): Unit
    def stockRelease(at: Tick, stock: WipStock[?]): Unit

  trait Management:

    private val arrivalListeners: collection.mutable.Map[Id, Sink.Listener] = collection.mutable.Map()
    private val releaseListeners: collection.mutable.Map[Id, Buffer.OutboundListener] = collection.mutable.Map()

    protected def notifyRelease(at: Tick, stock: WipStock[?]): Unit =
      releaseListeners.values.foreach(l => l.stockRelease(at, stock))

    protected def notifyArrival(at: Tick, stock: WipStock[?]): Unit =
      arrivalListeners.values.foreach(l => l.stockArrival(at, stock))

    protected def notifyReady(at: Tick, stock: WipStock[?]): Unit =
      releaseListeners.values.foreach(l => l.stockReady(at, stock))

    final def subscribeArrivals(listener: Sink.Listener): UnitResult =
      arrivalListeners += listener.id -> listener
      AppSuccess.unit

    final def unsubscribeArrivals(listenerId: Id): UnitResult =
      arrivalListeners -= listenerId
      AppSuccess.unit

    final def subscribeOutbound(listener: Buffer.OutboundListener): UnitResult =
      releaseListeners += listener.id -> listener
      AppSuccess.unit

    final def unsubscribeOutbound(listenerId: Id): UnitResult =
      releaseListeners -= listenerId
      AppSuccess.unit

    final def subscribeAll(listener: Sink.Listener & Buffer.OutboundListener): UnitResult =
      subscribeArrivals(listener)
      subscribeOutbound(listener)

    final def unsubscribeAll(listenerId: Id): UnitResult =
      unsubscribeArrivals(listenerId)
      unsubscribeOutbound(listenerId)

  trait Component[INBOUND <: Material, OUTBOUND <: Material]
    extends Behavior[INBOUND, OUTBOUND], Management, Sink[INBOUND]:
      val id: Id

  trait Control:
    def triggerPack(at: Tick, inbounds: List[Id]): Unit
    def triggerRelease(at: Tick, stockId: Option[Id]): Unit

  trait DirectControl extends Control:
    self: Buffer[?, ?] =>

    override def triggerPack(at: Tick, inbounds: List[Id]): Unit = self.pack(at, inbounds)
    override def triggerRelease(at: Tick, stockId: Option[Id]): Unit = self.release(at, stockId)

  trait StochasticControl(val host: SimActor[PROTOCOL], packingTime: LongRVar, releaseTime: LongRVar) extends Control:
    self: Buffer[?, ?] =>
    override def triggerPack(at: Tick, inbounds: List[Id]): Unit =
      host.env.scheduleDelay(host)(packingTime(), MaterialPack(Id, Id /* NOT USED */, self.id, inbounds))
    override def triggerRelease(at: Tick, stockId: Option[Id]): Unit =
      host.env.scheduleDelay(host)(releaseTime(), MaterialRelease(Id, Id /* NOT USED */, self.id, stockId))


trait Buffer[INBOUND <: Material : Typeable, OUTBOUND <: Material]
extends Buffer.Component[INBOUND, OUTBOUND], Buffer.Control:
  def callBackBinding(at: Tick): PartialFunction[Buffer.PROTOCOL, UnitResult] = {
    case Buffer.MaterialArrival(mId, jobId, bufferId, mat: INBOUND) if bufferId == id => accept(at, mat)
    case Buffer.MaterialPack(mId, jobId, bufferId, inbounds: List[Id]) if bufferId == id => pack(at, inbounds).unit
    case Buffer.MaterialRelease(mId, jobId, bufferId, mat: Option[Id]) if bufferId == id => release(at, mat).unit
  }


abstract class AbstractBufferBase[INBOUND <: Material : Typeable, OUTBOUND <: Material](
  override val id: Id,
  val packer: (Tick, List[INBOUND]) => AppResult[OUTBOUND],
  val downstream: Sink[OUTBOUND])
  extends Buffer.Component[INBOUND, OUTBOUND]:

  // These are the methods to be implemented by specific behaviors. e.g. FIFO, Bounded, ...
  /**
    * Get the materials ready for release from the buffer.
    * If the list of Id's is empty, return all materials ready for release in a single operation (e.g. only one in a FIFO buffer)
    * If the list is not empty, return the available materials as if they were going to be processed in the order given by the list.
    *
    * For example, if the Buffer were a FIFO behavior, it would only return the elements of the input
    * list that were in the order of arrival until the first Non-ordered element is found
    *
    * @param at
    * @param stockId
    * @return
    */
  protected def _peekReady(at: Tick, stockId: List[Id]): AppResult[List[WipStock[OUTBOUND]]]
  /**
    * Remove the provided stockId's from the outbound buffer if they are available to release according to the rules of
    * `_peekReady`
    *
    * @param at
    * @param stockIds
    * @return
    */
  protected def _removeOutboundStock(at: Tick, stockIds: List[Id]): AppResult[List[WipStock[OUTBOUND]]]
  /**
    * Remove the single provided stockId from the outbound buffer if they are available to release according to the rules of
    * `_peekReady`
    *
    * @param at
    * @param stockId
    * @return
    */
  protected def _removeOutboundStock(at: Tick, stockId: Id): AppResult[WipStock[OUTBOUND]]
  /**
    * Get the available Inbound Materials from the buffer:
    * If the List of Id's is empty, return all available materials in a single operation (e.g. only the head in a FIFO situation)
    * If the list is not empty, return the available materials as if they were going to be processed in the order given by the list.
    *
    * For example, if the Buffer were a FIFO behavior, it would only return the elements of the input list that were in the order
    * of arrival until the first Non-ordered element is found.
    *
    * @param at
    * @param stockIds
    * @return
    */
  protected def _peekAvailable(at: Tick, stockIds: List[Id]): AppResult[List[WipStock[INBOUND]]]
  /**
    * Remove the provided stockId's from the outbound buffer if they are available to release according to the rules of
    * `_peekAvailable`
    *
    * @param at
    * @param stockIds
    * @return
    */
  protected def _removeInboundStock(at: Tick, stockIds: List[Id]): AppResult[List[WipStock[INBOUND]]]
  /**
    * Add a pack to the outbound buffer and make it ready for release according to the rules of the buffer
    *
    * @param at
    * @param pack
    * @return
    */
  protected def _readyPacked(at: Tick, pack: OUTBOUND): AppResult[WipStock[OUTBOUND]]
  /**
    * Add a load to the buffer and make it available for packing according to the rules for the buffer.
    *
    * @param at
    * @param load
    * @return
    */
  protected def _doAccept(at: Tick, load: INBOUND): AppResult[WipStock[INBOUND]]
  override def accept(at: Tick, load: INBOUND): UnitResult =
    for {
      rs <- _doAccept(at, load)
    } yield
      notifyArrival(at, rs)

  override def pack(at: Tick, reqMaterials: List[Id]): AppResult[WipStock[OUTBOUND]] =
    for {
      comps <- _peekAvailable(at, reqMaterials)
      pack <- if comps.size == reqMaterials.size then packer(at, comps.map(_.material))
              else AppFail.fail(s"Not all required materials in buffer $id are available: $comps")
      _ <- _removeInboundStock(at, reqMaterials)
      rs <- _readyPacked(at, pack)
    } yield
      notifyReady(at, rs)
      rs

  override def release(at: Tick, stockId: Option[Id]): AppResult[List[WipStock[OUTBOUND]]] =
    for {
      stockList <- stockId match
                      case None => _peekReady(at, List())
                      case Some(value) => _peekReady(at, List(value))
      rs <- stockList.map{st =>
        for {
          _ <- downstream.accept(at, st.material)
          _ <- _removeOutboundStock(at, st.id)
        } yield ()
      }.collectResults
    } yield
      // will not get here unless all are successful.
      // TODO Handle errors!!
      stockList.foreach{notifyRelease(at, _)}
      stockList

end AbstractBufferBase // class
