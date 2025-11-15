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

package org.apache.cassandra.repair.autorepair;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for RepairDecision
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class RepairDecisionTest
{
    @Test
    public void testScheduledDecision()
    {
        RepairDecision decision = RepairDecision.builder()
                                                .timestamp(System.currentTimeMillis())
                                                .keyspace("test_ks")
                                                .table("test_table")
                                                .tokenRange("(-100, 100]")
                                                .type(RepairDecision.DecisionType.SCHEDULED)
                                                .reason("Time for repair")
                                                .priority(0.8)
                                                .scheduledTime(System.currentTimeMillis())
                                                .build();

        assertTrue(decision.shouldRepair());
        assertEquals(RepairDecision.DecisionType.SCHEDULED, decision.getType());
        assertEquals("test_ks", decision.getKeyspace());
        assertEquals("test_table", decision.getTable());
    }

    @Test
    public void testDeferredDecision()
    {
        RepairDecision decision = RepairDecision.builder()
                                                .timestamp(System.currentTimeMillis())
                                                .keyspace("test_ks")
                                                .table("test_table")
                                                .tokenRange("(-100, 100]")
                                                .type(RepairDecision.DecisionType.DEFERRED)
                                                .reason("System load too high")
                                                .build();

        assertFalse(decision.shouldRepair());
        assertEquals(RepairDecision.DecisionType.DEFERRED, decision.getType());
        assertEquals("System load too high", decision.getReason());
    }

    @Test
    public void testSkippedDecision()
    {
        RepairDecision decision = RepairDecision.builder()
                                                .timestamp(System.currentTimeMillis())
                                                .keyspace("test_ks")
                                                .table("test_table")
                                                .tokenRange("(-100, 100]")
                                                .type(RepairDecision.DecisionType.SKIPPED)
                                                .reason("Below minimum interval")
                                                .build();

        assertFalse(decision.shouldRepair());
        assertEquals(RepairDecision.DecisionType.SKIPPED, decision.getType());
    }

    @Test
    public void testDecisionWithMetadata()
    {
        RepairDecision decision = RepairDecision.builder()
                                                .timestamp(System.currentTimeMillis())
                                                .keyspace("test_ks")
                                                .table("test_table")
                                                .tokenRange("(-100, 100]")
                                                .type(RepairDecision.DecisionType.SCHEDULED)
                                                .reason("Scheduled repair")
                                                .addMetadata("cpu_usage", "0.45")
                                                .addMetadata("pending_compactions", "3")
                                                .build();

        assertEquals("0.45", decision.getMetadata().get("cpu_usage"));
        assertEquals("3", decision.getMetadata().get("pending_compactions"));
    }
}
