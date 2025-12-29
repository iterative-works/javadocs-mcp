// PURPOSE: Integration tests for SourceCodeService with real Maven Central
// PURPOSE: Tests complete source fetching flow with real artifacts

package javadocsmcp.application

import javadocsmcp.infrastructure.{CoursierArtifactRepository, JarFileReader}

class SourceCodeServiceIntegrationTest extends munit.FunSuite {
  val repository = CoursierArtifactRepository()
  val reader = JarFileReader()
  val service = SourceCodeService(repository, reader)

  test("fetch real source code for org.slf4j.Logger") {
    val result = service.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")

    assert(result.isRight, "Should successfully fetch Logger source")
    val source = result.toOption.get
    assert(source.sourceText.contains("public interface Logger"))
    assert(source.sourceText.contains("void info(String msg)"))
    assertEquals(source.className.fullyQualifiedName, "org.slf4j.Logger")
    assertEquals(source.coordinates.groupId, "org.slf4j")
    assertEquals(source.coordinates.artifactId, "slf4j-api")
    assertEquals(source.coordinates.version, "2.0.9")
  }

  test("fetch real source code for guava ImmutableList") {
    val result = service.getSource(
      "com.google.guava:guava:32.1.3-jre",
      "com.google.common.collect.ImmutableList"
    )

    assert(result.isRight, "Should successfully fetch ImmutableList source")
    val source = result.toOption.get
    assert(source.sourceText.contains("ImmutableList"))
    assert(source.sourceText.contains("class") || source.sourceText.contains("abstract"))
  }

  test("handle inner class correctly") {
    val result = service.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger$Factory")

    assert(result.isRight, "Should fetch outer class source for inner class")
    val source = result.toOption.get
    assert(source.sourceText.contains("public interface Logger"))
  }
}
