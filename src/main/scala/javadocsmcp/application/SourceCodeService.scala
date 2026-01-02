// PURPOSE: Application service orchestrating source code retrieval
// PURPOSE: Coordinates artifact fetching, JAR reading, and source extraction

package javadocsmcp.application

import javadocsmcp.domain.{ArtifactCoordinates, ClassName, SourceCode, DocumentationError}
import javadocsmcp.domain.ports.{ArtifactRepository, JarContentReader, SourcePathResolver}
import java.io.File

class SourceCodeService(
  repository: ArtifactRepository,
  reader: JarContentReader,
  sourcePathResolver: Option[SourcePathResolver] = None
):
  def getSource(
    coordinatesStr: String,
    classNameStr: String,
    scalaVersion: Option[String] = None
  ): Either[DocumentationError, SourceCode] = {
    val effectiveScalaVersion = scalaVersion.getOrElse("3")
    for {
      coords <- ArtifactCoordinates.parse(coordinatesStr)
      className <- ClassName.parse(classNameStr)
      sourcesJar <- repository.fetchSourcesJar(coords, effectiveScalaVersion)
      sourceText <- resolveSource(coords, className, sourcesJar, coordinatesStr, classNameStr, effectiveScalaVersion)
    } yield SourceCode(sourceText, className, coords)
  }

  private def resolveSource(
    coords: ArtifactCoordinates,
    className: ClassName,
    sourcesJar: File,
    coordinatesStr: String,
    classNameStr: String,
    scalaVersion: String
  ): Either[DocumentationError, String] =
    if coords.scalaArtifact then
      // For Scala artifacts, try TASTy-based resolution first, then filename convention
      resolveScalaSource(className, sourcesJar, coordinatesStr, classNameStr, scalaVersion)
    else
      // Java artifacts only have .java files
      reader.readEntry(sourcesJar, className.toSourcePath)

  private def resolveScalaSource(
    className: ClassName,
    sourcesJar: File,
    coordinatesStr: String,
    classNameStr: String,
    scalaVersion: String
  ): Either[DocumentationError, String] =
    // Try TASTy-based resolution first if resolver is available
    val tastyResult = sourcePathResolver.flatMap { resolver =>
      resolver.resolveSourcePath(coordinatesStr, classNameStr, scalaVersion) match
        case Right(path) => Some(reader.readEntry(sourcesJar, path))
        case Left(_) => None // TASTy lookup failed, will fall back
    }

    tastyResult.getOrElse {
      // Fallback to filename convention: try .scala first, then .java
      reader.readEntry(sourcesJar, className.toScalaSourcePath)
        .orElse(reader.readEntry(sourcesJar, className.toSourcePath))
    }

object SourceCodeService:
  def apply(repository: ArtifactRepository, reader: JarContentReader): SourceCodeService =
    new SourceCodeService(repository, reader, None)

  def apply(
    repository: ArtifactRepository,
    reader: JarContentReader,
    sourcePathResolver: SourcePathResolver
  ): SourceCodeService =
    new SourceCodeService(repository, reader, Some(sourcePathResolver))
