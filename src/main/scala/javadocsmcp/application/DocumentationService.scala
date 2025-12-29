// PURPOSE: Application service orchestrating documentation retrieval
// PURPOSE: Coordinates artifact fetching, JAR reading, and HTML extraction

package javadocsmcp.application

import javadocsmcp.domain.{ArtifactCoordinates, ClassName, Documentation, DocumentationError}
import javadocsmcp.domain.ports.{ArtifactRepository, JarContentReader}

class DocumentationService(
  repository: ArtifactRepository,
  reader: JarContentReader
):
  def getDocumentation(coordinatesStr: String, classNameStr: String): Either[DocumentationError, Documentation] = {
    for {
      coords <- ArtifactCoordinates.parse(coordinatesStr)
      className <- ClassName.parse(classNameStr)
      jarFile <- repository.fetchJavadocJar(coords)
      htmlContent <- reader.readEntry(jarFile, className.toHtmlPath)
    } yield Documentation(htmlContent, className.fullyQualifiedName, coords)
  }

object DocumentationService:
  def apply(repository: ArtifactRepository, reader: JarContentReader): DocumentationService =
    new DocumentationService(repository, reader)
