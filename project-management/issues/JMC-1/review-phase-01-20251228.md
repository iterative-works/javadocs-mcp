# Code Review Results

**Review Context:** Phase 1: Fetch Javadoc HTML for Java class for issue JMC-1 (Iteration 1/3)
**Files Reviewed:** 9 files
**Skills Applied:** 4 (architecture, scala3, testing, style)
**Timestamp:** 2025-12-28 23:40:00
**Git Context:** git diff f371cbb

---

<review skill="architecture">

## Architecture Review

### Critical Issues

#### Application Layer Directly Depends on Concrete Infrastructure Classes
**Location:** `src/main/scala/javadocsmcp/application/DocumentationService.scala:9-11`
**Problem:** `DocumentationService` class constructor takes concrete infrastructure implementations (`CoursierArtifactRepository`, `JarFileReader`) instead of port interfaces
**Impact:** Violates Hexagonal Architecture and Dependency Inversion Principle. The application layer should depend on abstractions (ports), not concrete implementations.
**Recommendation:** Define port traits and have the application depend on those interfaces.

#### I/O Operations Without Effect Types
**Location:** `src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala:12-37`
**Problem:** The `fetchJavadocJar` method performs blocking I/O (network download) but returns `Either[DocumentationError, File]` instead of an effect type
**Impact:** Violates FCIS principle - the shell layer should explicitly declare effects.
**Recommendation:** For MVP, document that this blocks. Consider ZIO for future phases.

### Warnings

#### Missing Port/Adapter Package Structure
**Location:** `src/main/scala/javadocsmcp/infrastructure/`
**Problem:** No `infrastructure/ports/` directory exists
**Recommendation:** Create ports package and separate interface definitions from implementations.

#### Domain Methods Lack Smart Constructor Pattern
**Location:** `src/main/scala/javadocsmcp/domain/ArtifactCoordinates.scala:6-10`
**Problem:** Case class is public, allowing construction with invalid data via copy constructor
**Recommendation:** Consider making case class private with only smart constructor access.

### Suggestions

- Consider using Scala 3 opaque types for domain value objects
- Package organization could better reflect hexagonal architecture
- Consider adding domain services for complex logic as it grows

</review>

---

<review skill="scala3">

## Scala 3 Idioms Review

### Critical Issues

#### Sealed Trait Should Be Enum
**Location:** `src/main/scala/javadocsmcp/domain/Errors.scala:6-24`
**Problem:** `DocumentationError` is a sealed trait with simple case class variants - exact pattern Scala 3 enums replace
**Impact:** Increased boilerplate, less idiomatic Scala 3 code
**Recommendation:** Convert to Scala 3 enum with parameterized cases

### Warnings

#### Imperative Return Statements in Pure Functions
**Location:** `src/main/scala/javadocsmcp/domain/ArtifactCoordinates.scala:14-15,20-21`, `ClassName.scala:21-22`
**Problem:** Using `return` statements breaks expression-oriented programming style
**Recommendation:** Use expression-based control flow instead

### Suggestions

- Consider opaque types for domain values like `ClassName`
- Use Scala 3 `then` syntax for conditionals
- Rename `adderTool` variable to match actual purpose (`docTool` or `getDocTool`)

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

#### Integration Tests Disguised as Unit Tests
**Location:** `src/test/scala/javadocsmcp/infrastructure/`, `src/test/scala/javadocsmcp/application/`
**Problem:** All tests make real network calls to Maven Central, download real JARs. These are integration tests, not unit tests.
**Impact:** Tests are slow, can fail due to network issues, not isolated
**Recommendation:** Create in-memory test implementations. Keep ONE integration test file for real Maven Central verification.

#### Missing Test Trait Abstractions
**Location:** `src/main/scala/javadocsmcp/infrastructure/`
**Problem:** Concrete classes, not trait implementations - prevents dependency injection of test implementations
**Recommendation:** Extract traits to enable proper dependency injection

#### Shared Mutable State Between Tests
**Location:** `src/test/scala/javadocsmcp/application/DocumentationServiceTest.scala:9-11`
**Problem:** Tests share instances at class level
**Recommendation:** Create fresh instances in each test

### Warnings

- Tests using `.get` on Options without clear assertion
- Missing edge case tests (extra colons, whitespace, special characters)
- No test for infrastructure error propagation (corrupted JAR)
- Test names don't follow behavior-driven style

### Suggestions

- Consider property-based testing for domain logic
- Add tests for presentation layer (McpServer, ToolDefinitions)
- Create shared test fixtures
- Add tests for Main entry point argument parsing

</review>

---

<review skill="style">

## Code Style Review

### Critical Issues

None found.

### Warnings

#### Use of `return` Keyword in Scala 3
**Location:** `ArtifactCoordinates.scala:15,21`, `ClassName.scala:22`
**Problem:** Multiple uses of `return` keyword - not idiomatic Scala
**Recommendation:** Refactor to use if-else expressions without `return`

#### Misleading Variable Name `adderTool`
**Location:** `ToolDefinitions.scala:18`
**Problem:** Variable named `adderTool` for documentation tool - copy-paste artifact
**Recommendation:** Rename to `docTool` or `getDocTool`

### Suggestions

- Consider explicit imports instead of wildcards for clarity
- Use Scala 3 `then` syntax for conditionals
- Consider logging exception details in failure cases

</review>

---

## Summary

- **Critical issues:** 5 (must fix before merge)
- **Warnings:** 10 (should fix)
- **Suggestions:** 12 (nice to have)

### By Skill
- architecture: 2 critical, 2 warnings, 3 suggestions
- scala3: 1 critical, 2 warnings, 3 suggestions
- testing: 3 critical, 4 warnings, 4 suggestions
- style: 0 critical, 2 warnings, 2 suggestions

### Priority Fixes for This Iteration

1. **[style]** Remove `return` statements - use expression-based control flow
2. **[style]** Rename `adderTool` to `docTool`
3. **[scala3]** Convert `DocumentationError` sealed trait to Scala 3 enum

### Deferred to Future Phases

The following issues are valid but represent significant architectural changes that should be planned for future phases:

- **Port/adapter pattern with traits** - Requires restructuring infrastructure layer
- **Effect types (ZIO)** - Significant change to entire codebase
- **Test abstractions with in-memory implementations** - Requires trait extraction first
- **Smart constructor pattern with opaque types** - Nice-to-have optimization

These are documented and can be addressed in Phase 7 (caching) or a dedicated refactoring phase.
