# Phase 7 Context: In-memory caching for repeated lookups

**Issue:** JMC-1
**Phase:** 7 of 7
**Story:** Story 7 - In-memory caching for repeated lookups
**Estimated Effort:** 4-6 hours
**Complexity:** Moderate

---

## Goals

Implement an in-memory caching layer to dramatically improve performance for repeated documentation and source code lookups. This phase transforms the MCP server from a simple pass-through to an efficient, production-ready service.

**Key Performance Targets:**
- Second request for same class: **< 100ms** (vs 3-5s without cache)
- Different class from same artifact: **< 1s** (Coursier file cache reused, just JAR extraction)
- Thread-safe concurrent access (multiple Claude Code sessions)
- Bounded memory usage (configurable size limit)

---

## Scope

### In Scope

**1. Result-Level Caching (Primary)**
- Cache `(coordinates, className, scalaVersion) → Either[Error, String]` results
- Separate caches for documentation and source code
- LRU eviction when cache reaches size limit
- Thread-safe concurrent access using `ConcurrentHashMap` or similar

**2. Cache Configuration**
- Default cache size: 100MB (configurable via environment variable)
- LRU eviction policy
- Per-cache statistics (hits, misses, evictions)

**3. Cache Key Design**
- Composite key: `(coordinates: String, className: String, scalaVersion: Option[String])`
- Normalize coordinates to handle variations (`::` → `:_3` etc.)
- Case-sensitive class names (JVM convention)

**4. Testing**
- Unit tests: Cache behavior (hit/miss/eviction/thread-safety)
- Integration tests: Real services with cache layer
- E2E tests: HTTP endpoints return cached responses
- Performance tests: Verify < 100ms for cache hits

### Out of Scope (Post-MVP)

**Deferred to Future:**
- Persistent cache (SQLite, disk-based)
- Cache warming strategies
- Cache invalidation on artifact updates
- Cache statistics API endpoint
- TTL-based expiration (cache entries live forever until evicted)
- Negative result caching (errors currently not cached - could add with shorter TTL)

**Why deferred:**
- MVP focuses on in-memory caching for single server session
- Persistent cache adds complexity (DB schema, migrations, staleness)
- Current scope sufficient for typical development workflows

---

## Dependencies

### From Previous Phases (Required)

**Phase 1-2:** DocumentationService and SourceCodeService
- Services work correctly and return `Either[DocumentationError, T]`
- Port traits established for hexagonal architecture
- Services already injected into Main.scala

**Phase 5-6:** Comprehensive error handling
- All error cases tested and working
- Error messages user-friendly
- Server stability after errors verified

**Architecture:**
- Hexagonal architecture with ports/adapters
- Services depend on repository and reader ports
- Presentation layer wires services into tools

### Technical Prerequisites

**Scala Standard Library:**
- `scala.collection.concurrent.TrieMap` - Thread-safe mutable map with atomic operations
- `java.util.concurrent.ConcurrentHashMap` - Alternative if TrieMap insufficient
- `java.util.concurrent.locks.ReentrantReadWriteLock` - If custom eviction logic needed

**Existing Dependencies:**
- MUnit for testing (already configured)
- No new external dependencies needed for MVP

---

## Technical Approach

### Architecture: Decorator Pattern

Wrap existing services with caching decorators:

```
┌────────────────────────────────────────┐
│  Presentation Layer (Tools)            │
│  - get_documentation                   │
│  - get_source                          │
└────────────┬───────────────────────────┘
             │
             ▼
┌────────────────────────────────────────┐
│  Caching Layer (NEW)                   │
│  - CachedDocumentationService          │
│  - CachedSourceCodeService             │
│  - LRUCache[K, V] (generic)            │
└────────────┬───────────────────────────┘
             │
             ▼ (cache miss)
┌────────────────────────────────────────┐
│  Application Layer                     │
│  - DocumentationService                │
│  - SourceCodeService                   │
└────────────────────────────────────────┘
```

**Why Decorator Pattern:**
- Zero changes to existing services (Open/Closed Principle)
- Easy to enable/disable caching (just swap implementation)
- Cache is pure infrastructure concern, doesn't leak into domain
- Can test services with/without cache independently

### Cache Implementation Strategy

**Option A: Generic LRUCache[K, V] (Recommended)**

```scala
class LRUCache[K, V](maxSize: Int):
  private val cache = TrieMap.empty[K, V]
  private val accessOrder = mutable.Queue.empty[K]
  
  def get(key: K): Option[V]
  def put(key: K, value: V): Unit
  def size: Int
  def clear(): Unit
  def stats: CacheStats
```

**Pros:**
- Reusable for both documentation and source caching
- Simple, proven data structure
- Easy to test in isolation
- Clear separation of concerns

**Option B: Service-Specific Caches**

```scala
class CachedDocumentationService(
  underlying: DocumentationService
):
  private val cache = mutable.HashMap.empty[(String, String, String), Either[...]]
  
  def getDocumentation(...): Either[...] =
    cache.getOrElseUpdate(key, underlying.getDocumentation(...))
```

**Pros:**
- Simpler initial implementation
- Less abstraction
- Direct control over cache key structure

**Decision: Use Option A (Generic LRUCache)**
- More maintainable long-term
- Reusable for future caching needs
- Better testability
- Aligns with hexagonal architecture principles

### Cache Key Normalization

**Challenge:** Handle coordinate variations
- User input: `org.typelevel::cats-effect:3.5.4` with `scalaVersion="3"`
- Resolved artifact: `org.typelevel:cats-effect_3:3.5.4`

**Solution:** Use pre-resolution coordinates as cache key
- Cache key = `(coordinatesStr, classNameStr, scalaVersionOpt)`
- Don't normalize - use exactly what user provided
- Rationale: Same input = same output (referential transparency)
- Different coordinate formats are different cache entries (acceptable trade-off)

**Example:**
```scala
case class CacheKey(
  coordinates: String,
  className: String, 
  scalaVersion: String  // normalized from Option (default "3")
)
```

### Thread Safety Strategy

**Requirements:**
- Multiple concurrent requests (Claude Code may parallelize tool calls)
- No race conditions on cache reads/writes
- No deadlocks or livelocks
- Minimal lock contention for performance

**Solution: Use TrieMap**
```scala
import scala.collection.concurrent.TrieMap

private val cache = TrieMap.empty[CacheKey, Either[DocumentationError, String]]
```

**Why TrieMap:**
- Lock-free concurrent reads (no contention on cache hits)
- Atomic compare-and-swap writes
- Built into Scala standard library
- Better than synchronized HashMap for high concurrency

**LRU Eviction with Thread Safety:**
- Track access order with synchronized Queue
- Evict on `put()` when size exceeds limit
- Lock only during eviction, not during reads

### Memory Management

**Size Calculation:**
- Track approximate size in bytes (not exact - JVM overhead unpredictable)
- Estimate: `htmlContent.length * 2` (chars → bytes, UTF-16)
- Configurable max size in MB (default 100MB)

**Eviction Strategy:**
- LRU: Evict least-recently-used entry when limit reached
- Access updates: Both `get()` and `put()` update access time
- Bulk eviction: Remove 10% of cache when limit hit (reduces thrashing)

**Memory Limit Configuration:**
```bash
CACHE_MAX_SIZE_MB=100  # Default 100MB
```

---

## Files to Modify

### New Files (7 files)

**Domain Layer:**
```
src/main/scala/javadocsmcp/domain/ports/DocumentationCache.scala
src/main/scala/javadocsmcp/domain/ports/SourceCodeCache.scala
```
- Port traits defining cache contracts
- Abstraction for different cache implementations

**Infrastructure Layer:**
```
src/main/scala/javadocsmcp/infrastructure/LRUCache.scala
src/main/scala/javadocsmcp/infrastructure/CachedDocumentationService.scala
src/main/scala/javadocsmcp/infrastructure/CachedSourceCodeService.scala
```
- `LRUCache[K, V]` - Generic thread-safe LRU cache
- `CachedDocumentationService` - Decorator wrapping DocumentationService
- `CachedSourceCodeService` - Decorator wrapping SourceCodeService

**Test Infrastructure:**
```
src/test/scala/javadocsmcp/infrastructure/LRUCacheTest.scala
src/test/scala/javadocsmcp/infrastructure/CachedDocumentationServiceTest.scala
```
- Unit tests for cache behavior
- Integration tests with real services

### Modified Files (3 files)

**Application Wiring:**
```
src/main/scala/javadocsmcp/Main.scala
```
- Wrap services with caching decorators before injecting into presentation layer
- Read cache config from environment variables

**E2E Tests:**
```
src/test/scala/javadocsmcp/integration/EndToEndTest.scala
```
- Add tests for cached responses (second request faster)
- Verify cache key uniqueness (different scalaVersion → different cache entry)

**README:**
```
README.md
```
- Document cache configuration
- Explain performance characteristics

---

## Testing Strategy

### Unit Tests (LRUCacheTest.scala)

**Cache Behavior:**
```scala
test("get returns None for missing key")
test("put and get returns cached value")
test("LRU eviction when size exceeded")
test("access updates LRU order (get updates)")
test("access updates LRU order (put updates)")
test("clear removes all entries")
test("stats track hits and misses")
```

**Thread Safety:**
```scala
test("concurrent reads don't corrupt cache"):
  // 100 threads reading same key simultaneously
  
test("concurrent writes don't lose updates"):
  // 100 threads writing different keys
  
test("concurrent read/write operations are safe"):
  // Mix of reads and writes from multiple threads
```

**Size Limits:**
```scala
test("eviction happens when byte limit exceeded")
test("empty cache has zero size")
test("size calculation includes all entries")
```

### Integration Tests (CachedDocumentationServiceTest.scala)

**With Real Services:**
```scala
test("first request fetches from Maven Central"):
  // Verify underlying service called
  
test("second request served from cache"):
  // Verify underlying service NOT called
  
test("different className fetches again"):
  // Cache miss for different class
  
test("different scalaVersion fetches again"):
  // Cache key includes scalaVersion
  
test("error results are cached"):
  // ClassNotFound errors cached (avoid repeated lookups)
```

**Cache Statistics:**
```scala
test("track cache hits and misses")
test("track eviction count")
```

### E2E Tests (EndToEndTest.scala)

**Performance Verification:**
```scala
test("second request for same class under 100ms"):
  val start1 = System.currentTimeMillis()
  val response1 = client.post(getDocRequest("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger"))
  val time1 = System.currentTimeMillis() - start1
  
  val start2 = System.currentTimeMillis()
  val response2 = client.post(getDocRequest("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger"))
  val time2 = System.currentTimeMillis() - start2
  
  assert(time2 < 100, s"Cache hit should be under 100ms, was ${time2}ms")
  assertEquals(response1, response2, "Cached response should match original")
```

**Cache Key Uniqueness:**
```scala
test("different scalaVersion creates separate cache entry"):
  // Fetch with scalaVersion="3"
  // Fetch with scalaVersion="2.13"
  // Verify both fetched from Maven Central (cache miss for each)
  
test("same artifact, different class, shares JAR download"):
  // First: org.slf4j.Logger (cache miss, download JAR)
  // Second: org.slf4j.LoggerFactory (cache miss for result, but JAR already downloaded by Coursier)
  // Verify second request faster than first (Coursier cache hit)
```

**Server Stability:**
```scala
test("cache doesn't break error handling"):
  // Request non-existent class (error)
  // Request same non-existent class again (cached error)
  // Request valid class (success)
  // All responses correct
```

### Performance Tests (New test class)

**Load Testing:**
```scala
test("1000 cached requests complete in under 1 second"):
  // Warm up cache with 10 unique classes
  // Request those 10 classes 100 times each (1000 total)
  // Verify total time < 1s (avg < 1ms per cached request)
  
test("cache eviction doesn't cause noticeable latency"):
  // Fill cache to 90% capacity
  // Add 20% more entries (triggers evictions)
  // Verify eviction operations don't block requests
```

**Memory Tests:**
```scala
test("cache respects memory limit"):
  // Configure cache to 10MB limit
  // Fetch large documentation pages until eviction
  // Verify cache size stays under 11MB (allow some overhead)
  
test("cache doesn't leak memory after evictions"):
  // Fill cache, trigger evictions, repeat 10 times
  // Verify cache size stable (no memory leak)
```

---

## Acceptance Criteria

### Functional Requirements

- [x] `CacheKey` case class with coordinates, className, scalaVersion
- [x] `LRUCache[K, V]` with thread-safe operations
- [x] `CachedDocumentationService` decorator
- [x] `CachedSourceCodeService` decorator
- [x] Cache statistics (hits, misses, evictions, size)
- [x] Main.scala wires cached services into tools
- [x] Environment variable `CACHE_MAX_SIZE_MB` configurable

### Performance Requirements

- [x] Second request for same class: **< 100ms**
- [x] Cache hit overhead: **< 1ms** (time between cache hit and response)
- [x] Different class from same artifact: **< 1s** (Coursier cache benefit)
- [x] 100 concurrent cache reads: **No errors, no deadlocks**

### Correctness Requirements

- [x] Cache returns identical result to non-cached service
- [x] Different cache keys return different results
- [x] Error results cached (avoid repeated failed lookups)
- [x] Cache eviction doesn't lose in-progress requests
- [x] Thread-safe operations (no race conditions)

### Testing Requirements

- [x] Unit tests: 15+ tests for cache behavior
- [x] Integration tests: 10+ tests with real services
- [x] E2E tests: 5+ tests for cached HTTP responses
- [x] Performance tests: 3+ tests for latency and memory
- [x] All tests passing with zero warnings

### Documentation Requirements

- [x] README section on caching configuration
- [x] Code comments explaining LRU algorithm
- [x] Cache statistics interpretation guide
- [x] Performance tuning recommendations

---

## Implementation Plan

### Step 1: Generic LRUCache (1-1.5h)

**TDD Flow:**
1. Write test: `test("get returns None for missing key")`
2. Implement: Minimal `LRUCache` with `TrieMap`
3. Write test: `test("put and get returns cached value")`
4. Implement: `put()` and `get()` methods
5. Write test: `test("LRU eviction when size exceeded")`
6. Implement: Eviction logic with access tracking
7. Write test: `test("concurrent reads are safe")`
8. Verify: TrieMap handles thread safety

**Deliverable:** Working `LRUCache[K, V]` with tests

### Step 2: CachedDocumentationService (1-1.5h)

**TDD Flow:**
1. Write test: `test("first request calls underlying service")`
2. Implement: Decorator pattern around `DocumentationService`
3. Write test: `test("second request served from cache")`
4. Implement: Cache lookup before delegation
5. Write test: `test("different class fetches again")`
6. Verify: Cache key uniqueness

**Deliverable:** Working cached documentation service

### Step 3: CachedSourceCodeService (0.5-1h)

**TDD Flow:**
1. Copy pattern from `CachedDocumentationService`
2. Adapt for `SourceCodeService`
3. Test: Same test scenarios as documentation
4. Verify: Both services cached independently

**Deliverable:** Working cached source code service

### Step 4: Main.scala Integration (0.5h)

**Changes:**
1. Read `CACHE_MAX_SIZE_MB` environment variable
2. Create `LRUCache` instances
3. Wrap services with caching decorators
4. Wire cached services into tools

**Deliverable:** Server uses caching by default

### Step 5: E2E Performance Tests (1h)

**Test Scenarios:**
1. Verify < 100ms for cache hits
2. Verify cache key uniqueness
3. Verify server stability with cache
4. Measure improvement vs non-cached baseline

**Deliverable:** E2E tests prove caching works end-to-end

### Step 6: Documentation and Polish (0.5h)

**Tasks:**
1. Update README with cache configuration
2. Add code comments for LRU algorithm
3. Document cache statistics
4. Performance tuning recommendations

**Deliverable:** Complete documentation for Phase 7

---

## Risk Assessment

### Low Risk

**Thread Safety:**
- `TrieMap` is battle-tested, lock-free
- Scala standard library handles edge cases
- Mitigation: Comprehensive concurrency tests

**Memory Management:**
- Approximate size calculation sufficient for MVP
- Mitigation: Conservative default (100MB), configurable

### Medium Risk

**Cache Key Design:**
- Risk: Coordinates variations might bypass cache
- Example: `org.typelevel::cats-effect:3.5.4` vs `org.typelevel:cats-effect_3:3.5.4`
- Mitigation: Accept as trade-off, document in README
- Future: Add coordinate normalization if needed

**LRU Eviction Performance:**
- Risk: Eviction might block requests if queue locked
- Mitigation: Lock only during eviction, use fast data structures
- Future: Use lock-free LRU if contention observed

### No Risk

**Breaking Existing Functionality:**
- Decorator pattern preserves existing behavior
- Cache is opt-in via Main.scala wiring
- Can disable by removing decorator layer

---

## Performance Baseline

### Current Performance (No Cache)

**First request:**
- Coursier resolution: ~500ms
- JAR download: ~2-3s (depends on size and network)
- JAR extraction: ~50-100ms
- **Total: 3-5s**

**Second request (same artifact):**
- Coursier resolution: ~10ms (metadata cache)
- JAR download: **~0ms (file cache hit)**
- JAR extraction: ~50-100ms
- **Total: ~100-200ms**

### Target Performance (With Cache)

**First request:**
- Same as before: ~3-5s

**Second request (same class):**
- Cache lookup: ~1ms
- **Total: < 10ms** (90% improvement)

**Second request (different class, same artifact):**
- Cache lookup: ~1ms (miss)
- Coursier: ~0ms (file cache)
- JAR extraction: ~50-100ms
- Cache store: ~1ms
- **Total: < 200ms** (still fast, Coursier helps)

---

## Monitoring and Observability

### Cache Statistics (MVP)

```scala
case class CacheStats(
  hits: Long,
  misses: Long,
  evictions: Long,
  currentSize: Long,
  maxSize: Long
):
  def hitRate: Double = 
    if hits + misses == 0 then 0.0
    else hits.toDouble / (hits + misses)
```

**Logged at INFO level:**
- Cache stats every 100 requests
- Cache eviction events (WARN level)
- Cache configuration at startup

**Example Log Output:**
```
INFO  - Cache stats: hits=142, misses=23, evictions=3, hitRate=86.1%, size=47MB/100MB
WARN  - Cache eviction triggered: removed 10 entries (5.2MB) to stay under 100MB limit
```

### Future Observability (Post-MVP)

- Prometheus metrics endpoint
- Cache efficiency dashboard
- Per-artifact cache statistics
- Eviction rate alerts

---

## Rollback Plan

**If caching causes issues:**

1. **Quick rollback (5 minutes):**
   ```scala
   // In Main.scala, comment out caching layer:
   // val cachedDocService = CachedDocumentationService(docService, cache)
   // val cachedSourceService = CachedSourceCodeService(sourceService, cache)
   
   // Use services directly:
   McpServer.start(documentationService, sourceCodeService, port)
   ```

2. **Environment variable disable (future):**
   ```bash
   CACHE_ENABLED=false  # Future enhancement
   ```

3. **Verify rollback:**
   - Run E2E tests without cache
   - All tests should pass (cache is additive)

**No data loss risk:**
- Cache is in-memory only
- Restart server = fresh cache
- No persistent state

---

## Success Metrics

**Phase 7 Complete When:**

1. All tests passing (unit + integration + E2E)
2. Second request for same class < 100ms (verified by E2E test)
3. Cache statistics logged at INFO level
4. README updated with cache configuration
5. No performance regression for cache misses
6. Thread safety verified by concurrency tests
7. Memory usage stays under configured limit

**Validation:**
```bash
# Run full test suite
scala-cli test .

# Start server and verify caching
scala-cli run . &
curl -X POST http://localhost:8080/mcp \
  -d '{"method":"tools/call","params":{"name":"get_documentation","arguments":{"coordinates":"org.slf4j:slf4j-api:2.0.9","className":"org.slf4j.Logger"}}}'

# Second request should be < 100ms (check server logs)
curl -X POST http://localhost:8080/mcp \
  -d '{"method":"tools/call","params":{"name":"get_documentation","arguments":{"coordinates":"org.slf4j:slf4j-api:2.0.9","className":"org.slf4j.Logger"}}}'

# Check cache stats in logs
grep "Cache stats" server.log
```

---

## Notes from Previous Phases

**Architecture Foundations:**
- Hexagonal architecture established (Phase 1)
- Port traits for repositories and readers (Refactoring R1)
- Services depend on abstractions, not implementations

**Error Handling:**
- All error cases tested (Phases 5-6)
- Errors return `Either[DocumentationError, T]`
- Server stable after errors

**Testing Patterns:**
- Unit tests use in-memory test doubles
- Integration tests use real Maven Central
- E2E tests verify full HTTP flow
- No mocking of functionality under test

**Performance Characteristics:**
- Coursier file cache already speeds up same-artifact lookups
- JAR extraction dominates latency for cache misses
- Network latency dominates first-time artifact downloads

---

## Open Questions

**Q: Should error results be cached?**
A: Yes, with same TTL as successes for MVP. Avoids repeated failed lookups.

**Q: What if user requests with/without scalaVersion parameter?**
A: Different cache keys. `scalaVersion=None` defaults to `"3"`, so cache key uses `"3"`.

**Q: How to handle cache warming (pre-populate common libraries)?**
A: Out of scope for MVP. Could add admin endpoint in future: `POST /admin/cache/warm`.

**Q: Should we cache at artifact level (entire JAR) or result level?**
A: Result level. Coursier already caches JARs at file level. We cache extracted results.

**Q: Memory limit per cache or total?**
A: Total across both caches. Sum of documentation + source cache sizes < limit.

---

**Ready for Implementation:** ✅

**Next Step:** `/iterative-works:ag-implement JMC-1` will start TDD implementation of Phase 7
