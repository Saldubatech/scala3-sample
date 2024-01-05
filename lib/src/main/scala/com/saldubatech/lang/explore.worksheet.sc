import com.saldubatech.lang.LogicCalculus
import com.saldubatech.lang.INJECTOR
import com.saldubatech.lang.translator


val a = 1

case class Test(val n: Int, val str: String)

val LC: LogicCalculus[Test] = LogicCalculus[Test]()
val output = scala.collection.mutable.ArrayBuffer[String]()

import LC.*


val p1 = Test(1, "a")
val p2 = Test(2, "b")
val p3 = Test(3, "c")
val p4 = Test(4, "d")


def isStr(name: String, s: String) = (t: Test) => {
  output += s"$name($t) = ${t.str == s}"
  t.str == s
}

val isA: PREDICATE = isStr("isA", "a")
val isB: PREDICATE = isStr("isB", "b")
val isC: PREDICATE= isStr("isC", "c")

val aOrB = isA || isB

aOrB(p1)

aOrB(p2)

aOrB(p3)

output


output.clear()

output

val manySeq = Seq(isA, isB, isC)
val oneSeq = Seq(isA)
val zeroSeq: Seq[PREDICATE] = Seq()

all(zeroSeq*)(p1)
all(oneSeq*)(p1)
output
output.clear()
all(oneSeq*)(p4)
output
output.clear()

all(manySeq*)(p2)
output

output.clear()
all(manySeq*)(p1)
output
output.clear()

any(manySeq*)(p4)
output

output.clear()
any(manySeq*)(p2)
output

case class TestWrapper(t: Test)

val twLC = LogicCalculus[TestWrapper]

implicit val inj: INJECTOR[TestWrapper, Test] = (w: TestWrapper) => w.t

val tr = translator(LC, twLC)

val twIsA: twLC.PREDICATE = tr(isA)

val wP1 = TestWrapper(p1)
val wP2 = TestWrapper(p2)

twIsA(wP1)
twIsA(wP2)

val prjIsA = LC.project(isA)

prjIsA(wP1)
prjIsA(wP2)

