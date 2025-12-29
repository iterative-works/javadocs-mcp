# Implementation Log: MVP: Implement core MCP server with documentation and source tools

**Issue:** JMC-1

This log tracks the evolution of implementation across phases.

---

## Phase 1: Fetch Javadoc HTML for Java class (2025-12-28)

**What was built:**

- **Domain Layer:**
  - `ArtifactCoordinates.scala` - Value object for Maven coordinates with validation
  - `ClassName.scala` - Value object converting class names to HTML paths
  - `Documentation.scala` - Entity containing HTML content and metadata
  - `Errors.scala` - Scala 3 enum for type-safe error handling

- **Application Layer:**
  - `DocumentationService.scala` - Orchestrates fetching documentation from coordinates

- **Infrastructure Layer:**
  - `CoursierArtifactRepository.scala` - Fetches -javadoc.jar from Maven Central
  - `JarFileReader.scala` - Extracts HTML files from JAR archives

- **Presentation Layer:**
  - `ToolDefinitions.scala` - Chimp MCP tool definition for `get_documentation`
  - `McpServer.scala` - HTTP server using Tapir Netty
  - `Main.scala` - Application entry point

**Decisions made:**

- Used Chimp MCP library (`com.softwaremill.chimp::core:0.1.6`) for type-safe tool definitions
- Coursier handles artifact resolution and caching in `~/.cache/coursier`
- Inner class names (e.g., `Logger$Factory`) map to outer class HTML (`Logger.html`)
- Used Scala 3 enum for `DocumentationError` instead of sealed trait hierarchy
- Expression-based control flow (no `return` statements) per Scala 3 idioms

**Patterns applied:**

- **DDD Layered Architecture:** domain/application/infrastructure/presentation
- **Functional Core, Imperative Shell:** Pure domain logic, I/O at edges
- **Smart Constructors:** `parse()` methods return `Either[Error, T]`
- **Value Objects:** Immutable case classes for domain concepts

**Testing:**

- Unit tests: 9 tests (domain logic validation)
- Integration tests: 11 tests (real Maven Central artifacts)
- E2E tests: 0 (deferred - MCP protocol testing requires client)

**Code review:**

- Iterations: 1
- Review file: review-phase-01-20251228.md
- Fixed: Removed `return` statements, converted to Scala 3 enum, renamed variable
- Deferred: Port/adapter abstractions, effect types (ZIO), test trait extraction

**For next phases:**

- Available utilities:
  - `CoursierArtifactRepository.fetchJavadocJar()` - reusable for sources
  - `JarFileReader.readEntry()` - works for any JAR content
  - `ArtifactCoordinates.parse()` - shared validation
- Extension points:
  - Add `get_source` tool in `ToolDefinitions`
  - Extend `JarFileReader` for different file types
  - Add caching layer around `DocumentationService`
- Notes:
  - Scala coordinates (`::`) not yet supported - Phase 3
  - No caching yet - Phase 7

**Files changed:**

```
M  README.md
M  project.scala
A  src/main/scala/javadocsmcp/Main.scala
A  src/main/scala/javadocsmcp/application/DocumentationService.scala
A  src/main/scala/javadocsmcp/domain/ArtifactCoordinates.scala
A  src/main/scala/javadocsmcp/domain/ClassName.scala
A  src/main/scala/javadocsmcp/domain/Documentation.scala
A  src/main/scala/javadocsmcp/domain/Errors.scala
A  src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala
A  src/main/scala/javadocsmcp/infrastructure/JarFileReader.scala
A  src/main/scala/javadocsmcp/presentation/McpServer.scala
A  src/main/scala/javadocsmcp/presentation/ToolDefinitions.scala
A  src/test/scala/javadocsmcp/application/DocumentationServiceTest.scala
A  src/test/scala/javadocsmcp/domain/ArtifactCoordinatesTest.scala
A  src/test/scala/javadocsmcp/domain/ClassNameTest.scala
A  src/test/scala/javadocsmcp/infrastructure/CoursierArtifactRepositoryTest.scala
A  src/test/scala/javadocsmcp/infrastructure/JarFileReaderTest.scala
```

---

## Refactoring R1: Extract Port Traits for Hexagonal Architecture (2025-12-29)

**What was refactored:**

- **Domain Ports:**
  - Created `domain/ports/ArtifactRepository.scala` trait
  - Created `domain/ports/DocumentationReader.scala` trait

- **Infrastructure Updates:**
  - `CoursierArtifactRepository` now extends `ArtifactRepository`
  - `JarFileReader` now extends `DocumentationReader`

- **Application Updates:**
  - `DocumentationService` now depends on trait types, not concrete implementations
  - Imports from `domain.ports`, not `infrastructure`

- **Test Improvements:**
  - Created `testkit/InMemoryArtifactRepository.scala` for unit testing
  - Created `testkit/InMemoryDocumentationReader.scala` for unit testing
  - Refactored `DocumentationServiceTest` to use in-memory implementations (true unit tests)
  - Created `DocumentationServiceIntegrationTest` for real Maven Central verification

**Why:**

Code review identified that `DocumentationService` violated the Dependency Inversion Principle by depending directly on concrete infrastructure classes. This prevented proper unit testing and broke hexagonal architecture principles.

**Impact:**

- Unit tests now run without network calls or file I/O
- `DocumentationService` can be tested in isolation
- Infrastructure implementations can be swapped without changing application layer
- One integration test still verifies real Maven Central works

**Testing:**

- Unit tests: 5 tests (with in-memory implementations)
- Integration tests: 1 test (real Maven Central)
- All existing tests continue to pass

**Files changed:**

```
A  src/main/scala/javadocsmcp/domain/ports/ArtifactRepository.scala
A  src/main/scala/javadocsmcp/domain/ports/DocumentationReader.scala
M  src/main/scala/javadocsmcp/application/DocumentationService.scala
M  src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala
M  src/main/scala/javadocsmcp/infrastructure/JarFileReader.scala
A  src/test/scala/javadocsmcp/testkit/InMemoryArtifactRepository.scala
A  src/test/scala/javadocsmcp/testkit/InMemoryDocumentationReader.scala
M  src/test/scala/javadocsmcp/application/DocumentationServiceTest.scala
A  src/test/scala/javadocsmcp/application/DocumentationServiceIntegrationTest.scala
```

---

## Phase 1 Completion: E2E Tests and Polish (2025-12-29)

**What was added:**

- **E2E Test Suite:**
  - `EndToEndTest.scala` - Complete HTTP-level tests for MCP protocol
  - Server lifecycle management with `ServerHandle` case class
  - HTTP client integration using sttp
  - JSON-RPC validation using circe

- **Server Testability:**
  - `McpServer.startAsync()` - Non-blocking server start returning handle
  - `ServerHandle.stop()` - Clean server shutdown for tests
  - Background thread with daemon flag for proper cleanup

**Test Coverage:**

- `tools/list` endpoint - Verifies tool discovery
- Happy path - Fetches `org.slf4j.Logger` documentation
- Error case - Non-existent artifact returns `isError: true`
- Error case - Non-existent class returns `isError: true`
- Response time assertion - Under 5 seconds

**Dependencies Added:**

- `sttp.client3::core:3.9.0` - HTTP client for E2E tests
- `io.circe::circe-parser:0.14.6` - JSON parsing for response validation

**Code Review:**

- Iterations: 1
- Review file: review-phase-01-e2e-20251229.md
- Result: PASSED - No critical issues
- Minor warnings: Code duplication in McpServer (acceptable), Thread.sleep in tests (pragmatic)

**Verification:**

- All 25 tests passing
- 6/6 scenarios verified
- Response time within requirements

**Final Testing Summary:**

- Unit tests: 9 tests (domain logic)
- Integration tests: 12 tests (Coursier + JAR reading + service)
- E2E tests: 4 tests (full HTTP MCP flow)
- Total: 25 tests

**Files changed:**

```
M  project.scala
M  src/main/scala/javadocsmcp/presentation/McpServer.scala
A  src/test/scala/javadocsmcp/integration/EndToEndTest.scala
```

---

## Phase 2: Fetch source code for Java class (2025-12-29)

**What was built:**

- **Domain Layer:**
  - `SourceCode.scala` - Entity representing Java source code with metadata
  - `ClassName.scala` - Added `toSourcePath()` method for `.java` file paths
  - `Errors.scala` - Added `SourcesNotAvailable` error type

- **Application Layer:**
  - `SourceCodeService.scala` - Orchestrates source code fetching from coordinates

- **Infrastructure Layer:**
  - `CoursierArtifactRepository.scala` - Added `fetchSourcesJar()` using `Classifier("sources")`
  - Renamed `DocumentationReader` → `JarContentReader` (generic naming)

- **Presentation Layer:**
  - `ToolDefinitions.scala` - Added `get_source` tool with `GetSourceInput` schema
  - `McpServer.scala` - Wired `SourceCodeService`, registered both tools
  - `Main.scala` - Updated dependency injection for new service

**Decisions made:**

- Renamed `DocumentationReader` → `JarContentReader` for clarity (works with any JAR content)
- Extended existing `ArtifactRepository` port rather than creating new one
- `SourceCodeService` mirrors `DocumentationService` structure for consistency
- New `SourcesNotAvailable` error provides helpful message suggesting `get_documentation`

**Patterns applied:**

- **Port Extension:** Added `fetchSourcesJar()` to existing `ArtifactRepository` port
- **Parallel Service Structure:** `SourceCodeService` mirrors `DocumentationService`
- **Code Reuse:** `JarFileReader` works unchanged for both HTML and Java files

**Testing:**

- Unit tests: 8 new tests (5 SourceCodeService + 3 ClassName toSourcePath)
- Integration tests: 3 new tests (real Maven Central with slf4j, guava)
- E2E tests: 3 new tests (get_source happy path + error cases)
- Total: 14 new tests, all passing

**Code review:**

- Iterations: 1
- Review file: review-phase-02-20251229.md
- Result: PASSED - 0 critical issues, 6 warnings, 11 suggestions
- Key feedback: DRY opportunity in ClassName path methods, missing scalafmt config
- Positive: Clean hexagonal architecture, proper FCIS compliance

**For next phases:**

- Available utilities:
  - `CoursierArtifactRepository.fetchSourcesJar()` - ready for Scala artifacts
  - `ClassName.toSourcePath()` - reusable for Scala (`.scala` extension)
  - `SourceCodeService` pattern - template for future tools
- Extension points:
  - Phase 3: Add Scala coordinate handling (`::` separator)
  - Phase 4: Add `.scala` extension support to `ClassName`
  - Phase 7: Add caching layer around both services
- Notes:
  - Scala coordinates (`::`) not yet supported - Phase 3
  - Inner class handling works identically for sources

**Files changed:**

```
M  src/main/scala/javadocsmcp/Main.scala
M  src/main/scala/javadocsmcp/application/DocumentationService.scala
A  src/main/scala/javadocsmcp/application/SourceCodeService.scala
M  src/main/scala/javadocsmcp/domain/ClassName.scala
M  src/main/scala/javadocsmcp/domain/Errors.scala
A  src/main/scala/javadocsmcp/domain/SourceCode.scala
M  src/main/scala/javadocsmcp/domain/ports/ArtifactRepository.scala
D  src/main/scala/javadocsmcp/domain/ports/DocumentationReader.scala
A  src/main/scala/javadocsmcp/domain/ports/JarContentReader.scala
M  src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala
M  src/main/scala/javadocsmcp/infrastructure/JarFileReader.scala
M  src/main/scala/javadocsmcp/presentation/McpServer.scala
M  src/main/scala/javadocsmcp/presentation/ToolDefinitions.scala
M  src/test/scala/javadocsmcp/application/DocumentationServiceTest.scala
A  src/test/scala/javadocsmcp/application/SourceCodeServiceIntegrationTest.scala
A  src/test/scala/javadocsmcp/application/SourceCodeServiceTest.scala
M  src/test/scala/javadocsmcp/domain/ClassNameTest.scala
M  src/test/scala/javadocsmcp/infrastructure/CoursierArtifactRepositoryTest.scala
M  src/test/scala/javadocsmcp/infrastructure/JarFileReaderTest.scala
M  src/test/scala/javadocsmcp/integration/EndToEndTest.scala
M  src/test/scala/javadocsmcp/testkit/InMemoryArtifactRepository.scala
D  src/test/scala/javadocsmcp/testkit/InMemoryDocumentationReader.scala
A  src/test/scala/javadocsmcp/testkit/InMemoryJarContentReader.scala
```

---

## Phase 3: Fetch Scaladoc HTML for Scala class (2025-12-29)

**What was built:**

- **Domain Layer:**
  - `ArtifactCoordinates.scala` - Added `scalaArtifact: Boolean` field and `::` separator parsing
  - Added `parseScalaCoordinates()` private method for Scala coordinate handling

- **Infrastructure Layer:**
  - `CoursierArtifactRepository.scala` - Added `resolveArtifactName()` for Scala `_3` suffix
  - Refactored `fetchJavadocJar()` and `fetchSourcesJar()` to use common `fetchJar()` method
  - Added comment documenting Scala 3 assumption

- **Presentation Layer:**
  - `ToolDefinitions.scala` - Updated descriptions with Scala examples

**Decisions made:**

- Use `::` separator to detect Scala coordinates (e.g., `org.typelevel::cats-effect:3.5.4`)
- Append `_3` suffix for Scala 3 artifacts (Scala 2.x support deferred to future phase)
- Scaladoc uses same `-javadoc` classifier as Javadoc (no classifier change needed)
- No changes to `DocumentationService` - transparent support via parsing layer

**Patterns applied:**

- **DRY Refactoring:** Extracted `resolveArtifactName()` and `fetchJar()` common methods
- **Transparent Extension:** Service layer unchanged, coordinate parsing handles Java/Scala difference
- **Domain-Driven Parsing:** Coordinate type detected and tracked in domain model

**Coursier Research Findings:**

- Coursier does NOT automatically handle `::` syntax in `Dependency.parse()`
- Must manually append Scala version suffix (`_3`, `_2.13`, etc.) to artifact ID
- Module construction uses `ModuleName(artifactId_3)` for Scala artifacts

**Testing:**

- Unit tests: 5 new tests (Scala coordinate parsing and regression)
- Integration tests: 4 new tests (cats-effect and ZIO Scaladoc fetching)
- E2E tests: 3 new tests (Scala documentation via HTTP)
- All 46 tests passing

**Code review:**

- Iterations: 1 (+ 1 fix iteration)
- Review file: review-phase-03-20251229.md
- Result: PASSED after fixing DRY violation
- Fixed: Extracted `resolveArtifactName()` and `fetchJar()` methods
- Deferred: Scala 2.x support (per MVP scope), boolean→enum refactor

**For next phases:**

- Available utilities:
  - `ArtifactCoordinates.parse()` handles both `:` and `::`
  - `resolveArtifactName()` applies Scala version suffix
  - `fetchJar()` generalized for any classifier
- Extension points:
  - Phase 4: Add `.scala` extension support to `ClassName.toSourcePath()`
  - Future: Add `scalaVersion` parameter for Scala 2.x support
- Notes:
  - Hardcoded `_3` suffix - only Scala 3 artifacts supported
  - Can fetch Scaladoc for cats-effect, zio, and other Scala 3 libraries

**Files changed:**

```
M  src/main/scala/javadocsmcp/domain/ArtifactCoordinates.scala
M  src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala
M  src/main/scala/javadocsmcp/presentation/ToolDefinitions.scala
M  src/test/scala/javadocsmcp/domain/ArtifactCoordinatesTest.scala
M  src/test/scala/javadocsmcp/infrastructure/CoursierArtifactRepositoryTest.scala
M  src/test/scala/javadocsmcp/application/DocumentationServiceIntegrationTest.scala
M  src/test/scala/javadocsmcp/integration/EndToEndTest.scala
```

---

### Refactoring R1: Replace hardcoded Scala suffix with coursier/dependency library (2025-12-29)

**Trigger:** Code review identified that the hardcoded `_3` suffix in `CoursierArtifactRepository.resolveArtifactName()` is inflexible - it only supports Scala 3 artifacts and provides no way for users to fetch Scala 2.13 library documentation.

**What changed:**

- **Dependencies:**
  - Added `io.get-coursier::dependency:0.2.3` library for proper Scala coordinate resolution

- **Infrastructure Layer:**
  - `CoursierArtifactRepository.scala` - Replaced `resolveArtifactName()` with `resolveArtifact()` using `DependencyParser` and `ScalaParameters.applyParams()`
  - Added `scalaVersion: String = "3"` parameter to `fetchJavadocJar()` and `fetchSourcesJar()`

- **Port Trait:**
  - `ArtifactRepository.scala` - Added `scalaVersion` parameter to fetch method signatures

- **Application Layer:**
  - `DocumentationService.scala` - Added `scalaVersion: Option[String] = None` parameter, defaults to "3"
  - `SourceCodeService.scala` - Added `scalaVersion: Option[String] = None` parameter, defaults to "3"

- **Presentation Layer:**
  - `GetDocInput` and `GetSourceInput` - Added `scalaVersion: Option[String] = None` field
  - Tool descriptions updated to document the new parameter

- **Test Infrastructure:**
  - `InMemoryArtifactRepository` - Added scalaVersion call capture for test verification

**Before → After:**

```scala
// Before: hardcoded _3 suffix
private def resolveArtifactName(coords: ArtifactCoordinates): String =
  if coords.scalaArtifact then s"${coords.artifactId}_3"
  else coords.artifactId

// After: coursier/dependency library resolves correctly
private def resolveArtifact(coords: ArtifactCoordinates, scalaVersion: String): (String, String, String) =
  if coords.scalaArtifact then
    val dep = DependencyParser.parse(s"${coords.groupId}::${coords.artifactId}:${coords.version}")
    val resolved = dep.toOption.get.applyParams(ScalaParameters(scalaVersion))
    (resolved.module.organization, resolved.module.name, resolved.version)
  else
    (coords.groupId, coords.artifactId, coords.version)
```

**Benefits:**

- Can now fetch Scala 2.13 documentation with `scalaVersion="2.13"`
- Can now fetch Scala 2.12 documentation with `scalaVersion="2.12"`
- Defaults to Scala 3 (`_3` suffix) for backward compatibility
- Explicit suffix escape hatch: `org.typelevel:cats-effect_2.13:3.5.4` bypasses resolution

**Testing:**

- Unit tests: 6 new tests for scalaVersion parameter passing
- Integration tests: 3 new tests for Scala version resolution
- E2E tests: 5 new tests for scalaVersion parameter via MCP
- Total: 56 tests passing (up from 46)

**Code review:**

- Iterations: 1
- Review file: review-refactor-03-R1-20251229.md
- Critical issues found: 3 (missing tests for scalaVersion)
- All critical issues fixed

**Files changed:**

```
M  project.scala
M  src/main/scala/javadocsmcp/domain/ports/ArtifactRepository.scala
M  src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala
M  src/main/scala/javadocsmcp/application/DocumentationService.scala
M  src/main/scala/javadocsmcp/application/SourceCodeService.scala
M  src/main/scala/javadocsmcp/presentation/ToolDefinitions.scala
M  src/test/scala/javadocsmcp/testkit/InMemoryArtifactRepository.scala
M  src/test/scala/javadocsmcp/application/DocumentationServiceTest.scala
M  src/test/scala/javadocsmcp/application/SourceCodeServiceTest.scala
M  src/test/scala/javadocsmcp/infrastructure/CoursierArtifactRepositoryTest.scala
M  src/test/scala/javadocsmcp/integration/EndToEndTest.scala
```

---

## Phase 4: Fetch source code for Scala class (2025-12-29)

**What was built:**

- **Domain Layer:**
  - `ClassName.scala` - Added `toScalaSourcePath()` method for `.scala` file paths
  - Refactored path methods to use private `toPath(extension)` DRY helper
  - Now supports: `toHtmlPath()` → `.html`, `toSourcePath()` → `.java`, `toScalaSourcePath()` → `.scala`

- **Application Layer:**
  - `SourceCodeService.scala` - Added extension fallback logic for Scala artifacts
  - Tries `.scala` first, falls back to `.java` for mixed-source projects
  - Java artifacts continue to use `.java` only (no change)

**Decisions made:**

- DRY refactoring: Extracted private `toPath(extension)` helper in `ClassName`
- Elegant fallback using `orElse`: `readEntry(jar, scalaPath).orElse(readEntry(jar, javaPath))`
- No infrastructure changes needed - `JarFileReader` already works for any file extension
- No presentation changes needed - `get_source` tool already supports Scala coordinates from Phase 3

**Patterns applied:**

- **DRY:** Single `toPath()` method generates all path variants
- **Functional Composition:** `orElse` for clean fallback logic
- **Transparent Extension:** Service layer change minimal, domain logic handles extension

**Testing:**

- Unit tests: 5 new tests (ClassName + SourceCodeService fallback)
- Integration tests: 2 new tests (cats-effect IO + ZIO real source)
- E2E tests: 3 new tests (Scala source via MCP + error handling)
- All tests passing

**Code review:**

- Iterations: 1
- Review file: review-phase-04-20251229.md
- Result: PASSED - 0 critical issues, 2 warnings, 7 suggestions
- Warnings: Test duplication (kept for coverage confidence)
- Positive feedback: Clean DRY refactoring, elegant `orElse` composition

**For next phases:**

- Available utilities:
  - `ClassName.toScalaSourcePath()` - `.scala` file path generation
  - `ClassName.toPath(extension)` - generic path helper (private)
  - Extension fallback pattern in `SourceCodeService`
- Extension points:
  - Phase 5: Error handling when artifact missing
  - Phase 6: Error handling when class missing in artifact
  - Phase 7: Caching layer around services
- Notes:
  - Mixed Java/Scala projects supported via fallback
  - Inner class stripping works for Scala too

**Files changed:**

```
M  src/main/scala/javadocsmcp/application/SourceCodeService.scala
M  src/main/scala/javadocsmcp/domain/ClassName.scala
M  src/test/scala/javadocsmcp/application/SourceCodeServiceIntegrationTest.scala
M  src/test/scala/javadocsmcp/application/SourceCodeServiceTest.scala
M  src/test/scala/javadocsmcp/domain/ClassNameTest.scala
M  src/test/scala/javadocsmcp/integration/EndToEndTest.scala
```

---

## Human Review: Scala Source Lookup Strategy (2025-12-29)

**Reviewer:** Michal

**Context:** Discussed approach for finding Scala source files in sources JARs, given that Scala doesn't enforce file naming conventions like Java does.

**Problem identified:**

Current approach (`className.toScalaSourcePath()` → `cats/effect/IO.scala`) only works by luck when library authors happen to name files after their main class. In Scala, a file `Utils.scala` can contain `class Foo`, `trait Bar`, `object Baz` - there's no compiler-enforced naming convention.

**Decisions made:**

1. **Use TASTy-based source lookup for Scala 3:**
   - TASTy files contain `sourceFile.path` with the original source file path
   - Rationale: TASTy is the authoritative source of truth for Scala 3 symbol locations

2. **Upgrade to Scala 3.7.4 (from LTS 3.3.x):**
   - Required for tasty-query 1.6.1 compatibility
   - Rationale: tasty-query is the cleanest API for TASTy analysis without requiring full compiler infrastructure
   - Trade-off accepted: Moving off LTS for this capability

3. **Add tasty-query dependency:**
   - `ch.epfl.scala::tasty-query:1.6.1`
   - Provides `Context`, `ClasspathLoaders`, symbol lookup, and source position access

4. **Path mapping algorithm:**
   - TASTy paths are project-relative (e.g., `core/shared/src/main/scala/cats/effect/IO.scala`)
   - Sources JAR paths are package-relative (e.g., `cats/effect/IO.scala`)
   - Extract package-relative suffix by finding where package path starts in TASTy path

**Spike results:**

```
Class: cats.effect.IO
  TASTy sourceFile.path: core/shared/src/main/scala/cats/effect/IO.scala
  Sources JAR path:      cats/effect/IO.scala
```

**Algorithm:**
```scala
def extractPackageRelativePath(tastyPath: String, packageName: String): String =
  val packagePath = packageName.replace('.', '/') + "/"
  val idx = tastyPath.indexOf(packagePath)
  if idx >= 0 then tastyPath.substring(idx)
  else tastyPath.split('/').last  // fallback to filename
```

**Impact on stories:**

- Phase 4 (Scala source) implementation approach will change
- Current `ClassName.toScalaSourcePath()` approach remains as fallback
- New `TastySourceResolver` component needed for TASTy-based lookup

**Action items:**

- [x] Upgrade Scala to 3.7.4
- [x] Add tasty-query dependency
- [ ] Implement `TastySourceResolver` in infrastructure layer
- [ ] Update `SourceCodeService` to use TASTy-based lookup for Scala artifacts
- [ ] Add tests for TASTy-based source resolution

**Files changed:**

```
M  project.scala (Scala 3.3 → 3.7.4, added tasty-query)
M  src/main/scala/javadocsmcp/presentation/McpServer.scala (fixed unused import)
```

---
