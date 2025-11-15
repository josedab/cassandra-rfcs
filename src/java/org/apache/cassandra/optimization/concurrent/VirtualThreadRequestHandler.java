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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Request handler that uses virtual threads for read operations.
 * Virtual threads automatically yield on blocking operations, allowing
 * efficient handling of thousands of concurrent requests.
 */
public class VirtualThreadRequestHandler
{
    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadRequestHandler.class);

    private final VirtualThreadExecutor executor;

    public VirtualThreadRequestHandler(VirtualThreadExecutor executor)
    {
        this.executor = executor;
    }

    /**
     * Handle a read request using virtual threads.
     * The virtual thread will automatically yield when blocking on I/O,
     * freeing up the carrier thread for other work.
     */
    public <T> void handleRead(ReadCommand<T> command, ResponseHandler<T> handler)
    {
        executor.submit(() -> {
            try {
                // Virtual thread automatically yields on blocking operations
                T result = command.execute();
                handler.onSuccess(result);
            } catch (Exception e) {
                logger.error("Error handling read command", e);
                handler.onFailure(e);
            }
        });
    }

    /**
     * Handle a write request using virtual threads
     */
    public <T> void handleWrite(WriteCommand<T> command, ResponseHandler<T> handler)
    {
        executor.submit(() -> {
            try {
                T result = command.execute();
                handler.onSuccess(result);
            } catch (Exception e) {
                logger.error("Error handling write command", e);
                handler.onFailure(e);
            }
        });
    }

    /**
     * Command interface for read operations
     */
    public interface ReadCommand<T>
    {
        T execute() throws Exception;
    }

    /**
     * Command interface for write operations
     */
    public interface WriteCommand<T>
    {
        T execute() throws Exception;
    }

    /**
     * Response handler interface
     */
    public interface ResponseHandler<T>
    {
        void onSuccess(T result);
        void onFailure(Exception e);
    }
}
