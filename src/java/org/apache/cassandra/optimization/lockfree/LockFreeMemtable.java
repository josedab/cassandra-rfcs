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

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lock-free memtable implementation using ConcurrentSkipListMap.
 * Provides concurrent insert/read operations without blocking.
 */
public class LockFreeMemtable<K extends Comparable<K>, V>
{
    private static final Logger logger = LoggerFactory.getLogger(LockFreeMemtable.class);

    private final ConcurrentSkipListMap<K, RowData<V>> data;
    private final AtomicLong size;
    private final StampedLock flushLock;
    private final long maxSize;

    public LockFreeMemtable(long maxSize)
    {
        this.maxSize = maxSize;
        this.data = new ConcurrentSkipListMap<>();
        this.size = new AtomicLong(0);
        this.flushLock = new StampedLock();

        logger.info("LockFreeMemtable initialized with max size: {} bytes", maxSize);
    }

    /**
     * Insert a row into the memtable using lock-free merge
     */
    public void insert(K key, V row)
    {
        RowData<V> newData = new RowData<>(row);

        RowData<V> existing = data.merge(key, newData, (oldData, newRow) -> {
            // Lock-free merge using the compute function
            return oldData.merge(newRow);
        });

        // Update size atomically
        long rowSize = newData.serializedSize();
        long currentSize = size.addAndGet(rowSize);

        if (currentSize > maxSize)
        {
            logger.warn("Memtable size {} exceeds maximum {}, flush recommended", currentSize, maxSize);
        }
    }

    /**
     * Get a row from the memtable
     */
    public V get(K key)
    {
        RowData<V> rowData = data.get(key);
        return rowData != null ? rowData.getData() : null;
    }

    /**
     * Get current memtable size in bytes
     */
    public long getSize()
    {
        return size.get();
    }

    /**
     * Check if memtable should be flushed
     */
    public boolean shouldFlush()
    {
        return size.get() >= maxSize;
    }

    /**
     * Get the number of rows in the memtable
     */
    public int getRowCount()
    {
        return data.size();
    }

    /**
     * Clear the memtable
     */
    public void clear()
    {
        long stamp = flushLock.writeLock();
        try
        {
            data.clear();
            size.set(0);
        }
        finally
        {
            flushLock.unlockWrite(stamp);
        }
    }

    /**
     * Get the underlying data map (for iteration during flush)
     */
    public ConcurrentSkipListMap<K, RowData<V>> getData()
    {
        return data;
    }

    /**
     * Row data wrapper
     */
    public static class RowData<V>
    {
        private final V data;
        private final long timestamp;
        private volatile long cachedSize = -1;

        public RowData(V data)
        {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public V getData()
        {
            return data;
        }

        public long getTimestamp()
        {
            return timestamp;
        }

        /**
         * Merge this row with another (newer wins)
         */
        public RowData<V> merge(RowData<V> other)
        {
            return other.timestamp > this.timestamp ? other : this;
        }

        /**
         * Calculate serialized size (simplified - would be more complex in reality)
         */
        public long serializedSize()
        {
            if (cachedSize >= 0)
                return cachedSize;

            // Simplified size calculation
            // In reality, this would depend on the actual data structure
            cachedSize = estimateSize(data);
            return cachedSize;
        }

        private long estimateSize(V data)
        {
            // Simple estimation - in real implementation would serialize
            return 128; // Base row overhead
        }
    }
}
