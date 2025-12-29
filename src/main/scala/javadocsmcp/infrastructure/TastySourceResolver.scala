// PURPOSE: Resolves Scala source file paths using TASTy metadata
// PURPOSE: Extracts source file location from TASTy-encoded class information

package javadocsmcp.infrastructure

import javadocsmcp.domain.{ArtifactCoordinates, DocumentationError}
import javadocsmcp.domain.ports.{ArtifactRepository, SourcePathResolver}
import tastyquery.jdk.ClasspathLoaders
import tastyquery.Contexts.*
import tastyquery.Symbols.*
import tastyquery.Names.*
import java.io.File
import scala.util.{Try, Success, Failure}

class TastySourceResolver(repository: ArtifactRepository) extends SourcePathResolver:

  def resolveSourcePath(
    coordinates: String,
    className: String,
    scalaVersion: String
  ): Either[DocumentationError, String] =
    for
      coords <- ArtifactCoordinates.parse(coordinates)
      mainJar <- repository.fetchMainJar(coords, scalaVersion)
      sourcePath <- lookupSourcePath(mainJar, className)
    yield sourcePath

  private def lookupSourcePath(jarPath: File, className: String): Either[DocumentationError, String] =
    Try {
      // Strip inner class suffix (e.g., cats.effect.IO$Pure -> cats.effect.IO)
      val outerClassName = className.split('$').head

      // Split into package and simple name
      val lastDot = outerClassName.lastIndexOf('.')
      val (packageName, simpleName) = if lastDot >= 0 then
        (outerClassName.substring(0, lastDot), outerClassName.substring(lastDot + 1))
      else
        ("", outerClassName)

      // Load classpath from JAR
      val classpath = ClasspathLoaders.read(List(jarPath.toPath))
      given ctx: Context = Context.initialize(classpath)

      // Find the package
      val pkg = ctx.findPackage(packageName)

      // Find the class symbol
      val classSymbol = pkg.getDecl(typeName(simpleName))

      classSymbol match
        case Some(sym: ClassSymbol) =>
          // Get the source file from the symbol's tree
          sym.tree match
            case Some(tree) =>
              val sourceFile = tree.pos.sourceFile
              val fullPath = sourceFile.path
              if fullPath.isEmpty then
                throw new RuntimeException(s"No source path for class $className")
              TastySourceResolver.extractPackageRelativePath(fullPath, packageName)
            case None =>
              throw new RuntimeException(s"No tree available for class $className")
        case Some(other) =>
          throw new RuntimeException(s"$className is not a class symbol: ${other.getClass.getSimpleName}")
        case None =>
          throw new RuntimeException(s"Class $className not found in JAR")
    } match
      case Success(path) => Right(path)
      case Failure(_) => Left(DocumentationError.ClassNotFound(className))

object TastySourceResolver:

  def apply(repository: ArtifactRepository): TastySourceResolver =
    new TastySourceResolver(repository)

  /**
   * Extracts a package-relative path from a project-relative path.
   *
   * TASTy paths are project-relative (e.g., "core/shared/src/main/scala/cats/effect/IO.scala")
   * but sources JARs use package-relative paths (e.g., "cats/effect/IO.scala").
   *
   * This finds the package path within the full path and returns everything from there.
   */
  def extractPackageRelativePath(tastyPath: String, packageName: String): String =
    if packageName.isEmpty then
      // Empty package - just return the filename
      tastyPath.split('/').last
    else
      val packagePath = packageName.replace('.', '/') + "/"
      val idx = tastyPath.indexOf(packagePath)
      if idx >= 0 then
        tastyPath.substring(idx)
      else
        // Fallback to filename only if package path not found
        tastyPath.split('/').last
