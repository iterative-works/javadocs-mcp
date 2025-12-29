// PURPOSE: Domain entity representing fetched javadoc documentation
// PURPOSE: Contains HTML content and metadata for a Java class

package javadocsmcp.domain

case class Documentation(
  htmlContent: String,
  className: String,
  coordinates: ArtifactCoordinates
)
