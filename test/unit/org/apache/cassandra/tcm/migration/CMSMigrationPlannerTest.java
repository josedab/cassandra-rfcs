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

package org.apache.cassandra.tcm.migration;

import java.time.Duration;

import org.junit.Test;

import org.apache.cassandra.tcm.migration.CMSMigrationPlanner.MigrationPlan;
import org.apache.cassandra.tcm.migration.CMSMigrationPlanner.MigrationPhase;
import org.apache.cassandra.tcm.migration.CMSMigrationPlanner.MigrationOptions;

import static org.junit.Assert.*;

/**
 * Unit tests for CMSMigrationPlanner.
 */
public class CMSMigrationPlannerTest
{
    @Test
    public void testMigrationPlanCreation()
    {
        MigrationPlan plan = new MigrationPlan();

        MigrationPhase phase1 = new MigrationPhase("test_phase", Duration.ofMinutes(10));
        phase1.addStep("Step 1");
        phase1.addStep("Step 2");

        plan.addPhase(phase1);

        assertEquals(1, plan.getPhases().size());
        assertEquals(Duration.ofMinutes(10), plan.getEstimatedDuration());
    }

    @Test
    public void testMigrationPhaseSteps()
    {
        MigrationPhase phase = new MigrationPhase("test_phase", Duration.ofMinutes(5));
        phase.addStep("Step 1");
        phase.addStep("Step 2");
        phase.addStep("Step 3");

        assertEquals("test_phase", phase.getName());
        assertEquals(3, phase.getSteps().size());
        assertEquals(Duration.ofMinutes(5), phase.getEstimatedDuration());
    }

    @Test
    public void testMigrationPhaseMetadata()
    {
        MigrationPhase phase = new MigrationPhase("test_phase", Duration.ofMinutes(5));
        phase.setMetadata("key1", "value1");
        phase.setMetadata("key2", "value2");

        assertEquals(2, phase.getMetadata().size());
        assertEquals("value1", phase.getMetadata().get("key1"));
        assertEquals("value2", phase.getMetadata().get("key2"));
    }

    @Test
    public void testMigrationOptions()
    {
        MigrationOptions options = new MigrationOptions();

        assertFalse(options.hasExplicitMembers());
        assertEquals(3, options.getCmsSize());
        assertEquals(100, options.getBatchSize());
        assertEquals(10, options.getThrottleMs());
        assertTrue(options.isBackupEnabled());
        assertTrue(options.isCleanupEnabled());

        options.setCmsSize(5);
        options.setBackupEnabled(false);

        assertEquals(5, options.getCmsSize());
        assertFalse(options.isBackupEnabled());
    }

    @Test
    public void testMigrationPlanMultiplePhases()
    {
        MigrationPlan plan = new MigrationPlan();

        plan.addPhase(new MigrationPhase("phase1", Duration.ofMinutes(10)));
        plan.addPhase(new MigrationPhase("phase2", Duration.ofMinutes(20)));
        plan.addPhase(new MigrationPhase("phase3", Duration.ofMinutes(5)));

        assertEquals(3, plan.getPhases().size());
        assertEquals(Duration.ofMinutes(35), plan.getEstimatedDuration());
    }
}
