package com.saldubatech.lang.predicate

import org.scalatest.wordspec.AnyWordSpec

import scala.reflect.ClassTag


// https://www.scalatest.org/user_guide/selecting_a_style


def stringRepo: InMemoryPlatform.Repo[String] = InMemoryPlatform.Repo[String]()
class InMemRepoSpec extends AnyWordSpec {
  "A Repository" when {
    import InMemoryPlatform.plainRequirement
    "empty" must {
      "return an empty list" when {
        "presented with the True Predicate" in {
          val underTest = stringRepo
          val preq = summon[ClassTag[InMemoryPlatform.REQUIRES[String, Predicate.TRUE.type]]]
          val strPlain = summon[InMemoryPlatform.Requirement[String]]
          val strPlainCt = summon[ClassTag[strPlain.type]]
          assert(preq == strPlainCt)
          assert(underTest.find(Predicate.TRUE).isEmpty)
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
      import InMemoryPlatform.orderRequirement
      val underTest = stringRepo
      val probe = "asdfasdf"
      val r = underTest.add(probe)
      assert(underTest.find(Predicate.TRUE).size == 1)
    }
  }
  "An Equality Predicate" when {
    import InMemoryPlatform.orderRequirement
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
