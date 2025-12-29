# Refactoring R1: TASTy-based Scala source lookup

**Phase:** 4
**Created:** 2025-12-29
**Status:** Planned

## Decision Summary

The current filename-convention approach (`cats.effect.IO` → `cats/effect/IO.scala`) only works by luck when library authors happen to name files after their main class. In Scala, a file `Utils.scala` can contain `class Foo`, `trait Bar`, `object Baz` - there's no compiler-enforced naming convention like Java's "public class must match filename" rule.

TASTy files (Scala 3's typed AST format) contain the authoritative source file path for every symbol. We use tasty-query to look up the class symbol and extract its source file location.

## Current State

**SourceCodeService.scala** - tries extension-based lookup:
```scala
if coordinates.scalaArtifact then
  jarReader.readEntry(jarPath, className.toScalaSourcePath)  // cats/effect/IO.scala
    .orElse(jarReader.readEntry(jarPath, className.toSourcePath))  // fallback: cats/effect/IO.java
else
  jarReader.readEntry(jarPath, className.toSourcePath)
```

**Problem:** This fails when the source file isn't named after the class (common in Scala).

## Target State

**New TastySourceResolver** - looks up source path from TASTy:
```scala
trait SourcePathResolver:
  def resolveSourcePath(
    mainJar: Path,
    className: ClassName,
    packageName: String
  ): Either[DocumentationError, String]

class TastySourceResolver extends SourcePathResolver:
  def resolveSourcePath(...): Either[...] =
    // 1. Load TASTy from main JAR
    // 2. Look up class symbol
    // 3. Extract sourceFile.path
    // 4. Map to package-relative path
```

**Updated SourceCodeService** - uses TASTy resolver for Scala 3:
```scala
if coordinates.scalaArtifact then
  for
    mainJar <- repository.fetchMainJar(coordinates, scalaVersion)
    sourcePath <- sourcePathResolver.resolveSourcePath(mainJar, className, packageName)
    sourcesJar <- repository.fetchSourcesJar(coordinates, scalaVersion)
    content <- jarReader.readEntry(sourcesJar, sourcePath)
  yield SourceCode(content, className.value)
else
  // Java path unchanged
```

## Constraints

- PRESERVE: Existing tests must pass
- PRESERVE: Java source lookup unchanged
- PRESERVE: `ClassName.toScalaSourcePath()` as fallback for Scala 2 or TASTy failures
- DO NOT TOUCH: Domain entities (ArtifactCoordinates, ClassName, SourceCode)
- DO NOT TOUCH: Presentation layer (tool definitions, MCP endpoint)

## Tasks

### Tests First (TDD)

- [ ] [test] Add unit test: `TastySourceResolver` returns correct path for known class
- [ ] [test] Add unit test: `TastySourceResolver` extracts package-relative path from project-relative
- [ ] [test] Add unit test: `TastySourceResolver` returns error for non-existent class
- [ ] [test] Add integration test: Resolve source path for `cats.effect.IO` from real JAR
- [ ] [test] Add integration test: Resolve source path for `zio.ZIO` from real JAR

### Implementation

- [ ] [impl] Create `SourcePathResolver` port trait in domain/ports
- [ ] [impl] Implement `TastySourceResolver` in infrastructure layer
- [ ] [impl] Add `fetchMainJar()` method to `ArtifactRepository` port
- [ ] [impl] Implement `fetchMainJar()` in `CoursierArtifactRepository`
- [ ] [impl] Update `SourceCodeService` to use `SourcePathResolver` for Scala artifacts
- [ ] [impl] Add fallback to filename convention if TASTy lookup fails

### Integration

- [ ] [integ] Run all tests and verify no regressions
- [ ] [integ] Test with cats-effect, zio, and other Scala 3 libraries

### Verification

- [ ] [verify] Existing E2E tests still pass
- [ ] [verify] New TASTy-based lookup works for cats.effect.IO
- [ ] [verify] Fallback works for Scala 2 artifacts

## Verification Checklist

- [ ] All 95+ existing tests pass
- [ ] New TastySourceResolver tests pass
- [ ] TASTy lookup correctly resolves `cats.effect.IO` to `cats/effect/IO.scala`
- [ ] TASTy lookup correctly resolves classes not named after their file
- [ ] Fallback to filename convention works when TASTy unavailable
- [ ] No performance regression (TASTy loading should be fast)

## Technical Notes

**Path mapping algorithm:**

TASTy paths are project-relative (e.g., `core/shared/src/main/scala/cats/effect/IO.scala`)
Sources JAR paths are package-relative (e.g., `cats/effect/IO.scala`)

```scala
def extractPackageRelativePath(tastyPath: String, packageName: String): String =
  val packagePath = packageName.replace('.', '/') + "/"
  val idx = tastyPath.indexOf(packagePath)
  if idx >= 0 then tastyPath.substring(idx)
  else tastyPath.split('/').last  // fallback to filename only
```

**tasty-query usage:**

```scala
import tastyquery.jdk.ClasspathLoaders
import tastyquery.Contexts.*
import tastyquery.Symbols.*
import tastyquery.Trees.*

val classpath = ClasspathLoaders.read(List(jarPath))
given ctx: Context = Context.initialize(classpath)

val pkg = ctx.findPackage(packageName)
val classSymbol = pkg.getDecl(typeName(simpleClassName))

classSymbol.flatMap(_.tree) match
  case Some(classDef: ClassDef) =>
    val sourcePath = classDef.pos.sourceFile.path
    Right(extractPackageRelativePath(sourcePath, packageName))
  case _ =>
    Left(ClassNotFound(...))
```

## Estimated Effort

- Tests: 1-2 hours
- Implementation: 2-3 hours
- Integration: 30 min
- Total: 4-6 hours
