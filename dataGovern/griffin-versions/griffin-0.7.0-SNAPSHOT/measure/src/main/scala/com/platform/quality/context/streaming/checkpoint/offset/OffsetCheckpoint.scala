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

import com.platform.quality.Loggable
import com.platform.quality.context.streaming.checkpoint.lock.CheckpointLock

trait OffsetCheckpoint extends Loggable with Serializable {

  def init(): Unit
  def available(): Boolean
  def close(): Unit

  def cache(kvs: Map[String, String]): Unit
  def read(keys: Iterable[String]): Map[String, String]
  def delete(keys: Iterable[String]): Unit
  def clear(): Unit

  def listKeys(path: String): List[String]

  def genLock(s: String): CheckpointLock

}
