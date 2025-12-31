# Phase 7 Tasks: In-memory caching for repeated lookups

**Issue:** JMC-1
**Phase:** 7 of 7
**Story:** In-memory caching for repeated lookups
**Estimated Effort:** 4-6 hours

---

## Setup Tasks

- [ ] Review cache port traits pattern from existing architecture
- [ ] Identify cache configuration requirements (environment variables)

---

## Generic LRUCache Implementation (1-1.5h)

### Tests First

- [ ] Write test: "get returns None for missing key"
- [ ] Write test: "put and get returns cached value"
- [ ] Write test: "size calculation includes all entries"
- [ ] Write test: "empty cache has zero size"

### Implementation

- [ ] Implement LRUCache with TrieMap for thread-safe storage
- [ ] Implement get() method with access tracking
- [ ] Implement put() method with size management
- [ ] Implement size() method for current cache size

### LRU Eviction Tests

- [ ] Write test: "LRU eviction when size exceeded"
- [ ] Write test: "access updates LRU order on get"
- [ ] Write test: "access updates LRU order on put"
- [ ] Write test: "eviction happens when byte limit exceeded"

### LRU Eviction Implementation

- [ ] Implement access order tracking with Queue
- [ ] Implement LRU eviction logic (remove least recently used)
- [ ] Implement byte size limit enforcement
- [ ] Implement bulk eviction (10% when limit hit)

### Cache Statistics Tests

- [ ] Write test: "stats track hits and misses"
- [ ] Write test: "stats track eviction count"
- [ ] Write test: "hitRate calculation is correct"

### Cache Statistics Implementation

- [ ] Implement CacheStats case class
- [ ] Implement hit/miss tracking in get()
- [ ] Implement eviction count tracking
- [ ] Implement clear() method for testing

### Thread Safety Tests

- [ ] Write test: "concurrent reads don't corrupt cache"
- [ ] Write test: "concurrent writes don't lose updates"
- [ ] Write test: "concurrent read/write operations are safe"

### Thread Safety Verification

- [ ] Verify TrieMap handles concurrent reads
- [ ] Verify synchronized access order updates
- [ ] Run all LRUCache tests and verify passing

---

## CachedDocumentationService (1-1.5h)

### Port Definition

- [ ] Create DocumentationCache port trait (if not using generic cache)
- [ ] Define cache key structure (coordinates + className + scalaVersion)

### Decorator Tests

- [ ] Write test: "first request calls underlying service"
- [ ] Write test: "second request served from cache (cache hit)"
- [ ] Write test: "different className fetches again (cache miss)"
- [ ] Write test: "different scalaVersion fetches again (different key)"

### Decorator Implementation

- [ ] Implement CachedDocumentationService decorator
- [ ] Implement cache key generation from input parameters
- [ ] Implement cache lookup before delegation to underlying service
- [ ] Implement cache storage after successful service call

### Error Caching Tests

- [ ] Write test: "error results are cached (avoid repeated failed lookups)"
- [ ] Write test: "cached error matches original error"

### Error Caching Implementation

- [ ] Update cache to store Either[Error, String] results
- [ ] Cache both success and error responses
- [ ] Run all CachedDocumentationService tests and verify passing

---

## CachedSourceCodeService (0.5-1h)

### Source Cache Tests

- [ ] Write test: "first request calls underlying source service"
- [ ] Write test: "second request served from cache"
- [ ] Write test: "different className fetches again"
- [ ] Write test: "error results are cached for source lookups"

### Source Cache Implementation

- [ ] Implement CachedSourceCodeService following documentation pattern
- [ ] Implement cache key generation for source requests
- [ ] Implement cache lookup and storage logic
- [ ] Run all CachedSourceCodeService tests and verify passing

---

## Main.scala Integration (0.5h)

### Configuration

- [ ] Read CACHE_MAX_SIZE_MB environment variable with default 100MB
- [ ] Create LRUCache instances for documentation and source

### Service Wiring

- [ ] Create CachedDocumentationService wrapping existing documentationService
- [ ] Create CachedSourceCodeService wrapping existing sourceCodeService
- [ ] Wire cached services into MCP tools (replace non-cached versions)
- [ ] Verify Main.scala compiles without errors

---

## E2E Performance Tests (1h)

### Cache Hit Performance Tests

- [ ] Write test: "second request for same class under 100ms"
- [ ] Write test: "cache hit overhead under 1ms"
- [ ] Write test: "cached response identical to original response"

### Cache Key Uniqueness Tests

- [ ] Write test: "different scalaVersion creates separate cache entry"
- [ ] Write test: "same artifact different class shares JAR download benefit"

### Server Stability Tests

- [ ] Write test: "cache doesn't break error handling"
- [ ] Write test: "cached error returned correctly on second request"
- [ ] Write test: "valid request after error works correctly"

### E2E Test Execution

- [ ] Run all E2E performance tests and verify passing
- [ ] Verify < 100ms target met for cache hits
- [ ] Verify all responses correct (cached and non-cached)

---

## Load and Memory Tests (Optional - if time permits)

### Load Tests

- [ ] Write test: "1000 cached requests complete in under 1 second"
- [ ] Write test: "100 concurrent cache reads - no errors"

### Memory Tests

- [ ] Write test: "cache respects memory limit"
- [ ] Write test: "cache doesn't leak memory after evictions"

### Load Test Execution

- [ ] Run load tests and verify performance targets
- [ ] Run memory tests and verify limits respected

---

## Documentation and Polish (0.5h)

### Code Documentation

- [ ] Add PURPOSE comment to LRUCache.scala
- [ ] Add PURPOSE comment to CachedDocumentationService.scala
- [ ] Add PURPOSE comment to CachedSourceCodeService.scala
- [ ] Add inline comments explaining LRU algorithm

### README Updates

- [ ] Document CACHE_MAX_SIZE_MB configuration in README
- [ ] Explain cache performance characteristics
- [ ] Add cache statistics interpretation guide
- [ ] Add performance tuning recommendations

### Final Verification

- [ ] Run complete test suite (unit + integration + E2E)
- [ ] Verify zero warnings in test output
- [ ] Verify all acceptance criteria met
- [ ] Start server and manually verify caching with curl

---

## Acceptance Criteria Verification

- [ ] CacheKey case class with coordinates, className, scalaVersion exists
- [ ] LRUCache[K, V] with thread-safe operations exists
- [ ] CachedDocumentationService decorator exists and works
- [ ] CachedSourceCodeService decorator exists and works
- [ ] Cache statistics (hits, misses, evictions, size) implemented
- [ ] Main.scala wires cached services into tools
- [ ] CACHE_MAX_SIZE_MB environment variable configurable
- [ ] Second request for same class < 100ms (verified by test)
- [ ] Cache hit overhead < 1ms (verified by test)
- [ ] Different class from same artifact < 1s (Coursier benefit)
- [ ] 100 concurrent cache reads work without errors
- [ ] Cache returns identical result to non-cached service
- [ ] Different cache keys return different results
- [ ] Error results cached correctly
- [ ] Thread-safe operations verified
- [ ] Unit tests: 15+ tests for cache behavior passing
- [ ] Integration tests: 10+ tests with real services passing
- [ ] E2E tests: 5+ tests for cached HTTP responses passing
- [ ] All tests passing with zero warnings
- [ ] README section on caching configuration complete

---

**Total Tasks:** ~60 tasks
**Estimated Time:** 4-6 hours
**Task Size:** 15-30 minutes each (test + implementation pairs)

**Implementation Flow:**
1. Build generic LRUCache with comprehensive tests
2. Apply to DocumentationService with decorator pattern
3. Apply same pattern to SourceCodeService
4. Wire into Main.scala
5. Verify end-to-end with performance tests
6. Document and polish

**TDD Approach:**
- Every implementation task has corresponding test task(s) first
- Tests written to fail initially, then implementation makes them pass
- Run tests after each implementation to verify progress
