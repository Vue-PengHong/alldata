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

package com.platform.quality.datasource.cache

import com.platform.quality.datasource.TimestampStorage
import org.apache.spark.sql._

/**
 * data source cache in parquet format
 */
case class StreamingCacheParquetClient(
    sparkSession: SparkSession,
    param: Map[String, Any],
    dsName: String,
    index: Int,
    timestampStorage: TimestampStorage)
    extends StreamingCacheClient {

  sparkSession.sparkContext.hadoopConfiguration.set("parquet.enable.summary-metadata", "false")

  protected def writeDataFrame(dfw: DataFrameWriter[Row], path: String): Unit = {
    info(s"write path: $path")
    dfw.parquet(path)
  }

  protected def readDataFrame(dfr: DataFrameReader, path: String): DataFrame = {
    dfr.parquet(path)
  }

}
