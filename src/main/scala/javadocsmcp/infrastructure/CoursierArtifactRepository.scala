// PURPOSE: Repository for fetching library artifacts from Maven Central using Coursier
// PURPOSE: Downloads and caches artifacts with javadoc or sources classifiers

package javadocsmcp.infrastructure

import coursier.*
import javadocsmcp.domain.{ArtifactCoordinates, DocumentationError}
import javadocsmcp.domain.ports.ArtifactRepository
import DocumentationError.*
import java.io.File
import scala.util.{Try, Success, Failure}

class CoursierArtifactRepository extends ArtifactRepository:

  // Resolves artifact name with Scala version suffix if needed.
  // Note: Assumes Scala 3 (_3 suffix). Scala 2.x artifacts not yet supported.
  private def resolveArtifactName(coords: ArtifactCoordinates): String =
    if coords.scalaArtifact then s"${coords.artifactId}_3"
    else coords.artifactId

  private def fetchJar(
    coords: ArtifactCoordinates,
    classifier: Classifier,
    errorConstructor: String => DocumentationError
  ): Either[DocumentationError, File] =
    val artifactName = resolveArtifactName(coords)
    Try {
      val module = Module(
        Organization(coords.groupId),
        ModuleName(artifactName)
      )

      val attributes = Attributes(Type.jar, classifier)
      val dependency = Dependency(module, coords.version).withAttributes(attributes)

      val fetch = Fetch()
        .addDependencies(dependency)

      val files = fetch.run()

      if files.isEmpty then
        throw new RuntimeException(s"No ${classifier.value} JAR found for ${coords.groupId}:$artifactName:${coords.version}")

      files.head
    } match
      case Success(file) => Right(file)
      case Failure(_) => Left(errorConstructor(s"${coords.groupId}:$artifactName:${coords.version}"))

  def fetchJavadocJar(coords: ArtifactCoordinates): Either[DocumentationError, File] =
    fetchJar(coords, Classifier("javadoc"), ArtifactNotFound.apply)

  def fetchSourcesJar(coords: ArtifactCoordinates): Either[DocumentationError, File] =
    fetchJar(coords, Classifier("sources"), SourcesNotAvailable.apply)

object CoursierArtifactRepository:
  def apply(): CoursierArtifactRepository = new CoursierArtifactRepository()
