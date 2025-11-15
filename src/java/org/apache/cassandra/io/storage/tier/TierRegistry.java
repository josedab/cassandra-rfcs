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
package org.apache.cassandra.io.storage.tier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for managing storage tiers.
 */
public class TierRegistry
{
    private static final Logger logger = LoggerFactory.getLogger(TierRegistry.class);
    private static final TierRegistry instance = new TierRegistry();

    private final Map<String, StorageTier> tiers = new ConcurrentHashMap<>();
    private volatile String defaultTierName = "hot";

    private TierRegistry()
    {
        // Private constructor for singleton
    }

    public static TierRegistry instance()
    {
        return instance;
    }

    /**
     * Register a storage tier.
     *
     * @param tier the tier to register
     */
    public void registerTier(StorageTier tier)
    {
        String name = tier.getName();
        if (tiers.containsKey(name))
        {
            logger.warn("Tier {} already registered, replacing", name);
        }
        tiers.put(name, tier);
        logger.info("Registered storage tier: {} (priority={})", name, tier.getPriority());
    }

    /**
     * Unregister a storage tier.
     *
     * @param tierName the name of the tier to unregister
     */
    public void unregisterTier(String tierName)
    {
        StorageTier removed = tiers.remove(tierName);
        if (removed != null)
        {
            logger.info("Unregistered storage tier: {}", tierName);
        }
    }

    /**
     * Get a tier by name.
     *
     * @param tierName the tier name
     * @return the tier, or null if not found
     */
    public StorageTier getTier(String tierName)
    {
        return tiers.get(tierName);
    }

    /**
     * Get all registered tiers.
     *
     * @return unmodifiable collection of all tiers
     */
    public Collection<StorageTier> getAllTiers()
    {
        return Collections.unmodifiableCollection(tiers.values());
    }

    /**
     * Get all tiers sorted by priority (lowest first).
     *
     * @return list of tiers sorted by priority
     */
    public List<StorageTier> getTiersByPriority()
    {
        return tiers.values().stream()
                   .sorted(Comparator.comparingInt(StorageTier::getPriority))
                   .collect(Collectors.toList());
    }

    /**
     * Get the default tier.
     *
     * @return the default tier
     */
    public StorageTier getDefaultTier()
    {
        return tiers.get(defaultTierName);
    }

    /**
     * Set the default tier name.
     *
     * @param tierName the name of the tier to use as default
     */
    public void setDefaultTier(String tierName)
    {
        if (!tiers.containsKey(tierName))
        {
            throw new IllegalArgumentException("Tier not registered: " + tierName);
        }
        this.defaultTierName = tierName;
        logger.info("Set default tier to: {}", tierName);
    }

    /**
     * Get the next higher priority tier (lower priority number).
     *
     * @param currentTier the current tier
     * @return the higher priority tier, or null if already at highest
     */
    public StorageTier getHigherTier(StorageTier currentTier)
    {
        int currentPriority = currentTier.getPriority();
        return tiers.values().stream()
                   .filter(t -> t.getPriority() < currentPriority)
                   .max(Comparator.comparingInt(StorageTier::getPriority))
                   .orElse(null);
    }

    /**
     * Get the next lower priority tier (higher priority number).
     *
     * @param currentTier the current tier
     * @return the lower priority tier, or null if already at lowest
     */
    public StorageTier getLowerTier(StorageTier currentTier)
    {
        int currentPriority = currentTier.getPriority();
        return tiers.values().stream()
                   .filter(t -> t.getPriority() > currentPriority)
                   .min(Comparator.comparingInt(StorageTier::getPriority))
                   .orElse(null);
    }

    /**
     * Check if a tier is registered.
     *
     * @param tierName the tier name
     * @return true if the tier is registered
     */
    public boolean hasTier(String tierName)
    {
        return tiers.containsKey(tierName);
    }

    /**
     * Get the number of registered tiers.
     *
     * @return the number of tiers
     */
    public int getTierCount()
    {
        return tiers.size();
    }
}
