package com.saldubatech.dcf.layout

import com.saldubatech.lang.types.*
import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.util.LogEnabled

class LayoutBuilder extends Topology:

  // pending
  private val starts = collection.mutable.ListBuffer.empty[Start]
  private val ends = collection.mutable.ListBuffer.empty[End]
  private val channels = collection.mutable.ListBuffer.empty[Channel]
  private val partials = collection.mutable.ListBuffer.empty[Flow]

  // Completed
  private val lines = collection.mutable.ListBuffer.empty[Line]

  private case class E(val behavior: Sink, lId: Id = Id) extends End:
    override lazy val id: Id = lId

  private case class S(val behavior: Source, lId: Id = Id) extends Start:
    override lazy val id: Id = lId
    override def to(ch: Channel): StartFlow =
      val rs = SF(this, List(ch))
      partials += rs
      rs

    override def to(f: SegmentFlow): StartFlow =
      partials -= f
      val rs = SF(this, f.string)
      partials += rs
      rs

    override def to(eCh: EndFlow): Line =
      starts -= this
      partials -= eCh
      val rs = L(this, eCh.string, eCh.end)
      lines += rs
      rs

  private case class C(val behavior: Transport, lId: Id = Id) extends Channel:
    override lazy val id: Id = lId
    def to(ch: Channel): SegmentFlow = SGF(List(this, ch))
    def to(fl: SegmentFlow): SegmentFlow = SGF(this :: fl.string)
    def to(ef: EndFlow): EndFlow = EF(List(this), ef.end)

  private case class EF(override val string: List[Channel], end: End, lId: Id = Id) extends EndFlow:
    override lazy val id: Id = lId

  private case class SF(override val start: Start, string: List[Channel], lId: Id = Id) extends StartFlow:
    override lazy val id: Id = lId
    override def to(ch: Channel): StartFlow = SF(this.start, ch :: this.string)
    override def to(eCh: EndFlow): Line = L(this.start, this.string ++ eCh.string, eCh.end)
    override def to(e: End): Line = L(this.start, this.string, e)

  private case class SGF(override val string: List[Channel], lId: Id = Id) extends SegmentFlow:
    override lazy val id: Id = lId
    def to(ch: Channel): SegmentFlow = SGF(ch :: this.string)
    def to(e: End): EndFlow = EF(this.string, e)

  private case class L(val start: Start, string: List[Channel], end: End, lId: Id = Id) extends Line:
    override lazy val id: Id = lId
    override def reify: UnitResult =
      val s = AppSuccess(end.behavior)
      string.reverse.foldLeft[AppResult[Sink]](s)((sink, channel) => for {
        s <- sink
        rs <- channel.behavior.build(s)
      } yield rs).flatMap(s => start.behavior.build(s))

  override def addStart(behavior: Source): AppResult[Start] =
    starts += S(behavior)
    AppSuccess(starts.last)

  override def addEnd(behavior: Sink): AppResult[End] =
    ends += E(behavior)
    AppSuccess(ends.last)

  override def addChannel(behavior: Transport): AppResult[Channel] =
    channels += C(behavior)
    AppSuccess(channels.last)
