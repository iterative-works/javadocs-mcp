// PURPOSE: In-memory implementation of DocumentationReader for unit testing
// PURPOSE: Allows tests to control documentation content without file I/O

package javadocsmcp.testkit

import javadocsmcp.domain.DocumentationError
import javadocsmcp.domain.ports.DocumentationReader
import java.io.File

class InMemoryDocumentationReader(
  entries: Map[(File, String), String] = Map.empty
) extends DocumentationReader:

  def readEntry(source: File, path: String): Either[DocumentationError, String] =
    entries.get((source, path)) match
      case Some(content) => Right(content)
      case None => Left(DocumentationError.ClassNotFound(path))

object InMemoryDocumentationReader:
  def empty: InMemoryDocumentationReader = new InMemoryDocumentationReader()

  def withEntries(entries: ((File, String), String)*): InMemoryDocumentationReader =
    new InMemoryDocumentationReader(entries.toMap)
