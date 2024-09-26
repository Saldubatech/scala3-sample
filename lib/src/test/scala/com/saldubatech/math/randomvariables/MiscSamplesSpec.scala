/*
 * Copyright (c) 2019. Salduba Technologies LLC, all right reserved
 */

package com.saldubatech.math.randomvariables

import com.saldubatech.test.BaseSpec
import org.apache.spark.SparkContext
import org.apache.spark.sql.SparkSession
import org.apache.spark.mllib.stat.Statistics
import org.apache.spark.rdd.RDD
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterAll
import com.saldubatech.util.LogEnabled
import com.saldubatech.math.randomvariables.Distributions.DoubleRVar
import com.saldubatech.math.randomvariables.Distributions.exponential
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.{StructType, IntegerType, StructField}
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.Column


class MiscSamplesSpec extends AnyWordSpec
    with Matchers
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with LogEnabled  {
  val allowedError = 5.0
  "Some Sample Math Tools" when {
    val spark: SparkSession = SparkSession.builder.appName("Test Spark Session").config("spark.master", "local").getOrCreate()

    val sc: SparkContext = spark.sparkContext
    "A Kolmogorov-Smirnov test" should {

      "Reject the hypothesis for arbitrary numbers" in {

        val data: RDD[Double] = sc.parallelize(Seq(0.1, 0.15, 0.2, 0.3, 0.25))
        val myCDF = Map(0.1 -> 0.5, 0.15 -> 0.3, 0.2 -> 0.1, 0.3 -> 0.07, 0.25 -> 0.03)
        val testResult2 = Statistics.kolmogorovSmirnovTest(data, myCDF)
        def expCdf(l: Double)(x: Double): Double = 1.0 - scala.math.exp(-l*x)
        assert(testResult2.pValue < 0.5)
        val testResult3 = Statistics.kolmogorovSmirnovTest(data, expCdf(5.0))
//        assert(testResult3.pValue < 0.5)
      }
      "Accept the hypothesis for an exponential set" in {
        val mean = 200.00
        val exp: DoubleRVar = exponential(mean)
        val seq = (0 to 1000).map{_ => exp()}
        val c = seq.toList.size
        val m = seq.fold(0.0)((l, r) => l + r)/c.toDouble
        println(s"Count: $c, Mean: $m")
        val data: RDD[Double] = sc.parallelize(seq)
        def expCdf(l: Double)(x: Double): Double = 1.0 - scala.math.exp(-l*x)
        val testResult = Statistics.kolmogorovSmirnovTest(data, expCdf(1.0/mean))
        println(s">>>>> The result to accept is: $testResult")

      }
    }
    "A Chi-Square Test" should {
      "Reject the hypothesis for arbitrary numbers against a made up distribution" in {
        import org.apache.spark.mllib.linalg._

        val s = Seq(0.1, 0.15, 0.2, 0.3, 0.25)
        // Observed frequencies
        val v: Vector = Vectors.dense(0.1, 0.15, 0.2, 0.3, 0.25)
        // Expected Frequencies
        val expected = Vectors.dense(0.5, 0.3, 0.1, 0.07, 0.03)
        //val data: RDD[Double] = sc.parallelize(Seq(0.1, 0.15, 0.2, 0.3, 0.25))
        val myCDF = Map(0.1 -> 0.2, 0.15 -> 0.6, 0.2 -> 0.05, 0.3 -> 0.05, 0.25 -> 0.1)
        val chi = Statistics.chiSqTest(v, expected)
        println(s"The result is $chi")
      }
      "Accept the hypothesis for exponential distribution" in {
        import org.apache.spark.mllib.linalg._
        import ch.cern.sparkhistogram.Histogram
        import spark.implicits._
        import scala.jdk.CollectionConverters._
        val nSamples = 100000
        val mean = 200.00
        val exp: DoubleRVar = exponential(mean)
        val sample = (0 to nSamples).map{i => i -> exp().toInt}.toList
        val max = sample.fold(0 -> 0){ (l, r) => if l._2 > r._2 then l else r}
        def expCdf(x: Double): Double = 1.0 - scala.math.exp(-x/mean)

        val rdd: RDD[(Int, Int)] = sc.parallelize(sample)
        val rows: java.util.List[Row] = sample.map{r => Row(r._1, r._2)}.asJava
        val sch = StructType(
          List(
            StructField("index", IntegerType, nullable=false),
            StructField("sample", IntegerType, nullable=false)
          )
        )

        val df = spark.createDataFrame(rows, sch)

        val hist = Histogram(spark)
        val observedDS: Dataset[Row] = hist.computeHistogram("sample", 0, max._2, 20)(df)

        var lastCdf = 0.0

        val expectedArray =
          observedDS.select("value")
            .collect
            .map{
              r =>
                val bin = r.get(0) match
                  case bd: BigDecimal => bd.toDouble
                  case n: Number => n.doubleValue

                val cdf = expCdf(bin)
                val rs = (cdf - lastCdf)
                lastCdf = cdf
                rs
            }

        val observedArray = observedDS.select("count").map{
          f => f.get(0) match
            case bd: BigDecimal => bd.toDouble/nSamples.toDouble
            case n: Number => n.doubleValue/nSamples.toDouble
          }.collect
        val observed = Vectors.dense(observedArray)
        val expected = Vectors.dense(expectedArray)
        val chi = Statistics.chiSqTest(observed, expected)
        assert(chi.pValue > 0.95)
        println(s"The result is $chi")
      }

    }
//    spark.close()
  }
}
