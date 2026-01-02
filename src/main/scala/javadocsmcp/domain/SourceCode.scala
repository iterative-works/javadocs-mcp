// PURPOSE: Domain entity representing Java source code from a library
// PURPOSE: Encapsulates source text with its class and artifact metadata

package javadocsmcp.domain

case class SourceCode(
  sourceText: String,
  className: ClassName,
  coordinates: ArtifactCoordinates
)
