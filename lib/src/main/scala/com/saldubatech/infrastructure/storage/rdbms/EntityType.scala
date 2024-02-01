package com.saldubatech.infrastructure.storage.rdbms

import zio.{IO, URLayer, ZIO, ZLayer}

trait EntityType[P <: Payload] :
  type PL_TUPLE <: Product
  trait RecordTemplate:
    val recordId: Id
    val entityId: Id
    val coordinates: TimeCoordinates
    val payload: P

  type Record <: RecordTemplate
  type EIO[A] = ZIO[EntityRepo, PersistenceError, A]

  trait EntityRepo:
    def add(data: P, overrideRId: Id = Id()): EIO[Id]

    def delete(id: Id): EIO[Long]

    def getAll: EIO[List[Record]]

    def getById(id: Id): EIO[Option[Record]]

    def update(itemId: Id, data: P): EIO[Option[Unit]]


  def record(recordId: Id, entityId: Id, coordinates: TimeCoordinates, payload: P): Record

