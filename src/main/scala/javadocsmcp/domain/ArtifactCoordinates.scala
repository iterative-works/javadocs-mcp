// PURPOSE: Value object for Maven/Scala artifact coordinates (groupId:artifactId:version or groupId::artifactId:version)
// PURPOSE: Validates and parses coordinate strings into structured data supporting both Java and Scala artifacts

package javadocsmcp.domain

import DocumentationError.*

case class ArtifactCoordinates(
  groupId: String,
  artifactId: String,
  version: String,
  scalaArtifact: Boolean = false
)

object ArtifactCoordinates {
  def parse(coordinates: String): Either[DocumentationError, ArtifactCoordinates] =
    if coordinates.isEmpty then
      Left(InvalidCoordinates(coordinates))
    else if coordinates.contains("::") then
      parseScalaCoordinates(coordinates)
    else
      parseJavaCoordinates(coordinates)

  private def parseScalaCoordinates(coordinates: String): Either[DocumentationError, ArtifactCoordinates] =
    val parts = coordinates.split("::")
    if parts.length != 2 then
      Left(InvalidCoordinates(coordinates))
    else
      val groupId = parts(0).trim
      val remaining = parts(1).split(":")
      if remaining.length != 2 then
        Left(InvalidCoordinates(coordinates))
      else
        val artifactId = remaining(0).trim
        val version = remaining(1).trim
        if groupId.isEmpty || artifactId.isEmpty || version.isEmpty then
          Left(InvalidCoordinates(coordinates))
        else
          Right(ArtifactCoordinates(groupId, artifactId, version, scalaArtifact = true))

  private def parseJavaCoordinates(coordinates: String): Either[DocumentationError, ArtifactCoordinates] =
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
        Right(ArtifactCoordinates(groupId, artifactId, version, scalaArtifact = false))
}
