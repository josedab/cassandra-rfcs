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
package org.apache.cassandra.optimization.lockfree;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lock-free row cache with LRU eviction.
 * Uses atomic operations for thread-safe access without blocking.
 */
public class LockFreeRowCache<K, V>
{
    private static final Logger logger = LoggerFactory.getLogger(LockFreeRowCache.class);

    private final ConcurrentHashMap<K, CachedRow<V>> cache;
    private final AtomicLong size;
    private final ConcurrentLinkedDeque<K> lru;
    private final long maxSize;

    public LockFreeRowCache(long maxSize)
    {
        this.maxSize = maxSize;
        this.cache = new ConcurrentHashMap<>();
        this.size = new AtomicLong(0);
        this.lru = new ConcurrentLinkedDeque<>();

        logger.info("LockFreeRowCache initialized with max size: {} bytes", maxSize);
    }

    /**
     * Get a row from the cache
     */
    public V get(K key)
    {
        CachedRow<V> row = cache.get(key);
        if (row != null)
        {
            // Update access time atomically
            row.touch();

            // Move to front of LRU (lock-free)
            lru.remove(key);
            lru.addFirst(key);

            return row.getData();
        }
        return null;
    }

    /**
     * Put a row into the cache
     */
    public void put(K key, V row)
    {
        CachedRow<V> cachedRow = new CachedRow<>(row);
        CachedRow<V> previous = cache.put(key, cachedRow);

        // Update size
        long delta = cachedRow.size() - (previous != null ? previous.size() : 0);
        long currentSize = size.addAndGet(delta);

        // Update LRU
        if (previous == null)
        {
            lru.addFirst(key);
        }
        else
        {
            // Move to front
            lru.remove(key);
            lru.addFirst(key);
        }

        // Evict if necessary (lock-free)
        while (currentSize > maxSize)
        {
            K victim = lru.pollLast();
            if (victim != null)
            {
                evict(victim);
                currentSize = size.get();
            }
            else
            {
                break;
            }
        }
    }

    /**
     * Evict a specific key from the cache
     */
    private void evict(K key)
    {
        CachedRow<V> removed = cache.remove(key);
        if (removed != null)
        {
            size.addAndGet(-removed.size());
            lru.remove(key);
        }
    }

    /**
     * Get current cache size in bytes
     */
    public long getSize()
    {
        return size.get();
    }

    /**
     * Get number of entries in cache
     */
    public int getEntryCount()
    {
        return cache.size();
    }

    /**
     * Clear the cache
     */
    public void clear()
    {
        cache.clear();
        lru.clear();
        size.set(0);
    }

    /**
     * Cached row wrapper
     */
    public static class CachedRow<V>
    {
        private final V data;
        private final AtomicLong lastAccess;
        private final long rowSize;

        public CachedRow(V data)
        {
            this.data = data;
            this.lastAccess = new AtomicLong(System.nanoTime());
            this.rowSize = estimateSize(data);
        }

        public V getData()
        {
            return data;
        }

        public void touch()
        {
            lastAccess.set(System.nanoTime());
        }

        public long getLastAccess()
        {
            return lastAccess.get();
        }

        public long size()
        {
            return rowSize;
        }

        private long estimateSize(V data)
        {
            // Simplified size estimation
            // In reality, would use actual serialized size
            return 256; // Base overhead
        }
    }
}
