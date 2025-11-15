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
 * Parameters for query execution including page size, timeout, and parallelism.
 */
public class ExecutionParams
{
    private int pageSize;
    private long timeout;
    private int parallelism;
    private boolean adaptiveExecution;

    public ExecutionParams()
    {
        this.pageSize = 100;
        this.timeout = 10000L; // 10 seconds
        this.parallelism = 1;
        this.adaptiveExecution = false;
    }

    public int getPageSize()
    {
        return pageSize;
    }

    public void setPageSize(int pageSize)
    {
        this.pageSize = Math.max(1, pageSize);
    }

    public long getTimeout()
    {
        return timeout;
    }

    public void setTimeout(long timeout)
    {
        this.timeout = Math.max(0, timeout);
    }

    public int getParallelism()
    {
        return parallelism;
    }

    public void setParallelism(int parallelism)
    {
        this.parallelism = Math.max(1, parallelism);
    }

    public boolean isAdaptiveExecution()
    {
        return adaptiveExecution;
    }

    public void setAdaptiveExecution(boolean adaptiveExecution)
    {
        this.adaptiveExecution = adaptiveExecution;
    }

    /**
     * Increase parallelism for better throughput.
     */
    public void increaseParallelism()
    {
        this.parallelism = Math.min(parallelism * 2, 32);
    }

    /**
     * Decrease parallelism to reduce resource usage.
     */
    public void decreaseParallelism()
    {
        this.parallelism = Math.max(parallelism / 2, 1);
    }

    /**
     * Create a copy of these execution parameters.
     */
    public ExecutionParams copy()
    {
        ExecutionParams copy = new ExecutionParams();
        copy.pageSize = this.pageSize;
        copy.timeout = this.timeout;
        copy.parallelism = this.parallelism;
        copy.adaptiveExecution = this.adaptiveExecution;
        return copy;
    }

    @Override
    public String toString()
    {
        return String.format("ExecutionParams{pageSize=%d, timeout=%dms, parallelism=%d, adaptive=%s}",
                             pageSize, timeout, parallelism, adaptiveExecution);
    }
}
