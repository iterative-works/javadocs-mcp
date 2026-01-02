// PURPOSE: Integration tests for CachedSourceCodeService with real services
// PURPOSE: Verifies cache behavior, hit/miss scenarios, and error caching for source code

package javadocsmcp.infrastructure

import javadocsmcp.application.SourceCodeService
import javadocsmcp.domain.{ArtifactCoordinates, DocumentationError}
import javadocsmcp.testkit.{InMemoryArtifactRepository, InMemoryJarContentReader}
import java.io.File

class CachedSourceCodeServiceTest extends munit.FunSuite:

  private def testJar: File = new File("/fake/path/test.jar")
  private def testSourceContent: String = "public class Logger { /* source code */ }"
  private def slf4jCoords: ArtifactCoordinates = ArtifactCoordinates("org.slf4j", "slf4j-api", "2.0.9")

  test("first request calls underlying source service"):
    val jar = testJar
    val source = testSourceContent
    val coords = slf4jCoords
    val repository = InMemoryArtifactRepository.withSourcesJar(coords, jar)
    val reader = InMemoryJarContentReader.withEntries(
      (jar, "org/slf4j/Logger.java") -> source
    )
    val underlyingService = SourceCodeService(repository, reader)
    val cache = LRUCache[CacheKey, Either[DocumentationError, String]](maxSizeBytes = 10000)
    val cachedService = CachedSourceCodeService(underlyingService, cache)

    val result = cachedService.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")

    assert(result.isRight, s"Should succeed but got: $result")
    val sourceCode = result.toOption.get
    assertEquals(sourceCode.sourceText, source)

    // Verify it was a cache miss
    val stats = cache.stats
    assertEquals(stats.misses, 1L, "Should have 1 miss (first request)")
    assertEquals(stats.hits, 0L, "Should have 0 hits")

  test("second request served from cache"):
    val jar = testJar
    val source = testSourceContent
    val coords = slf4jCoords
    val repository = InMemoryArtifactRepository.withSourcesJar(coords, jar)
    val reader = InMemoryJarContentReader.withEntries(
      (jar, "org/slf4j/Logger.java") -> source
    )
    val underlyingService = SourceCodeService(repository, reader)
    val cache = LRUCache[CacheKey, Either[DocumentationError, String]](maxSizeBytes = 10000)
    val cachedService = CachedSourceCodeService(underlyingService, cache)

    // First request
    val result1 = cachedService.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")
    assert(result1.isRight, "First request should succeed")

    // Second request (should be cached)
    val result2 = cachedService.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")
    assert(result2.isRight, "Second request should succeed")

    assertEquals(result1.toOption.get.sourceText, result2.toOption.get.sourceText,
      "Both results should have same content")

    // Verify cache hit
    val stats = cache.stats
    assertEquals(stats.misses, 1L, "Should have 1 miss (first request)")
    assertEquals(stats.hits, 1L, "Should have 1 hit (second request)")

  test("different className fetches again"):
    val jar = testJar
    val loggerSource = "public class Logger { /* logger */ }"
    val factorySource = "public class LoggerFactory { /* factory */ }"
    val coords = slf4jCoords
    val repository = InMemoryArtifactRepository.withSourcesJar(coords, jar)
    val reader = InMemoryJarContentReader.withEntries(
      (jar, "org/slf4j/Logger.java") -> loggerSource,
      (jar, "org/slf4j/LoggerFactory.java") -> factorySource
    )
    val underlyingService = SourceCodeService(repository, reader)
    val cache = LRUCache[CacheKey, Either[DocumentationError, String]](maxSizeBytes = 10000)
    val cachedService = CachedSourceCodeService(underlyingService, cache)

    // First class
    val result1 = cachedService.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")
    assert(result1.isRight, "Logger request should succeed")

    // Different class (should be cache miss)
    val result2 = cachedService.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.LoggerFactory")
    assert(result2.isRight, "LoggerFactory request should succeed")

    assertEquals(result1.toOption.get.sourceText, loggerSource, "Logger should have correct content")
    assertEquals(result2.toOption.get.sourceText, factorySource, "LoggerFactory should have correct content")

    // Verify 2 misses, 0 hits
    val stats = cache.stats
    assertEquals(stats.misses, 2L, "Should have 2 misses (different classes)")
    assertEquals(stats.hits, 0L, "Should have 0 hits")

  test("error results are cached for source lookups"):
    val jar = testJar
    val coords = slf4jCoords
    val repository = InMemoryArtifactRepository.withSourcesJar(coords, jar)
    val reader = InMemoryJarContentReader.withEntries()  // No entries, will cause ClassNotFound
    val underlyingService = SourceCodeService(repository, reader)
    val cache = LRUCache[CacheKey, Either[DocumentationError, String]](maxSizeBytes = 10000)
    val cachedService = CachedSourceCodeService(underlyingService, cache)

    // First request (should fail with ClassNotFound)
    val result1 = cachedService.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.MissingClass")
    assert(result1.isLeft, "First request should fail")

    // Second request (should return cached error)
    val result2 = cachedService.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.MissingClass")
    assert(result2.isLeft, "Second request should also fail")

    // Verify cache hit (error was cached)
    val stats = cache.stats
    assertEquals(stats.misses, 1L, "Should have 1 miss (first request)")
    assertEquals(stats.hits, 1L, "Should have 1 hit (cached error)")
