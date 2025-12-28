// PURPOSE: Value object for fully qualified Java class names
// PURPOSE: Converts class names to javadoc HTML file paths, handling inner classes

package javadocsmcp.domain

case class ClassName(fullyQualifiedName: String) {
  def toHtmlPath: String = {
    // Strip inner class suffix (e.g., Logger$Factory -> Logger)
    val outerClass = fullyQualifiedName.split('$').head
    // Convert package separators to path separators
    val path = outerClass.replace('.', '/')
    // Add HTML extension
    s"$path.html"
  }
}

object ClassName {
  def parse(name: String): Either[DocumentationError, ClassName] = {
    val trimmed = name.trim

    if (trimmed.isEmpty) {
      return Left(InvalidClassName(name))
    }

    Right(ClassName(trimmed))
  }
}
