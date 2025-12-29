# Refactoring R1: Extract Port Traits for Hexagonal Architecture

**Phase:** 1
**Created:** 2025-12-29
**Status:** Complete

## Decision Summary

Code review identified that `DocumentationService` violates the Dependency Inversion Principle by depending directly on concrete infrastructure classes (`CoursierArtifactRepository`, `JarFileReader`). This prevents proper unit testing and breaks hexagonal architecture principles.

We will extract port traits in `domain/ports/` that define the contracts the application layer needs, then update the application and infrastructure layers to use these abstractions.

## Current State

**File: `src/main/scala/javadocsmcp/application/DocumentationService.scala`**
- Constructor takes concrete classes: `CoursierArtifactRepository`, `JarFileReader`
- Imports directly from infrastructure package
- Cannot be unit tested with in-memory implementations

**File: `src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala`**
- Concrete class with `fetchJavadocJar(coords: ArtifactCoordinates): Either[DocumentationError, File]`
- No interface extracted

**File: `src/main/scala/javadocsmcp/infrastructure/JarFileReader.scala`**
- Concrete class with `readEntry(jarFile: File, path: String): Either[DocumentationError, String]`
- No interface extracted

**File: `src/main/scala/javadocsmcp/Main.scala`**
- Wires concrete implementations directly

**Tests:**
- All tests use real implementations (integration tests)
- No unit tests with in-memory implementations possible

## Target State

**New: `src/main/scala/javadocsmcp/domain/ports/ArtifactRepository.scala`**
```scala
trait ArtifactRepository:
  def fetchJavadocJar(coords: ArtifactCoordinates): Either[DocumentationError, File]
```

**New: `src/main/scala/javadocsmcp/domain/ports/DocumentationReader.scala`**
```scala
trait DocumentationReader:
  def readEntry(source: File, path: String): Either[DocumentationError, String]
```

**Updated: `DocumentationService.scala`**
- Constructor takes `ArtifactRepository` and `DocumentationReader` traits
- Imports from `domain.ports`, not infrastructure

**Updated: Infrastructure classes**
- `CoursierArtifactRepository extends ArtifactRepository`
- `JarFileReader extends DocumentationReader`

**Updated: `Main.scala`**
- Unchanged behavior, just wires implementations that now implement traits

**New: Test implementations**
- `InMemoryArtifactRepository` for unit testing
- `InMemoryDocumentationReader` for unit testing

**Updated: Tests**
- `DocumentationServiceTest` uses in-memory implementations (true unit tests)
- Keep ONE integration test file for real Maven Central verification

## Constraints

- PRESERVE: All existing behavior must remain unchanged
- PRESERVE: All existing tests must continue to pass
- PRESERVE: Method signatures on infrastructure classes (just add `extends Trait`)
- DO NOT TOUCH: Domain logic (`ArtifactCoordinates`, `ClassName`, etc.)
- DO NOT TOUCH: Presentation layer (`McpServer`, `ToolDefinitions`)
- DO NOT TOUCH: Error handling logic

## Step 1: Extract Traits and Update Wiring

- [x] [impl] Create `domain/ports/` directory
- [x] [impl] Create `domain/ports/ArtifactRepository.scala` trait
- [x] [impl] Create `domain/ports/DocumentationReader.scala` trait
- [x] [impl] Update `CoursierArtifactRepository` to extend `ArtifactRepository`
- [x] [impl] Update `JarFileReader` to extend `DocumentationReader`
- [x] [impl] Update `DocumentationService` to depend on trait types
- [x] [impl] Update `Main.scala` imports (if needed) - No changes needed
- [x] [verify] Run all existing tests, ensure they pass
- [x] [commit] Commit: "refactor(arch): extract port traits for hexagonal architecture"

## Step 2: Create Test Implementations and Refactor Tests

- [x] [impl] Create `test/.../testkit/InMemoryArtifactRepository.scala`
- [x] [impl] Create `test/.../testkit/InMemoryDocumentationReader.scala`
- [x] [test] Create fresh instances in each test (remove shared mutable state)
- [x] [test] Refactor `DocumentationServiceTest` to use in-memory implementations
- [x] [test] Rename current integration tests or move to integration package
- [x] [test] Keep ONE integration test for real Maven Central verification
- [x] [verify] Run all tests, ensure behavior unchanged
- [x] [commit] Commit: "test(arch): add in-memory implementations for unit testing"

## Verification

- [x] All existing tests pass unchanged
- [x] `DocumentationService` imports only from `domain.ports`, not `infrastructure`
- [x] Infrastructure classes implement port traits
- [x] New unit tests use in-memory implementations
- [x] At least one integration test verifies real Maven Central works
- [x] No regressions in functionality
