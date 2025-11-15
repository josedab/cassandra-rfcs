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

package org.apache.cassandra.db.virtual;

import java.util.Date;

import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.compaction.UnifiedCompactionStrategy;
import org.apache.cassandra.db.compaction.unified.UCSMonitor;
import org.apache.cassandra.db.marshal.*;
import org.apache.cassandra.dht.LocalPartitioner;
import org.apache.cassandra.schema.TableMetadata;

/**
 * Virtual table exposing UCS efficiency metrics.
 * Part of RFC-0003: Unified Compaction Strategy Production Hardening.
 */
public class UCSEfficiencyTable extends AbstractVirtualTable
{
    private static final String KEYSPACE_NAME = "keyspace_name";
    private static final String TABLE_NAME = "table_name";
    private static final String METRIC_NAME = "metric_name";
    private static final String METRIC_VALUE = "metric_value";
    private static final String MEASUREMENT_TIME = "measurement_time";
    private static final String COMPARISON_TO_STCS = "comparison_to_stcs";
    private static final String COMPARISON_TO_LCS = "comparison_to_lcs";

    private final UCSMonitor monitor = new UCSMonitor();

    public UCSEfficiencyTable(String keyspace)
    {
        super(TableMetadata.builder(keyspace, "ucs_efficiency")
                           .comment("UCS efficiency metrics and comparisons")
                           .kind(TableMetadata.Kind.VIRTUAL)
                           .partitioner(new LocalPartitioner(CompositeType.getInstance(UTF8Type.instance, UTF8Type.instance)))
                           .addPartitionKeyColumn(KEYSPACE_NAME, UTF8Type.instance)
                           .addPartitionKeyColumn(TABLE_NAME, UTF8Type.instance)
                           .addClusteringColumn(MEASUREMENT_TIME, TimestampType.instance)
                           .addClusteringColumn(METRIC_NAME, UTF8Type.instance)
                           .addRegularColumn(METRIC_VALUE, DoubleType.instance)
                           .addRegularColumn(COMPARISON_TO_STCS, DoubleType.instance)
                           .addRegularColumn(COMPARISON_TO_LCS, DoubleType.instance)
                           .build());
    }

    @Override
    public DataSet data()
    {
        SimpleDataSet result = new SimpleDataSet(metadata());

        // Iterate through all keyspaces and tables
        for (String keyspaceName : Keyspace.all())
        {
            Keyspace keyspace = Keyspace.open(keyspaceName);
            for (ColumnFamilyStore cfs : keyspace.getColumnFamilyStores())
            {
                if (cfs.getCompactionStrategy() instanceof UnifiedCompactionStrategy)
                {
                    addEfficiencyRows(result, cfs);
                }
            }
        }

        return result;
    }

    private void addEfficiencyRows(SimpleDataSet result, ColumnFamilyStore cfs)
    {
        UCSMonitor.EfficiencyMetrics metrics = monitor.getEfficiencyMetrics(cfs);
        Date measurementTime = new Date(metrics.timestamp);

        // Write amplification
        result.row(cfs.keyspace.getName(), cfs.name, measurementTime, "write_amplification")
              .column(METRIC_VALUE, metrics.writeAmplification)
              .column(COMPARISON_TO_STCS, metrics.comparisonToSTCS.getOrDefault("write_amplification", 0.0))
              .column(COMPARISON_TO_LCS, metrics.comparisonToLCS.getOrDefault("write_amplification", 0.0));

        // Read amplification
        result.row(cfs.keyspace.getName(), cfs.name, measurementTime, "read_amplification")
              .column(METRIC_VALUE, metrics.readAmplification)
              .column(COMPARISON_TO_STCS, metrics.comparisonToSTCS.getOrDefault("read_amplification", 0.0))
              .column(COMPARISON_TO_LCS, metrics.comparisonToLCS.getOrDefault("read_amplification", 0.0));

        // Space amplification
        result.row(cfs.keyspace.getName(), cfs.name, measurementTime, "space_amplification")
              .column(METRIC_VALUE, metrics.spaceAmplification)
              .column(COMPARISON_TO_STCS, metrics.comparisonToSTCS.getOrDefault("space_amplification", 0.0))
              .column(COMPARISON_TO_LCS, metrics.comparisonToLCS.getOrDefault("space_amplification", 0.0));
    }
}
