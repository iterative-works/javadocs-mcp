// PURPOSE: Unit tests for SourceCodeService using in-memory test doubles
// PURPOSE: Tests orchestration logic in isolation without network or file I/O

package javadocsmcp.application

import javadocsmcp.domain.{ArtifactCoordinates, DocumentationError}
import javadocsmcp.testkit.{InMemoryArtifactRepository, InMemoryJarContentReader}
import java.io.File

class SourceCodeServiceTest extends munit.FunSuite:

  // Test fixture factories - create fresh instances for each test
  private def testJar: File = new File("/fake/path/test-sources.jar")
  private def testSourceContent: String = "public interface Logger { void info(String msg); }"
  private def slf4jCoords: ArtifactCoordinates = ArtifactCoordinates("org.slf4j", "slf4j-api", "2.0.9")

  test("fetch source code for valid coordinates and class"):
    val jar = testJar
    val source = testSourceContent
    val coords = slf4jCoords
    val repository = InMemoryArtifactRepository.withSourcesJar(coords, jar)
    val reader = InMemoryJarContentReader.withEntries(
      (jar, "org/slf4j/Logger.java") -> source
    )
    val service = SourceCodeService(repository, reader)

    val result = service.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")

    assert(result.isRight, s"Should succeed but got: $result")
    val sourceCode = result.toOption.get
    assertEquals(sourceCode.className.fullyQualifiedName, "org.slf4j.Logger")
    assertEquals(sourceCode.sourceText, source)
    assertEquals(sourceCode.coordinates.groupId, "org.slf4j")
    assertEquals(sourceCode.coordinates.artifactId, "slf4j-api")
    assertEquals(sourceCode.coordinates.version, "2.0.9")

  test("handle inner class by stripping suffix"):
    val jar = testJar
    val source = testSourceContent
    val coords = slf4jCoords
    val repository = InMemoryArtifactRepository.withSourcesJar(coords, jar)
    val reader = InMemoryJarContentReader.withEntries(
      (jar, "org/slf4j/Logger.java") -> source
    )
    val service = SourceCodeService(repository, reader)

    val result = service.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger$Factory")

    assert(result.isRight, s"Should strip inner class suffix but got: $result")
    val sourceCode = result.toOption.get
    assertEquals(sourceCode.sourceText, source)

  test("return error for sources JAR not available"):
    val repository = InMemoryArtifactRepository.empty
    val reader = InMemoryJarContentReader.empty
    val service = SourceCodeService(repository, reader)

    val result = service.getSource("com.fake:nonexistent:1.0.0", "com.fake.Class")

    assert(result.isLeft, "Should return error for sources JAR not available")
    result.left.foreach { error =>
      assert(error.isInstanceOf[DocumentationError.SourcesNotAvailable])
    }

  test("return error for non-existent class in valid sources JAR"):
    val jar = testJar
    val coords = slf4jCoords
    val repository = InMemoryArtifactRepository.withSourcesJar(coords, jar)
    val reader = InMemoryJarContentReader.empty
    val service = SourceCodeService(repository, reader)

    val result = service.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.NonExistent")

    assert(result.isLeft, "Should return error for non-existent class")
    result.left.foreach { error =>
      assert(error.isInstanceOf[DocumentationError.ClassNotFound])
    }

  test("return error for invalid coordinates format"):
    val repository = InMemoryArtifactRepository.empty
    val reader = InMemoryJarContentReader.empty
    val service = SourceCodeService(repository, reader)

    val result = service.getSource("invalid", "org.slf4j.Logger")

    assert(result.isLeft, "Should return error for invalid coordinates")
    result.left.foreach { error =>
      assert(error.isInstanceOf[DocumentationError.InvalidCoordinates])
    }

  test("pass scalaVersion parameter to repository"):
    val jar = testJar
    val source = "trait IO[+A]"
    val coords = ArtifactCoordinates("org.typelevel", "cats-effect", "3.5.4", scalaArtifact = true)
    val repository = new InMemoryArtifactRepository(sourcesArtifacts = Map(coords -> jar))
    val reader = InMemoryJarContentReader.withEntries(
      (jar, "cats/effect/IO.scala") -> source
    )
    val service = SourceCodeService(repository, reader)

    service.getSource("org.typelevel::cats-effect:3.5.4", "cats.effect.IO", Some("2.13"))

    assertEquals(repository.lastSourcesScalaVersion, Some("2.13"))

  test("use default scalaVersion=3 when not specified"):
    val jar = testJar
    val source = "trait IO[+A]"
    val coords = ArtifactCoordinates("org.typelevel", "cats-effect", "3.5.4", scalaArtifact = true)
    val repository = new InMemoryArtifactRepository(sourcesArtifacts = Map(coords -> jar))
    val reader = InMemoryJarContentReader.withEntries(
      (jar, "cats/effect/IO.scala") -> source
    )
    val service = SourceCodeService(repository, reader)

    service.getSource("org.typelevel::cats-effect:3.5.4", "cats.effect.IO")

    assertEquals(repository.lastSourcesScalaVersion, Some("3"))

  test("use default scalaVersion=3 when None is explicitly passed"):
    val jar = testJar
    val source = "trait IO[+A]"
    val coords = ArtifactCoordinates("org.typelevel", "cats-effect", "3.5.4", scalaArtifact = true)
    val repository = new InMemoryArtifactRepository(sourcesArtifacts = Map(coords -> jar))
    val reader = InMemoryJarContentReader.withEntries(
      (jar, "cats/effect/IO.scala") -> source
    )
    val service = SourceCodeService(repository, reader)

    service.getSource("org.typelevel::cats-effect:3.5.4", "cats.effect.IO", None)

    assertEquals(repository.lastSourcesScalaVersion, Some("3"))
