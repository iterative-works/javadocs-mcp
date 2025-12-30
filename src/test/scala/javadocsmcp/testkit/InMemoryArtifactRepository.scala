// PURPOSE: In-memory implementation of ArtifactRepository for unit testing
// PURPOSE: Allows tests to control artifact resolution without network calls

package javadocsmcp.testkit

import javadocsmcp.domain.{ArtifactCoordinates, DocumentationError}
import javadocsmcp.domain.ports.ArtifactRepository
import java.io.File
import scala.collection.mutable

class InMemoryArtifactRepository(
  javadocArtifacts: Map[ArtifactCoordinates, File] = Map.empty,
  sourcesArtifacts: Map[ArtifactCoordinates, File] = Map.empty,
  mainArtifacts: Map[ArtifactCoordinates, File] = Map.empty
) extends ArtifactRepository:

  // Captures scalaVersion parameters for test verification
  private val javadocScalaVersionCalls: mutable.ListBuffer[String] = mutable.ListBuffer.empty
  private val sourcesScalaVersionCalls: mutable.ListBuffer[String] = mutable.ListBuffer.empty
  private val mainScalaVersionCalls: mutable.ListBuffer[String] = mutable.ListBuffer.empty

  def fetchJavadocJar(coords: ArtifactCoordinates, scalaVersion: String = "3"): Either[DocumentationError, File] =
    javadocScalaVersionCalls += scalaVersion
    javadocArtifacts.get(coords) match
      case Some(file) => Right(file)
      case None => Left(DocumentationError.ArtifactNotFound(
        s"${coords.groupId}:${coords.artifactId}:${coords.version}"))

  def fetchSourcesJar(coords: ArtifactCoordinates, scalaVersion: String = "3"): Either[DocumentationError, File] =
    sourcesScalaVersionCalls += scalaVersion
    sourcesArtifacts.get(coords) match
      case Some(file) => Right(file)
      case None => Left(DocumentationError.SourcesNotAvailable(
        s"${coords.groupId}:${coords.artifactId}:${coords.version}"))

  def fetchMainJar(coords: ArtifactCoordinates, scalaVersion: String = "3"): Either[DocumentationError, File] =
    mainScalaVersionCalls += scalaVersion
    mainArtifacts.get(coords) match
      case Some(file) => Right(file)
      case None => Left(DocumentationError.ArtifactNotFound(
        s"${coords.groupId}:${coords.artifactId}:${coords.version}"))

  // Test inspection methods
  def lastJavadocScalaVersion: Option[String] = javadocScalaVersionCalls.lastOption
  def lastSourcesScalaVersion: Option[String] = sourcesScalaVersionCalls.lastOption
  def lastMainScalaVersion: Option[String] = mainScalaVersionCalls.lastOption
  def allJavadocScalaVersionCalls: List[String] = javadocScalaVersionCalls.toList
  def allSourcesScalaVersionCalls: List[String] = sourcesScalaVersionCalls.toList
  def allMainScalaVersionCalls: List[String] = mainScalaVersionCalls.toList

object InMemoryArtifactRepository:
  def empty: InMemoryArtifactRepository = new InMemoryArtifactRepository()

  def withArtifact(coords: ArtifactCoordinates, file: File): InMemoryArtifactRepository =
    new InMemoryArtifactRepository(javadocArtifacts = Map(coords -> file))

  def withArtifacts(artifacts: (ArtifactCoordinates, File)*): InMemoryArtifactRepository =
    new InMemoryArtifactRepository(javadocArtifacts = artifacts.toMap)

  def withSourcesJar(coords: ArtifactCoordinates, file: File): InMemoryArtifactRepository =
    new InMemoryArtifactRepository(sourcesArtifacts = Map(coords -> file))

  def withBoth(coords: ArtifactCoordinates, javadocFile: File, sourcesFile: File): InMemoryArtifactRepository =
    new InMemoryArtifactRepository(
      javadocArtifacts = Map(coords -> javadocFile),
      sourcesArtifacts = Map(coords -> sourcesFile)
    )
