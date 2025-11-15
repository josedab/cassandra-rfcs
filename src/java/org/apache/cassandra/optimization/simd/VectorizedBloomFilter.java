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
package org.apache.cassandra.optimization.simd;

import java.util.concurrent.atomic.AtomicLongArray;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/**
 * Vectorized Bloom filter using SIMD for parallel hash computation.
 * Provides faster membership testing through parallel hash evaluation.
 */
public class VectorizedBloomFilter
{
    private static final VectorSpecies<Integer> INT_SPECIES = IntVector.SPECIES_PREFERRED;
    private static final int[] HASH_SEEDS = {0x1234, 0x5678, 0x9ABC, 0xDEF0};

    private final AtomicLongArray filter;
    private final int numHashes;
    private final int filterSize;
    private final boolean simdEnabled;

    public VectorizedBloomFilter(int filterSize, int numHashes)
    {
        this.filterSize = filterSize;
        this.numHashes = Math.min(numHashes, HASH_SEEDS.length);

        // Use longs to store bits (64 bits per element)
        int arraySize = (filterSize + 63) / 64;
        this.filter = new AtomicLongArray(arraySize);

        this.simdEnabled = VectorizedCRC.isSIMDAvailable();
    }

    /**
     * Add an element to the bloom filter
     */
    public void add(byte[] key)
    {
        int[] hashes = computeHashes(key);

        for (int i = 0; i < numHashes; i++)
        {
            int hash = hashes[i];
            int position = Math.abs(hash % filterSize);
            setBit(position);
        }
    }

    /**
     * Check if an element might be in the set
     */
    public boolean mightContain(byte[] key)
    {
        int[] hashes = computeHashes(key);

        for (int i = 0; i < numHashes; i++)
        {
            int hash = hashes[i];
            int position = Math.abs(hash % filterSize);

            if (!getBit(position))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Compute hash values using SIMD when available
     */
    private int[] computeHashes(byte[] key)
    {
        if (simdEnabled && key.length >= INT_SPECIES.length())
        {
            return computeHashesVectorized(key);
        }
        else
        {
            return computeHashesScalar(key);
        }
    }

    /**
     * Vectorized hash computation
     */
    private int[] computeHashesVectorized(byte[] key)
    {
        int[] hashes = new int[numHashes];

        // Load hash seeds into vector
        IntVector seedVector = IntVector.fromArray(INT_SPECIES, HASH_SEEDS, 0);

        // Compute hash from key bytes
        int keyHash = 0;
        for (byte b : key)
        {
            keyHash = 31 * keyHash + (b & 0xFF);
        }

        // Generate multiple hashes using vector operations
        IntVector keyVector = IntVector.broadcast(INT_SPECIES, keyHash);
        IntVector resultVector = seedVector.mul(keyVector)
                                           .lanewise(VectorOperators.ROL, 13)
                                           .mul(0xC2B2AE35);

        resultVector.intoArray(hashes, 0);

        return hashes;
    }

    /**
     * Scalar hash computation fallback
     */
    private int[] computeHashesScalar(byte[] key)
    {
        int[] hashes = new int[numHashes];

        for (int i = 0; i < numHashes; i++)
        {
            int hash = HASH_SEEDS[i];

            for (byte b : key)
            {
                hash = 31 * hash + (b & 0xFF);
            }

            hash ^= hash >>> 16;
            hash *= 0x85ebca6b;
            hash ^= hash >>> 13;
            hash *= 0xc2b2ae35;
            hash ^= hash >>> 16;

            hashes[i] = hash;
        }

        return hashes;
    }

    /**
     * Set a bit in the filter
     */
    private void setBit(int position)
    {
        int index = position / 64;
        int bit = position % 64;
        long mask = 1L << bit;

        long oldValue, newValue;
        do
        {
            oldValue = filter.get(index);
            newValue = oldValue | mask;
        }
        while (!filter.compareAndSet(index, oldValue, newValue));
    }

    /**
     * Get a bit from the filter
     */
    private boolean getBit(int position)
    {
        int index = position / 64;
        int bit = position % 64;
        long mask = 1L << bit;

        return (filter.get(index) & mask) != 0;
    }

    /**
     * Clear the filter
     */
    public void clear()
    {
        for (int i = 0; i < filter.length(); i++)
        {
            filter.set(i, 0);
        }
    }

    /**
     * Get filter size
     */
    public int getFilterSize()
    {
        return filterSize;
    }
}
