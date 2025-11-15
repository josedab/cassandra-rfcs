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

import java.time.Instant;

import org.apache.cassandra.db.marshal.*;
import org.apache.cassandra.dht.LocalPartitioner;
import org.apache.cassandra.schema.TableMetadata;
import org.apache.cassandra.tcm.migration.CMSMigrationTracker;
import org.apache.cassandra.utils.FBUtilities;

/**
 * Virtual table exposing real-time CMS migration status.
 * Maps to system_views.cms_migration_status as defined in RFC-0001.
 */
public class CMSMigrationStatusTable extends AbstractVirtualTable
{
    private static final String NODE_ID = "node_id";
    private static final String MIGRATION_PHASE = "migration_phase";
    private static final String MIGRATION_STATE = "migration_state";
    private static final String PROGRESS_PERCENTAGE = "progress_percentage";
    private static final String STARTED_AT = "started_at";
    private static final String LAST_UPDATE = "last_update";
    private static final String ESTIMATED_COMPLETION = "estimated_completion";
    private static final String ERROR_MESSAGE = "error_message";

    private final CMSMigrationTracker tracker;

    public CMSMigrationStatusTable(String keyspace, CMSMigrationTracker tracker)
    {
        super(TableMetadata.builder(keyspace, "cms_migration_status")
                          .kind(TableMetadata.Kind.VIRTUAL)
                          .partitioner(new LocalPartitioner(UUIDType.instance))
                          .addPartitionKeyColumn(NODE_ID, UUIDType.instance)
                          .addRegularColumn(MIGRATION_PHASE, UTF8Type.instance)
                          .addRegularColumn(MIGRATION_STATE, UTF8Type.instance)
                          .addRegularColumn(PROGRESS_PERCENTAGE, Int32Type.instance)
                          .addRegularColumn(STARTED_AT, TimestampType.instance)
                          .addRegularColumn(LAST_UPDATE, TimestampType.instance)
                          .addRegularColumn(ESTIMATED_COMPLETION, TimestampType.instance)
                          .addRegularColumn(ERROR_MESSAGE, UTF8Type.instance)
                          .build());
        this.tracker = tracker;
    }

    @Override
    public DataSet data()
    {
        SimpleDataSet result = new SimpleDataSet(metadata());

        CMSMigrationTracker.MigrationState state = tracker.getCurrentState();
        CMSMigrationTracker.MigrationProgress progress = state.getProgress();

        result.row(FBUtilities.getLocalHostUUID())
              .column(MIGRATION_PHASE, progress != null ? progress.phase : "none")
              .column(MIGRATION_STATE, state.name())
              .column(PROGRESS_PERCENTAGE, progress != null ? (int) progress.percentage : 0)
              .column(STARTED_AT, progress != null ? progress.timestamp : null)
              .column(LAST_UPDATE, progress != null ? progress.timestamp : null)
              .column(ESTIMATED_COMPLETION, progress != null ? progress.estimatedCompletion : null)
              .column(ERROR_MESSAGE, state.getErrorMessage());

        return result;
    }
}
