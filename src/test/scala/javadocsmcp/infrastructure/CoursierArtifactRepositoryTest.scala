// PURPOSE: Integration tests for Coursier artifact resolution and download
// PURPOSE: Tests real Maven Central interaction with well-known artifacts

package javadocsmcp.infrastructure

import javadocsmcp.domain.{ArtifactCoordinates, DocumentationError}

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

  test("fetch sources JAR for slf4j-api") {
    val coords = ArtifactCoordinates("org.slf4j", "slf4j-api", "2.0.9")
    val result = repository.fetchSourcesJar(coords)

    assert(result.isRight, "Should successfully fetch slf4j sources JAR from Maven Central")
    val jarFile = result.toOption.get
    assert(jarFile.exists(), "Downloaded sources JAR file should exist")
    assert(jarFile.getName.endsWith(".jar"), "File should be a JAR")
  }

  test("return error for sources JAR when artifact does not exist") {
    val coords = ArtifactCoordinates("com.fake.nonexistent", "does-not-exist", "1.0.0")
    val result = repository.fetchSourcesJar(coords)

    assert(result.isLeft, "Should return error for non-existent artifact sources")
  }

  test("fetch Scaladoc JAR for cats-effect") {
    val coords = ArtifactCoordinates(
      groupId = "org.typelevel",
      artifactId = "cats-effect",
      version = "3.5.4",
      scalaArtifact = true
    )
    val result = repository.fetchJavadocJar(coords)

    assert(result.isRight, "Should successfully fetch cats-effect Scaladoc JAR")
    val jarFile = result.toOption.get
    assert(jarFile.exists(), "Downloaded JAR file should exist")
    assert(jarFile.getName.contains("cats-effect"), "Filename should contain artifact name")
    assert(jarFile.getName.contains("javadoc"), "Should be javadoc JAR")
  }

  test("fetch Scaladoc JAR for ZIO") {
    val coords = ArtifactCoordinates(
      groupId = "dev.zio",
      artifactId = "zio",
      version = "2.0.21",
      scalaArtifact = true
    )
    val result = repository.fetchJavadocJar(coords)

    assert(result.isRight, "Should successfully fetch ZIO Scaladoc JAR")
    val jarFile = result.toOption.get
    assert(jarFile.exists(), "Downloaded JAR file should exist")
  }

  test("return error for non-existent Scala artifact") {
    val coords = ArtifactCoordinates(
      groupId = "com.fake",
      artifactId = "nonexistent",
      version = "1.0.0",
      scalaArtifact = true
    )
    val result = repository.fetchJavadocJar(coords)

    assert(result.isLeft, "Should return error for non-existent Scala artifact")
  }

  test("fetch sources JAR for Scala artifact (cats-effect)") {
    val coords = ArtifactCoordinates(
      groupId = "org.typelevel",
      artifactId = "cats-effect",
      version = "3.5.4",
      scalaArtifact = true
    )
    val result = repository.fetchSourcesJar(coords)

    assert(result.isRight, "Should successfully fetch cats-effect sources JAR")
    val jarFile = result.toOption.get
    assert(jarFile.exists(), "Downloaded sources JAR file should exist")
  }

  test("resolve org.typelevel::cats-effect:3.5.4 with scalaVersion=3 to cats-effect_3") {
    val coords = ArtifactCoordinates(
      groupId = "org.typelevel",
      artifactId = "cats-effect",
      version = "3.5.4",
      scalaArtifact = true
    )
    val result = repository.fetchJavadocJar(coords, scalaVersion = "3")

    assert(result.isRight, "Should successfully fetch cats-effect with Scala 3")
    val jarFile = result.toOption.get
    assert(jarFile.getName.contains("_3"), "JAR filename should contain _3 suffix")
  }

  test("resolve org.typelevel::cats-effect:3.5.4 with scalaVersion=2.13 to cats-effect_2.13") {
    val coords = ArtifactCoordinates(
      groupId = "org.typelevel",
      artifactId = "cats-effect",
      version = "3.5.4",
      scalaArtifact = true
    )
    val result = repository.fetchJavadocJar(coords, scalaVersion = "2.13")

    assert(result.isRight, "Should successfully fetch cats-effect with Scala 2.13")
    val jarFile = result.toOption.get
    assert(jarFile.getName.contains("_2.13"), "JAR filename should contain _2.13 suffix")
  }

  test("Java coordinates unchanged regardless of scalaVersion") {
    val coords = ArtifactCoordinates("org.slf4j", "slf4j-api", "2.0.9")
    val result = repository.fetchJavadocJar(coords, scalaVersion = "2.13")

    assert(result.isRight, "Should successfully fetch slf4j regardless of scalaVersion")
    val jarFile = result.toOption.get
    assert(!jarFile.getName.contains("_2.13"), "JAR filename should not contain Scala suffix for Java artifact")
    assert(!jarFile.getName.contains("_3"), "JAR filename should not contain Scala suffix for Java artifact")
  }

  // Error detection tests - verify correct error types are returned

  test("non-existent artifact returns ArtifactNotFound error") {
    val coords = ArtifactCoordinates("com.nonexistent", "fake-library", "1.0.0")
    val result = repository.fetchJavadocJar(coords)

    assert(result.isLeft, "Should return error for non-existent artifact")
    result.left.foreach { error =>
      assert(error.isInstanceOf[DocumentationError.ArtifactNotFound],
        s"Should return ArtifactNotFound error, got: ${error.getClass.getName}")
      assert(error.message.contains("com.nonexistent:fake-library:1.0.0"),
        "Error message should include artifact coordinates")
      assert(error.message.contains("Maven Central"),
        "Error message should suggest checking Maven Central")
    }
  }

  test("non-existent artifact for sources returns ArtifactNotFound error") {
    val coords = ArtifactCoordinates("com.nonexistent", "fake-library", "1.0.0")
    val result = repository.fetchSourcesJar(coords)

    assert(result.isLeft, "Should return error for non-existent artifact")
    result.left.foreach { error =>
      assert(error.isInstanceOf[DocumentationError.ArtifactNotFound],
        s"Should return ArtifactNotFound error, got: ${error.getClass.getName}")
    }
  }

  test("missing javadoc classifier returns JavadocNotAvailable error") {
    // Note: Most real artifacts publish javadoc, making it hard to test the actual error path
    // This test verifies the error message format is correct for JavadocNotAvailable
    // The error detection logic is tested through the non-existent artifact tests above

    val error = DocumentationError.JavadocNotAvailable("test:artifact:1.0.0")
    assert(error.message.contains("get_source"),
      "JavadocNotAvailable message should suggest using get_source")
    assert(error.message.contains("don't publish javadoc"),
      "JavadocNotAvailable message should explain libraries don't publish javadoc")
  }

  test("error messages include artifact coordinates") {
    val coords = ArtifactCoordinates("com.fake.test", "nonexistent-lib", "9.9.9")
    val result = repository.fetchJavadocJar(coords)

    assert(result.isLeft, "Should return error")
    result.left.foreach { error =>
      assert(error.message.contains("com.fake.test:nonexistent-lib:9.9.9"),
        "Error message should include full artifact coordinates")
    }
  }

  test("error messages for sources include coordinates") {
    val coords = ArtifactCoordinates("com.fake.test", "nonexistent-lib", "9.9.9")
    val result = repository.fetchSourcesJar(coords)

    assert(result.isLeft, "Should return error")
    result.left.foreach { error =>
      assert(error.message.contains("com.fake.test:nonexistent-lib:9.9.9"),
        "Error message should include full artifact coordinates")
    }
  }
}
