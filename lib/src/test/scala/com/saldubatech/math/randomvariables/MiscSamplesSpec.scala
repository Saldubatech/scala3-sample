/*
 * Copyright (c) 2019. Salduba Technologies LLC, all right reserved
 */

package com.saldubatech.math.randomvariables

import com.saldubatech.test.BaseSpec
import org.apache.spark.{SparkContext}
import org.apache.spark.sql.SparkSession
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterAll
import com.saldubatech.util.LogEnabled


class MiscSamplesSpec extends AnyWordSpec
    with Matchers
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with LogEnabled  {
  val allowedError = 5.0
  "Some Sample Math Tools" when {
    "Shown as examples" should {
      "A Kolmogorov-Smirnov Test Sample" in {
        import org.apache.spark.mllib.stat.Statistics
        import org.apache.spark.rdd.RDD

        val spark: SparkSession = SparkSession.builder.appName("Test Spark Session").config("spark.master", "local").getOrCreate()

        val sc: SparkContext = spark.sparkContext

        val data: RDD[Double] = sc.parallelize(Seq(0.1, 0.15, 0.2, 0.3, 0.25))
        val myCDF = Map(0.1 -> 0.2, 0.15 -> 0.6, 0.2 -> 0.05, 0.3 -> 0.05, 0.25 -> 0.1)
        val testResult2 = Statistics.kolmogorovSmirnovTest(data, myCDF)
        def expCdf(l: Double)(x: Double): Double = 1.0 - scala.math.exp(l)
        assert(testResult2.pValue < 0.5)
        val testResult3 = Statistics.kolmogorovSmirnovTest(data, expCdf(5.0))
        assert(testResult3.pValue < 0.5)
        println(s"##### The result2 is: $testResult2")
        println(s"##### The result3 is: $testResult3")
      }

    }
  }
}
