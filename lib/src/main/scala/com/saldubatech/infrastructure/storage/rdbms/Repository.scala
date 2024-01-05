package com.saldubatech.infrastructure.storage.rdbms

// Requires a TypeClass EntityType[P] to be able to create and manage records
class RepositoryTemplate[P <: Identified](using val entity: EntityType[P]):
  trait Repository {
    def add(payload: P)(using coord: TimeCoordinates): PersistenceIO[entity.Record]

    def update(data: P)(using coord: TimeCoordinates): PersistenceIO[entity.Record]
    def delete(id: Id)(using tc: TimeCoordinates): PersistenceIO[entity.Record]
    def get(id: Id)(using coord: TimeCoordinates): PersistenceIO[Option[entity.Record]]
  }


trait JournalledRepository[P <: Identified](using val entity: EntityType[P], updateRequest: UpdateRequestType[P])
