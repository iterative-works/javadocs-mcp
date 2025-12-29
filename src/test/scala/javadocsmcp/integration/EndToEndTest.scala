// PURPOSE: End-to-end test for MCP HTTP server with real network calls
// PURPOSE: Verifies complete flow from HTTP request to MCP tool response

package javadocsmcp.integration

import javadocsmcp.application.{DocumentationService, SourceCodeService}
import javadocsmcp.infrastructure.{CoursierArtifactRepository, JarFileReader}
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
    val documentationService = DocumentationService(repository, reader)
    val sourceCodeService = SourceCodeService(repository, reader)

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

    assert(errorMessage.toLowerCase.contains("sources") || errorMessage.toLowerCase.contains("not available"),
      s"Expected 'sources not available' message, got: $errorMessage")
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

  test("should fetch Scaladoc for cats.effect.IO") {
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
    assert(html.contains("IO"), s"Expected 'IO' in Scaladoc HTML content")
    assert(html.nonEmpty, "HTML content should not be empty")

    val responseTime = endTime - startTime
    assert(responseTime < 5000, s"Response time $responseTime ms exceeded 5 seconds")
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

    val errorMessage = response.hcursor.downField("result").downField("content")
      .downArray.downField("text").as[String]
      .orElse(response.hcursor.downField("error").downField("message").as[String])
      .getOrElse("")

    assert(errorMessage.toLowerCase.contains("artifact") || errorMessage.toLowerCase.contains("not found"),
      s"Expected 'artifact not found' message, got: $errorMessage")
  }

  test("should return error for non-existent class in Scala artifact") {
    val params = Json.obj(
      "name" -> Json.fromString("get_documentation"),
      "arguments" -> Json.obj(
        "coordinates" -> Json.fromString("org.typelevel::cats-effect:3.5.4"),
        "className" -> Json.fromString("cats.effect.NonExistent")
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
