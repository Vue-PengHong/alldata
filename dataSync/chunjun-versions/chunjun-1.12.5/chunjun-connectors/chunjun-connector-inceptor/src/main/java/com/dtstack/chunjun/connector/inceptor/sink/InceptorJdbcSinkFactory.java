/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtstack.chunjun.connector.inceptor.sink;

import com.dtstack.chunjun.conf.SyncConf;
import com.dtstack.chunjun.connector.inceptor.conf.InceptorConf;
import com.dtstack.chunjun.connector.inceptor.dialect.InceptorDialect;
import com.dtstack.chunjun.connector.inceptor.util.InceptorDbUtil;
import com.dtstack.chunjun.connector.jdbc.conf.JdbcConf;
import com.dtstack.chunjun.connector.jdbc.sink.JdbcOutputFormatBuilder;
import com.dtstack.chunjun.connector.jdbc.sink.JdbcSinkFactory;
import com.dtstack.chunjun.converter.AbstractRowConverter;
import com.dtstack.chunjun.util.TableUtil;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSink;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.RowType;

public class InceptorJdbcSinkFactory extends JdbcSinkFactory {

    InceptorDialect inceptorDialect;

    public InceptorJdbcSinkFactory(SyncConf syncConf) {
        super(syncConf, null);
        inceptorDialect = InceptorDbUtil.getDialectWithDriverType(jdbcConf);
        jdbcConf.setJdbcUrl(inceptorDialect.appendJdbcTransactionType(jdbcConf.getJdbcUrl()));
        super.jdbcDialect = inceptorDialect;
    }

    @Override
    public DataStreamSink<RowData> createSink(DataStream<RowData> dataSet) {
        JdbcOutputFormatBuilder builder = getBuilder();
        builder.setJdbcConf(jdbcConf);
        builder.setJdbcDialect(jdbcDialect);

        AbstractRowConverter rowConverter = null;
        // ??????????????????transform
        if (!useAbstractBaseColumn) {
            final RowType rowType =
                    TableUtil.createRowType(jdbcConf.getColumn(), getRawTypeConverter());
            rowConverter = jdbcDialect.getRowConverter(rowType);
        }
        builder.setRowConverter(rowConverter);
        if (builder instanceof InceptorHdfsOutputFormatBuilder) {
            // InceptorHdfsOutputFormatBuilder ???????????????????????????????????????????????????????????? ?????????????????????true????????????orc text
            // parquet????????????????????????
            ((InceptorHdfsOutputFormatBuilder) builder).setTransactionTable(true);
        }

        return createOutput(dataSet, builder.finish());
    }

    @Override
    protected Class<? extends JdbcConf> getConfClass() {
        return InceptorConf.class;
    }

    @Override
    protected JdbcOutputFormatBuilder getBuilder() {
        return ((InceptorDialect) jdbcDialect).getOutputFormatBuilder();
    }
}
