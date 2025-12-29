// PURPOSE: In-memory implementation of ArtifactRepository for unit testing
// PURPOSE: Allows tests to control artifact resolution without network calls

package javadocsmcp.testkit

import javadocsmcp.domain.{ArtifactCoordinates, DocumentationError}
import javadocsmcp.domain.ports.ArtifactRepository
import java.io.File

class InMemoryArtifactRepository(
  artifacts: Map[String, File] = Map.empty
) extends ArtifactRepository:

  def fetchJavadocJar(coords: ArtifactCoordinates): Either[DocumentationError, File] =
    val key = s"${coords.groupId}:${coords.artifactId}:${coords.version}"
    artifacts.get(key) match
      case Some(file) => Right(file)
      case None => Left(DocumentationError.ArtifactNotFound(key))

object InMemoryArtifactRepository:
  def empty: InMemoryArtifactRepository = new InMemoryArtifactRepository()

  def withArtifacts(artifacts: (String, File)*): InMemoryArtifactRepository =
    new InMemoryArtifactRepository(artifacts.toMap)
