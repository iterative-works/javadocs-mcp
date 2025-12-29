// PURPOSE: In-memory implementation of SourcePathResolver for unit testing
// PURPOSE: Allows tests to control source path resolution without TASTy analysis

package javadocsmcp.testkit

import javadocsmcp.domain.DocumentationError
import javadocsmcp.domain.ports.SourcePathResolver

class InMemorySourcePathResolver(
  paths: Map[(String, String), String] = Map.empty
) extends SourcePathResolver:

  def resolveSourcePath(
    coordinates: String,
    className: String,
    scalaVersion: String
  ): Either[DocumentationError, String] =
    paths.get((coordinates, className)) match
      case Some(path) => Right(path)
      case None => Left(DocumentationError.ClassNotFound(className))

object InMemorySourcePathResolver:
  def empty: InMemorySourcePathResolver = new InMemorySourcePathResolver()

  def withPaths(paths: ((String, String), String)*): InMemorySourcePathResolver =
    new InMemorySourcePathResolver(paths.toMap)

  def always(path: String): InMemorySourcePathResolver =
    new InMemorySourcePathResolver() {
      override def resolveSourcePath(
        coordinates: String,
        className: String,
        scalaVersion: String
      ): Either[DocumentationError, String] = Right(path)
    }

  def alwaysFail: InMemorySourcePathResolver =
    new InMemorySourcePathResolver() {
      override def resolveSourcePath(
        coordinates: String,
        className: String,
        scalaVersion: String
      ): Either[DocumentationError, String] =
        Left(DocumentationError.ClassNotFound(className))
    }
