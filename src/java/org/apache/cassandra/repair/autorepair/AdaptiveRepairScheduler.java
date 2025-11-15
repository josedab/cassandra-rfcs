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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * Adaptive repair scheduler that adjusts repair timing based on system load.
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class AdaptiveRepairScheduler
{
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveRepairScheduler.class);

    private final LoadMonitor loadMonitor;
    private final RepairRateController rateController;
    private final double loadThreshold;

    public AdaptiveRepairScheduler(double loadThreshold)
    {
        this(new LoadMonitor(), new RepairRateController(), loadThreshold);
    }

    @VisibleForTesting
    public AdaptiveRepairScheduler(LoadMonitor loadMonitor,
                                  RepairRateController rateController,
                                  double loadThreshold)
    {
        this.loadMonitor = loadMonitor;
        this.rateController = rateController;
        this.loadThreshold = loadThreshold;
    }

    /**
     * Check if repair should be deferred based on current system load
     *
     * @return true if repair should be deferred, false otherwise
     */
    public boolean shouldDeferRepair()
    {
        double currentLoad = loadMonitor.getCurrentLoad();

        // Adjust repair rate based on load
        rateController.adjustRate(currentLoad);

        // Defer if load exceeds threshold
        if (currentLoad > loadThreshold)
        {
            logger.debug("Deferring repair due to high system load: {:.2f} > {:.2f}",
                        currentLoad, loadThreshold);
            return true;
        }

        // Check predicted future load
        double predictedLoad = loadMonitor.predictFutureLoad(Duration.ofMinutes(15));
        if (predictedLoad > loadThreshold * 1.2)  // 20% margin for prediction uncertainty
        {
            logger.debug("Deferring repair due to predicted high load: {:.2f}",
                        predictedLoad);
            return true;
        }

        return false;
    }

    /**
     * Get the next repair delay based on current load
     *
     * @param baseDelay The base delay at normal conditions
     * @return The adjusted delay
     */
    public Duration getNextRepairDelay(Duration baseDelay)
    {
        return rateController.getNextRepairDelay(baseDelay);
    }

    /**
     * Get current system load (0.0 to 1.0)
     */
    public double getCurrentLoad()
    {
        return loadMonitor.getCurrentLoad();
    }

    /**
     * Get current repair rate multiplier
     */
    public double getCurrentRate()
    {
        return rateController.getCurrentRate();
    }

    /**
     * Get the load monitor (for testing and metrics)
     */
    @VisibleForTesting
    public LoadMonitor getLoadMonitor()
    {
        return loadMonitor;
    }

    /**
     * Get the rate controller (for testing and metrics)
     */
    @VisibleForTesting
    public RepairRateController getRateController()
    {
        return rateController;
    }

    /**
     * Get load threshold
     */
    public double getLoadThreshold()
    {
        return loadThreshold;
    }

    @Override
    public String toString()
    {
        return String.format("AdaptiveRepairScheduler{load=%.2f, rate=%.2f, threshold=%.2f}",
                           loadMonitor.getCurrentLoad(),
                           rateController.getCurrentRate(),
                           loadThreshold);
    }
}
