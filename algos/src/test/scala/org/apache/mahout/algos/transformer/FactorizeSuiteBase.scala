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
package org.apache.mahout.algos.transformer


/**
  * Created by rawkintrevo on 11/30/16.
  */
class FactorizeSuiteBase {
  /**
    * import org.apache.mahout.algos.transformer.Factorize

val A = dense((1,2,3), (1,3,2), (4,1,1))
val drmA = drmParallelize(A)
val col = drmA(::, 0 until 1)
val f = new Factorize()

f.fit(col)
val expectedFit = sparse(
          (0, 1.0) :: Nil,
          (0, 1.0) :: Nil,
          (1, 1.0) :: Nil
              )

val actualFit = f.transform(col).collect(::, ::)

expectedFit === actualFit
    */
}
