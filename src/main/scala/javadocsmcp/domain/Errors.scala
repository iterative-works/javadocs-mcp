// PURPOSE: Domain error types for documentation retrieval failures
// PURPOSE: Provides type-safe error handling for artifact and class resolution

package javadocsmcp.domain

enum DocumentationError:
  case ArtifactNotFound(coordinates: String)
  case ClassNotFound(className: String)
  case InvalidCoordinates(input: String)
  case InvalidClassName(input: String)

  def message: String = this match
    case ArtifactNotFound(coordinates) => s"Artifact not found: $coordinates"
    case ClassNotFound(className) => s"Class not found in javadoc: $className"
    case InvalidCoordinates(input) => s"Invalid Maven coordinates format: $input. Expected format: groupId:artifactId:version"
    case InvalidClassName(input) => s"Invalid class name: $input"
