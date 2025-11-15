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
package org.apache.cassandra.optimization.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for distributed reads using structured concurrency.
 * Structured concurrency ensures proper cleanup and cancellation of parallel tasks.
 */
public class StructuredConcurrencyHandler
{
    private static final Logger logger = LoggerFactory.getLogger(StructuredConcurrencyHandler.class);

    /**
     * Execute a distributed read across multiple replicas using structured concurrency.
     * This ensures all parallel reads are properly managed and cleaned up.
     */
    public <T> T executeDistributedRead(DistributedReadCommand<T> command) throws Exception
    {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            // Launch parallel reads to replicas
            List<Subtask<T>> tasks = new ArrayList<>();

            for (ReplicaCommand<T> replica : command.getReplicas()) {
                tasks.add(scope.fork(() -> replica.execute()));
            }

            // Wait for completion
            scope.join();
            scope.throwIfFailed();

            // Collect responses
            List<T> responses = tasks.stream()
                .map(Subtask::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            return command.mergeResponses(responses);
        }
    }

    /**
     * Execute with first successful response strategy
     */
    public <T> T executeWithFirstSuccess(DistributedReadCommand<T> command) throws Exception
    {
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<T>()) {
            // Fork tasks for each replica
            for (ReplicaCommand<T> replica : command.getReplicas()) {
                scope.fork(() -> replica.execute());
            }

            // Wait for first successful response
            scope.join();

            return scope.result();
        }
    }

    /**
     * Command interface for distributed reads
     */
    public interface DistributedReadCommand<T>
    {
        List<ReplicaCommand<T>> getReplicas();
        T mergeResponses(List<T> responses);
    }

    /**
     * Command interface for individual replica reads
     */
    public interface ReplicaCommand<T>
    {
        T execute() throws Exception;
    }
}
