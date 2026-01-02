// PURPOSE: Application service orchestrating documentation retrieval
// PURPOSE: Coordinates artifact fetching, JAR reading, and HTML extraction

package javadocsmcp.application

import javadocsmcp.domain.{ArtifactCoordinates, ClassName, Documentation, DocumentationError}
import javadocsmcp.domain.ports.{ArtifactRepository, JarContentReader}

class DocumentationService(
  repository: ArtifactRepository,
  reader: JarContentReader
):
  def getDocumentation(
    coordinatesStr: String,
    classNameStr: String,
    scalaVersion: Option[String] = None
  ): Either[DocumentationError, Documentation] = {
    val effectiveScalaVersion = scalaVersion.getOrElse("3")
    for {
      coords <- ArtifactCoordinates.parse(coordinatesStr)
      className <- ClassName.parse(classNameStr)
      jarFile <- repository.fetchJavadocJar(coords, effectiveScalaVersion)
      htmlContent <- reader.readEntry(jarFile, className.toHtmlPath)
    } yield Documentation(htmlContent, className.fullyQualifiedName, coords)
  }

object DocumentationService:
  def apply(repository: ArtifactRepository, reader: JarContentReader): DocumentationService =
    new DocumentationService(repository, reader)
