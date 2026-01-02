// PURPOSE: Value object for fully qualified Java class names
// PURPOSE: Converts class names to javadoc HTML file paths, handling inner classes

package javadocsmcp.domain

import DocumentationError.*

case class ClassName(fullyQualifiedName: String) {
  private def toPath(extension: String): String = {
    // Strip inner class suffix (e.g., Logger$Factory -> Logger)
    val outerClass = fullyQualifiedName.split('$').head
    // Convert package separators to path separators
    val path = outerClass.replace('.', '/')
    // Add extension
    s"$path$extension"
  }

  def toHtmlPath: String = toPath(".html")

  def toSourcePath: String = toPath(".java")

  def toScalaSourcePath: String = toPath(".scala")
}

object ClassName {
  def parse(name: String): Either[DocumentationError, ClassName] =
    val trimmed = name.trim
    if trimmed.isEmpty then
      Left(InvalidClassName(name))
    else
      Right(ClassName(trimmed))
}
