package org.apache.mahout.algos.transformer

import org.apache.mahout.math.{Matrix, Vector}
import org.apache.mahout.math.drm._

import scala.org.apache.mahout.algos.transformer.Transformer
import scala.reflect.ClassTag
import org.apache.mahout.math.drm
import org.apache.mahout.math.scalabindings._
import org.apache.mahout.math.scalabindings.RLikeVectorOps

import org.apache.mahout.math.scalabindings.RLikeOps._
import org.apache.mahout.math.scalabindings._
import org.apache.mahout.math.scalabindings.RLikeVectorOps
import org.apache.mahout.math.scalabindings.MatrixOps
import org.apache.mahout.math._
import org.apache.mahout.math.scalabindings._
import org.apache.mahout.math.scalabindings.RLikeOps._
import org.apache.mahout.math.drm.RLikeDrmOps._
import org.apache.mahout.sparkbindings._
import org.apache.mahout.math.MatrixView

import collection._
import JavaConversions._




class Factorize extends Transformer{

  def transform[K: ClassTag](input: DrmLike[K]): DrmLike[K] ={
    if (!isFit) {
      //throw an error
    }


    implicit val ctx = input.context
    val bcastK = drmBroadcast(fitParams.get("k").get)
    val bcastFactorMap = drmBroadcast(fitParams.get("factorMap").get)

    val res = input.mapBlock() {
      case (keys, block) => {
        val k: Int = fitParams.get("k").get.get(0).toInt
        val output = new SparseMatrix(block.nrow, k)
        // This is how we take a vector of mapping to a map
        val fm = bcastFactorMap.all.toSeq.map(e => e.get -> e.index).toMap
        for (i <- 0 until output.nrow){
          output(i, ::) =  svec(fm.get(block.getQuick(i,0)).get -> 1.0 :: Nil, cardinality = k)
        }
        (keys, output)
      }
    }
    res
  }

  def fit[Int](input: DrmLike[Int]) = {
    // this should be done via allReduceBlock or something.
    val v: Vector = input.collect(::, 0)
    var a = new Array[Double](v.length)
    for (i <- 0 until v.length){
      a(i) = v.getElement(i).get
    }

    fitParams("factorMap") = dvec(a.distinct) //a.distinct.zipWithIndex.toMap
    fitParams("k") = dvec(a.distinct.length)

    isFit = true
  }

  def summary(): String = {
    println(
      s"""
        |${fitParams.get("k").get(0).toInt} categories
      """.stripMargin)
  }

}
