// PURPOSE: Generic thread-safe LRU cache with size-based eviction policy
// PURPOSE: Provides in-memory caching with configurable memory limits and access tracking

package javadocsmcp.infrastructure

import scala.collection.mutable

class LRUCache[K, V](maxSizeBytes: Long):
  private val cache = mutable.Map.empty[K, V]
  // OPTIMIZATION NOTE: accessOrder uses Queue with O(n) filterInPlace for access updates.
  // For high-throughput scenarios, consider replacing with a doubly-linked list
  // (Map[K, (V, Node[K])]) for O(1) removal/insertion. Deferred per YAGNI - current
  // implementation is sufficient for MCP's sequential request pattern.
  private val accessOrder = mutable.Queue.empty[K]
  private var _currentSize: Long = 0L
  private var _hits: Long = 0L
  private var _misses: Long = 0L
  private var _evictions: Long = 0L

  def get(key: K): Option[V] = synchronized {
    cache.get(key) match
      case Some(value) =>
        _hits += 1
        updateAccessOrder(key)
        Some(value)
      case None =>
        _misses += 1
        None
  }

  def put(key: K, value: V): Unit = synchronized {
    val valueSize = estimateSize(value)

    // Use Map.put's return value to get old value in one operation
    // (more efficient than contains + get + put)
    val oldValueOpt = cache.put(key, value)
    oldValueOpt.foreach { oldValue =>
      _currentSize -= estimateSize(oldValue)
    }
    _currentSize += valueSize

    // Evict BEFORE updating access order to avoid evicting the key we just added.
    // If we updated access order first, the new key would be at the end of the queue
    // (most recent), but if the cache is full, the eviction loop could eventually
    // reach and evict it.
    while _currentSize > maxSizeBytes && accessOrder.nonEmpty do
      val evictKey = accessOrder.dequeue()
      cache.remove(evictKey).foreach { evictedValue =>
        _currentSize -= estimateSize(evictedValue)
        _evictions += 1
      }

    // Update access order AFTER eviction to ensure the newly added key
    // is marked as most recently used and won't be immediately evicted
    updateAccessOrder(key)
  }

  def currentSize: Long = synchronized { _currentSize }

  def clear(): Unit = synchronized {
    cache.clear()
    accessOrder.clear()
    _currentSize = 0L
  }

  def stats: CacheStats = synchronized {
    CacheStats(
      hits = _hits,
      misses = _misses,
      evictions = _evictions,
      currentSize = _currentSize,
      maxSize = maxSizeBytes
    )
  }

  private def updateAccessOrder(key: K): Unit =
    // Remove key from queue if it exists, then add to end (most recent)
    // Note: O(n) scan - see OPTIMIZATION NOTE above for potential improvement
    accessOrder.filterInPlace(_ != key)
    accessOrder.enqueue(key)

  // Approximate size estimation for memory budgeting.
  // - String: length * 2 approximates JVM UTF-16 internal representation
  // - Other: 64 bytes as arbitrary default (object header + references)
  //
  // DESIGN NOTE: This is intentionally rough. For precise control, consider
  // making sizeOf a constructor parameter: LRUCache[K, V](maxSizeBytes, sizeOf: V => Long)
  // Deferred per YAGNI - current estimation works for our String-based cache usage.
  private def estimateSize(value: V): Long =
    value match
      case s: String => s.length * 2L
      case _ => 64L

object LRUCache:
  def apply[K, V](maxSizeBytes: Long): LRUCache[K, V] =
    new LRUCache[K, V](maxSizeBytes)

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
