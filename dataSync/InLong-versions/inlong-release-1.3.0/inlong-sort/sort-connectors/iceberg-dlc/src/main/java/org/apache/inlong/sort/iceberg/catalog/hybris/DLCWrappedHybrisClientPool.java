/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.inlong.sort.iceberg.catalog.hybris;

import com.qcloud.dlc.metastore.DLCDataCatalogMetastoreClient;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.IMetaStoreClient;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.iceberg.ClientPoolImpl;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.hive.RuntimeMetaException;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransportException;

/**
 * DLC Catalog client pool.
 */
public class DLCWrappedHybrisClientPool extends ClientPoolImpl<IMetaStoreClient, TException> {

    // use appropriate ctor depending on whether we're working with Hive2 or Hive3 dependencies
    // we need to do this because there is a breaking API change between Hive2 and Hive3
    private static final DynConstructors.Ctor<DLCDataCatalogMetastoreClient> CLIENT_CTOR = DynConstructors.builder()
            .impl(DLCDataCatalogMetastoreClient.class, HiveConf.class)
            .impl(DLCDataCatalogMetastoreClient.class, Configuration.class).build();

    private final HiveConf hiveConf;

    public DLCWrappedHybrisClientPool(int poolSize, Configuration conf) {
        // Do not allow retry by default as we rely on RetryingHiveClient
        super(poolSize, TTransportException.class, false);
        this.hiveConf = new HiveConf(conf, DLCWrappedHybrisClientPool.class);
        this.hiveConf.addResource(conf);
    }

    @Override
    protected IMetaStoreClient newClient()  {
        try {
            try {
                return CLIENT_CTOR.newInstance(hiveConf);
            } catch (RuntimeException e) {
                // any MetaException would be wrapped into RuntimeException during reflection,
                // so let's double-check type here
                if (e.getCause() instanceof MetaException) {
                    throw (MetaException) e.getCause();
                }
                throw e;
            }
        } catch (MetaException e) {
            throw new RuntimeMetaException(e, "Failed to connect to Hive Metastore");
        } catch (Throwable t) {
            if (t.getMessage().contains("Another instance of Derby may have already booted")) {
                throw new RuntimeMetaException(t, "Failed to start an embedded metastore because embedded "
                        + "Derby supports only one client at a time. To fix this, use a metastore that supports "
                        + "multiple clients.");
            }

            throw new RuntimeMetaException(t, "Failed to connect to Hive Metastore");
        }
    }

    @Override
    protected IMetaStoreClient reconnect(IMetaStoreClient client) {
        try {
            client.close();
            client.reconnect();
        } catch (MetaException e) {
            throw new RuntimeMetaException(e, "Failed to reconnect to Hive Metastore");
        }
        return client;
    }

    @Override
    protected boolean isConnectionException(Exception e) {
        return super.isConnectionException(e) || (e != null && e instanceof MetaException
                && e.getMessage().contains("Got exception: org.apache.thrift.transport.TTransportException"));
    }

    @Override
    protected void close(IMetaStoreClient client) {
        client.close();
    }

    @VisibleForTesting
    HiveConf hiveConf() {
        return hiveConf;
    }
}
