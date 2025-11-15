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
package org.apache.cassandra.cql3.query.planning;

/**
 * Represents an estimate of the amount of data that will be read for a query.
 */
public class DataSizeEstimate
{
    private final long bytes;
    private final long compressedBytes;

    public DataSizeEstimate(long bytes)
    {
        this(bytes, bytes);
    }

    public DataSizeEstimate(long bytes, long compressedBytes)
    {
        this.bytes = bytes;
        this.compressedBytes = compressedBytes;
    }

    public long getBytes()
    {
        return bytes;
    }

    public long getCompressedBytes()
    {
        return compressedBytes;
    }

    public double getCompressionRatio()
    {
        return bytes > 0 ? (double) compressedBytes / bytes : 1.0;
    }

    @Override
    public String toString()
    {
        return String.format("DataSizeEstimate{bytes=%d, compressedBytes=%d, ratio=%.2f}",
                             bytes, compressedBytes, getCompressionRatio());
    }
}
