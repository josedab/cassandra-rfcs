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

import java.util.List;
import java.util.Map;

import org.apache.cassandra.db.marshal.*;
import org.apache.cassandra.dht.LocalPartitioner;
import org.apache.cassandra.schema.TableMetadata;
import org.apache.cassandra.tcm.migration.CMSMigrationTracker;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.TimeUUID;

/**
 * Virtual table exposing CMS migration events log.
 * Maps to system_views.cms_migration_events as defined in RFC-0001.
 */
public class CMSMigrationEventsTable extends AbstractVirtualTable
{
    private static final String EVENT_ID = "event_id";
    private static final String NODE_ID = "node_id";
    private static final String EVENT_TYPE = "event_type";
    private static final String EVENT_DESCRIPTION = "event_description";
    private static final String EVENT_TIMESTAMP = "event_timestamp";
    private static final String METADATA = "metadata";

    private final CMSMigrationTracker tracker;

    public CMSMigrationEventsTable(String keyspace, CMSMigrationTracker tracker)
    {
        super(TableMetadata.builder(keyspace, "cms_migration_events")
                          .kind(TableMetadata.Kind.VIRTUAL)
                          .partitioner(new LocalPartitioner(TimeUUIDType.instance))
                          .addPartitionKeyColumn(EVENT_ID, TimeUUIDType.instance)
                          .addRegularColumn(NODE_ID, UUIDType.instance)
                          .addRegularColumn(EVENT_TYPE, UTF8Type.instance)
                          .addRegularColumn(EVENT_DESCRIPTION, UTF8Type.instance)
                          .addRegularColumn(EVENT_TIMESTAMP, TimestampType.instance)
                          .addRegularColumn(METADATA, MapType.getInstance(UTF8Type.instance, UTF8Type.instance, true))
                          .build());
        this.tracker = tracker;
    }

    @Override
    public DataSet data()
    {
        SimpleDataSet result = new SimpleDataSet(metadata());

        List<CMSMigrationTracker.MigrationEvent> events = tracker.getAllEvents();

        for (CMSMigrationTracker.MigrationEvent event : events)
        {
            // Convert UUID to TimeUUID for event_id
            TimeUUID eventId = TimeUUID.fromUuid(event.eventId);

            result.row(eventId)
                  .column(NODE_ID, FBUtilities.getLocalHostUUID())
                  .column(EVENT_TYPE, event.type.name())
                  .column(EVENT_DESCRIPTION, event.description)
                  .column(EVENT_TIMESTAMP, event.timestamp)
                  .column(METADATA, event.metadata);
        }

        return result;
    }
}
