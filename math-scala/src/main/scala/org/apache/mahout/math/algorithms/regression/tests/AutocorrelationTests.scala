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

package org.apache.mahout.math.algorithms.regression.tests

import org.apache.mahout.math.algorithms.regression.Regressor
import org.apache.mahout.math.drm.DrmLike
import org.apache.mahout.math.drm.RLikeDrmOps._
import org.apache.mahout.math.function.Functions.SQUARE
import org.apache.mahout.math.scalabindings.RLikeOps._


object AutocorrelationTests {

  //https://en.wikipedia.org/wiki/Durbin%E2%80%93Watson_statistic
  /*
  To test for positive autocorrelation at significance α, the test statistic d is compared to lower and upper critical values (dL,α and dU,α):
      If d < dL,α, there is statistical evidence that the error terms are positively autocorrelated.
      If d > dU,α, there is no statistical evidence that the error terms are positively autocorrelated.
      If dL,α < d < dU,α, the test is inconclusive.

      Rule of Thumb:
       d < 2 : positive auto-correlation
       d = 2 : no auto-correlation
       d > 2 : negative auto-correlation
  */
  def DurbinWaton[K](model: Regressor[K]): Regressor[K] = {
    val e: DrmLike[K] = model.residuals(1 until model.residuals.nrow.toInt, 0 until 1)
    val e_t_1: DrmLike[K] = model.residuals(0 until model.residuals.nrow.toInt - 1, 0 until 1)
    val numerator = (e - e_t_1).assign(SQUARE).colSums
    val denominator = model.residuals.assign(SQUARE).colSums
    val dw = (numerator / denominator)
    model.testResults("durbin-watson test statistic") = dw
    model.summary += s"\nDurbin Watson Test Statistic: ${dw}"
    model
  }

}
