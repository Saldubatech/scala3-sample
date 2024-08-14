package com.saldubatech.test

import com.saldubatech.util.LogEnabled
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.{AnyWordSpec, AnyWordSpecLike}
import org.scalatest.EitherValues

import scala.collection.mutable
import scala.concurrent.duration.*

object BaseSpec

trait BaseSpec
  extends AnyWordSpec
    with Matchers
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with EitherValues
    with LogEnabled {

  val name: String = this.getClass.getName + "_Spec"


  def unsupported: Nothing = {
    throw new UnsupportedOperationException()
  }

}

