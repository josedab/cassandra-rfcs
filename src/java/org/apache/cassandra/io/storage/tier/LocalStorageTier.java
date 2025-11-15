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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.io.sstable.Descriptor;
import org.apache.cassandra.io.sstable.format.SSTableReader;
import org.apache.cassandra.io.sstable.format.SSTableWriter;
import org.apache.cassandra.io.sstable.metadata.MetadataComponent;

/**
 * Local filesystem-based storage tier implementation.
 *
 * This tier stores SSTables on local disks (NVMe, SSD, HDD, etc.).
 */
public class LocalStorageTier extends StorageTier
{
    private static final Logger logger = LoggerFactory.getLogger(LocalStorageTier.class);

    private final File dataDirectory;
    private final DiskSpaceMonitor spaceMonitor;

    public LocalStorageTier(String name, int priority, TierConfiguration config, File dataDirectory)
    {
        super(name, priority, config);
        this.dataDirectory = dataDirectory;
        this.spaceMonitor = new DiskSpaceMonitor(dataDirectory);

        if (!dataDirectory.exists())
        {
            logger.info("Creating data directory for tier {}: {}", name, dataDirectory);
            if (!dataDirectory.mkdirs())
            {
                logger.error("Failed to create data directory: {}", dataDirectory);
            }
        }
    }

    @Override
    public CompletableFuture<SSTableReader> writeSSTable(SSTableWriter writer, MetadataComponent metadata)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            long startTime = System.nanoTime();
            try
            {
                // Set the target directory for the writer
                File targetPath = getTargetPath(writer.descriptor);

                // Complete the write
                SSTableReader reader = writer.finish();

                // Update metrics
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                metrics.recordWrite(reader.onDiskLength(), elapsedMs);

                logger.debug("Wrote SSTable {} to tier {} in {}ms",
                            reader.descriptor, name, elapsedMs);

                return reader;
            }
            catch (Exception e)
            {
                logger.error("Failed to write SSTable to tier {}", name, e);
                throw new RuntimeException("Failed to write SSTable to tier " + name, e);
            }
        });
    }

    @Override
    public CompletableFuture<SSTableReader> readSSTable(Descriptor descriptor)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            long startTime = System.nanoTime();
            try
            {
                File sstableFile = getSSTableFile(descriptor);
                if (!sstableFile.exists())
                {
                    throw new IOException("SSTable not found: " + sstableFile);
                }

                // Open the SSTable reader
                SSTableReader reader = SSTableReader.open(descriptor);

                // Update metrics
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                metrics.recordRead(reader.onDiskLength(), elapsedMs);

                logger.debug("Read SSTable {} from tier {} in {}ms",
                            descriptor, name, elapsedMs);

                return reader;
            }
            catch (Exception e)
            {
                logger.error("Failed to read SSTable {} from tier {}", descriptor, name, e);
                throw new RuntimeException("Failed to read SSTable from tier " + name, e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteSSTable(Descriptor descriptor)
    {
        return CompletableFuture.runAsync(() ->
        {
            try
            {
                File sstableFile = getSSTableFile(descriptor);
                if (sstableFile.exists())
                {
                    Files.delete(sstableFile.toPath());
                    logger.debug("Deleted SSTable {} from tier {}", descriptor, name);
                }
            }
            catch (IOException e)
            {
                logger.error("Failed to delete SSTable {} from tier {}", descriptor, name, e);
                throw new RuntimeException("Failed to delete SSTable from tier " + name, e);
            }
        });
    }

    @Override
    public boolean isAvailable()
    {
        return dataDirectory.exists() && dataDirectory.canWrite();
    }

    @Override
    public long getAvailableSpace()
    {
        return spaceMonitor.getAvailableSpace();
    }

    @Override
    public double getExpectedLatency(OperationType op)
    {
        // Return average latency for this operation type
        switch (op)
        {
            case READ:
                return metrics.getAverageReadLatency();
            case WRITE:
                return metrics.getAverageWriteLatency();
            case DELETE:
                return 1.0; // Delete is typically fast
            default:
                return 0.0;
        }
    }

    private File getTargetPath(Descriptor descriptor)
    {
        return new File(dataDirectory, descriptor.baseFilename());
    }

    private File getSSTableFile(Descriptor descriptor)
    {
        return new File(dataDirectory, descriptor.baseFilename());
    }

    /**
     * Monitors disk space for a data directory.
     */
    private static class DiskSpaceMonitor
    {
        private final File directory;

        DiskSpaceMonitor(File directory)
        {
            this.directory = directory;
        }

        long getAvailableSpace()
        {
            try
            {
                FileStore store = Files.getFileStore(directory.toPath());
                return store.getUsableSpace();
            }
            catch (IOException e)
            {
                logger.warn("Failed to get available space for {}", directory, e);
                return 0;
            }
        }
    }
}
