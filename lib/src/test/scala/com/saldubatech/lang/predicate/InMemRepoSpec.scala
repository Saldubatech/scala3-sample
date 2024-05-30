package com.saldubatech.lang.predicate

import com.saldubatech.lang.predicate.Predicate.TRUE
import org.scalatest.wordspec.AnyWordSpec

import scala.reflect.ClassTag


// https://www.scalatest.org/user_guide/selecting_a_style


def stringRepo: InMemoryPlatform.Repo[String] = InMemoryPlatform.Repo[String]()
class InMemRepoSpec extends AnyWordSpec {
  "A Repository" when {
    import InMemoryPlatform.plainRequirement
    import InMemoryPlatform.orderRequirement
    //given InMemoryPlatform.StringPlainRequirement()
    //given InMemoryStringClassifierRequirement()
    "empty" must {
      "return an empty list" when {
        "presented with the True Predicate" in {
          val underTest = stringRepo
          val preq = summon[ClassTag[InMemoryPlatform.REQUIRES[String, Predicate.TRUE.type]]]
          val strPlain = summon[ClassTag[InMemoryPlatform.StringPlainRequirement]]
          //assert(preq == strPlain)
          assert(underTest.find2(using InMemoryPlatform.plainRequirement[String])(Predicate.TRUE).isEmpty)
          assert(underTest.find2(Predicate.TRUE).isEmpty)
        }
      }
    }
    "adding One Item" must {
      "return the same item" in {
        val underTest = stringRepo
        val probe = "asdfasdf"
        val r = underTest.add(probe)
        assert(r == probe)
      }
    }
  }
  it should {
    "Have a single item after adding it" in {
      given InMemoryPlatform.StringClassifier()
      val underTest = stringRepo
      val probe = "asdfasdf"
      val r = underTest.add(probe)
      assert(underTest.find(Predicate.TRUE).size == 1)
    }
  }
  "An Equality Predicate" when {
    //given InMemoryStringPlainRequirement()
    given InMemoryPlatform.StringClassifier()
    val probe = "asdfasdf"
    val testPredicate = Predicate.Eq(probe)
    "applied to an empty Repo" must {
      val underTest = stringRepo
      "find no elements" in {
        assert(underTest.find(testPredicate).isEmpty)
      }
    }
    "applied to a Repo with matching element" must {
      val underTest = stringRepo
      underTest.add(probe)
      "find the element" in {
        assert(underTest.find(testPredicate).size == 1)
      }
    }
    "applied to a Repo without not matching element" must {
      val underTest = stringRepo
      underTest.add(probe+"different")
      "find the element" in {
        assert(underTest.find(testPredicate).isEmpty)
      }
    }
  }

  "A Set" when {
    "Initialized" should {
      "be Empty" in {
        assert(Set().isEmpty)
      }
      "produce NoSuchElementException when head is invoked" in {
        assertThrows[NoSuchElementException] {
          Set.empty.head
        }
      }
    }
  }
}
