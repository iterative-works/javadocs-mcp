// PURPOSE: Application service orchestrating documentation retrieval
// PURPOSE: Coordinates artifact fetching, JAR reading, and HTML extraction

package javadocsmcp.application

import javadocsmcp.domain.{ArtifactCoordinates, ClassName, Documentation, DocumentationError}
import javadocsmcp.infrastructure.{CoursierArtifactRepository, JarFileReader}

class DocumentationService(
  repository: CoursierArtifactRepository,
  reader: JarFileReader
) {
  def getDocumentation(coordinatesStr: String, classNameStr: String): Either[DocumentationError, Documentation] = {
    for {
      coords <- ArtifactCoordinates.parse(coordinatesStr)
      className <- ClassName.parse(classNameStr)
      jarFile <- repository.fetchJavadocJar(coords)
      htmlContent <- reader.readEntry(jarFile, className.toHtmlPath)
    } yield Documentation(htmlContent, className.fullyQualifiedName, coords)
  }
}

object DocumentationService {
  def apply(repository: CoursierArtifactRepository, reader: JarFileReader): DocumentationService =
    new DocumentationService(repository, reader)
}
