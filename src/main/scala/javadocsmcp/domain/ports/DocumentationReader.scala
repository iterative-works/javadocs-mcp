// PURPOSE: Port trait defining the contract for reading documentation from sources
// PURPOSE: Abstracts documentation reading to allow different implementations

package javadocsmcp.domain.ports

import javadocsmcp.domain.DocumentationError
import java.io.File

trait DocumentationReader:
  def readEntry(source: File, path: String): Either[DocumentationError, String]
