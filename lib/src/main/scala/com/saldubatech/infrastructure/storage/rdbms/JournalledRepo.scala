package com.saldubatech.infrastructure.storage.rdbms

import com.saldubatech.lang.meta.Conditions
import com.saldubatech.types.datetime.Epoch
import zio.Ref
enum Idempotency:
  case Ignore, Confirm, Reject

enum History:
  case Linear, Branched

enum DeletedEntities:
  case Include, Exclude
case class UpdateOptions(idempotency: Idempotency = Idempotency.Confirm, history: History = History.Branched)

case class FindOptions(deleted: DeletedEntities = DeletedEntities.Exclude)

object Defaults:
  val find: FindOptions = FindOptions()
  val update: UpdateOptions = UpdateOptions()

trait JournalledRepo[P <: Payload](using val entity: EntityType[P], val conditions: Conditions[entity.Record]):
  import entity._
  import conditions._

  def add(data: P, overrideRId: Id = Id()): EIO[Record]

  def updateAt(eId: Id, data: P, effectiveAt: Epoch = Epoch.now, options: UpdateOptions = Defaults.update): EIO[List[Record]]

  def updateFrom(baseRecord: Id, data: P, options: UpdateOptions = Defaults.update): EIO[List[Record]]

  def delete(eId: Id, effectiveAt: Epoch = Epoch.now): EIO[Option[Record]]

  def getRecordById(rId: Id, options: FindOptions = Defaults.find): EIO[Option[Record]]

  def getEntityAt(eId: Id, at: TimeCoordinates, options: FindOptions = Defaults.find): EIO[Option[Record]]

  def getLineage(eId: Id,
                 from: TimeCoordinates = TimeCoordinates.origin,
                 to: TimeCoordinates = TimeCoordinates.now): EIO[List[Record]]

  def find(c: Condition, asOf: TimeCoordinates, options: FindOptions = Defaults.find): EIO[Seq[Record]]


