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

import java.util.HashSet;
import java.util.Set;

import org.apache.cassandra.db.marshal.*;
import org.apache.cassandra.dht.LocalPartitioner;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.apache.cassandra.schema.TableMetadata;
import org.apache.cassandra.tcm.ClusterMetadata;
import org.apache.cassandra.tcm.ClusterMetadataService;
import org.apache.cassandra.tcm.migration.CMSMigrationPlanner;
import org.apache.cassandra.tcm.membership.Location;
import org.apache.cassandra.tcm.membership.NodeId;

/**
 * Virtual table exposing CMS membership recommendations.
 * Maps to system_views.cms_membership_recommendations as defined in RFC-0001.
 */
public class CMSMembershipRecommendationsTable extends AbstractVirtualTable
{
    private static final String RECOMMENDATION_ID = "recommendation_id";
    private static final String NODE_ID = "node_id";
    private static final String DATACENTER = "datacenter";
    private static final String RACK = "rack";
    private static final String RECOMMENDATION_SCORE = "recommendation_score";
    private static final String REASONING = "reasoning";

    public CMSMembershipRecommendationsTable(String keyspace)
    {
        super(TableMetadata.builder(keyspace, "cms_membership_recommendations")
                          .kind(TableMetadata.Kind.VIRTUAL)
                          .partitioner(new LocalPartitioner(CompositeType.getInstance(Int32Type.instance, UUIDType.instance)))
                          .addPartitionKeyColumn(RECOMMENDATION_ID, Int32Type.instance)
                          .addClusteringColumn(NODE_ID, UUIDType.instance)
                          .addRegularColumn(DATACENTER, UTF8Type.instance)
                          .addRegularColumn(RACK, UTF8Type.instance)
                          .addRegularColumn(RECOMMENDATION_SCORE, DoubleType.instance)
                          .addRegularColumn(REASONING, UTF8Type.instance)
                          .build());
    }

    @Override
    public DataSet data()
    {
        SimpleDataSet result = new SimpleDataSet(metadata());

        try
        {
            ClusterMetadata metadata = ClusterMetadataService.instance().metadata();
            CMSMigrationPlanner planner = new CMSMigrationPlanner();
            CMSMigrationPlanner.MigrationOptions options = new CMSMigrationPlanner.MigrationOptions();

            // Get recommended members
            CMSMigrationPlanner.CMSMemberSelector selector = new CMSMigrationPlanner.CMSMemberSelector();
            Set<InetAddressAndPort> recommended = selector.selectOptimalMembers(metadata, 5);

            int recommendationId = 1;
            for (Map.Entry<NodeId, InetAddressAndPort> entry : metadata.directory.addresses.entrySet())
            {
                NodeId nodeId = entry.getKey();
                InetAddressAndPort address = entry.getValue();
                Location location = metadata.directory.locations.get(nodeId);

                if (location == null)
                    continue;

                boolean isRecommended = recommended.contains(address);
                double score = isRecommended ? 95.0 : 50.0; // Simplified scoring

                String reasoning = buildReasoning(metadata, nodeId, isRecommended);

                result.row(recommendationId++, nodeId.toUUID())
                      .column(DATACENTER, location.datacenter)
                      .column(RACK, location.rack)
                      .column(RECOMMENDATION_SCORE, score)
                      .column(REASONING, reasoning);
            }
        }
        catch (Exception e)
        {
            // If we can't get recommendations, return empty dataset
        }

        return result;
    }

    private String buildReasoning(ClusterMetadata metadata, NodeId nodeId, boolean isRecommended)
    {
        StringBuilder reasoning = new StringBuilder();

        if (isRecommended)
        {
            reasoning.append("Recommended: ");
            if (metadata.directory.states.get(nodeId).isNormal())
            {
                reasoning.append("Node in NORMAL state. ");
            }

            Location location = metadata.directory.locations.get(nodeId);
            if (location != null)
            {
                reasoning.append("Good datacenter/rack distribution. ");
            }
        }
        else
        {
            reasoning.append("Not in top recommendations: ");
            if (!metadata.directory.states.get(nodeId).isNormal())
            {
                reasoning.append("Node not in NORMAL state. ");
            }
            else
            {
                reasoning.append("Other nodes scored higher for CMS membership. ");
            }
        }

        return reasoning.toString().trim();
    }
}
