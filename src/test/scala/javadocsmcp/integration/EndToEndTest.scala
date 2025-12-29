// PURPOSE: End-to-end test for MCP HTTP server with real network calls
// PURPOSE: Verifies complete flow from HTTP request to MCP tool response

package javadocsmcp.integration

import javadocsmcp.application.{DocumentationService, SourceCodeService}
import javadocsmcp.infrastructure.{CoursierArtifactRepository, JarFileReader, TastySourceResolver}
import javadocsmcp.presentation.McpServer
import sttp.client3.*
import io.circe.parser.*
import io.circe.Json

class EndToEndTest extends munit.FunSuite:

  var server: Option[McpServer.ServerHandle] = None
  val testPort = 8888

  override def beforeAll(): Unit = {
    val repository = CoursierArtifactRepository()
    val reader = JarFileReader()
    val sourcePathResolver = TastySourceResolver(repository)
    val documentationService = DocumentationService(repository, reader)
    val sourceCodeService = SourceCodeService(repository, reader, sourcePathResolver)

    server = Some(McpServer.startAsync(documentationService, sourceCodeService, testPort))
    // Give server time to start - needs extra time with both services
    Thread.sleep(5000)
  }

  override def afterAll(): Unit = {
    server.foreach(_.stop())
  }

  def makeRequest(method: String, params: Json): Json = {
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

    val response = request.send(backend)
    response.body match {
      case Right(body) => parse(body).getOrElse(Json.Null)
      case Left(error) => fail(s"HTTP request failed: $error")
    }
  }

  test("server should respond to tools/list request") {
    val response = makeRequest("tools/list", Json.obj())

    val tools = response.hcursor.downField("result").downField("tools").as[List[Json]]
    assert(tools.isRight, s"Expected tools array in response: $response")

    val toolNames = tools.getOrElse(List.empty).flatMap(_.hcursor.downField("name").as[String].toOption)
    assert(toolNames.contains("get_documentation"), s"Expected get_documentation tool, got: $toolNames")
    assert(toolNames.contains("get_source"), s"Expected get_source tool, got: $toolNames")
  }

  test("should fetch documentation for org.slf4j.Logger") {
    val params = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.slf4j:slf4j-api:2.0.9"),
        "className" -> Json.fromString("org.slf4j.Logger")
      )
    )

    val startTime = System.currentTimeMillis()
    val response = makeRequest("tools/call", params)
    val endTime = System.currentTimeMillis()

    val result = response.hcursor.downField("result")
    val content = result.downField("content").as[List[Json]]

    assert(content.isRight, s"Expected content array in response: $response")

    val textContent = content.getOrElse(List.empty)
      .flatMap(_.hcursor.downField("text").as[String].toOption)
      .headOption

    assert(textContent.isDefined, "Expected text content in response")
    val html = textContent.get
    assert(html.contains("Logger"), s"Expected 'Logger' in HTML content")
    assert(html.contains("void info(String msg)") || html.contains("info"),
      s"Expected 'info' method in HTML content")

    val responseTime = endTime - startTime
    assert(responseTime < 5000, s"Response time $responseTime ms exceeded 5 seconds")
  }

  test("should return error for non-existent artifact") {
    val params = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("com.fake:nonexistent:1.0.0"),
        "className" -> Json.fromString("com.fake.FakeClass")
      )
    )

    val response = makeRequest("tools/call", params)

    // Check for error in result or error field
    val hasError = response.hcursor.downField("result").downField("isError").as[Boolean].getOrElse(false) ||
                   response.hcursor.downField("error").succeeded

    assert(hasError, s"Expected error response for non-existent artifact: $response")

    val errorMessage = response.hcursor.downField("result").downField("content")
      .downArray.downField("text").as[String]
      .orElse(response.hcursor.downField("error").downField("message").as[String])
      .getOrElse("")

    assert(errorMessage.toLowerCase.contains("artifact") || errorMessage.toLowerCase.contains("not found"),
      s"Expected 'artifact not found' message, got: $errorMessage")
  }

  test("should return error for non-existent class in valid artifact") {
    val params = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.slf4j:slf4j-api:2.0.9"),
        "className" -> Json.fromString("org.slf4j.NonExistentClass")
      )
    )

    val response = makeRequest("tools/call", params)

    // Check for error in result or error field
    val hasError = response.hcursor.downField("result").downField("isError").as[Boolean].getOrElse(false) ||
                   response.hcursor.downField("error").succeeded

    assert(hasError, s"Expected error response for non-existent class: $response")

    val errorMessage = response.hcursor.downField("result").downField("content")
      .downArray.downField("text").as[String]
      .orElse(response.hcursor.downField("error").downField("message").as[String])
      .getOrElse("")

    assert(errorMessage.toLowerCase.contains("class") || errorMessage.toLowerCase.contains("not found"),
      s"Expected 'class not found' message, got: $errorMessage")
  }

  test("should fetch source code for org.slf4j.Logger") {
    val params = Json.obj(
      "name" -> Json.fromString("get_source"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.slf4j:slf4j-api:2.0.9"),
        "className" -> Json.fromString("org.slf4j.Logger")
      )
    )

    val startTime = System.currentTimeMillis()
    val response = makeRequest("tools/call", params)
    val endTime = System.currentTimeMillis()

    val result = response.hcursor.downField("result")
    val content = result.downField("content").as[List[Json]]

    assert(content.isRight, s"Expected content array in response: $response")

    val textContent = content.getOrElse(List.empty)
      .flatMap(_.hcursor.downField("text").as[String].toOption)
      .headOption

    assert(textContent.isDefined, "Expected text content in response")
    val source = textContent.get
    assert(source.contains("public interface Logger"), s"Expected 'public interface Logger' in source")
    assert(source.contains("void info(String msg)"), s"Expected 'void info(String msg)' method in source")

    val responseTime = endTime - startTime
    assert(responseTime < 5000, s"Response time $responseTime ms exceeded 5 seconds")
  }

  test("should return error for artifact without sources JAR") {
    val params = Json.obj(
      "name" -> Json.fromString("get_source"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("com.fake:nonexistent:1.0.0"),
        "className" -> Json.fromString("com.fake.FakeClass")
      )
    )

    val response = makeRequest("tools/call", params)

    // Check for error in result or error field
    val hasError = response.hcursor.downField("result").downField("isError").as[Boolean].getOrElse(false) ||
                   response.hcursor.downField("error").succeeded

    assert(hasError, s"Expected error response for non-existent sources: $response")

    val errorMessage = response.hcursor.downField("result").downField("content")
      .downArray.downField("text").as[String]
      .orElse(response.hcursor.downField("error").downField("message").as[String])
      .getOrElse("")

    // Since the artifact doesn't exist, we expect "Artifact not found" error
    assert(errorMessage.toLowerCase.contains("artifact") || errorMessage.toLowerCase.contains("not found"),
      s"Expected error message for non-existent artifact, got: $errorMessage")
  }

  test("should return error for non-existent class in sources JAR") {
    val params = Json.obj(
      "name" -> Json.fromString("get_source"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.slf4j:slf4j-api:2.0.9"),
        "className" -> Json.fromString("org.slf4j.NonExistentClass")
      )
    )

    val response = makeRequest("tools/call", params)

    // Check for error in result or error field
    val hasError = response.hcursor.downField("result").downField("isError").as[Boolean].getOrElse(false) ||
                   response.hcursor.downField("error").succeeded

    assert(hasError, s"Expected error response for non-existent class: $response")

    val errorMessage = response.hcursor.downField("result").downField("content")
      .downArray.downField("text").as[String]
      .orElse(response.hcursor.downField("error").downField("message").as[String])
      .getOrElse("")

    assert(errorMessage.toLowerCase.contains("class") || errorMessage.toLowerCase.contains("not found"),
      s"Expected 'class not found' message, got: $errorMessage")
  }

  // Scala version parameter tests

  test("should fetch Scaladoc for cats.effect.IO with default scalaVersion") {
    val params = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.typelevel::cats-effect:3.5.4"),
        "className" -> Json.fromString("cats.effect.IO")
      )
    )

    val startTime = System.currentTimeMillis()
    val response = makeRequest("tools/call", params)
    val endTime = System.currentTimeMillis()

    val result = response.hcursor.downField("result")
    val content = result.downField("content").as[List[Json]]

    assert(content.isRight, s"Expected content array in response: $response")

    val textContent = content.getOrElse(List.empty)
      .flatMap(_.hcursor.downField("text").as[String].toOption)
      .headOption

    assert(textContent.isDefined, "Expected text content in response")
    val html = textContent.get
    assert(html.contains("IO"), s"Expected 'IO' in HTML content")

    val responseTime = endTime - startTime
    assert(responseTime < 10000, s"Response time $responseTime ms exceeded 10 seconds")
  }

  test("should fetch Scala 2.13 artifact with explicit scalaVersion parameter") {
    val params = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.typelevel::cats-effect:3.5.4"),
        "className" -> Json.fromString("cats.effect.IO"),
        "scalaVersion" -> Json.fromString("2.13")
      )
    )

    val response = makeRequest("tools/call", params)

    val result = response.hcursor.downField("result")
    val content = result.downField("content").as[List[Json]]

    assert(content.isRight, s"Expected content array in response: $response")

    val textContent = content.getOrElse(List.empty)
      .flatMap(_.hcursor.downField("text").as[String].toOption)
      .headOption

    assert(textContent.isDefined, "Expected text content in response")
    val html = textContent.get
    assert(html.contains("IO"), s"Expected 'IO' in HTML content for Scala 2.13 version")
  }

  test("should fetch explicit suffix coordinate without scalaVersion parameter") {
    val params = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.typelevel:cats-effect_2.13:3.5.4"),
        "className" -> Json.fromString("cats.effect.IO")
      )
    )

    val response = makeRequest("tools/call", params)

    val result = response.hcursor.downField("result")
    val content = result.downField("content").as[List[Json]]

    assert(content.isRight, s"Expected content array in response: $response")

    val textContent = content.getOrElse(List.empty)
      .flatMap(_.hcursor.downField("text").as[String].toOption)
      .headOption

    assert(textContent.isDefined, "Expected text content in response")
    val html = textContent.get
    assert(html.contains("IO"), s"Expected 'IO' in HTML content for explicit _2.13 suffix")
  }

  test("should return error for non-existent Scala artifact") {
    val params = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("com.fake::nonexistent:1.0.0"),
        "className" -> Json.fromString("com.fake.FakeClass")
      )
    )

    val response = makeRequest("tools/call", params)

    val hasError = response.hcursor.downField("result").downField("isError").as[Boolean].getOrElse(false) ||
                   response.hcursor.downField("error").succeeded

    assert(hasError, s"Expected error response for non-existent Scala artifact: $response")
  }

  test("should return error for non-existent class in Scala artifact") {
    val params = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.typelevel::cats-effect:3.5.4"),
        "className" -> Json.fromString("cats.effect.NonExistentClass")
      )
    )

    val response = makeRequest("tools/call", params)

    val hasError = response.hcursor.downField("result").downField("isError").as[Boolean].getOrElse(false) ||
                   response.hcursor.downField("error").succeeded

    assert(hasError, s"Expected error response for non-existent class in Scala artifact: $response")

    val errorMessage = response.hcursor.downField("result").downField("content")
      .downArray.downField("text").as[String]
      .orElse(response.hcursor.downField("error").downField("message").as[String])
      .getOrElse("")

    assert(errorMessage.toLowerCase.contains("class") || errorMessage.toLowerCase.contains("not found"),
      s"Expected 'class not found' message, got: $errorMessage")
  }

  // Scala source code tests

  test("should fetch Scala source for cats.effect.IO") {
    val params = Json.obj(
      "name" -> Json.fromString("get_source"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.typelevel::cats-effect:3.5.4"),
        "className" -> Json.fromString("cats.effect.IO")
      )
    )

    val startTime = System.currentTimeMillis()
    val response = makeRequest("tools/call", params)
    val endTime = System.currentTimeMillis()

    val result = response.hcursor.downField("result")
    val content = result.downField("content").as[List[Json]]

    assert(content.isRight, s"Expected content array in response: $response")

    val textContent = content.getOrElse(List.empty)
      .flatMap(_.hcursor.downField("text").as[String].toOption)
      .headOption

    assert(textContent.isDefined, "Expected text content in response")
    val source = textContent.get
    assert(source.contains("sealed abstract class IO"),
      s"Expected 'sealed abstract class IO' in Scala source")
    assert(source.contains("def flatMap"),
      s"Expected 'def flatMap' in Scala source")

    val responseTime = endTime - startTime
    assert(responseTime < 5000, s"Response time $responseTime ms exceeded 5 seconds")
  }

  test("should fetch Scala source for zio.ZIO") {
    val params = Json.obj(
      "name" -> Json.fromString("get_source"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("dev.zio::zio:2.0.21"),
        "className" -> Json.fromString("zio.ZIO")
      )
    )

    val response = makeRequest("tools/call", params)

    val result = response.hcursor.downField("result")
    val content = result.downField("content").as[List[Json]]

    assert(content.isRight, s"Expected content array in response: $response")

    val textContent = content.getOrElse(List.empty)
      .flatMap(_.hcursor.downField("text").as[String].toOption)
      .headOption

    assert(textContent.isDefined, "Expected text content in response")
    val source = textContent.get
    assert(source.contains("sealed trait ZIO") || source.contains("sealed abstract class ZIO"),
      s"Expected Scala trait or class definition in source")
  }

  test("should return error for non-existent Scala class in sources") {
    val params = Json.obj(
      "name" -> Json.fromString("get_source"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.typelevel::cats-effect:3.5.4"),
        "className" -> Json.fromString("cats.effect.NonExistentClass")
      )
    )

    val response = makeRequest("tools/call", params)

    val hasError = response.hcursor.downField("result").downField("isError").as[Boolean].getOrElse(false) ||
                   response.hcursor.downField("error").succeeded

    assert(hasError, s"Expected error response for non-existent class: $response")

    val errorMessage = response.hcursor.downField("result").downField("content")
      .downArray.downField("text").as[String]
      .orElse(response.hcursor.downField("error").downField("message").as[String])
      .getOrElse("")

    assert(errorMessage.toLowerCase.contains("class") || errorMessage.toLowerCase.contains("not found"),
      s"Expected 'class not found' message, got: $errorMessage")
  }

  // Enhanced error message tests - verify Phase 5 improvements

  test("artifact not found error includes Maven Central suggestion") {
    val params = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("com.nonexistent:fake-library:1.0.0"),
        "className" -> Json.fromString("com.fake.FakeClass")
      )
    )

    val response = makeRequest("tools/call", params)

    val hasError = response.hcursor.downField("result").downField("isError").as[Boolean].getOrElse(false)
    assert(hasError, s"Expected error response: $response")

    val errorMessage = response.hcursor.downField("result").downField("content")
      .downArray.downField("text").as[String]
      .getOrElse("")

    assert(errorMessage.contains("com.nonexistent:fake-library:1.0.0"),
      s"Error message should include artifact coordinates")
    assert(errorMessage.contains("Maven Central") || errorMessage.contains("maven.org"),
      s"Error message should suggest checking Maven Central: $errorMessage")
    assert(errorMessage.contains("spelling") || errorMessage.contains("Spelling"),
      s"Error message should suggest checking spelling: $errorMessage")
  }

  test("artifact not found error is multi-line and helpful") {
    val params = Json.obj(
      "name" -> Json.fromString("get_source"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("com.fake.test:nonexistent:9.9.9"),
        "className" -> Json.fromString("com.fake.Class")
      )
    )

    val response = makeRequest("tools/call", params)

    val errorMessage = response.hcursor.downField("result").downField("content")
      .downArray.downField("text").as[String]
      .getOrElse("")

    // Verify multi-line format
    assert(errorMessage.contains("\n") || errorMessage.contains("\\n"),
      s"Error message should be multi-line formatted: $errorMessage")
    assert(errorMessage.contains("Please check"),
      s"Error message should include suggestions: $errorMessage")
  }

  test("server remains stable after error responses") {
    // Trigger an error
    val errorParams = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("com.error:test:1.0.0"),
        "className" -> Json.fromString("com.error.Test")
      )
    )

    val errorResponse = makeRequest("tools/call", errorParams)
    assert(errorResponse.hcursor.downField("result").downField("isError").as[Boolean].getOrElse(false),
      "First request should return error")

    // Immediately make a valid request
    val validParams = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.slf4j:slf4j-api:2.0.9"),
        "className" -> Json.fromString("org.slf4j.Logger")
      )
    )

    val validResponse = makeRequest("tools/call", validParams)
    val content = validResponse.hcursor.downField("result").downField("content").as[List[Json]]

    assert(content.isRight, s"Server should handle valid request after error: $validResponse")
    val textContent = content.getOrElse(List.empty)
      .flatMap(_.hcursor.downField("text").as[String].toOption)
      .headOption

    assert(textContent.isDefined, "Valid request should return content")
    assert(textContent.get.contains("Logger"), "Valid request should return Logger documentation")
  }
