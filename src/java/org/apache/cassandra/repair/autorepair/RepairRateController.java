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

import com.google.common.annotations.VisibleForTesting;

/**
 * Controls the rate of repair operations based on system load.
 * Part of RFC-0002: Auto Repair Stabilization and Enhancement (CEP-37)
 */
public class RepairRateController
{
    private static final double MIN_RATE = 0.1;  // 10% of normal rate
    private static final double MAX_RATE = 1.5;  // 150% of normal rate
    private static final double INITIAL_RATE = 1.0;  // Normal rate

    private volatile double currentRate = INITIAL_RATE;

    /**
     * Adjust repair rate based on current system load
     *
     * @param systemLoad Load score between 0.0 (no load) and 1.0 (max load)
     */
    public void adjustRate(double systemLoad)
    {
        if (systemLoad > 0.8)
        {
            // High load - reduce repair rate aggressively
            currentRate = Math.max(MIN_RATE, currentRate * 0.9);
        }
        else if (systemLoad > 0.6)
        {
            // Moderate-high load - reduce repair rate gradually
            currentRate = Math.max(MIN_RATE, currentRate * 0.95);
        }
        else if (systemLoad < 0.3)
        {
            // Low load - increase repair rate
            currentRate = Math.min(MAX_RATE, currentRate * 1.1);
        }
        else if (systemLoad < 0.5)
        {
            // Moderate load - gradually return to normal
            currentRate = currentRate * 0.95 + INITIAL_RATE * 0.05;
        }
        // else: moderate load (0.5-0.6) - maintain current rate
    }

    /**
     * Get the next repair delay based on current rate
     *
     * @param baseDelay The base delay at normal rate
     * @return The adjusted delay
     */
    public Duration getNextRepairDelay(Duration baseDelay)
    {
        long adjustedMillis = (long) (baseDelay.toMillis() / currentRate);
        return Duration.ofMillis(adjustedMillis);
    }

    /**
     * Get current repair rate multiplier
     */
    public double getCurrentRate()
    {
        return currentRate;
    }

    /**
     * Reset to initial rate (for testing)
     */
    @VisibleForTesting
    public void reset()
    {
        currentRate = INITIAL_RATE;
    }

    /**
     * Set rate directly (for testing)
     */
    @VisibleForTesting
    public void setRate(double rate)
    {
        this.currentRate = Math.max(MIN_RATE, Math.min(MAX_RATE, rate));
    }

    @Override
    public String toString()
    {
        return String.format("RepairRateController{rate=%.2f (%.0f%%)}", currentRate, currentRate * 100);
    }
}
