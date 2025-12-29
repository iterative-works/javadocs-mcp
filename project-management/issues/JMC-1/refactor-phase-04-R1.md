# Refactoring R1: TASTy-based Scala source lookup

**Phase:** 4
**Created:** 2025-12-29
**Status:** Complete

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

- [x] [test] Add unit test: `TastySourceResolver` returns correct path for known class
- [x] [test] Add unit test: `TastySourceResolver` extracts package-relative path from project-relative
- [x] [test] Add unit test: `TastySourceResolver` returns error for non-existent class
- [x] [test] Add integration test: Resolve source path for `cats.effect.IO` from real JAR
- [x] [test] Add integration test: Resolve source path for `zio.ZIO` from real JAR

### Implementation

- [x] [impl] Create `SourcePathResolver` port trait in domain/ports
- [x] [impl] Implement `TastySourceResolver` in infrastructure layer
- [x] [impl] Add `fetchMainJar()` method to `ArtifactRepository` port
- [x] [impl] Implement `fetchMainJar()` in `CoursierArtifactRepository`
- [x] [impl] Update `SourceCodeService` to use `SourcePathResolver` for Scala artifacts
- [x] [impl] Add fallback to filename convention if TASTy lookup fails

### Integration

- [x] [integ] Run all tests and verify no regressions
- [x] [integ] Test with cats-effect, zio, and other Scala 3 libraries

### Verification

- [x] [verify] Existing E2E tests still pass
- [x] [verify] New TASTy-based lookup works for cats.effect.IO
- [x] [verify] Fallback works for Scala 2 artifacts

## Verification Checklist

- [x] All 95+ existing tests pass (104 tests now)
- [x] New TastySourceResolver tests pass
- [x] TASTy lookup correctly resolves `cats.effect.IO` to `cats/effect/IO.scala`
- [x] TASTy lookup correctly resolves classes not named after their file
- [x] Fallback to filename convention works when TASTy unavailable
- [x] No performance regression (TASTy loading should be fast)

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

## Implementation Summary

### Files Created
- `src/main/scala/javadocsmcp/domain/ports/SourcePathResolver.scala` - Port trait
- `src/main/scala/javadocsmcp/infrastructure/TastySourceResolver.scala` - TASTy-based implementation
- `src/test/scala/javadocsmcp/infrastructure/TastySourceResolverTest.scala` - Unit tests
- `src/test/scala/javadocsmcp/infrastructure/TastySourceResolverIntegrationTest.scala` - Integration tests
- `src/test/scala/javadocsmcp/testkit/InMemorySourcePathResolver.scala` - Test double

### Files Modified
- `src/main/scala/javadocsmcp/domain/ports/ArtifactRepository.scala` - Added `fetchMainJar()`
- `src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala` - Implemented `fetchMainJar()`
- `src/main/scala/javadocsmcp/application/SourceCodeService.scala` - Uses SourcePathResolver with fallback
- `src/main/scala/javadocsmcp/Main.scala` - Wires up TastySourceResolver
- `src/test/scala/javadocsmcp/testkit/InMemoryArtifactRepository.scala` - Added `fetchMainJar()` support
- `src/test/scala/javadocsmcp/integration/EndToEndTest.scala` - Uses TastySourceResolver

## Estimated Effort

- Tests: 1-2 hours
- Implementation: 2-3 hours
- Integration: 30 min
- Total: 4-6 hours
