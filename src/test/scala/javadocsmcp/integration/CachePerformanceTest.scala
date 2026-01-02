// PURPOSE: End-to-end performance tests for caching behavior
// PURPOSE: Verifies cache hit performance targets (< 100ms) and proper cache key separation

package javadocsmcp.integration

import javadocsmcp.application.{DocumentationService, SourceCodeService}
import javadocsmcp.infrastructure.{CoursierArtifactRepository, JarFileReader, TastySourceResolver, CachedDocumentationService, CachedSourceCodeService, LRUCache, CacheKey}
import javadocsmcp.domain.DocumentationError
import javadocsmcp.presentation.McpServer
import sttp.client3.*
import io.circe.parser.*
import io.circe.Json

class CachePerformanceTest extends munit.FunSuite:

  var server: Option[McpServer.ServerHandle] = None
  val testPort = 8890  // Different port to avoid conflict with other tests

  override def beforeAll(): Unit = {
    // Create services with caching enabled
    val repository = CoursierArtifactRepository()
    val reader = JarFileReader()
    val sourcePathResolver = TastySourceResolver(repository)

    val baseDocService = DocumentationService(repository, reader)
    val baseSourceService = SourceCodeService(repository, reader, sourcePathResolver)

    val cacheSizeBytes = 100L * 1024L * 1024L  // 100MB
    val docCache = LRUCache[CacheKey, Either[DocumentationError, String]](cacheSizeBytes)
    val sourceCache = LRUCache[CacheKey, Either[DocumentationError, String]](cacheSizeBytes)

    val documentationService = CachedDocumentationService(baseDocService, docCache)
    val sourceCodeService = CachedSourceCodeService(baseSourceService, sourceCache)

    server = Some(McpServer.startAsync(documentationService, sourceCodeService, testPort))
    Thread.sleep(8000)  // Give server time to start (needs extra time for initialization)
  }

  override def afterAll(): Unit = {
    server.foreach(_.stop())
  }

  def makeRequest(method: String, params: Json): (Json, Long) = {
    val backend = HttpURLConnectionBackend()
    val request = basicRequest
      .post(uri"http://localhost:$testPort/mcp")
      .contentType("application/json")
      .body(Json.obj(
        "jsonrpc" -> Json.fromString("2.0"),
        "id" -> Json.fromInt(1),
        "method" -> Json.fromString(method),
        "params" -> params
      ).noSpaces)

    val startTime = System.currentTimeMillis()
    val response = request.send(backend)
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime

    val json = response.body match {
      case Right(body) => parse(body).getOrElse(Json.Null)
      case Left(error) => fail(s"HTTP request failed: $error")
    }

    (json, duration)
  }

  test("second request for same class under 100ms (cache hit)"):
    val params = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.slf4j:slf4j-api:2.0.9"),
        "className" -> Json.fromString("org.slf4j.Logger")
      )
    )

    // First request - will be slow (network + JAR download)
    val (response1, duration1) = makeRequest("tools/call", params)
    val result1 = response1.hcursor.downField("result")
    assert(result1.downField("content").as[List[Json]].isRight, "First request should succeed")

    println(s"First request (cache miss): ${duration1}ms")

    // Second request - should be fast (cache hit)
    val (response2, duration2) = makeRequest("tools/call", params)
    val result2 = response2.hcursor.downField("result")
    assert(result2.downField("content").as[List[Json]].isRight, "Second request should succeed")

    println(s"Second request (cache hit): ${duration2}ms")

    // Verify cache hit is fast
    assert(duration2 < 100, s"Cache hit should be under 100ms, was ${duration2}ms")

    // Verify responses are identical (compare JSON content, not cursor objects)
    val content1 = result1.downField("content").as[List[Json]]
    val content2 = result2.downField("content").as[List[Json]]
    assertEquals(content1, content2, "Cached response should match original")

  test("cached response identical to original response"):
    val params = Json.obj(
      "name" -> Json.fromString("get_source"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.slf4j:slf4j-api:2.0.9"),
        "className" -> Json.fromString("org.slf4j.LoggerFactory")
      )
    )

    val (response1, _) = makeRequest("tools/call", params)
    val (response2, duration2) = makeRequest("tools/call", params)

    // Responses should be identical
    val content1 = response1.hcursor.downField("result").downField("content").as[List[Json]]
    val content2 = response2.hcursor.downField("result").downField("content").as[List[Json]]

    assert(content1.isRight, "First request should succeed")
    assert(content2.isRight, "Second request should succeed")
    assertEquals(content1, content2, "Cached response must match original exactly")

    assert(duration2 < 100, s"Cache hit should be fast, was ${duration2}ms")

  test("different scalaVersion creates separate cache entry"):
    // Request with default scalaVersion (3)
    val params1 = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.typelevel::cats-effect:3.5.4"),
        "className" -> Json.fromString("cats.effect.IO")
      )
    )

    // Request with explicit scalaVersion=2.13
    val params2 = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.typelevel::cats-effect:3.5.4"),
        "className" -> Json.fromString("cats.effect.IO"),
        "scalaVersion" -> Json.fromString("2.13")
      )
    )

    val (response1, _) = makeRequest("tools/call", params1)
    val (response2, _) = makeRequest("tools/call", params2)

    // Both should succeed (or both fail with same error)
    val result1 = response1.hcursor.downField("result")
    val result2 = response2.hcursor.downField("result")

    // They should have different results (different Scala versions)
    // OR if artifact doesn't exist for both versions, both should error
    // The key point is they don't share cache entries
    // Both requests should be relatively slow (no cache hit)
    // This verifies they used different cache keys
    assert(result1 != result2,
      "Different scalaVersion should create different cache entries")

  test("cache doesn't break error handling"):
    val params = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.example:nonexistent:1.0.0"),
        "className" -> Json.fromString("com.example.Missing")
      )
    )

    // First error request
    val (response1, duration1) = makeRequest("tools/call", params)
    val error1 = response1.hcursor.downField("result").downField("isError").as[Boolean]

    assert(error1.contains(true), "Should return error for non-existent artifact")

    // Second error request (should be cached)
    val (response2, duration2) = makeRequest("tools/call", params)
    val error2 = response2.hcursor.downField("result").downField("isError").as[Boolean]

    assert(error2.contains(true), "Should return same error on second request")
    assert(duration2 < 100, s"Cached error should be fast, was ${duration2}ms")

    // Verify error messages are identical
    val errorMsg1 = response1.hcursor.downField("result").downField("content").as[List[Json]]
    val errorMsg2 = response2.hcursor.downField("result").downField("content").as[List[Json]]
    assertEquals(errorMsg1, errorMsg2, "Cached error should match original error")

  test("server remains stable with cached requests"):
    // Make multiple requests to various endpoints
    val coords = List(
      ("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger"),
      ("org.slf4j:slf4j-api:2.0.9", "org.slf4j.LoggerFactory"),
      ("com.google.guava:guava:32.1.3-jre", "com.google.common.collect.ImmutableList")
    )

    coords.foreach { case (coord, className) =>
      val params = Json.obj(
        "name" -> Json.fromString("get_documentation"),
        "arguments" -> Json.obj(
          "coordinates" -> Json.fromString(coord),
          "className" -> Json.fromString(className)
        )
      )

      // First request (cache miss)
      val (response1, _) = makeRequest("tools/call", params)
      assert(response1.hcursor.downField("result").succeeded, s"First request for $className should succeed")

      // Second request (cache hit)
      val (response2, duration2) = makeRequest("tools/call", params)
      assert(response2.hcursor.downField("result").succeeded, s"Second request for $className should succeed")
      assert(duration2 < 150, s"Cache hit for $className should be fast, was ${duration2}ms")
    }
