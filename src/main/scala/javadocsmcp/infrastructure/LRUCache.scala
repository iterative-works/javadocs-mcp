// PURPOSE: Generic thread-safe LRU cache with size-based eviction policy
// PURPOSE: Provides in-memory caching with configurable memory limits and access tracking

package javadocsmcp.infrastructure

import scala.collection.mutable

class LRUCache[K, V](maxSizeBytes: Long):
  private val cache = mutable.Map.empty[K, V]
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

    // If key already exists, remove its old size
    if cache.contains(key) then
      cache.get(key).foreach { oldValue =>
        _currentSize -= estimateSize(oldValue)
      }

    // Add new value
    cache.put(key, value)
    _currentSize += valueSize
    updateAccessOrder(key)

    // Evict if necessary
    while _currentSize > maxSizeBytes && accessOrder.nonEmpty do
      val evictKey = accessOrder.dequeue()
      cache.remove(evictKey).foreach { evictedValue =>
        _currentSize -= estimateSize(evictedValue)
        _evictions += 1
      }
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
    accessOrder.filterInPlace(_ != key)
    accessOrder.enqueue(key)

  private def estimateSize(value: V): Long =
    value match
      case s: String => s.length * 2L  // UTF-16 encoding, 2 bytes per char
      case _ => 64L  // Default estimate for other types

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
