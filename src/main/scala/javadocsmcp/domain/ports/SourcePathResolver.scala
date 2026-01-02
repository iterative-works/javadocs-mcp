// PURPOSE: Port trait for resolving source file paths from compiled artifacts
// PURPOSE: Abstracts source path resolution to allow different implementations

package javadocsmcp.domain.ports

import javadocsmcp.domain.DocumentationError

trait SourcePathResolver:
  /**
   * Resolves the source file path for a given class.
   *
   * @param coordinates Maven coordinates (e.g., "org.typelevel::cats-effect:3.5.4")
   * @param className Fully qualified class name (e.g., "cats.effect.IO")
   * @param scalaVersion Scala version for cross-built artifacts (e.g., "3")
   * @return Either an error or the package-relative source path (e.g., "cats/effect/IO.scala")
   */
  def resolveSourcePath(
    coordinates: String,
    className: String,
    scalaVersion: String
  ): Either[DocumentationError, String]
