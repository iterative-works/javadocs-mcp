// PURPOSE: Value object for Maven artifact coordinates (groupId:artifactId:version)
// PURPOSE: Validates and parses Maven coordinate strings into structured data

package javadocsmcp.domain

import DocumentationError.*

case class ArtifactCoordinates(
  groupId: String,
  artifactId: String,
  version: String
)

object ArtifactCoordinates {
  def parse(coordinates: String): Either[DocumentationError, ArtifactCoordinates] =
    if coordinates.isEmpty then
      Left(InvalidCoordinates(coordinates))
    else
      val parts = coordinates.split(":")
      if parts.length != 3 then
        Left(InvalidCoordinates(coordinates))
      else
        val groupId = parts(0).trim
        val artifactId = parts(1).trim
        val version = parts(2).trim
        if groupId.isEmpty || artifactId.isEmpty || version.isEmpty then
          Left(InvalidCoordinates(coordinates))
        else
          Right(ArtifactCoordinates(groupId, artifactId, version))
}
