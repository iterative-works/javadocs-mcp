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

  test("fetch Scala source for cats.effect.IO") {
    val result = service.getSource("org.typelevel::cats-effect:3.5.4", "cats.effect.IO")

    assert(result.isRight, s"Should successfully fetch IO.scala source but got: $result")
    val source = result.toOption.get
    assert(source.sourceText.contains("sealed abstract class IO"),
      "Should contain Scala-specific syntax 'sealed abstract class IO'")
    assert(source.sourceText.contains("def flatMap"),
      "Should contain flatMap method")
    assertEquals(source.className.fullyQualifiedName, "cats.effect.IO")
    assertEquals(source.coordinates.groupId, "org.typelevel")
    assertEquals(source.coordinates.artifactId, "cats-effect")
    assertEquals(source.coordinates.version, "3.5.4")
  }

  test("fetch Scala source for zio.ZIO") {
    val result = service.getSource("dev.zio::zio:2.0.21", "zio.ZIO")

    assert(result.isRight, s"Should successfully fetch ZIO.scala source but got: $result")
    val source = result.toOption.get
    assert(source.sourceText.contains("sealed trait ZIO") || source.sourceText.contains("sealed abstract class ZIO"),
      "Should contain Scala-specific trait or class definition")
    assertEquals(source.className.fullyQualifiedName, "zio.ZIO")
    assertEquals(source.coordinates.groupId, "dev.zio")
    assertEquals(source.coordinates.artifactId, "zio")
    assertEquals(source.coordinates.version, "2.0.21")
  }
}
