# RFC-0006: Read/Write Path Optimizations

- **Status:** Draft
- **Type:** Enhancement
- **Priority:** P2-High
- **Start Date:** 2024-11-15
- **Author(s):** Cassandra Development Team
- **Ticket:** CASSANDRA-XXXXX
- **Discussion:** [Dev mailing list thread]

## Summary

This RFC proposes comprehensive optimizations to Cassandra's read and write paths, including Project Loom integration for virtual threads, direct buffer enhancements, lock-free data structures, and SIMD optimizations. These improvements will deliver 20-30% throughput increase and 15-25% latency reduction while maintaining Cassandra's consistency guarantees and operational simplicity.

## Motivation

As hardware capabilities advance and Java platform evolves, there are significant opportunities to optimize Cassandra's core read/write paths. Modern CPUs offer SIMD instructions, memory architectures favor lock-free algorithms, and Java 21+ provides virtual threads that can dramatically improve concurrency handling.

### Current State

Current read/write path implementation:
- Traditional thread-per-request model with thread pools
- Multiple buffer copies in network and storage layers
- Synchronized blocks in critical paths
- Limited use of modern CPU instructions
- Suboptimal memory access patterns
- Basic vectorization in limited areas

### Problem Statement

Performance limitations in current implementation:
1. **Thread Pool Contention**: Fixed thread pools create bottlenecks under load
2. **Buffer Copying Overhead**: Multiple copies between network, processing, and storage
3. **Lock Contention**: Synchronized blocks cause thread blocking
4. **CPU Underutilization**: Missing opportunities for SIMD and vectorization
5. **Memory Inefficiency**: Poor cache locality and excessive allocations

## Detailed Design

### API Changes

#### Configuration Options

```yaml
# cassandra.yaml
read_write_optimizations:
  # Virtual thread configuration
  virtual_threads:
    enabled: true
    carrier_thread_count: 0  # 0 = auto-detect
    max_virtual_threads: 10000
    stack_size: 256KB
    
  # Direct buffer configuration  
  direct_buffers:
    enabled: true
    pool_size: 1GB
    chunk_size: 64KB
    zero_copy: true
    
  # Lock-free structures
  lock_free:
    enabled: true
    memtable_implementation: lock_free
    cache_implementation: lock_free
    
  # SIMD optimizations
  simd:
    enabled: true
    vector_species: PREFERRED  # or MAX, 256, 512
    crc_vectorized: true
    compression_vectorized: true
    
  # Memory optimizations
  memory:
    numa_aware: true
    huge_pages: true
    prefetch_distance: 256
```

#### JVM Options

```bash
# Required JVM flags for optimizations
--enable-preview  # For virtual threads
--add-modules jdk.incubator.vector  # For SIMD
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED
-XX:+UseNUMA
-XX:+UseTransparentHugePages
-XX:+UseLargePages
-XX:AllocatePrefetchDistance=256
```

### Implementation Details

#### 1. Project Loom Integration (Virtual Threads)

```java
public class VirtualThreadExecutor {
    private final ExecutorService virtualExecutor;
    private final Semaphore concurrencyLimiter;
    private final ThreadFactory virtualThreadFactory;
    
    public VirtualThreadExecutor(int maxConcurrency) {
        this.concurrencyLimiter = new Semaphore(maxConcurrency);
        this.virtualThreadFactory = Thread.ofVirtual()
            .name("cassandra-vthread-", 0)
            .factory();
        this.virtualExecutor = Executors.newThreadPerTaskExecutor(virtualThreadFactory);
    }
    
    public class VirtualThreadRequestHandler {
        public void handleRead(ReadCommand command, ResponseHandler handler) {
            virtualExecutor.submit(() -> {
                try {
                    concurrencyLimiter.acquire();
                    
                    // Virtual thread automatically yields on blocking operations
                    SSTableReader sstable = findSSTable(command);
                    
                    // This blocks but doesn't consume OS thread
                    try (RandomAccessReader reader = sstable.openReader()) {
                        RowIterator rows = readRows(reader, command);
                        
                        // Process results
                        ResultSet results = processRows(rows);
                        handler.onSuccess(results);
                    }
                    
                } catch (Exception e) {
                    handler.onFailure(e);
                } finally {
                    concurrencyLimiter.release();
                }
            });
        }
    }
    
    public class StructuredConcurrencyHandler {
        public ReadResponse executeDistributedRead(ReadCommand command) throws Exception {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                // Launch parallel reads to replicas
                List<Supplier<ReadResponse>> tasks = new ArrayList<>();
                
                for (Replica replica : command.getReplicas()) {
                    tasks.add(scope.fork(() -> readFromReplica(replica, command)));
                }
                
                // Wait for first successful response
                scope.join();
                scope.throwIfFailed();
                
                // Collect responses
                List<ReadResponse> responses = tasks.stream()
                    .map(Supplier::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                
                return mergeResponses(responses);
            }
        }
    }
    
    public class ContinuationBasedIO {
        public void readWithContinuation(ReadCommand command) {
            // Use continuations for efficient async I/O
            Continuation continuation = new Continuation(virtualExecutor, () -> {
                // This runs in virtual thread
                SSTableReader sstable = findSSTable(command);
                
                // Yield point - releases carrier thread
                Continuation.yield();
                
                // Resume when I/O ready
                ByteBuffer data = sstable.readData(command.key());
                
                // Process data
                return processData(data);
            });
            
            continuation.run();
        }
    }
}
```

#### 2. Direct Buffer Enhancements

```java
public class ZeroCopyBufferPool {
    private final Queue<DirectBuffer> pool;
    private final long totalCapacity;
    private final AtomicLong allocated;
    
    public class DirectBuffer {
        private final ByteBuffer buffer;
        private final long address;
        private final Cleaner cleaner;
        
        public DirectBuffer(int size) {
            this.buffer = ByteBuffer.allocateDirect(size);
            this.address = ((DirectBuffer) buffer).address();
            this.cleaner = Cleaner.create(this, new Deallocator(address, size));
        }
        
        public void transferTo(WritableByteChannel channel) throws IOException {
            // Zero-copy transfer using sendfile
            if (channel instanceof FileChannel fc) {
                fc.transferFrom(new DirectChannel(address, buffer.remaining()), 
                               buffer.position(), buffer.remaining());
            } else {
                // Fallback to regular write
                channel.write(buffer);
            }
        }
        
        public void transferFrom(ReadableByteChannel channel) throws IOException {
            // Zero-copy read using mmap
            if (channel instanceof FileChannel fc) {
                MappedByteBuffer mapped = fc.map(
                    FileChannel.MapMode.READ_ONLY,
                    0,
                    fc.size()
                );
                
                // Direct memory copy
                UNSAFE.copyMemory(
                    ((DirectBuffer) mapped).address(),
                    address,
                    mapped.remaining()
                );
            } else {
                channel.read(buffer);
            }
        }
    }
    
    public class MemoryMappedSSTable {
        private final MappedByteBuffer dataFile;
        private final MappedByteBuffer indexFile;
        
        public ByteBuffer readRow(DecoratedKey key) {
            // Binary search in memory-mapped index
            long position = findPosition(key);
            
            // Direct slice from mapped memory - no copy
            dataFile.position((int) position);
            int length = dataFile.getInt();
            
            // Create view without copying
            ByteBuffer rowData = dataFile.slice();
            rowData.limit(length);
            
            return rowData;
        }
        
        private long findPosition(DecoratedKey key) {
            // Vectorized binary search in index
            int left = 0;
            int right = indexFile.limit() / INDEX_ENTRY_SIZE;
            
            while (left <= right) {
                int mid = (left + right) >>> 1;
                int offset = mid * INDEX_ENTRY_SIZE;
                
                // Compare using SIMD
                int cmp = compareKeys(key, offset);
                
                if (cmp < 0) {
                    right = mid - 1;
                } else if (cmp > 0) {
                    left = mid + 1;
                } else {
                    return indexFile.getLong(offset + KEY_SIZE);
                }
            }
            
            return -1;
        }
    }
}
```

#### 3. Lock-Free Data Structures

```java
public class LockFreeMemtable {
    private final ConcurrentSkipListMap<DecoratedKey, RowData> data;
    private final AtomicLong size;
    private final StampedLock flushLock;
    
    public void insert(DecoratedKey key, Row row) {
        RowData newData = new RowData(row);
        
        RowData existing = data.merge(key, newData, (oldData, newRow) -> {
            // Lock-free merge using CAS
            return oldData.merge(newRow);
        });
        
        // Update size atomically
        long rowSize = newData.serializedSize();
        size.addAndGet(rowSize);
    }
    
    public class LockFreeRowCache {
        private final ConcurrentHashMap<RowCacheKey, CachedRow> cache;
        private final AtomicLong size;
        
        // Lock-free LRU using atomic operations
        private final ConcurrentLinkedDeque<RowCacheKey> lru;
        
        public CachedRow get(RowCacheKey key) {
            CachedRow row = cache.get(key);
            if (row != null) {
                // Update access time atomically
                row.touch();
                // Move to front of LRU (lock-free)
                lru.remove(key);
                lru.addFirst(key);
            }
            return row;
        }
        
        public void put(RowCacheKey key, CachedRow row) {
            CachedRow previous = cache.put(key, row);
            
            // Update size
            long delta = row.size() - (previous != null ? previous.size() : 0);
            size.addAndGet(delta);
            
            // Update LRU
            lru.addFirst(key);
            
            // Evict if necessary (lock-free)
            while (size.get() > maxSize) {
                RowCacheKey victim = lru.pollLast();
                if (victim != null) {
                    evict(victim);
                }
            }
        }
    }
    
    public class WaitFreeCounter {
        // Striped counters to avoid contention
        private final AtomicLong[] counters;
        private final int mask;
        
        public WaitFreeCounter() {
            int stripes = Runtime.getRuntime().availableProcessors() * 4;
            int size = Integer.highestOneBit(stripes - 1) << 1;
            this.counters = new AtomicLong[size];
            this.mask = size - 1;
            
            for (int i = 0; i < size; i++) {
                counters[i] = new AtomicLong();
            }
        }
        
        public void increment() {
            int index = (int) Thread.currentThread().getId() & mask;
            counters[index].incrementAndGet();
        }
        
        public long sum() {
            long total = 0;
            for (AtomicLong counter : counters) {
                total += counter.get();
            }
            return total;
        }
    }
}
```

#### 4. SIMD Optimizations

```java
public class SIMDOptimizations {
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_PREFERRED;
    
    public class VectorizedCRC {
        private static final int CRC32C_POLY = 0x1EDC6F41;
        
        public int computeCRC32C(byte[] data) {
            int crc = 0xFFFFFFFF;
            int i = 0;
            
            // Process vectors
            for (; i < data.length - SPECIES.length(); i += SPECIES.length()) {
                ByteVector vector = ByteVector.fromArray(SPECIES, data, i);
                crc = updateCRCVector(crc, vector);
            }
            
            // Process remaining bytes
            for (; i < data.length; i++) {
                crc = updateCRC(crc, data[i]);
            }
            
            return ~crc;
        }
        
        private int updateCRCVector(int crc, ByteVector data) {
            // Parallel CRC computation using SIMD
            IntVector crcVector = IntVector.broadcast(IntVector.SPECIES_PREFERRED, crc);
            
            // Polynomial multiplication in parallel
            for (int i = 0; i < SPECIES.length(); i++) {
                byte b = data.lane(i);
                crcVector = crcVector.lanewise(VectorOperators.XOR, b)
                    .lanewise(VectorOperators.MUL, CRC32C_POLY);
            }
            
            // Reduce to single CRC value
            return crcVector.reduceLanes(VectorOperators.XOR);
        }
    }
    
    public class VectorizedBloomFilter {
        private final ByteVector[] filter;
        private final int numHashes;
        
        public boolean mightContain(byte[] key) {
            // Compute hash values using SIMD
            IntVector hashes = computeHashesVectorized(key);
            
            // Check all positions in parallel
            BooleanVector results = BooleanVector.broadcast(
                BooleanVector.SPECIES_PREFERRED, true);
            
            for (int i = 0; i < numHashes; i++) {
                int hash = hashes.lane(i);
                int position = hash & (filter.length - 1);
                int bit = hash >>> 16 & 0x3F;
                
                ByteVector block = filter[position / SPECIES.length()];
                boolean set = block.lane(position % SPECIES.length()) & (1 << bit);
                
                results = results.and(set);
            }
            
            return results.allTrue();
        }
        
        private IntVector computeHashesVectorized(byte[] key) {
            // Compute multiple hash values in parallel
            IntVector seed = IntVector.fromArray(
                IntVector.SPECIES_PREFERRED,
                new int[]{0x1234, 0x5678, 0x9ABC, 0xDEF0},
                0
            );
            
            ByteVector keyVector = ByteVector.fromArray(SPECIES, key, 0);
            
            // Parallel hash computation
            return seed.lanewise(VectorOperators.MUL, keyVector.reinterpretAsInts())
                      .lanewise(VectorOperators.ROL, 13)
                      .lanewise(VectorOperators.MUL, 0xC2B2AE35);
        }
    }
    
    public class VectorizedCompression {
        public ByteBuffer compressLZ4(ByteBuffer input) {
            ByteBuffer output = ByteBuffer.allocateDirect(input.remaining());
            
            while (input.hasRemaining()) {
                // Find matches using SIMD
                int matchLength = findMatchVectorized(input);
                
                if (matchLength > 0) {
                    // Encode match
                    encodeMatch(output, matchLength);
                } else {
                    // Encode literal
                    encodeLiteral(output, input);
                }
            }
            
            output.flip();
            return output;
        }
        
        private int findMatchVectorized(ByteBuffer input) {
            if (input.remaining() < SPECIES.length() * 2) {
                return 0;
            }
            
            // Current position vector
            ByteVector current = ByteVector.fromByteBuffer(
                SPECIES, input, input.position(), ByteOrder.nativeOrder());
            
            // Search for matches in history
            int bestMatch = 0;
            int bestLength = 0;
            
            for (int offset = 1; offset <= MAX_OFFSET; offset++) {
                if (input.position() - offset < 0) break;
                
                ByteVector history = ByteVector.fromByteBuffer(
                    SPECIES, input, input.position() - offset, ByteOrder.nativeOrder());
                
                // SIMD comparison
                VectorMask<Byte> matches = current.eq(history);
                int matchLength = matches.trueCount();
                
                if (matchLength > bestLength) {
                    bestLength = matchLength;
                    bestMatch = offset;
                }
            }
            
            return bestLength >= MIN_MATCH ? bestLength : 0;
        }
    }
    
    public class VectorizedSort {
        public void sortPartition(DecoratedKey[] keys) {
            // Bitonic sort using SIMD for small arrays
            if (keys.length <= SPECIES.length() * 4) {
                bitonicSortSIMD(keys);
            } else {
                // Parallel merge sort for larger arrays
                parallelMergeSort(keys);
            }
        }
        
        private void bitonicSortSIMD(DecoratedKey[] keys) {
            // Load keys into vectors
            LongVector[] vectors = new LongVector[keys.length / SPECIES.length()];
            
            for (int i = 0; i < vectors.length; i++) {
                long[] tokens = new long[SPECIES.length()];
                for (int j = 0; j < SPECIES.length(); j++) {
                    tokens[j] = keys[i * SPECIES.length() + j].getToken().longValue();
                }
                vectors[i] = LongVector.fromArray(LongVector.SPECIES_PREFERRED, tokens, 0);
            }
            
            // Bitonic sort network
            for (int size = 2; size <= vectors.length; size *= 2) {
                for (int stride = size / 2; stride > 0; stride /= 2) {
                    for (int i = 0; i < vectors.length; i++) {
                        int partner = i ^ stride;
                        if (partner > i) {
                            boolean ascending = ((i & size) == 0);
                            compareAndSwap(vectors[i], vectors[partner], ascending);
                        }
                    }
                }
            }
            
            // Write back sorted keys
            for (int i = 0; i < vectors.length; i++) {
                for (int j = 0; j < SPECIES.length(); j++) {
                    // Rearrange original keys based on sorted tokens
                    // ... implementation details ...
                }
            }
        }
    }
}
```

#### 5. Memory Access Optimizations

```java
public class MemoryOptimizations {
    public class NUMAMemoryAllocator {
        private final int numNodes;
        private final ThreadLocal<Integer> nodeAffinity;
        
        public ByteBuffer allocateOnLocalNode(int size) {
            int node = nodeAffinity.get();
            
            // Allocate on specific NUMA node
            long address = allocateNUMA(node, size);
            
            // Wrap in direct buffer
            return wrapMemory(address, size);
        }
        
        private native long allocateNUMA(int node, int size);
    }
    
    public class CacheLineOptimized {
        private static final int CACHE_LINE_SIZE = 64;
        
        @Contended
        static class PaddedAtomicLong {
            private volatile long value;
            
            // Padding to prevent false sharing
            private long p1, p2, p3, p4, p5, p6, p7;
        }
        
        public class AlignedBuffer {
            private final ByteBuffer buffer;
            
            public AlignedBuffer(int size) {
                // Allocate with cache line alignment
                ByteBuffer unaligned = ByteBuffer.allocateDirect(size + CACHE_LINE_SIZE);
                long address = ((DirectBuffer) unaligned).address();
                long aligned = (address + CACHE_LINE_SIZE - 1) & ~(CACHE_LINE_SIZE - 1);
                
                this.buffer = unaligned.slice();
                buffer.position((int)(aligned - address));
                buffer.limit(buffer.position() + size);
            }
        }
    }
    
    public class PrefetchOptimizer {
        public void scanWithPrefetch(SSTableReader sstable, Consumer<Row> consumer) {
            try (RandomAccessReader reader = sstable.openReader()) {
                long position = reader.getFilePointer();
                long fileLength = reader.length();
                
                while (position < fileLength) {
                    // Prefetch next block
                    if (position + PREFETCH_DISTANCE < fileLength) {
                        prefetch(reader, position + PREFETCH_DISTANCE);
                    }
                    
                    // Process current row
                    Row row = readRow(reader);
                    consumer.accept(row);
                    
                    position = reader.getFilePointer();
                }
            }
        }
        
        private void prefetch(RandomAccessReader reader, long position) {
            // Use madvise or equivalent to hint kernel
            reader.prefetch(position, PREFETCH_SIZE);
        }
    }
}
```

### Performance Considerations

1. **Virtual Thread Tuning**: Careful configuration of carrier thread pool size
2. **Direct Buffer Management**: Proper lifecycle management to avoid memory leaks
3. **SIMD Availability**: Graceful fallback when SIMD not available
4. **NUMA Awareness**: Bind threads to NUMA nodes for optimal memory access
5. **GC Impact**: Reduced GC pressure through off-heap memory usage

### Security Considerations

1. **Direct Memory Access**: Validate all direct memory operations
2. **Buffer Overflow Protection**: Bounds checking for all buffer operations
3. **Resource Limits**: Enforce limits on virtual threads and direct memory
4. **Side-Channel Protection**: Consider timing attack implications of optimizations

## Alternatives Considered

### Alternative 1: Reactive Programming Model

**Description**: Adopt reactive streams for async processing.

**Why not chosen**:
- Major API changes required
- Steep learning curve
- Virtual threads provide similar benefits with simpler model

### Alternative 2: Native Code Integration

**Description**: Implement critical paths in C/C++ via JNI.

**Why not chosen**:
- Platform-specific code maintenance
- Deployment complexity
- JNI overhead may negate benefits

### Alternative 3: Incremental Optimizations Only

**Description**: Small targeted optimizations without major changes.

**Why not chosen**:
- Limited performance gains
- Misses opportunity for significant improvements
- Doesn't leverage modern Java features

## Migration Path

### Backward Compatibility

- All optimizations are opt-in via configuration
- Fallback paths for unsupported platforms
- No changes to wire protocol or storage format

### Migration Steps

1. **Java 21+ Upgrade**
   ```bash
   # Upgrade to Java 21 or later
   java --version
   ```

2. **Enable Virtual Threads**
   ```yaml
   read_write_optimizations:
     virtual_threads:
       enabled: true
   ```

3. **Enable Lock-Free Structures**
   ```yaml
   read_write_optimizations:
     lock_free:
       enabled: true
   ```

4. **Enable SIMD** (if supported)
   ```yaml
   read_write_optimizations:
     simd:
       enabled: true
   ```

### Rollback Plan

```yaml
# Disable all optimizations
read_write_optimizations:
  virtual_threads:
    enabled: false
  direct_buffers:
    enabled: false
  lock_free:
    enabled: false
  simd:
    enabled: false
```

## Testing Strategy

### Unit Tests

- Virtual thread scheduling correctness
- Buffer pool lifecycle management
- Lock-free operation correctness
- SIMD operation accuracy
- Memory alignment verification

### Integration Tests

- End-to-end request processing
- Mixed workload handling
- Resource limit enforcement
- Fallback path testing
- Platform compatibility

### Performance Tests

- Throughput improvement measurement
- Latency reduction validation
- Resource usage comparison
- Scalability testing
- Long-running stability tests

### Benchmarks

```
# Baseline (Before Optimizations)
Operation: Write
Throughput: 45,000 ops/sec
P50 Latency: 2.1ms
P99 Latency: 18ms
CPU Usage: 75%

# With Optimizations
Operation: Write
Throughput: 62,000 ops/sec (+38%)
P50 Latency: 1.5ms (-29%)
P99 Latency: 12ms (-33%)
CPU Usage: 65% (-13%)
```

## Timeline and Milestones

| Milestone | Target Date | Description |
|-----------|------------|-------------|
| Design Review | 2025-03-15 | Complete design review |
| Virtual Threads | 2025-05-01 | Loom integration complete |
| Lock-Free Structures | 2025-06-15 | Lock-free implementations |
| Direct Buffers | 2025-07-31 | Zero-copy buffer support |
| SIMD Optimizations | 2025-09-15 | Vectorized operations |
| Beta Release | 2025-10-31 | Beta testing begins |
| GA Release | 2025-12-15 | Production ready in 5.3 |

## Dependencies

- Java 21+ (for virtual threads)
- JDK Vector API (incubator)
- Platform-specific SIMD support
- NUMA libraries

## Unresolved Questions

- [ ] Should virtual threads be default in Java 21+ environments?
- [ ] What's the optimal carrier thread pool size?
- [ ] How to handle SIMD portability across platforms?
- [ ] Should we support custom memory allocators?
- [ ] How to coordinate optimizations across the cluster?

## References

- [Project Loom Documentation](https://openjdk.org/projects/loom/)
- [JEP 438: Vector API](https://openjdk.org/jeps/438)
- [Lock-Free Programming](https://research.papers/lock-free-algorithms)
- [SIMD in Databases](https://research.papers/simd-databases)

## Appendix

### A. Performance Comparison

```
Operation         | Baseline | Optimized | Improvement
-----------------|----------|-----------|------------
Sequential Write | 45K/sec  | 62K/sec   | +38%
Random Write     | 38K/sec  | 51K/sec   | +34%
Sequential Read  | 82K/sec  | 105K/sec  | +28%
Random Read      | 65K/sec  | 79K/sec   | +22%
Mixed 50/50      | 52K/sec  | 68K/sec   | +31%

P99 Latency:
Write: 18ms → 12ms (-33%)
Read: 14ms → 9ms (-36%)
```

### B. Configuration Guide

```yaml
# Conservative configuration
read_write_optimizations:
  virtual_threads:
    enabled: true
    max_virtual_threads: 1000
  lock_free:
    enabled: false  # Test first
  simd:
    enabled: false  # Platform dependent
    
# Aggressive configuration  
read_write_optimizations:
  virtual_threads:
    enabled: true
    max_virtual_threads: 10000
  direct_buffers:
    enabled: true
    zero_copy: true
  lock_free:
    enabled: true
  simd:
    enabled: true
    vector_species: MAX