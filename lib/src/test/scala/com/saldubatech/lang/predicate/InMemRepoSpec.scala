package com.saldubatech.lang.predicate

import com.saldubatech.lang.predicate.platforms.InMemoryPlatform

import org.scalatest.wordspec.AnyWordSpec

import scala.reflect.Typeable


// https://www.scalatest.org/user_guide/selecting_a_style

type SELF[T] = T

def stringRepo: InMemoryPlatform.InMemoryRepo[String] = InMemoryPlatform.repoFor[String]

class InMemRepoSpec extends AnyWordSpec {
  "A Repository" when {
    import com.saldubatech.lang.predicate.platforms.InMemoryPlatform.plainRequirement
    "empty" must {
      "return an empty list" when {
        "presented with the True Predicate" in {
          val underTest = stringRepo
          assert(underTest.find(Predicate.TRUE).isEmpty)
        }
      }
    }
    "adding One Item" must {
      "return the same item" in {
        val underTest = stringRepo
        val probe = "NothingToSee"
        val r = underTest.add(probe)
        assert(r == probe)
      }
    }
  }
  it should {
    "Have a single item after adding it" in {
      import com.saldubatech.lang.predicate.platforms.InMemoryPlatform.orderRequirement
      val underTest = stringRepo
      val probe = "NothingToSee"
      val r = underTest.add(probe)
      assert(underTest.find(Predicate.TRUE).size == 1)
    }
  }
  "An Equality Predicate" when {
    import com.saldubatech.lang.predicate.platforms.InMemoryPlatform.orderRequirement
    val probe = "NothingToSee"
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
