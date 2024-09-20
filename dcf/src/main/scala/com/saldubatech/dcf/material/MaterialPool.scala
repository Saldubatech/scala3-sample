package com.saldubatech.dcf.material

import com.saldubatech.dcf.job.JobSpec
import com.saldubatech.dcf.material.Material
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.{AppFail, AppResult, AppSuccess, UnitResult, CollectedError, AppError}
import com.saldubatech.sandbox.ddes.Tick

object MaterialPool:
  class SimpleInMemory[M <: Material](override val stationId: Id) extends MaterialPool[M]:
    private case class MaterialArrival(at: Tick, material: M)
    private given ordering : Ordering[MaterialArrival] with {
      def compare(l: MaterialArrival, r: MaterialArrival): Int = (l.at - r.at).toInt
    }

    private val _pool = collection.mutable.Map.empty[Id, MaterialArrival]
    private val _byArrival: collection.mutable.SortedSet[MaterialArrival] = collection.mutable.SortedSet.empty

    override def add(at: Tick, m: M): Unit =
      val arr = MaterialArrival(at, m)
      _pool += m.id -> arr
      _byArrival += arr

    override def add(at: Tick, m: List[M]): Unit =
      val mArr = m.map(m => m.id -> MaterialArrival(at, m)).toMap
      _pool ++= mArr
      _byArrival ++= mArr.values

    override def remove(at: Tick, mId: Id): Unit =
      _pool.remove(mId)
      _byArrival.filterInPlace(p => p.material.id != mId)

    override def remove(at: Tick, f: M => Boolean): Unit =
      _pool.filterInPlace((id, ma) => !f(ma.material))
      _byArrival.filterInPlace( p => !f(p._2))

    override def collector(at: Tick): PartialFunction[Id, M] =
      val keys = _pool.keySet
      return { case id if keys(id) => _pool(id).material }

    override def content(at: Tick, mId: Id): Option[M] = _pool.get(mId).map(_.material)
    override def content(at: Tick, by: Option[Tick] = None): List[M] =
      by.fold(_byArrival.toList.map{_.material})(byT => _byArrival.takeWhile(ma => ma.at <= byT).toList.map{_.material})

    override def checkJob(at: Tick, job: JobSpec): AppResult[Wip.New] =
      val keys = _pool.keySet
      job.rawMaterials.collect{
        case rId if keys(rId) => _pool(rId)
      } match
        case l if l.size == job.rawMaterials.size => AppSuccess(Wip.New(job.id, job, l.map(_._2), stationId, at))
        case other => AppFail.fail(s"Material Requirements for Job[${job.id}] not available at Station[$stationId]")


end MaterialPool // object


trait MaterialPool[M <: Material]:
  val stationId: Id
  def add(at: Tick, m: M): Unit
  def add(at: Tick, m: List[M]): Unit
  def remove(at: Tick, f: M => Boolean): Unit
  def remove(at: Tick, mId: Id): Unit
  def collector(at: Tick): PartialFunction[Id, M]
  def content(at: Tick, mId: Id): Option[M]
  def content(at: Tick, by: Option[Tick] = None): List[M]
  def checkJob(at: Tick, job: JobSpec): AppResult[Wip.New]
