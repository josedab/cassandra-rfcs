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

import java.time.Duration;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for LoadMonitor
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class LoadMonitorTest
{
    private LoadMonitor loadMonitor;

    @Before
    public void setUp()
    {
        loadMonitor = new LoadMonitor(10);
    }

    @Test
    public void testLoadScoreCalculation()
    {
        LoadSnapshot snapshot = LoadSnapshot.builder()
                                            .cpuUsage(0.5)
                                            .memoryUsage(0.7)
                                            .compactionPending(5)
                                            .activeRepairs(1)
                                            .readLatency(10.0)
                                            .writeLatency(15.0)
                                            .timestamp(System.currentTimeMillis())
                                            .build();

        double score = loadMonitor.calculateLoadScore(snapshot);

        assertTrue("Load score should be between 0 and 1", score >= 0.0 && score <= 1.0);
    }

    @Test
    public void testLowLoadScore()
    {
        LoadSnapshot snapshot = LoadSnapshot.builder()
                                            .cpuUsage(0.1)
                                            .memoryUsage(0.5)
                                            .compactionPending(0)
                                            .activeRepairs(0)
                                            .readLatency(5.0)
                                            .writeLatency(5.0)
                                            .timestamp(System.currentTimeMillis())
                                            .build();

        double score = loadMonitor.calculateLoadScore(snapshot);

        assertTrue("Low load should result in low score", score < 0.3);
    }

    @Test
    public void testHighLoadScore()
    {
        LoadSnapshot snapshot = LoadSnapshot.builder()
                                            .cpuUsage(0.9)
                                            .memoryUsage(0.95)
                                            .compactionPending(15)
                                            .activeRepairs(5)
                                            .readLatency(60.0)
                                            .writeLatency(70.0)
                                            .timestamp(System.currentTimeMillis())
                                            .build();

        double score = loadMonitor.calculateLoadScore(snapshot);

        assertTrue("High load should result in high score", score > 0.7);
    }

    @Test
    public void testRecentSnapshotsRetrieval()
    {
        loadMonitor.clearHistory();

        // Add some snapshots
        for (int i = 0; i < 5; i++)
        {
            loadMonitor.getCurrentLoad();
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }

        List<LoadSnapshot> recent = loadMonitor.getRecentSnapshots(Duration.ofSeconds(10));

        assertTrue("Should have recent snapshots", recent.size() > 0);
    }
}
