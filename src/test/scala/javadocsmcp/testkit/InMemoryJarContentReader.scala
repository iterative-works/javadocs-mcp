// PURPOSE: In-memory implementation of JarContentReader for unit testing
// PURPOSE: Allows tests to control JAR content without file I/O

package javadocsmcp.testkit

import javadocsmcp.domain.DocumentationError
import javadocsmcp.domain.ports.JarContentReader
import java.io.File

class InMemoryJarContentReader(
  entries: Map[(File, String), String] = Map.empty
) extends JarContentReader:

  def readEntry(source: File, path: String): Either[DocumentationError, String] =
    entries.get((source, path)) match
      case Some(content) => Right(content)
      case None => Left(DocumentationError.ClassNotFound(path))

object InMemoryJarContentReader:
  def empty: InMemoryJarContentReader = new InMemoryJarContentReader()

  def withEntries(entries: ((File, String), String)*): InMemoryJarContentReader =
    new InMemoryJarContentReader(entries.toMap)
