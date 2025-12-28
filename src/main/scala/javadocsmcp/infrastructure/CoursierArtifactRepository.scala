// PURPOSE: Repository for fetching javadoc JAR artifacts from Maven Central using Coursier
// PURPOSE: Downloads and caches javadoc artifacts with -javadoc classifier

package javadocsmcp.infrastructure

import coursier.*
import javadocsmcp.domain.{ArtifactCoordinates, ArtifactNotFound, DocumentationError}
import java.io.File
import scala.util.{Try, Success, Failure}

class CoursierArtifactRepository {
  def fetchJavadocJar(coords: ArtifactCoordinates): Either[DocumentationError, File] = {
    Try {
      val module = Module(
        Organization(coords.groupId),
        ModuleName(coords.artifactId)
      )

      val attributes = Attributes(Type.jar, Classifier("javadoc"))
      val dependency = Dependency(module, coords.version).withAttributes(attributes)

      val fetch = Fetch()
        .addDependencies(dependency)

      val files = fetch.run()

      if (files.isEmpty) {
        throw new RuntimeException(s"No javadoc JAR found for ${coords.groupId}:${coords.artifactId}:${coords.version}")
      }

      files.head
    } match {
      case Success(file) => Right(file)
      case Failure(exception) =>
        Left(ArtifactNotFound(s"${coords.groupId}:${coords.artifactId}:${coords.version}"))
    }
  }
}

object CoursierArtifactRepository {
  def apply(): CoursierArtifactRepository = new CoursierArtifactRepository()
}
