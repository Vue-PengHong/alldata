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

package com.platform.quality.context.streaming.checkpoint.offset

import com.platform.quality.configuration.dqdefinition.CheckpointParam

import scala.util.Try
import scala.util.matching.Regex

case class OffsetCheckpointFactory(
    checkpointParams: Iterable[CheckpointParam],
    metricName: String)
    extends Serializable {

  val ZK_REGEX: Regex = """^(?i)zk|zookeeper$""".r

  def getOffsetCheckpoint(checkpointParam: CheckpointParam): Option[OffsetCheckpoint] = {
    val config = checkpointParam.getConfig
    val offsetCheckpointTry = checkpointParam.getType match {
      case ZK_REGEX() => Try(OffsetCheckpointInZK(config, metricName))
      case _ => throw new Exception("not supported info cache type")
    }
    offsetCheckpointTry.toOption
  }

}
