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

import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.compaction.UnifiedCompactionStrategy;
import org.apache.cassandra.db.compaction.unified.UCSMonitor;
import org.apache.cassandra.db.marshal.*;
import org.apache.cassandra.dht.LocalPartitioner;
import org.apache.cassandra.schema.TableMetadata;

/**
 * Virtual table exposing UCS shard distribution and balance.
 * Part of RFC-0003: Unified Compaction Strategy Production Hardening.
 */
public class UCSShardsTable extends AbstractVirtualTable
{
    private static final String KEYSPACE_NAME = "keyspace_name";
    private static final String TABLE_NAME = "table_name";
    private static final String SHARD_ID = "shard_id";
    private static final String LEVEL = "level";
    private static final String SSTABLE_COUNT = "sstable_count";
    private static final String TOTAL_SIZE_BYTES = "total_size_bytes";
    private static final String AVERAGE_SSTABLE_SIZE_BYTES = "average_sstable_size_bytes";
    private static final String READ_AMPLIFICATION_SCORE = "read_amplification_score";

    private final UCSMonitor monitor = new UCSMonitor();

    public UCSShardsTable(String keyspace)
    {
        super(TableMetadata.builder(keyspace, "ucs_shards")
                           .comment("UCS shard distribution and balance metrics")
                           .kind(TableMetadata.Kind.VIRTUAL)
                           .partitioner(new LocalPartitioner(CompositeType.getInstance(UTF8Type.instance, UTF8Type.instance)))
                           .addPartitionKeyColumn(KEYSPACE_NAME, UTF8Type.instance)
                           .addPartitionKeyColumn(TABLE_NAME, UTF8Type.instance)
                           .addClusteringColumn(SHARD_ID, Int32Type.instance)
                           .addClusteringColumn(LEVEL, Int32Type.instance)
                           .addRegularColumn(SSTABLE_COUNT, Int32Type.instance)
                           .addRegularColumn(TOTAL_SIZE_BYTES, LongType.instance)
                           .addRegularColumn(AVERAGE_SSTABLE_SIZE_BYTES, LongType.instance)
                           .addRegularColumn(READ_AMPLIFICATION_SCORE, DoubleType.instance)
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
                    addShardRows(result, cfs);
                }
            }
        }

        return result;
    }

    private void addShardRows(SimpleDataSet result, ColumnFamilyStore cfs)
    {
        UCSMonitor.ShardStatistics stats = monitor.getShardStatistics(cfs);

        for (UCSMonitor.ShardInfo shard : stats.shards)
        {
            for (UCSMonitor.LevelInfo level : shard.levels)
            {
                result.row(cfs.keyspace.getName(), cfs.name, shard.shardId, level.level)
                      .column(SSTABLE_COUNT, level.sstableCount)
                      .column(TOTAL_SIZE_BYTES, level.totalSize)
                      .column(AVERAGE_SSTABLE_SIZE_BYTES, level.averageSize)
                      .column(READ_AMPLIFICATION_SCORE, shard.readAmplification);
            }
        }
    }
}
