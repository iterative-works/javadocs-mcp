// PURPOSE: Integration test for DocumentationService with real Maven Central
// PURPOSE: Verifies end-to-end flow with actual network calls and file I/O

package javadocsmcp.application

import javadocsmcp.infrastructure.{CoursierArtifactRepository, JarFileReader}

class DocumentationServiceIntegrationTest extends munit.FunSuite:

  // Use fresh instances for each test to avoid shared mutable state
  def createService(): DocumentationService =
    val repository = CoursierArtifactRepository()
    val reader = JarFileReader()
    DocumentationService(repository, reader)

  test("fetch documentation from real Maven Central"):
    val service = createService()

    val result = service.getDocumentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")

    assert(result.isRight, s"Should successfully fetch Logger documentation: $result")
    val doc = result.toOption.get
    assertEquals(doc.className, "org.slf4j.Logger")
    assert(doc.htmlContent.contains("Logger"), "HTML should contain Logger")
    assert(doc.htmlContent.contains("interface"), "Logger is an interface")
