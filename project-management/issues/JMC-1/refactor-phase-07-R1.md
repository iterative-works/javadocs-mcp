# Refactoring R1: Fix LRUCache Thread Safety

**Phase:** 7
**Created:** 2025-12-31
**Status:** Planned

## Decision Summary

Code review (composition skill) found thread safety issues in LRUCache:

1. **Race condition in `put()`**: `valueSize` is calculated outside the synchronized block, then used inside. Between calculating size and acquiring the lock, another thread could modify the cache.

2. **TrieMap/Queue synchronization mismatch**: Using lock-free TrieMap with synchronized mutable.Queue creates inconsistency. The `get()` method reads from cache outside synchronized, then updates accessOrder inside synchronized. This creates an ABA problem where:
   - Thread A does `cache.get("key")` → returns Some(value)
   - Thread B does `put("key2")` which evicts "key"
   - Thread A updates accessOrder with "key" - but "key" is no longer in cache
   - Cache and queue become inconsistent

**Solution:** Simplify to consistent full synchronization. This is appropriate for an in-memory cache where contention is low (MCP typically serves sequential requests).

## Current State

File: `src/main/scala/javadocsmcp/infrastructure/LRUCache.scala`

```scala
class LRUCache[K, V](maxSizeBytes: Long):
  private val cache = TrieMap.empty[K, V]           // Lock-free concurrent
  private val accessOrder = mutable.Queue.empty[K]   // NOT concurrent-safe
  // ...

  def get(key: K): Option[V] =
    cache.get(key) match                            // Outside synchronized!
      case Some(value) =>
        synchronized {
          _hits += 1
          updateAccessOrder(key)                    // Queue update inside
        }
        Some(value)
      // ...

  def put(key: K, value: V): Unit =
    val valueSize = estimateSize(value)             // Outside synchronized!
    synchronized {
      // Uses valueSize that could be stale
      // ...
    }
```

## Target State

File: `src/main/scala/javadocsmcp/infrastructure/LRUCache.scala`

```scala
class LRUCache[K, V](maxSizeBytes: Long):
  private val cache = mutable.Map.empty[K, V]       // Standard mutable map
  private val accessOrder = mutable.Queue.empty[K]
  // ...

  def get(key: K): Option[V] = synchronized {       // Everything inside synchronized
    cache.get(key) match
      case Some(value) =>
        _hits += 1
        updateAccessOrder(key)
        Some(value)
      case None =>
        _misses += 1
        None
  }

  def put(key: K, value: V): Unit = synchronized {  // Everything inside synchronized
    val valueSize = estimateSize(value)             // Size calc inside
    // ...
  }
```

## Constraints

- PRESERVE: All existing tests must continue passing
- PRESERVE: LRUCache public API (get, put, currentSize, clear, stats)
- PRESERVE: CacheStats case class unchanged
- PRESERVE: LRUCache.apply factory method unchanged
- DO NOT TOUCH: CachedDocumentationService
- DO NOT TOUCH: CachedSourceCodeService
- DO NOT TOUCH: Main.scala wiring
- DO NOT TOUCH: Test files (they verify behavior, should still pass)

## Tasks

- [ ] [impl] [Analysis] Review LRUCache.scala and confirm all race condition locations
- [ ] [impl] [Refactor] Replace TrieMap with mutable.Map
- [ ] [impl] [Refactor] Move `get()` body entirely inside synchronized block
- [ ] [impl] [Refactor] Move `put()` valueSize calculation inside synchronized block
- [ ] [impl] [Refactor] Ensure `currentSize`, `clear()`, `stats` are synchronized
- [ ] [impl] [Verify] Run LRUCacheTest - all 15 tests must pass
- [ ] [impl] [Verify] Run CachedDocumentationServiceTest - all tests must pass
- [ ] [impl] [Verify] Run CachedSourceCodeServiceTest - all tests must pass
- [ ] [impl] [Verify] Run CachePerformanceTest - all E2E tests must pass
- [ ] [impl] [Verify] Run full test suite - zero failures, zero warnings

## Verification

- [ ] All existing tests pass without modification
- [ ] No race conditions possible - all state access synchronized
- [ ] Cache performance still meets < 100ms target for cache hits
- [ ] Thread-safety tests pass (concurrent reads/writes tests)
- [ ] No regressions in functionality
