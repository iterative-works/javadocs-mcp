// PURPOSE: Port trait defining the contract for fetching javadoc artifacts
// PURPOSE: Abstracts artifact retrieval to allow different implementations

package javadocsmcp.domain.ports

import javadocsmcp.domain.{ArtifactCoordinates, DocumentationError}
import java.io.File

trait ArtifactRepository:
  def fetchJavadocJar(coords: ArtifactCoordinates): Either[DocumentationError, File]
