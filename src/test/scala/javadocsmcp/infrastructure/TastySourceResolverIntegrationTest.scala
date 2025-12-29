// PURPOSE: Integration tests for TastySourceResolver with real JAR files
// PURPOSE: Tests TASTy-based source path resolution using actual Maven artifacts

package javadocsmcp.infrastructure

import javadocsmcp.domain.DocumentationError

class TastySourceResolverIntegrationTest extends munit.FunSuite:

  val repository = CoursierArtifactRepository()
  val resolver = TastySourceResolver(repository)

  test("resolve source path for cats.effect.IO from real JAR"):
    val result = resolver.resolveSourcePath(
      "org.typelevel::cats-effect:3.5.4",
      "cats.effect.IO",
      "3"
    )

    assert(result.isRight, s"Should resolve source path but got: $result")
    val path = result.toOption.get
    // cats-effect source path should be cats/effect/IO.scala
    assertEquals(path, "cats/effect/IO.scala")

  test("resolve source path for zio.ZIO from real JAR"):
    val result = resolver.resolveSourcePath(
      "dev.zio::zio:2.0.21",
      "zio.ZIO",
      "3"
    )

    assert(result.isRight, s"Should resolve source path but got: $result")
    val path = result.toOption.get
    // ZIO source path should be zio/ZIO.scala
    assertEquals(path, "zio/ZIO.scala")

  test("return error for non-existent class"):
    val result = resolver.resolveSourcePath(
      "org.typelevel::cats-effect:3.5.4",
      "cats.effect.NonExistentClass",
      "3"
    )

    assert(result.isLeft, "Should return error for non-existent class")
    result.left.foreach { error =>
      assert(error.isInstanceOf[DocumentationError.ClassNotFound])
    }

  test("resolve source path for inner class uses outer class path"):
    val result = resolver.resolveSourcePath(
      "org.typelevel::cats-effect:3.5.4",
      "cats.effect.IO$Pure",
      "3"
    )

    // Inner classes should resolve to the same file as their outer class
    // The TASTy lookup should handle this correctly
    assert(result.isRight, s"Should resolve source path for inner class but got: $result")
    val path = result.toOption.get
    assertEquals(path, "cats/effect/IO.scala")
