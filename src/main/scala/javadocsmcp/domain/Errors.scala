// PURPOSE: Domain error types for documentation retrieval failures
// PURPOSE: Provides type-safe error handling for artifact and class resolution

package javadocsmcp.domain

enum DocumentationError:
  case ArtifactNotFound(coordinates: String)
  case JavadocNotAvailable(coordinates: String)
  case SourcesNotAvailable(coordinates: String)
  case ClassNotFound(className: String)
  case InvalidCoordinates(input: String)
  case InvalidClassName(input: String)

  def message: String = this match
    case ArtifactNotFound(coordinates) =>
      s"""Artifact not found: $coordinates
         |
         |Please check:
         |- Spelling of groupId, artifactId, and version
         |- Artifact exists on Maven Central: https://search.maven.org/
         |- Version number is correct""".stripMargin

    case JavadocNotAvailable(coordinates) =>
      s"""Javadoc JAR not available for: $coordinates
         |
         |Some libraries don't publish javadoc to Maven Central.
         |Try using get_source instead to view the source code.""".stripMargin

    case SourcesNotAvailable(coordinates) =>
      s"""Sources JAR not available for: $coordinates
         |
         |Some libraries don't publish sources to Maven Central.
         |Try using get_documentation instead to view the API documentation.""".stripMargin

    case ClassNotFound(className) =>
      s"""Class not found in JAR: $className
         |
         |Please check:
         |- Class name spelling and capitalization (case-sensitive)
         |- Class is part of this artifact (not a transitive dependency)""".stripMargin

    case InvalidCoordinates(input) =>
      s"""Invalid Maven coordinates format: $input
         |
         |Expected formats:
         |- Java: groupId:artifactId:version (e.g., org.slf4j:slf4j-api:2.0.9)
         |- Scala: groupId::artifactId:version (e.g., org.typelevel::cats-effect:3.5.4)""".stripMargin

    case InvalidClassName(input) =>
      s"""Invalid class name: $input
         |
         |Expected format: fully qualified class name (e.g., org.slf4j.Logger)""".stripMargin
