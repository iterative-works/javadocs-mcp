// PURPOSE: Value object for Maven artifact coordinates (groupId:artifactId:version)
// PURPOSE: Validates and parses Maven coordinate strings into structured data

package javadocsmcp.domain

case class ArtifactCoordinates(
  groupId: String,
  artifactId: String,
  version: String
)

object ArtifactCoordinates {
  def parse(coordinates: String): Either[DocumentationError, ArtifactCoordinates] = {
    if (coordinates.isEmpty) {
      return Left(InvalidCoordinates(coordinates))
    }

    val parts = coordinates.split(":")

    if (parts.length != 3) {
      return Left(InvalidCoordinates(coordinates))
    }

    val groupId = parts(0).trim
    val artifactId = parts(1).trim
    val version = parts(2).trim

    if (groupId.isEmpty || artifactId.isEmpty || version.isEmpty) {
      return Left(InvalidCoordinates(coordinates))
    }

    Right(ArtifactCoordinates(groupId, artifactId, version))
  }
}
