// PURPOSE: Integration tests for Coursier artifact resolution and download
// PURPOSE: Tests real Maven Central interaction with well-known artifacts

package javadocsmcp.infrastructure

import javadocsmcp.domain.ArtifactCoordinates

class CoursierArtifactRepositoryTest extends munit.FunSuite {
  val repository = CoursierArtifactRepository()

  test("fetch javadoc JAR for slf4j-api") {
    val coords = ArtifactCoordinates("org.slf4j", "slf4j-api", "2.0.9")
    val result = repository.fetchJavadocJar(coords)

    assert(result.isRight, "Should successfully fetch slf4j javadoc JAR from Maven Central")
    val jarFile = result.toOption.get
    assert(jarFile.exists(), "Downloaded JAR file should exist")
    assert(jarFile.getName.endsWith(".jar"), "File should be a JAR")
  }

  test("return error for non-existent artifact") {
    val coords = ArtifactCoordinates("com.fake.nonexistent", "does-not-exist", "1.0.0")
    val result = repository.fetchJavadocJar(coords)

    assert(result.isLeft, "Should return error for non-existent artifact")
  }

  test("fetch javadoc JAR for guava") {
    val coords = ArtifactCoordinates("com.google.guava", "guava", "32.1.3-jre")
    val result = repository.fetchJavadocJar(coords)

    assert(result.isRight, "Should successfully fetch guava javadoc JAR")
    val jarFile = result.toOption.get
    assert(jarFile.exists(), "Downloaded JAR file should exist")
  }
}
