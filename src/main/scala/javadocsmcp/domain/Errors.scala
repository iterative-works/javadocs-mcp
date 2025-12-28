// PURPOSE: Domain error types for documentation retrieval failures
// PURPOSE: Provides type-safe error handling for artifact and class resolution

package javadocsmcp.domain

sealed trait DocumentationError {
  def message: String
}

case class ArtifactNotFound(coordinates: String) extends DocumentationError {
  def message: String = s"Artifact not found: $coordinates"
}

case class ClassNotFound(className: String) extends DocumentationError {
  def message: String = s"Class not found in javadoc: $className"
}

case class InvalidCoordinates(input: String) extends DocumentationError {
  def message: String = s"Invalid Maven coordinates format: $input. Expected format: groupId:artifactId:version"
}

case class InvalidClassName(input: String) extends DocumentationError {
  def message: String = s"Invalid class name: $input"
}
