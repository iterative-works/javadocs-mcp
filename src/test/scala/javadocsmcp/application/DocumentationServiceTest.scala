// PURPOSE: Unit tests for DocumentationService using in-memory test doubles
// PURPOSE: Tests orchestration logic in isolation without network or file I/O

package javadocsmcp.application

import javadocsmcp.domain.DocumentationError
import javadocsmcp.testkit.{InMemoryArtifactRepository, InMemoryDocumentationReader}
import java.io.File

class DocumentationServiceTest extends munit.FunSuite:

  val testJar = new File("/fake/path/test.jar")
  val testHtmlContent = "<html><body>Test Logger documentation</body></html>"

  test("fetch documentation for valid coordinates and class"):
    val repository = InMemoryArtifactRepository.withArtifacts(
      "org.slf4j:slf4j-api:2.0.9" -> testJar
    )
    val reader = InMemoryDocumentationReader.withEntries(
      (testJar, "org/slf4j/Logger.html") -> testHtmlContent
    )
    val service = DocumentationService(repository, reader)

    val result = service.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")

    assert(result.isRight, s"Should succeed but got: $result")
    val doc = result.toOption.get
    assertEquals(doc.className, "org.slf4j.Logger")
    assertEquals(doc.htmlContent, testHtmlContent)
    assertEquals(doc.coordinates.groupId, "org.slf4j")
    assertEquals(doc.coordinates.artifactId, "slf4j-api")
    assertEquals(doc.coordinates.version, "2.0.9")

  test("handle inner class by stripping suffix"):
    val repository = InMemoryArtifactRepository.withArtifacts(
      "org.slf4j:slf4j-api:2.0.9" -> testJar
    )
    val reader = InMemoryDocumentationReader.withEntries(
      (testJar, "org/slf4j/Logger.html") -> testHtmlContent
    )
    val service = DocumentationService(repository, reader)

    val result = service.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger$Factory")

    assert(result.isRight, s"Should strip inner class suffix but got: $result")
    val doc = result.toOption.get
    assertEquals(doc.htmlContent, testHtmlContent)

  test("return error for non-existent artifact"):
    val repository = InMemoryArtifactRepository.empty
    val reader = InMemoryDocumentationReader.empty
    val service = DocumentationService(repository, reader)

    val result = service.getDocumentation("com.fake:nonexistent:1.0.0", "com.fake.Class")

    assert(result.isLeft, "Should return error for non-existent artifact")
    result.left.foreach { error =>
      assert(error.isInstanceOf[DocumentationError.ArtifactNotFound])
    }

  test("return error for non-existent class in valid artifact"):
    val repository = InMemoryArtifactRepository.withArtifacts(
      "org.slf4j:slf4j-api:2.0.9" -> testJar
    )
    val reader = InMemoryDocumentationReader.empty
    val service = DocumentationService(repository, reader)

    val result = service.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.NonExistent")

    assert(result.isLeft, "Should return error for non-existent class")
    result.left.foreach { error =>
      assert(error.isInstanceOf[DocumentationError.ClassNotFound])
    }

  test("return error for invalid coordinates format"):
    val repository = InMemoryArtifactRepository.empty
    val reader = InMemoryDocumentationReader.empty
    val service = DocumentationService(repository, reader)

    val result = service.getDocumentation("invalid", "org.slf4j.Logger")

    assert(result.isLeft, "Should return error for invalid coordinates")
    result.left.foreach { error =>
      assert(error.isInstanceOf[DocumentationError.InvalidCoordinates])
    }
