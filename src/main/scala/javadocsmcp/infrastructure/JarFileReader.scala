// PURPOSE: Reads HTML files from JAR archives
// PURPOSE: Extracts javadoc HTML content from downloaded -javadoc.jar artifacts

package javadocsmcp.infrastructure

import javadocsmcp.domain.DocumentationError
import DocumentationError.*
import java.io.File
import java.util.jar.JarFile
import scala.io.Source
import scala.util.{Success, Failure, Using}

class JarFileReader {
  def readEntry(jarFile: File, htmlPath: String): Either[DocumentationError, String] = {
    Using(new JarFile(jarFile)) { jar =>
      Option(jar.getEntry(htmlPath)) match {
        case Some(entry) =>
          val inputStream = jar.getInputStream(entry)
          val html = Source.fromInputStream(inputStream, "UTF-8").mkString
          inputStream.close()
          Right(html)
        case None =>
          Left(ClassNotFound(htmlPath))
      }
    } match {
      case Success(result) => result
      case Failure(exception) =>
        Left(ClassNotFound(htmlPath))
    }
  }
}

object JarFileReader {
  def apply(): JarFileReader = new JarFileReader()
}
