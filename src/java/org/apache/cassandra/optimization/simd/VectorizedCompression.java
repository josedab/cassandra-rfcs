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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

/**
 * Vectorized compression using SIMD for pattern matching.
 * Accelerates LZ4-style compression through parallel comparisons.
 */
public class VectorizedCompression
{
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final int MAX_OFFSET = 65535;
    private static final int MIN_MATCH = 4;
    private static final int HASH_LOG = 12;
    private static final int HASH_SIZE = 1 << HASH_LOG;
    private static final int HASH_MASK = HASH_SIZE - 1;

    private final boolean simdEnabled;

    public VectorizedCompression()
    {
        this.simdEnabled = VectorizedCRC.isSIMDAvailable();
    }

    /**
     * Compress data using LZ4-style compression with SIMD acceleration
     */
    public ByteBuffer compress(ByteBuffer input)
    {
        ByteBuffer output = ByteBuffer.allocate(input.remaining() + (input.remaining() / 255) + 16);

        if (simdEnabled)
        {
            compressWithSIMD(input, output);
        }
        else
        {
            compressScalar(input, output);
        }

        output.flip();
        return output;
    }

    /**
     * SIMD-accelerated compression
     */
    private void compressWithSIMD(ByteBuffer input, ByteBuffer output)
    {
        int[] hashTable = new int[HASH_SIZE];

        while (input.remaining() >= SPECIES.length())
        {
            int position = input.position();

            // Find match using SIMD
            MatchInfo match = findMatchVectorized(input, hashTable);

            if (match.length >= MIN_MATCH)
            {
                // Encode match
                encodeMatch(output, match.offset, match.length);
                input.position(position + match.length);
            }
            else
            {
                // Encode literal
                encodeLiteral(output, input.get());
            }

            // Update hash table
            updateHash(hashTable, input, position);
        }

        // Handle remaining bytes
        while (input.hasRemaining())
        {
            encodeLiteral(output, input.get());
        }
    }

    /**
     * Scalar compression fallback
     */
    private void compressScalar(ByteBuffer input, ByteBuffer output)
    {
        while (input.hasRemaining())
        {
            // Simple run-length encoding for demonstration
            byte current = input.get();
            int count = 1;

            while (input.hasRemaining() && input.get(input.position()) == current && count < 255)
            {
                input.get();
                count++;
            }

            if (count > 1)
            {
                output.put((byte) 0xFF); // Match marker
                output.put((byte) count);
                output.put(current);
            }
            else
            {
                output.put(current);
            }
        }
    }

    /**
     * Find match using SIMD pattern matching
     */
    private MatchInfo findMatchVectorized(ByteBuffer input, int[] hashTable)
    {
        if (input.remaining() < SPECIES.length() * 2)
        {
            return new MatchInfo(0, 0);
        }

        int position = input.position();

        // Current position vector
        ByteVector current = ByteVector.fromByteBuffer(
            SPECIES, input, position, ByteOrder.nativeOrder());

        // Search for matches in history
        int bestMatch = 0;
        int bestLength = 0;

        // Check hash table entries
        int hash = computeHash(input, position);
        int historyPos = hashTable[hash & HASH_MASK];

        if (historyPos > 0 && position - historyPos <= MAX_OFFSET)
        {
            // Compare using SIMD
            ByteVector history = ByteVector.fromByteBuffer(
                SPECIES, input, historyPos, ByteOrder.nativeOrder());

            VectorMask<Byte> matches = current.eq(history);
            int matchLength = countLeadingMatches(matches);

            if (matchLength >= MIN_MATCH)
            {
                bestLength = matchLength;
                bestMatch = position - historyPos;
            }
        }

        return new MatchInfo(bestMatch, bestLength);
    }

    /**
     * Count leading matching bytes in mask
     */
    private int countLeadingMatches(VectorMask<Byte> mask)
    {
        int count = 0;
        for (int i = 0; i < SPECIES.length(); i++)
        {
            if (mask.laneIsSet(i))
            {
                count++;
            }
            else
            {
                break;
            }
        }
        return count;
    }

    /**
     * Compute hash for position
     */
    private int computeHash(ByteBuffer input, int position)
    {
        if (input.remaining() < 4)
            return 0;

        int value = input.getInt(position);
        return (value * 2654435761) >>> (32 - HASH_LOG);
    }

    /**
     * Update hash table
     */
    private void updateHash(int[] hashTable, ByteBuffer input, int position)
    {
        int hash = computeHash(input, position);
        hashTable[hash & HASH_MASK] = position;
    }

    /**
     * Encode a match
     */
    private void encodeMatch(ByteBuffer output, int offset, int length)
    {
        output.put((byte) 0xFF); // Match marker
        output.putShort((short) offset);
        output.put((byte) length);
    }

    /**
     * Encode a literal byte
     */
    private void encodeLiteral(ByteBuffer output, byte value)
    {
        output.put(value);
    }

    /**
     * Match information
     */
    private static class MatchInfo
    {
        final int offset;
        final int length;

        MatchInfo(int offset, int length)
        {
            this.offset = offset;
            this.length = length;
        }
    }
}
