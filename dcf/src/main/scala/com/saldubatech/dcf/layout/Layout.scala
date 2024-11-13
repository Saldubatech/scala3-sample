package com.saldubatech.dcf.layout

import com.saldubatech.lang.{Id, Identified}
import com.saldubatech.lang.types.*
import com.saldubatech.util.LogEnabled

trait Source:
  def build(sink: Sink): UnitResult

trait Sink

trait Transport:
  def build(sink: Sink): AppResult[Sink]

trait Line extends Identified:
  def reify: UnitResult

trait Target extends Identified

trait Origin extends Identified:
  def to(ch: Channel): Origin


trait End extends Target:
  val behavior: Sink

trait Channel extends Target, Origin:
  val behavior: Transport

  def to(ch: Channel): SegmentFlow
  def to(fl: SegmentFlow): SegmentFlow
  def to(ef: EndFlow): EndFlow

trait Start extends Origin:
  val behavior: Source

  def to(ch: Channel): StartFlow
  def to(f: SegmentFlow): StartFlow
  def to(ef: EndFlow): Line

trait Flow:
  val string: List[Channel]

trait EndFlow extends Target, Flow:
  val end: End

trait SegmentFlow extends Target, Origin, Flow:
  def to(ch: Channel): SegmentFlow
  def to(e: End): EndFlow

extension (self: AppResult[SegmentFlow]) def to(ch: Channel): AppResult[SegmentFlow] = for {
  s <- self
} yield s.to(ch)

extension (self: AppResult[SegmentFlow]) def to(e: End): AppResult[EndFlow] = for {
  s <- self
} yield s.to(e)



trait StartFlow extends Origin, Flow:
  val start: Start
  def to(ch: Channel): StartFlow
  def to(eCh: EndFlow): Line
  def to(e: End): Line

trait Node:
  def inlet(idx: Int): End
  def outlet(idx: Int): Start


trait Topology:
  def addStart(behavior: Source): AppResult[Start]
  def addEnd(behavior: Sink): AppResult[End]
  def addChannel(behavior: Transport): AppResult[Channel]


object Ex:
  val s1, s2, s3, s4: Start = ???
  val e1, e2, e3, e4: End = ???
  val c1, c2, c3, c4: Channel = ???


//  val badStartEnd = s1 to e1
  val startEnd: Line = s1 to c1 to e1
  val freeString: SegmentFlow = c1 to c2 to c3
  val starter: StartFlow = s1 to c1 to c2
  val ender: EndFlow = c2 to c3 to e1
  val allTogether: Line = starter to ender
