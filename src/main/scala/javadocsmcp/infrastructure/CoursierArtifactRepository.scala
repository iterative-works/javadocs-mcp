// PURPOSE: Repository for fetching library artifacts from Maven Central using Coursier
// PURPOSE: Downloads and caches artifacts with javadoc or sources classifiers

package javadocsmcp.infrastructure

import coursier.{Fetch, Classifier, Attributes, Type}
import coursier.core.{Module as CoursierModule, Organization, ModuleName, Dependency as CoursierDependency}
import dependency.parser.DependencyParser
import dependency.ScalaParameters
import javadocsmcp.domain.{ArtifactCoordinates, DocumentationError}
import javadocsmcp.domain.ports.ArtifactRepository
import DocumentationError.*
import java.io.File
import scala.util.{Try, Success, Failure}

class CoursierArtifactRepository extends ArtifactRepository:

  // Resolves artifact coordinates with appropriate Scala version suffix using coursier/dependency library
  private def resolveArtifact(
    coords: ArtifactCoordinates,
    scalaVersion: String
  ): (String, String, String) =
    if coords.scalaArtifact then
      // Use coursier/dependency library for proper Scala version resolution
      val coordString = s"${coords.groupId}::${coords.artifactId}:${coords.version}"
      val parsedDep = DependencyParser.parse(coordString).toOption.getOrElse(
        throw new RuntimeException(s"Failed to parse Scala coordinates: $coordString")
      )
      val resolved = parsedDep.applyParams(ScalaParameters(scalaVersion))
      (resolved.module.organization, resolved.module.name, resolved.version)
    else
      (coords.groupId, coords.artifactId, coords.version)

  private def fetchJar(
    coords: ArtifactCoordinates,
    scalaVersion: String,
    classifier: Classifier,
    errorConstructor: String => DocumentationError
  ): Either[DocumentationError, File] =
    val (org, name, ver) = resolveArtifact(coords, scalaVersion)
    Try {
      val module = CoursierModule(
        Organization(org),
        ModuleName(name),
        Map.empty[String, String]
      )

      val attributes = Attributes(Type.jar, classifier)
      val dependency = CoursierDependency(module, ver).withAttributes(attributes)

      val fetch = Fetch()
        .addDependencies(dependency)

      val files = fetch.run()

      if files.isEmpty then
        throw new RuntimeException(s"No ${classifier.value} JAR found for $org:$name:$ver")

      files.head
    } match
      case Success(file) => Right(file)
      case Failure(ex) =>
        val coordsStr = s"$org:$name:$ver"
        // Distinguish between artifact not found vs classifier not found
        // coursier.error.ResolutionError: Artifact doesn't exist in repository
        // coursier.error.FetchError: Artifact exists but specific file/classifier not found
        ex match
          case _: coursier.error.ResolutionError =>
            // Artifact doesn't exist at all
            Left(DocumentationError.ArtifactNotFound(coordsStr))
          case _ =>
            // Classifier not found or other error - use the specific error constructor
            Left(errorConstructor(coordsStr))

  def fetchJavadocJar(coords: ArtifactCoordinates, scalaVersion: String = "3"): Either[DocumentationError, File] =
    fetchJar(coords, scalaVersion, Classifier("javadoc"), JavadocNotAvailable.apply)

  def fetchSourcesJar(coords: ArtifactCoordinates, scalaVersion: String = "3"): Either[DocumentationError, File] =
    fetchJar(coords, scalaVersion, Classifier("sources"), SourcesNotAvailable.apply)

  def fetchMainJar(coords: ArtifactCoordinates, scalaVersion: String = "3"): Either[DocumentationError, File] =
    fetchJar(coords, scalaVersion, Classifier.empty, ArtifactNotFound.apply)

object CoursierArtifactRepository:
  def apply(): CoursierArtifactRepository = new CoursierArtifactRepository()
