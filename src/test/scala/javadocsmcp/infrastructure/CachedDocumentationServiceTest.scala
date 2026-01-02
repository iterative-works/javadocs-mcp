// PURPOSE: Integration tests for CachedDocumentationService with real services
// PURPOSE: Verifies cache behavior, hit/miss scenarios, and error caching

package javadocsmcp.infrastructure

import javadocsmcp.application.DocumentationService
import javadocsmcp.domain.{ArtifactCoordinates, DocumentationError}
import javadocsmcp.testkit.{InMemoryArtifactRepository, InMemoryJarContentReader}
import java.io.File

class CachedDocumentationServiceTest extends munit.FunSuite:

  private def testJar: File = new File("/fake/path/test.jar")
  private def testHtmlContent: String = "<html><body>Test Logger documentation</body></html>"
  private def slf4jCoords: ArtifactCoordinates = ArtifactCoordinates("org.slf4j", "slf4j-api", "2.0.9")

  test("first request calls underlying service"):
    val jar = testJar
    val html = testHtmlContent
    val coords = slf4jCoords
    val repository = InMemoryArtifactRepository.withArtifact(coords, jar)
    val reader = InMemoryJarContentReader.withEntries(
      (jar, "org/slf4j/Logger.html") -> html
    )
    val underlyingService = DocumentationService(repository, reader)
    val cache = LRUCache[CacheKey, Either[DocumentationError, String]](maxSizeBytes = 10000)
    val cachedService = CachedDocumentationService(underlyingService, cache)

    val result = cachedService.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")

    assert(result.isRight, s"Should succeed but got: $result")
    val doc = result.toOption.get
    assertEquals(doc.htmlContent, html)

    // Verify it was a cache miss
    val stats = cache.stats
    assertEquals(stats.misses, 1L, "Should have 1 miss (first request)")
    assertEquals(stats.hits, 0L, "Should have 0 hits")

  test("second request served from cache (cache hit)"):
    val jar = testJar
    val html = testHtmlContent
    val coords = slf4jCoords
    val repository = InMemoryArtifactRepository.withArtifact(coords, jar)
    val reader = InMemoryJarContentReader.withEntries(
      (jar, "org/slf4j/Logger.html") -> html
    )
    val underlyingService = DocumentationService(repository, reader)
    val cache = LRUCache[CacheKey, Either[DocumentationError, String]](maxSizeBytes = 10000)
    val cachedService = CachedDocumentationService(underlyingService, cache)

    // First request
    val result1 = cachedService.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")
    assert(result1.isRight, "First request should succeed")

    // Second request (should be cached)
    val result2 = cachedService.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")
    assert(result2.isRight, "Second request should succeed")

    assertEquals(result1.toOption.get.htmlContent, result2.toOption.get.htmlContent,
      "Both results should have same content")

    // Verify cache hit
    val stats = cache.stats
    assertEquals(stats.misses, 1L, "Should have 1 miss (first request)")
    assertEquals(stats.hits, 1L, "Should have 1 hit (second request)")

  test("different className fetches again (cache miss)"):
    val jar = testJar
    val loggerHtml = "<html><body>Logger documentation</body></html>"
    val factoryHtml = "<html><body>LoggerFactory documentation</body></html>"
    val coords = slf4jCoords
    val repository = InMemoryArtifactRepository.withArtifact(coords, jar)
    val reader = InMemoryJarContentReader.withEntries(
      (jar, "org/slf4j/Logger.html") -> loggerHtml,
      (jar, "org/slf4j/LoggerFactory.html") -> factoryHtml
    )
    val underlyingService = DocumentationService(repository, reader)
    val cache = LRUCache[CacheKey, Either[DocumentationError, String]](maxSizeBytes = 10000)
    val cachedService = CachedDocumentationService(underlyingService, cache)

    // First class
    val result1 = cachedService.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")
    assert(result1.isRight, "Logger request should succeed")

    // Different class (should be cache miss)
    val result2 = cachedService.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.LoggerFactory")
    assert(result2.isRight, "LoggerFactory request should succeed")

    assertEquals(result1.toOption.get.htmlContent, loggerHtml, "Logger should have correct content")
    assertEquals(result2.toOption.get.htmlContent, factoryHtml, "LoggerFactory should have correct content")

    // Verify 2 misses, 0 hits
    val stats = cache.stats
    assertEquals(stats.misses, 2L, "Should have 2 misses (different classes)")
    assertEquals(stats.hits, 0L, "Should have 0 hits")

  test("different scalaVersion fetches again (different key)"):
    val jar = testJar
    val html = testHtmlContent
    val coords = slf4jCoords
    val repository = InMemoryArtifactRepository.withArtifact(coords, jar)
    val reader = InMemoryJarContentReader.withEntries(
      (jar, "org/slf4j/Logger.html") -> html
    )
    val underlyingService = DocumentationService(repository, reader)
    val cache = LRUCache[CacheKey, Either[DocumentationError, String]](maxSizeBytes = 10000)
    val cachedService = CachedDocumentationService(underlyingService, cache)

    // Request with default scalaVersion
    val result1 = cachedService.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")
    assert(result1.isRight, "First request should succeed")

    // Request with explicit scalaVersion (different cache key)
    val result2 = cachedService.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger", Some("2.13"))
    assert(result2.isRight, "Second request should succeed")

    // Verify 2 misses (different scalaVersion = different cache key)
    val stats = cache.stats
    assertEquals(stats.misses, 2L, "Should have 2 misses (different scalaVersion)")
    assertEquals(stats.hits, 0L, "Should have 0 hits")

  test("error results are cached (avoid repeated failed lookups)"):
    val jar = testJar
    val coords = slf4jCoords
    val repository = InMemoryArtifactRepository.withArtifact(coords, jar)
    val reader = InMemoryJarContentReader.withEntries()  // No entries, will cause ClassNotFound
    val underlyingService = DocumentationService(repository, reader)
    val cache = LRUCache[CacheKey, Either[DocumentationError, String]](maxSizeBytes = 10000)
    val cachedService = CachedDocumentationService(underlyingService, cache)

    // First request (should fail with ClassNotFound)
    val result1 = cachedService.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.MissingClass")
    assert(result1.isLeft, "First request should fail")

    // Second request (should return cached error)
    val result2 = cachedService.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.MissingClass")
    assert(result2.isLeft, "Second request should also fail")

    // Verify cache hit (error was cached)
    val stats = cache.stats
    assertEquals(stats.misses, 1L, "Should have 1 miss (first request)")
    assertEquals(stats.hits, 1L, "Should have 1 hit (cached error)")

  test("cached error matches original error"):
    val jar = testJar
    val coords = slf4jCoords
    val repository = InMemoryArtifactRepository.withArtifact(coords, jar)
    val reader = InMemoryJarContentReader.withEntries()
    val underlyingService = DocumentationService(repository, reader)
    val cache = LRUCache[CacheKey, Either[DocumentationError, String]](maxSizeBytes = 10000)
    val cachedService = CachedDocumentationService(underlyingService, cache)

    val result1 = cachedService.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.MissingClass")
    val result2 = cachedService.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.MissingClass")

    assertEquals(result1, result2, "Cached error should match original error")
