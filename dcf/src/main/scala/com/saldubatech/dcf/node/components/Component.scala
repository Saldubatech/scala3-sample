package com.saldubatech.dcf.node.components

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.{AppFail, AppResult, AppSuccess, UnitResult, CollectedError, AppError}

object Component:
  protected [components] def inStation[PAYLOAD](componentId: Id, collectionName: String)(finder: Id => Option[PAYLOAD])(id: Id): AppResult[PAYLOAD] =
      finder(id) match
        case None => AppFail.fail(s"$collectionName[${id}] not found in $componentId")
        case Some(p) => AppSuccess(p)

  trait Identity extends Identified:
    val stationId: Id

    override def toString: String = id
  end Identity // trait

  object API:
    trait Management[+LISTENER <: Identified] extends Subject[LISTENER]
  end API // object

end Component // object
