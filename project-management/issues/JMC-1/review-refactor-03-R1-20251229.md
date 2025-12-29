# Code Review Results

**Review Context:** Refactoring R1: Replace hardcoded Scala suffix with coursier/dependency library for JMC-1 Phase 3 (Iteration 1/3)
**Files Reviewed:** 7 files
**Skills Applied:** 3 (scala3, architecture, testing)
**Timestamp:** 2025-12-29
**Git Context:** git diff b8a5f0a

---

<review skill="scala3">

## Scala 3 Idioms Review

### Critical Issues

None found.

### Warnings

#### Consider Opaque Type for scalaVersion Parameter
**Location:** `src/main/scala/javadocsmcp/domain/ports/ArtifactRepository.scala:10-11`
**Problem:** `scalaVersion` is passed as raw `String` type throughout the codebase (in trait definition, implementations, and service layers), making it possible to pass invalid values or mix it up with other string parameters
**Impact:** Type safety issue - there's nothing preventing callers from passing invalid Scala versions like "4.0" or accidentally swapping parameters. This is a typical case of primitive obsession.
**Recommendation:** Consider introducing an opaque type or enum for valid Scala versions

#### Exception Throwing in Pure Code
**Location:** `src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala:26-28`
**Problem:** Using `throw new RuntimeException` inside what should be pure functional code wrapped in `Try`
**Impact:** Not idiomatic Scala 3 functional programming - the code uses `Try` to handle failures but then explicitly throws exceptions. The error path should use `Either` or explicit `Failure` construction.
**Recommendation:** Replace exception throwing with explicit error handling using Either

### Suggestions

#### Extension Method for Tuple Destructuring
**Location:** `src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala:40`
**Problem:** The return type `(String, String, String)` from `resolveArtifact` is not self-documenting
**Recommendation:** Consider using a case class or opaque type with extension methods for better semantics

#### Default Parameter Pattern for Optional Values
**Location:** `src/main/scala/javadocsmcp/application/DocumentationService.scala:18`
**Problem:** Using `Option[String]` with `.getOrElse("3")` pattern throughout service layer creates dual defaults
**Recommendation:** Either remove the default from repository trait and always pass explicit value from service, or remove the Option and use default parameter directly in services

</review>

---

<review skill="architecture">

## Architecture Review

### Critical Issues

None found.

### Warnings

#### Port Trait Located in Wrong Package
**Location:** `src/main/scala/javadocsmcp/domain/ports/ArtifactRepository.scala`
**Problem:** The `ArtifactRepository` trait is located in `domain/ports/` but ports should be defined at architectural boundaries in `infrastructure/ports/`
**Impact:** Violates hexagonal architecture pattern - ports are part of the infrastructure boundary, not the domain layer. Domain should not know about ports.
**Recommendation:** Move `ArtifactRepository` and other port interfaces to `infrastructure/ports/`

#### Infrastructure Layer Leaking Technical Details Through Exception
**Location:** `src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala:27`
**Problem:** The `resolveArtifact` method throws `RuntimeException` when parsing fails
**Impact:** Breaks FCIS principle - infrastructure should handle all exceptions and convert to domain errors.
**Recommendation:** Catch parsing failures and return them through the `Either` type

### Suggestions

#### Application Services Mixing Parsing and Orchestration
**Location:** `src/main/scala/javadocsmcp/application/DocumentationService.scala:18`
**Problem:** Application services perform string parsing (`ArtifactCoordinates.parse`, `ClassName.parse`) which is validation logic
**Recommendation:** Consider moving parsing to presentation layer so application services receive already-parsed domain objects

#### Test Implementation Not Utilizing scalaVersion Parameter
**Location:** `src/test/scala/javadocsmcp/testkit/InMemoryArtifactRepository.scala:15`
**Problem:** The `InMemoryArtifactRepository` accepts `scalaVersion` parameter but ignores it
**Recommendation:** If tests need version-aware behavior, make the lookup key include scalaVersion

#### Default Parameter Values in Port Interface
**Location:** `src/main/scala/javadocsmcp/domain/ports/ArtifactRepository.scala:10-11`
**Problem:** Port trait defines default parameter values (`scalaVersion: String = "3"`), which are implementation details
**Recommendation:** Remove defaults from trait, add them only in implementations

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

#### Missing Unit Tests for scalaVersion Parameter in Service Layer
**Location:** `src/test/scala/javadocsmcp/application/DocumentationServiceTest.scala` and `src/test/scala/javadocsmcp/application/SourceCodeServiceTest.scala`
**Problem:** The service layer now accepts an optional `scalaVersion` parameter, but there are no unit tests verifying this parameter is correctly passed through to the repository
**Impact:** Core feature functionality (Scala version selection) is not verified at the service layer, only at infrastructure level

#### Missing E2E Tests for scalaVersion Parameter
**Location:** `src/test/scala/javadocsmcp/integration/EndToEndTest.scala`
**Problem:** The refactoring plan explicitly mentions E2E tests for scalaVersion parameter, but no such tests exist in the E2E test file
**Impact:** The new scalaVersion parameter is not verified end-to-end through the MCP protocol

#### InMemoryArtifactRepository Ignores scalaVersion Parameter
**Location:** `src/test/scala/javadocsmcp/testkit/InMemoryArtifactRepository.scala:15-25`
**Problem:** The test double accepts `scalaVersion` parameter but completely ignores it
**Impact:** Tests using InMemoryArtifactRepository cannot detect bugs related to scalaVersion handling

### Warnings

#### Integration Tests Are Labeled as Unit Tests
**Location:** `src/test/scala/javadocsmcp/infrastructure/CoursierArtifactRepositoryTest.scala:1-2`
**Problem:** The file header says "Integration tests" but file makes real network calls to Maven Central

#### Test Coverage Gap: Error Handling for Invalid scalaVersion
**Location:** `src/test/scala/javadocsmcp/infrastructure/CoursierArtifactRepositoryTest.scala`
**Problem:** No tests verify behavior when invalid scalaVersion values are provided

#### Removed E2E Tests Without Replacement
**Location:** `src/test/scala/javadocsmcp/integration/EndToEndTest.scala:229-309`
**Problem:** Three E2E tests for Scala artifacts were deleted without replacement

### Suggestions

#### Consider ZIO Test Instead of MUnit
**Location:** All test files use `munit.FunSuite`
**Recommendation:** ZIO Test would align better with the functional core approach

#### Add Behavior-Focused Test Names for Scala Version Resolution
**Location:** `src/test/scala/javadocsmcp/infrastructure/CoursierArtifactRepositoryTest.scala:110-146`
**Recommendation:** Consider more behavior-focused names

#### Add Test for Default Parameter Behavior
**Location:** `src/test/scala/javadocsmcp/infrastructure/CoursierArtifactRepositoryTest.scala`
**Recommendation:** Add explicit test for default parameter behavior

</review>

---

## Summary

- **Critical issues:** 3 (must fix before merge)
- **Warnings:** 6 (should fix)
- **Suggestions:** 7 (nice to have)

### By Skill
- scala3: 0 critical, 2 warnings, 2 suggestions
- architecture: 0 critical, 2 warnings, 3 suggestions
- testing: 3 critical, 3 warnings, 3 suggestions
