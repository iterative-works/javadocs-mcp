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
}
