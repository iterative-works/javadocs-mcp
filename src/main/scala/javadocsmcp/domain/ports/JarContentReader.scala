// PURPOSE: Port trait defining the contract for reading content from JAR files
// PURPOSE: Abstracts JAR content reading to allow different implementations

package javadocsmcp.domain.ports

import javadocsmcp.domain.DocumentationError
import java.io.File

trait JarContentReader:
  def readEntry(source: File, path: String): Either[DocumentationError, String]
