//#full-example
package com.saldubatech.sandbox.pekkolearn

import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.saldubatech.sandbox.pekkolearn.Greeter.Greet
import com.saldubatech.sandbox.pekkolearn.Greeter.Greeted
import org.scalatest.wordspec.AnyWordSpecLike

//#definition
class PekkoQuickstartSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
//#definition

  "A Greeter" must {
    //#test
    "reply to greeted" in {
      val replyProbe = createTestProbe[Greeted]()
      val underTest = spawn(Greeter())
      underTest ! Greet("Santa", replyProbe.ref)
      replyProbe.expectMessage(Greeted("Santa", underTest.ref))
    }
    //#test
  }

}
//#full-example
