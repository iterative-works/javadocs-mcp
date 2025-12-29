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

  test("fetch Scaladoc for cats.effect.IO"):
    val service = createService()

    val result = service.getDocumentation("org.typelevel::cats-effect:3.5.4", "cats.effect.IO")

    assert(result.isRight, s"Should successfully fetch IO Scaladoc: $result")
    val doc = result.toOption.get
    assertEquals(doc.className, "cats.effect.IO")
    assert(doc.htmlContent.contains("IO"), "HTML should contain IO class documentation")
    assert(doc.htmlContent.nonEmpty, "HTML content should not be empty")

  test("fetch Scaladoc for zio.ZIO"):
    val service = createService()

    val result = service.getDocumentation("dev.zio::zio:2.0.21", "zio.ZIO")

    assert(result.isRight, s"Should successfully fetch ZIO Scaladoc: $result")
    val doc = result.toOption.get
    assertEquals(doc.className, "zio.ZIO")
    assert(doc.htmlContent.contains("ZIO"), "HTML should contain ZIO class documentation")
