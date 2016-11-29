/**
  * Licensed to the Apache Software Foundation (ASF) under one
  * or more contributor license agreements. See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership. The ASF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License. You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing,
  * software distributed under the License is distributed on an
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, either express or implied. See the License for the
  * specific language governing permissions and limitations
  * under the License.
  */
package scala.org.apache.mahout.algos.regression

import org.apache.mahout.math.drm.DrmLike
import org.apache.mahout.math.scalabindings.RLikeOps._
import org.apache.mahout.math.drm.RLikeDrmOps._
import org.apache.mahout.math._
import org.apache.mahout.math.drm
import org.apache.mahout.math.drm.DrmLike
import org.apache.mahout.algos.Model
import org.apache.mahout.math.scalabindings._
import org.apache.mahout.math.{Vector => MahoutVector}

/**
  * An Example of a Native Mahout Algo
  */
class OLS extends Regressor{

  def fit[Int](drmX: DrmLike[Int])= {

    if (drmX.nrow != Y.length){
      "throw an error here"
    }

    val drmXtX = drmX.t %*% drmX

    val drmXty = drmX.t %*% Y


    val XtX = drmXtX.collect
    val Xty = drmXty.collect(::, 0)

    fitParams("beta") = solve(XtX, Xty)

    isFit = true
  }

  def predict[Int](drmX: DrmLike[Int]): MahoutVector = {
    // throw warning if not fit
    (drmX %*% fitParams.get("beta").get).collect(::, 0)
  }

  def summary() = {
    "pass"
  }
}
