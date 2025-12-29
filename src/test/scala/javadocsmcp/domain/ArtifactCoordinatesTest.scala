// PURPOSE: Test suite for ArtifactCoordinates value object validation and parsing
// PURPOSE: Ensures Maven coordinates are correctly validated and parsed

package javadocsmcp.domain

class ArtifactCoordinatesTest extends munit.FunSuite {
  test("parse valid Maven coordinates") {
    val result = ArtifactCoordinates.parse("org.slf4j:slf4j-api:2.0.9")

    assert(result.isRight, "Should successfully parse valid coordinates")
    val coords = result.toOption.get
    assertEquals(coords.groupId, "org.slf4j")
    assertEquals(coords.artifactId, "slf4j-api")
    assertEquals(coords.version, "2.0.9")
  }

  test("reject coordinates with missing version") {
    val result = ArtifactCoordinates.parse("org.slf4j:slf4j-api")

    assert(result.isLeft, "Should reject coordinates without version")
  }

  test("reject coordinates with invalid format") {
    val result = ArtifactCoordinates.parse("invalid")

    assert(result.isLeft, "Should reject completely invalid format")
  }

  test("reject empty string") {
    val result = ArtifactCoordinates.parse("")

    assert(result.isLeft, "Should reject empty string")
  }

  test("parse valid Scala coordinates with :: separator") {
    val result = ArtifactCoordinates.parse("org.typelevel::cats-effect:3.5.4")

    assert(result.isRight, "Should successfully parse Scala coordinates")
    val coords = result.toOption.get
    assertEquals(coords.groupId, "org.typelevel")
    assertEquals(coords.artifactId, "cats-effect")
    assertEquals(coords.version, "3.5.4")
    assertEquals(coords.scalaArtifact, true)
  }

  test("parse zio Scala coordinates") {
    val result = ArtifactCoordinates.parse("dev.zio::zio:2.0.21")

    assert(result.isRight, "Should successfully parse zio coordinates")
    val coords = result.toOption.get
    assertEquals(coords.groupId, "dev.zio")
    assertEquals(coords.artifactId, "zio")
    assertEquals(coords.version, "2.0.21")
    assertEquals(coords.scalaArtifact, true)
  }

  test("reject Scala coordinates with missing version") {
    val result = ArtifactCoordinates.parse("org.typelevel::cats-effect")

    assert(result.isLeft, "Should reject Scala coordinates without version")
  }

  test("reject Scala coordinates with wrong separator count") {
    val result = ArtifactCoordinates.parse("org.typelevel:::cats-effect:3.5.4")

    assert(result.isLeft, "Should reject coordinates with triple colon")
  }

  test("Java coordinates still work (regression test)") {
    val result = ArtifactCoordinates.parse("org.slf4j:slf4j-api:2.0.9")

    assert(result.isRight, "Java coordinates should still work")
    val coords = result.toOption.get
    assertEquals(coords.scalaArtifact, false)
  }
}
