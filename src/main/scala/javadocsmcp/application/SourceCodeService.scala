// PURPOSE: Application service orchestrating source code retrieval
// PURPOSE: Coordinates artifact fetching, JAR reading, and source extraction

package javadocsmcp.application

import javadocsmcp.domain.{ArtifactCoordinates, ClassName, SourceCode, DocumentationError}
import javadocsmcp.domain.ports.{ArtifactRepository, JarContentReader}

class SourceCodeService(
  repository: ArtifactRepository,
  reader: JarContentReader
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
      sourceText <- reader.readEntry(sourcesJar, className.toSourcePath)
    } yield SourceCode(sourceText, className, coords)
  }

object SourceCodeService:
  def apply(repository: ArtifactRepository, reader: JarContentReader): SourceCodeService =
    new SourceCodeService(repository, reader)
