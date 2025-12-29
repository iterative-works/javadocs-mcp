# Phase 4 Context: Fetch source code for Scala class

**Issue:** JMC-1
**Phase:** 4 of 7
**Story:** Story 4 - Fetch source code for Scala library class

---

## 1. Goals

**What this phase accomplishes:**

- Enable the `get_source` tool to fetch Scala source code from Maven Central
- Support Scala artifact coordinates with `::` separator (e.g., `org.typelevel::cats-effect:3.5.4`)
- Try `.scala` extension first for Scala artifacts, falling back to `.java` for mixed-source projects

**User-facing change:**

After this phase, AI assistants can retrieve actual Scala source code for any Scala library:

```json
{
  "coordinates": "org.typelevel::cats-effect:3.5.4",
  "className": "cats.effect.IO"
}
```

Returns the actual `IO.scala` source file with Scala syntax (`sealed abstract class IO[+A]`, etc.)

---

## 2. Scope

**In scope:**

- Add `toScalaSourcePath()` method to `ClassName` domain object
- Modify `SourceCodeService` to try `.scala` first when `scalaArtifact = true`
- Fall back to `.java` if `.scala` not found (mixed-source projects)
- Integration tests with real Scala artifacts (cats-effect, zio)
- E2E tests via MCP protocol

**Out of scope:**

- Multiple source file discovery (return single file only)
- Scala 2.x cross-build handling (already supported via `scalaVersion` parameter from Phase 3 refactoring)
- Package objects (`package.scala`) - deferred to error handling phases
- Object companions (handled by inner class stripping - same file as class)

---

## 3. Dependencies

**What must exist from previous phases:**

From **Phase 2** (Java source):
- `SourceCode.scala` - Entity for source content
- `SourceCodeService.scala` - Service orchestrating source fetching
- `ClassName.toSourcePath()` - Returns `.java` path
- `ArtifactRepository.fetchSourcesJar()` - Fetches `-sources.jar`
- E2E test infrastructure for `get_source` tool

From **Phase 3** (Scala docs):
- `ArtifactCoordinates.scalaArtifact` - Boolean indicating Scala coordinates
- `scalaVersion` parameter in services and tools
- `resolveArtifact()` using coursier/dependency library

From **Phase 3 Refactoring R1**:
- `scalaVersion` parameter support in `SourceCodeService`
- `GetSourceInput.scalaVersion` optional field

**External dependencies:**
- Maven Central access (same as before)
- cats-effect and zio artifacts with `-sources.jar` available

---

## 4. Technical Approach

**High-level implementation strategy:**

1. **Extend `ClassName`** with extension-aware path methods:
   ```scala
   // Add to ClassName.scala
   def toScalaSourcePath: String = toPath(".scala")
   // Already exists: def toSourcePath: String = toPath(".java")
   ```

2. **Modify `SourceCodeService`** to try extensions based on artifact type:
   ```scala
   def getSource(coordinates: ArtifactCoordinates, className: ClassName, ...): Either[Error, SourceCode] =
     if coordinates.scalaArtifact then
       jarReader.readEntry(jarPath, className.toScalaSourcePath)
         .orElse(jarReader.readEntry(jarPath, className.toSourcePath)) // fallback to .java
     else
       jarReader.readEntry(jarPath, className.toSourcePath)
   ```

3. **No presentation layer changes** - `get_source` tool already supports Scala coordinates

**Key insight:** Phase 3's coordinate handling and scalaVersion parameter already work for sources. The only missing piece is trying `.scala` extension first.

---

## 5. Files to Modify

**Domain Layer:**
- `src/main/scala/javadocsmcp/domain/ClassName.scala`
  - Add `toScalaSourcePath` method
  - Keep existing `toSourcePath` for Java

**Application Layer:**
- `src/main/scala/javadocsmcp/application/SourceCodeService.scala`
  - Add extension fallback logic based on `coordinates.scalaArtifact`

**Domain Ports:**
- `src/main/scala/javadocsmcp/domain/ports/JarContentReader.scala`
  - May need to expose "entry exists" check for clean fallback

**Test Files:**
- `src/test/scala/javadocsmcp/domain/ClassNameTest.scala`
  - Add tests for `toScalaSourcePath`
- `src/test/scala/javadocsmcp/application/SourceCodeServiceTest.scala`
  - Add tests for Scala extension fallback
- `src/test/scala/javadocsmcp/application/SourceCodeServiceIntegrationTest.scala`
  - Add integration tests with real cats-effect sources
- `src/test/scala/javadocsmcp/integration/EndToEndTest.scala`
  - Add E2E tests for Scala source via MCP
- `src/test/scala/javadocsmcp/testkit/InMemoryJarContentReader.scala`
  - Update to support extension fallback testing

---

## 6. Testing Strategy

**Unit tests needed:**

1. `ClassName.toScalaSourcePath` returns `.scala` extension
   - `cats.effect.IO` → `cats/effect/IO.scala`
   - Inner class stripping: `cats.effect.IO$Pure` → `cats/effect/IO.scala`

2. `SourceCodeService` with Scala artifact:
   - Tries `.scala` first
   - Falls back to `.java` if `.scala` not found
   - Returns first successful result

3. `SourceCodeService` with Java artifact:
   - Only tries `.java` (no change from Phase 2)

**Integration tests needed:**

1. Fetch cats-effect `IO` source (real Scala file)
2. Fetch zio `ZIO` source (real Scala file)
3. Verify source contains Scala-specific syntax

**E2E tests needed:**

1. `get_source("org.typelevel::cats-effect:3.5.4", "cats.effect.IO")`
   - Status: success
   - Content: Contains `sealed abstract class IO`

2. `get_source` with non-existent Scala class
   - Status: error
   - Message: Class not found

---

## 7. Acceptance Criteria

**Phase 4 is complete when:**

- [ ] `get_source` returns valid Scala source for `cats.effect.IO`
- [ ] Source code contains Scala syntax (sealed abstract class, def flatMap, etc.)
- [ ] Java artifacts still return `.java` source correctly (regression)
- [ ] Response time under 5 seconds for first request
- [ ] All 56+ existing tests still pass
- [ ] New tests cover `.scala` extension and fallback behavior

**Gherkin scenario (from analysis.md):**

```gherkin
Scenario: Successfully fetch source for a Scala library class
  Given the MCP server is running
  And Maven Central contains artifact "org.typelevel::cats-effect:3.5.4" with sources JAR
  When I invoke tool "get_source" with coordinates "org.typelevel::cats-effect:3.5.4" and className "cats.effect.IO"
  Then I receive status "success"
  And the response contains Scala source code for class "IO"
  And the source includes Scala syntax like "sealed abstract class IO"
  And the response time is under 5 seconds for first request
```

---

## 8. Implementation Notes

**Why this phase is straightforward:**

Most infrastructure already exists from Phases 2 and 3:
- Coordinate parsing handles `::` (Phase 3)
- `scalaVersion` parameter works (Phase 3 R1)
- `fetchSourcesJar()` works for any artifact (Phase 2)
- `JarFileReader.readEntry()` works for any file (Phase 1)

**The only new logic:**
- Try `.scala` extension first for Scala artifacts
- Fall back to `.java` if not found

**Estimated effort:** 1-2 hours (lowest complexity phase)

---

## 9. Risks and Mitigations

**Risk:** cats-effect source files might be in unexpected locations
- **Mitigation:** Verify actual JAR structure before implementing
- **Fallback:** Use simpler artifact if cats-effect is unusual

**Risk:** Extension fallback might mask real errors
- **Mitigation:** Only fall back on "file not found", not on other errors
- **Mitigation:** Log when fallback is used

**Risk:** Mixed Java/Scala projects might have naming conflicts
- **Mitigation:** Scala-first ordering is correct (Scala files take precedence)
- **Note:** This is extremely rare in practice

---

## 10. Test Data

**Primary test artifact:** `org.typelevel::cats-effect:3.5.4`
- Known to have `-sources.jar` on Maven Central
- `cats.effect.IO` is the canonical class
- Contains distinct Scala syntax for verification

**Secondary test artifact:** `dev.zio::zio:2.0.21`
- Another popular Scala library
- `zio.ZIO` as test class
- Validates approach works across libraries

---

## Refactoring Decisions

### R1: TASTy-based Scala source lookup (2025-12-29)

**Trigger:** Code review discussion revealed that the filename-convention approach (`cats.effect.IO` → `cats/effect/IO.scala`) only works by luck. In Scala, unlike Java, a file `Utils.scala` can contain `class Foo`, `trait Bar`, `object Baz` - there's no compiler-enforced naming convention.

**Decision:** Replace filename-convention-based lookup with TASTy-based source resolution for Scala 3 artifacts.

**Rationale:**
- TASTy files contain `sourceFile.path` with the original source file path
- TASTy is the authoritative source of truth for Scala 3 symbol locations
- tasty-query library provides clean API without requiring full compiler infrastructure

**Scope:**
- Files affected:
  - NEW: `src/main/scala/javadocsmcp/infrastructure/TastySourceResolver.scala`
  - NEW: `src/main/scala/javadocsmcp/domain/ports/SourcePathResolver.scala`
  - MODIFY: `src/main/scala/javadocsmcp/application/SourceCodeService.scala`
- Components: Infrastructure layer (new), Application layer (modified)
- Boundaries: Keep existing `ClassName.toScalaSourcePath()` as fallback for Scala 2

**Approach:**
1. Fetch main JAR (contains `.tasty` files) in addition to sources JAR
2. Use tasty-query to look up class symbol by fully qualified name
3. Extract `sourceFile.path` from class definition position
4. Map project-relative path to package-relative path for sources JAR lookup
5. Fall back to filename convention if TASTy lookup fails (Scala 2 compatibility)

**Algorithm:**
```scala
def extractPackageRelativePath(tastyPath: String, packageName: String): String =
  val packagePath = packageName.replace('.', '/') + "/"
  val idx = tastyPath.indexOf(packagePath)
  if idx >= 0 then tastyPath.substring(idx)
  else tastyPath.split('/').last  // fallback to filename
```

**Dependencies added:**
- `ch.epfl.scala::tasty-query:1.6.1`
- Scala version upgraded to 3.7.4 (required for tasty-query compatibility)
