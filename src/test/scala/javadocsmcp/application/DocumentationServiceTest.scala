// PURPOSE: Integration tests for DocumentationService end-to-end flow
// PURPOSE: Tests complete orchestration from coordinates to HTML documentation

package javadocsmcp.application

import javadocsmcp.infrastructure.{CoursierArtifactRepository, JarFileReader}

class DocumentationServiceTest extends munit.FunSuite {
  val repository = CoursierArtifactRepository()
  val reader = JarFileReader()
  val service = DocumentationService(repository, reader)

  test("fetch documentation for org.slf4j.Logger") {
    val result = service.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")

    assert(result.isRight, "Should successfully fetch Logger documentation")
    val doc = result.toOption.get
    assertEquals(doc.className, "org.slf4j.Logger")
    assert(doc.htmlContent.contains("Logger"), "HTML should contain Logger")
    assert(doc.htmlContent.contains("interface"), "Logger is an interface")
  }

  test("handle inner class by stripping suffix") {
    val result = service.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger$Factory")

    assert(result.isRight, "Should successfully fetch Logger documentation (inner class stripped)")
    val doc = result.toOption.get
    assert(doc.htmlContent.contains("Logger"), "Should fetch outer class Logger.html")
  }

  test("return error for non-existent artifact") {
    val result = service.getDocumentation("com.fake:nonexistent:1.0.0", "com.fake.Class")

    assert(result.isLeft, "Should return error for non-existent artifact")
  }

  test("return error for non-existent class in valid artifact") {
    val result = service.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.NonExistent")

    assert(result.isLeft, "Should return error for non-existent class")
  }

  test("return error for invalid coordinates format") {
    val result = service.getDocumentation("invalid", "org.slf4j.Logger")

    assert(result.isLeft, "Should return error for invalid coordinates")
  }
}
