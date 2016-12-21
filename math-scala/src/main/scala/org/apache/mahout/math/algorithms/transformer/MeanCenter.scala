package org.apache.mahout.math.algorithms.transformer

import org.apache.mahout.math.drm
import org.apache.mahout.math.scalabindings._
import org.apache.mahout.math.scalabindings.RLikeVectorOps
import org.apache.mahout.math.{Vector => MahoutVector}
import org.apache.mahout.math.scalabindings.RLikeOps._
import org.apache.mahout.math.scalabindings._
import org.apache.mahout.math.scalabindings.RLikeVectorOps
import org.apache.mahout.math.scalabindings.RLikeMatrixOps
import org.apache.mahout.math.scalabindings.MatrixOps
import org.apache.mahout.math._
import org.apache.mahout.math.scalabindings._
import org.apache.mahout.math.drm._
import org.apache.mahout.math.scalabindings.RLikeOps._
import org.apache.mahout.math.drm.RLikeDrmOps._
import org.apache.mahout.sparkbindings._
import org.apache.mahout.math.Matrix

import collection._
import JavaConversions._
import scala.reflect.ClassTag

class MeanCenter extends Transformer {

  def fit[Int](input: DrmLike[Int]) = {
    fitParams("colMeansV") = input.colMeans
  }

  def transform[K: ClassTag](input: DrmLike[K]): DrmLike[K] = {

    if (!isFit) {
      //throw an error
    }

    implicit val ctx = input.context
    val colMeansV = fitParams.get("colMeansV").get
    val bcastV = drmBroadcast(colMeansV)

    val output = input.mapBlock(input.ncol) {
      case (keys, block) =>
        val copy: Matrix = block.cloned
        copy.foreach(row => row -= bcastV.value)
        (keys, copy)
    }
    output
  }

  def invTransform[K: ClassTag](input: DrmLike[K]): DrmLike[K] = {

    if (!isFit) {
      //throw an error
    }

    implicit val ctx = input.context
    val colMeansV = fitParams.get("colMeansV").get
    val bcastV = drmBroadcast(colMeansV)

    val output = input.mapBlock(input.ncol) {
      case (keys, block) =>
        val copy: Matrix = block.cloned
        copy.foreach(row => row += bcastV.value)
        (keys, copy)
    }
    output
  }

  def summary(): String = {
    "not implemented yet"
  }

}
