# Code Review Results

**Review Context:** Phase 3: Fetch Scaladoc HTML for Scala class - JMC-1
**Files Reviewed:** 7 files
**Skills Applied:** 4 (architecture, scala3, testing, style)
**Timestamp:** 2025-12-29 14:20:00
**Git Context:** git diff JMC-1...HEAD

---

<review skill="architecture">

## Architecture Review

### Critical Issues

#### 1. Code Duplication in CoursierArtifactRepository
**Location:** `src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala:14-79`
**Problem:** The methods `fetchJavadocJar` and `fetchSourcesJar` are nearly identical with only the classifier differing. The `artifactName` logic appears 4 times.
**Impact:** DRY violation, increased maintenance burden, risk of bugs
**Recommendation:** Extract common logic into private methods

```scala
private def resolveArtifactName(coords: ArtifactCoordinates): String =
  if coords.scalaArtifact then s"${coords.artifactId}_3"
  else coords.artifactId

private def fetchJar(
  coords: ArtifactCoordinates,
  classifier: Classifier,
  errorConstructor: String => DocumentationError
): Either[DocumentationError, File] = { /* common implementation */ }
```

#### 2. Hardcoded Scala Version (_3 suffix)
**Location:** `src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala:17,50`
**Problem:** The `_3` suffix is hardcoded, making it Scala 3-specific
**Status:** **DEFERRED** - Per MVP scope, Scala 2 support is explicitly out of scope

### Warnings

#### Boolean Flag Anti-Pattern
**Location:** `src/main/scala/javadocsmcp/domain/ArtifactCoordinates.scala:12`
**Problem:** `scalaArtifact: Boolean` could be better modeled as enum
**Recommendation:** Consider `enum ArtifactType { case Java, Scala }` for future phases

#### Domain Logic Duplication
**Location:** `src/main/scala/javadocsmcp/domain/ArtifactCoordinates.scala:36,49`
**Problem:** Validation `if groupId.isEmpty || artifactId.isEmpty || version.isEmpty` is duplicated
**Recommendation:** Extract into shared validation helper

</review>

---

<review skill="scala3">

## Scala 3 Idioms Review

### Critical Issues

None found.

### Warnings

Same as architecture review - code duplication and hardcoded Scala version.

### Suggestions

- Consider using Scala 3 `enum ArtifactType` instead of boolean
- Consider `extension` method for `resolvedArtifactId`
- Minor: Extract separator constants (`"::"`, `":"`)

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

None found.

### Warnings

#### Shared Mutable State in E2E Tests
**Location:** `src/test/scala/javadocsmcp/integration/EndToEndTest.scala:15`
**Problem:** `var server: Option[McpServer.ServerHandle]` is mutable
**Impact:** Minor - acceptable for E2E tests but could use `@volatile`

### Suggestions

- Consider test fixtures for common test data (slf4j, cats-effect coords)
- Consider property-based tests for coordinate parsing
- Document performance expectations in test names

</review>

---

<review skill="style">

## Code Style Review

### Critical Issues

None found.

### Warnings

Same as architecture - artifactName logic duplication.

### Suggestions

- Add comment explaining `_3` suffix limitation
- Consider using Scala 3's concise if-then syntax in CoursierArtifactRepository

</review>

---

## Summary

| Severity | Count | Status |
|----------|-------|--------|
| **Critical** | 1 | Needs fix (code duplication) |
| **Deferred** | 1 | Per MVP scope (Scala 2 support) |
| **Warnings** | 3 | Should address |
| **Suggestions** | 5 | Nice to have |

### Actionable Items (For This Phase)

| Priority | Issue | File | Action |
|----------|-------|------|--------|
| **High** | Code duplication | `CoursierArtifactRepository.scala` | Extract `resolveArtifactName()` and `fetchJar()` methods |
| Medium | Missing documentation | `CoursierArtifactRepository.scala` | Add comment about `_3` suffix assumption |

### Deferred Items

| Issue | Reason |
|-------|--------|
| Hardcoded `_3` Scala version | MVP scope explicitly defers Scala 2 support |
| Boolean flag → enum | Minor refactoring, can do in later phase |
| Validation duplication | Minor, can do in later phase |

---

## Review Decision

**Recommendation:** Fix the **code duplication** issue before merging (High priority). The hardcoded `_3` is acceptable per MVP scope.
