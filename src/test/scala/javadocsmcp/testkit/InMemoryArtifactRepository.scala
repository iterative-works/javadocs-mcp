// PURPOSE: In-memory implementation of ArtifactRepository for unit testing
// PURPOSE: Allows tests to control artifact resolution without network calls

package javadocsmcp.testkit

import javadocsmcp.domain.{ArtifactCoordinates, DocumentationError}
import javadocsmcp.domain.ports.ArtifactRepository
import java.io.File

class InMemoryArtifactRepository(
  artifacts: Map[ArtifactCoordinates, File] = Map.empty
) extends ArtifactRepository:

  def fetchJavadocJar(coords: ArtifactCoordinates): Either[DocumentationError, File] =
    artifacts.get(coords) match
      case Some(file) => Right(file)
      case None => Left(DocumentationError.ArtifactNotFound(
        s"${coords.groupId}:${coords.artifactId}:${coords.version}"))

object InMemoryArtifactRepository:
  def empty: InMemoryArtifactRepository = new InMemoryArtifactRepository()

  def withArtifact(coords: ArtifactCoordinates, file: File): InMemoryArtifactRepository =
    new InMemoryArtifactRepository(Map(coords -> file))

  def withArtifacts(artifacts: (ArtifactCoordinates, File)*): InMemoryArtifactRepository =
    new InMemoryArtifactRepository(artifacts.toMap)
