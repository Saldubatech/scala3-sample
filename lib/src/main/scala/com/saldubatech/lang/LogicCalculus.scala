package com.saldubatech.lang

class LogicCalculus[D] {
  type PREDICATE = (D) => Boolean

  def lift(v: Boolean): PREDICATE = (d: D) => v
  val TRUE: PREDICATE = lift(true)
  val FALSE: PREDICATE = lift(false)

  def project[OTHER](op: PREDICATE)(using inj: INJECTOR[OTHER, D]) = (o: OTHER) =>  op(inj(o))

  def unary(u: Boolean => Boolean): (PREDICATE) => PREDICATE = (p: PREDICATE) => (d: D) => u(p(d))

  def lBinary(b: (Boolean, Boolean) => Boolean, shortOn: Boolean): (PREDICATE, PREDICATE) => PREDICATE =
    (l: PREDICATE, r: PREDICATE) => (d: D) => {
      val lb = l(d)
      if(lb == shortOn) lb else b(lb, r(d))
    }
  def rBinary(b: (Boolean, Boolean) => Boolean, shortOn: Boolean): (PREDICATE, PREDICATE) => PREDICATE =
    (l: PREDICATE, r: PREDICATE) => (d: D) => {
      val rb = r(d)
      if(rb == shortOn) rb else b(l(d), rb)
    }
  def rightAssoc(la: (Boolean, Boolean) => Boolean, empty: Boolean, shortOn: Boolean): Seq[PREDICATE] => PREDICATE = {
    val breakOp: (PREDICATE, PREDICATE) => PREDICATE =
      (lp, rp) =>
        (d: D) => {
          val rightEval = rp(d)
          if(rightEval == shortOn) shortOn else la(lp(d), rightEval)
        }
    (ps : Seq[PREDICATE]) => (d: D) => ps match {
      case Seq() => empty
      case Seq(head) => head(d)
      case Seq(head, tail*) => la(head(d), rightAssoc(la, empty, shortOn)(tail)(d))
    }
  }

  def leftAssoc(ra: (Boolean, Boolean) => Boolean, empty: Boolean, shortOn: Boolean): Seq[PREDICATE] => PREDICATE = {
    val breakOp: (PREDICATE, PREDICATE) => PREDICATE =
        (lp: PREDICATE, rp: PREDICATE) =>
          (d: D) => {
            val leftEval = lp(d)
            if(leftEval == shortOn) shortOn else ra(leftEval, rp(d))
          }
    (ps: Seq[PREDICATE]) => (d: D) => ps match {
        case Seq() => empty
        case Seq(single) => single(d)
        case init :+ last => breakOp(leftAssoc(ra, empty, shortOn)(init), last)(d)
      }
  }

  extension (l: PREDICATE) def &&(r: PREDICATE): PREDICATE = lBinary( (lb, rb) => lb && rb, false )(l, r)
  extension (l: PREDICATE) def ||(r: PREDICATE): PREDICATE = lBinary( (lb, rb) => lb || rb, true )(l, r)
  extension (l: PREDICATE) def :&&(r: PREDICATE): PREDICATE = rBinary( (lb, rb) => lb && rb, false )(l, r)
  extension (l: PREDICATE) def :||(r: PREDICATE): PREDICATE = rBinary( (lb, rb) => lb || rb, true )(l, r)

  def !(p: PREDICATE) = unary( !_ )(p)
  def all(predicates: PREDICATE*): PREDICATE = leftAssoc((l, r) => l && r, false, false)(predicates)
  def any(predicates: PREDICATE*): PREDICATE = leftAssoc((l, r) => l || r, false, true)(predicates)
  def rightAll(predicates: PREDICATE*): PREDICATE = rightAssoc((l, r) => l && r, false, false)(predicates)
  def rightAny(predicates: PREDICATE*): PREDICATE = rightAssoc((l, r) => l || r, false, true)(predicates)

}

def projection[O, D](op: (O) => Boolean)(using inj: INJECTOR[D, O]) =(d: D) => op(inj(d))
