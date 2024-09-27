package com.saldubatech.dcf.material

import com.saldubatech.lang.Id
import com.saldubatech.ddes.types.Tick



object WipPool:
  class InMemory[W <: Wip] extends WipPool[W]:
    private val store = collection.mutable.SortedMap.empty[Id, W]

    def contents(at: Tick): List[W] = store.values.toList
    def contents(at: Tick, jobId: Id): Option[W] = store.get(jobId)
    def remove(at: Tick, jobId: Id): Unit = store.remove(jobId)
    def add(at: Tick, wip: W): Unit = store += wip.jobSpec.id -> wip

end WipPool // object


trait WipPool[W <: Wip]:
  def contents(at: Tick): List[W]
  def contents(at: Tick, jobId: Id): Option[W]
  def remove(at: Tick, jobId: Id): Unit
  def add(at: Tick, wip: W): Unit

