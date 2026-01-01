// PURPOSE: Unit tests for LRUCache behavior including eviction and thread safety
// PURPOSE: Verifies cache operations, LRU eviction policy, and concurrent access safety

package javadocsmcp.infrastructure

import munit.FunSuite

class LRUCacheTest extends FunSuite:

  test("get returns None for missing key"):
    val cache = LRUCache[String, String](maxSizeBytes = 1000)
    assertEquals(cache.get("missing"), None)

  test("put and get returns cached value"):
    val cache = LRUCache[String, String](maxSizeBytes = 1000)
    cache.put("key1", "value1")
    assertEquals(cache.get("key1"), Some("value1"))

  test("size calculation includes all entries"):
    val cache = LRUCache[String, String](maxSizeBytes = 1000)
    cache.put("key1", "value1")
    cache.put("key2", "value2")

    // Each value is approximately: value1 = 6 chars * 2 bytes = 12 bytes
    // value2 = 6 chars * 2 bytes = 12 bytes
    // Total ≈ 24 bytes
    assert(cache.currentSize > 0, "Cache size should be greater than 0")

  test("empty cache has zero size"):
    val cache = LRUCache[String, String](maxSizeBytes = 1000)
    assertEquals(cache.currentSize, 0L)

  test("LRU eviction when size exceeded"):
    // Each "X" char is 2 bytes, so "XXXX" = 8 bytes
    val cache = LRUCache[String, String](maxSizeBytes = 20)
    cache.put("key1", "XXXX")  // 8 bytes
    cache.put("key2", "XXXX")  // 8 bytes, total 16 bytes
    cache.put("key3", "XXXX")  // 8 bytes, total 24 bytes - should evict key1

    // key1 should be evicted (least recently used)
    assertEquals(cache.get("key1"), None, "key1 should be evicted")
    assertEquals(cache.get("key2"), Some("XXXX"), "key2 should still exist")
    assertEquals(cache.get("key3"), Some("XXXX"), "key3 should still exist")

  test("access updates LRU order on get"):
    val cache = LRUCache[String, String](maxSizeBytes = 20)
    cache.put("key1", "XXXX")  // 8 bytes
    cache.put("key2", "XXXX")  // 8 bytes, total 16 bytes

    // Access key1, making it most recently used
    cache.get("key1")

    cache.put("key3", "XXXX")  // 8 bytes, should evict key2 (not key1)

    assertEquals(cache.get("key1"), Some("XXXX"), "key1 should still exist (accessed recently)")
    assertEquals(cache.get("key2"), None, "key2 should be evicted (least recently used)")
    assertEquals(cache.get("key3"), Some("XXXX"), "key3 should still exist")

  test("access updates LRU order on put"):
    val cache = LRUCache[String, String](maxSizeBytes = 20)
    cache.put("key1", "XXXX")  // 8 bytes
    cache.put("key2", "XXXX")  // 8 bytes, total 16 bytes

    // Update key1, making it most recently used
    cache.put("key1", "YYYY")

    cache.put("key3", "XXXX")  // 8 bytes, should evict key2 (not key1)

    assertEquals(cache.get("key1"), Some("YYYY"), "key1 should still exist with new value")
    assertEquals(cache.get("key2"), None, "key2 should be evicted (least recently used)")
    assertEquals(cache.get("key3"), Some("XXXX"), "key3 should still exist")

  test("eviction happens when byte limit exceeded"):
    val cache = LRUCache[String, String](maxSizeBytes = 50)
    cache.put("key1", "XXXXXXXXXX")  // 20 bytes
    cache.put("key2", "XXXXXXXXXX")  // 20 bytes, total 40 bytes

    assert(cache.get("key1").isDefined, "key1 should exist")
    assert(cache.get("key2").isDefined, "key2 should exist")

    cache.put("key3", "XXXXXXXXXX")  // 20 bytes, total would be 60, should evict key1

    assertEquals(cache.get("key1"), None, "key1 should be evicted to stay under limit")
    assertEquals(cache.get("key2"), Some("XXXXXXXXXX"), "key2 should still exist")
    assertEquals(cache.get("key3"), Some("XXXXXXXXXX"), "key3 should still exist")
    assert(cache.currentSize <= 50, s"Cache size ${cache.currentSize} should be <= 50")

  test("stats track hits and misses"):
    val cache = LRUCache[String, String](maxSizeBytes = 1000)
    cache.put("key1", "value1")

    // First get is a hit
    cache.get("key1")
    val stats1 = cache.stats
    assertEquals(stats1.hits, 1L, "Should have 1 hit")
    assertEquals(stats1.misses, 0L, "Should have 0 misses")

    // Get for missing key is a miss
    cache.get("missing")
    val stats2 = cache.stats
    assertEquals(stats2.hits, 1L, "Should still have 1 hit")
    assertEquals(stats2.misses, 1L, "Should have 1 miss")

    // Another hit
    cache.get("key1")
    val stats3 = cache.stats
    assertEquals(stats3.hits, 2L, "Should have 2 hits")
    assertEquals(stats3.misses, 1L, "Should still have 1 miss")

  test("stats track eviction count"):
    val cache = LRUCache[String, String](maxSizeBytes = 20)
    cache.put("key1", "XXXX")  // 8 bytes
    cache.put("key2", "XXXX")  // 8 bytes, total 16 bytes

    val stats1 = cache.stats
    assertEquals(stats1.evictions, 0L, "No evictions yet")

    cache.put("key3", "XXXX")  // 8 bytes, should evict key1

    val stats2 = cache.stats
    assertEquals(stats2.evictions, 1L, "Should have 1 eviction")

    cache.put("key4", "XXXX")  // Should evict key2

    val stats3 = cache.stats
    assertEquals(stats3.evictions, 2L, "Should have 2 evictions")

  test("hitRate calculation is correct"):
    val cache = LRUCache[String, String](maxSizeBytes = 1000)

    // Empty cache has 0.0 hit rate
    assertEquals(cache.stats.hitRate, 0.0, "Empty cache should have 0.0 hit rate")

    cache.put("key1", "value1")

    // 2 hits, 1 miss = 2/3 = 0.666...
    cache.get("key1")  // hit
    cache.get("key1")  // hit
    cache.get("missing")  // miss

    val hitRate = cache.stats.hitRate
    assert(hitRate > 0.66 && hitRate < 0.67, s"Hit rate should be ~0.666, was $hitRate")

  test("clear removes all entries"):
    val cache = LRUCache[String, String](maxSizeBytes = 1000)
    cache.put("key1", "value1")
    cache.put("key2", "value2")

    assert(cache.get("key1").isDefined, "key1 should exist before clear")
    assert(cache.currentSize > 0, "Cache should have size before clear")

    cache.clear()

    assertEquals(cache.get("key1"), None, "key1 should not exist after clear")
    assertEquals(cache.get("key2"), None, "key2 should not exist after clear")
    assertEquals(cache.currentSize, 0L, "Cache size should be 0 after clear")

  test("concurrent reads don't corrupt cache"):
    val cache = LRUCache[String, String](maxSizeBytes = 10000)
    cache.put("shared", "value")

    // 100 threads reading simultaneously
    val threads = (1 to 100).map { i =>
      new Thread(() => {
        val value = cache.get("shared")
        assert(value.contains("value"), s"Thread $i should read correct value")
      })
    }

    threads.foreach(_.start())
    threads.foreach(_.join())

    // Cache should still be consistent
    assertEquals(cache.get("shared"), Some("value"), "Cache should still have correct value")

  test("concurrent writes don't lose updates"):
    val cache = LRUCache[Int, String](maxSizeBytes = 100000)

    // 100 threads writing different keys
    val threads = (1 to 100).map { i =>
      new Thread(() => {
        cache.put(i, s"value$i")
      })
    }

    threads.foreach(_.start())
    threads.foreach(_.join())

    // All writes should be present (cache is large enough)
    val missingKeys = (1 to 100).filter(i => cache.get(i).isEmpty)
    assert(missingKeys.isEmpty, s"All keys should be present, but missing: $missingKeys")

  test("concurrent read/write operations are safe"):
    val cache = LRUCache[String, String](maxSizeBytes = 10000)
    cache.put("key1", "initial")

    // Mix of reads and writes from multiple threads
    val readThreads = (1 to 50).map { _ =>
      new Thread(() => {
        (1 to 100).foreach { _ =>
          cache.get("key1")
        }
      })
    }

    val writeThreads = (1 to 50).map { i =>
      new Thread(() => {
        val threadId = i  // Capture i before lambda
        (1 to 100).foreach { j =>
          cache.put(s"key$threadId", s"value$j")
        }
      })
    }

    val allThreads = readThreads ++ writeThreads
    allThreads.foreach(_.start())
    allThreads.foreach(_.join())

    // No exceptions should occur, and cache should be consistent
    assert(cache.get("key1").isDefined, "Original key should still exist or have been evicted gracefully")
