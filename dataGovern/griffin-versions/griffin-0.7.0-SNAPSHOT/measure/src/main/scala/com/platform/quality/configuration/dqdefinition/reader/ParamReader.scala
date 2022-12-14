/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.platform.quality.configuration.dqdefinition.reader

import com.platform.quality.Loggable
import com.platform.quality.configuration.dqdefinition.Param

import scala.reflect.ClassTag
import scala.util.Try

trait ParamReader extends Loggable with Serializable {

  /**
   * read config param
   *
   * @tparam T     param type expected
   * @return       parsed param
   */
  def readConfig[T <: Param](implicit m: ClassTag[T]): Try[T]

  /**
   * validate config param
   *
   * @param param  param to be validated
   * @return       param itself
   */
  protected def validate[T <: Param](param: T): T = {
    griffinLogger.info(param.validate())
    param
  }

}
