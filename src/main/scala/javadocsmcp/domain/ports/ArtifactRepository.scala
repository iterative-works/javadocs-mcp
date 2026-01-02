// PURPOSE: Port trait defining the contract for fetching library artifacts
// PURPOSE: Abstracts artifact retrieval to allow different implementations

package javadocsmcp.domain.ports

import javadocsmcp.domain.{ArtifactCoordinates, DocumentationError}
import java.io.File

trait ArtifactRepository:
  def fetchJavadocJar(coords: ArtifactCoordinates, scalaVersion: String = "3"): Either[DocumentationError, File]
  def fetchSourcesJar(coords: ArtifactCoordinates, scalaVersion: String = "3"): Either[DocumentationError, File]
  def fetchMainJar(coords: ArtifactCoordinates, scalaVersion: String = "3"): Either[DocumentationError, File]
