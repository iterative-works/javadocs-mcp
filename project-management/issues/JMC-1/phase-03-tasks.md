# Phase 3 Implementation Tasks: Fetch Scaladoc HTML for Scala class

**Issue:** JMC-1
**Phase:** 3 of 7
**Story:** Fetch Scaladoc HTML for a Scala library class
**Estimated Effort:** 2-3 hours
**Status:** Not Started

---

## Task Overview

This phase extends the existing `get_documentation` tool to support Scala libraries with Scaladoc. The key challenge is handling Scala artifact coordinates with `::` separator and letting Coursier handle Scala version suffix resolution automatically.

**Key principle:** Each task should be completable in 15-30 minutes. Commit frequently.

**Strategy:** Extend existing components rather than creating parallel ones. Most logic remains unchanged.

---

## Phase 3.1: Research Coursier Scala Support (30 min)

### Understanding Coursier's Scala Resolution

- [ ] [research] Read Coursier documentation on Scala cross-version handling
- [ ] [research] Test in Scala REPL: Does Coursier's `Module` accept `::` separator?
- [ ] [research] Test: `Module(Organization("org.typelevel"), ModuleName("cats-effect"))` behavior
- [ ] [research] Investigate: Does Coursier auto-append `_3` or `_2.13` suffix?
- [ ] [research] Check `scala.util.Properties.versionNumberString` for runtime Scala version
- [ ] [research] Document findings: How should we construct Module for Scala artifacts?
- [ ] [research] Decision: Use Coursier's automatic suffix handling or manual construction?
- [ ] [notes] Document research findings in implementation log or comment

**Note:** This is exploratory - no code changes, no commit. Findings inform implementation approach.

---

## Phase 3.2: Extend Domain for Scala Coordinates (45 min)

### Add Scala Coordinate Parsing

- [ ] [test] Write failing test for parsing `"org.typelevel::cats-effect:3.5.4"` with `::` separator
- [ ] [test] Assert groupId = "org.typelevel", artifactId = "cats-effect", version = "3.5.4"
- [ ] [impl] Add `scalaArtifact: Boolean = false` field to `ArtifactCoordinates` case class
- [ ] [impl] Update `parse()` to detect `::` and route to Scala parser
- [ ] [impl] Implement `parseScalaCoordinates()` private method
- [ ] [impl] Split on `"::"`, then split remainder on `":"` for version
- [ ] [impl] Set `scalaArtifact = true` for Scala coordinates
- [ ] [impl] Run test, ensure it passes

### Additional Scala Coordinate Tests

- [ ] [test] Write failing test for `"dev.zio::zio:2.0.21"` → verify parsing
- [ ] [impl] Verify test passes with existing implementation
- [ ] [test] Write failing test for invalid Scala coordinates: `"org.typelevel::cats-effect"` (missing version)
- [ ] [impl] Ensure validation rejects invalid format
- [ ] [test] Write failing test for wrong separator count: `"org.typelevel:::cats-effect:3.5.4"`
- [ ] [impl] Ensure validation handles edge case

### Regression Tests

- [ ] [test] Write regression test: Java coordinates `"org.slf4j:slf4j-api:2.0.9"` still work
- [ ] [test] Assert `scalaArtifact = false` for Java coordinates
- [ ] [impl] Verify all existing tests still pass
- [ ] [setup] Commit: "feat(domain): add Scala coordinate parsing with :: separator"

---

## Phase 3.3: Extend Infrastructure for Scala Resolution (45 min)

### Update CoursierArtifactRepository for Scala

- [ ] [test] Write failing integration test: fetch Scaladoc JAR for `"org.typelevel::cats-effect:3.5.4"`
- [ ] [impl] Open `CoursierArtifactRepository.scala`
- [ ] [impl] Update `fetchJavadocJar()` to handle `scalaArtifact` field
- [ ] [impl] Based on Phase 3.1 research: Construct Scala Module appropriately
- [ ] [impl] Option A (if Coursier handles `::` automatically): Pass coordinates with `::`
- [ ] [impl] Option B (if manual): Append Scala version suffix to artifactId
- [ ] [impl] Keep `Classifier("javadoc")` unchanged (Scaladoc uses same classifier)
- [ ] [impl] Run integration test, verify cats-effect Scaladoc JAR downloads

### Test with Multiple Scala Libraries

- [ ] [test] Write integration test: fetch Scaladoc for `"dev.zio::zio:2.0.21"`
- [ ] [impl] Verify implementation works for different Scala library
- [ ] [test] Write test for non-existent Scala artifact → expect `ArtifactNotFound`
- [ ] [impl] Ensure error handling works same as Java artifacts

### Verify No Regression

- [ ] [test] Run all existing CoursierArtifactRepositoryTest tests
- [ ] [test] Verify Java artifact fetching still works (slf4j, guava)
- [ ] [setup] Commit: "feat(infra): add Scala artifact resolution to Coursier repository"

---

## Phase 3.4: Service Layer Verification (15 min)

### Verify DocumentationService Works Transparently

- [ ] [test] Write integration test in `DocumentationServiceIntegrationTest.scala`
- [ ] [test] Test: `service.getDocumentation("org.typelevel::cats-effect:3.5.4", "cats.effect.IO")`
- [ ] [impl] Run test with real `CoursierArtifactRepository` and `JarFileReader`
- [ ] [test] Assert result is `Right(Documentation)` with HTML content
- [ ] [test] Assert HTML contains "IO" class documentation
- [ ] [test] Verify className.fullyQualifiedName == "cats.effect.IO"
- [ ] [impl] **NO CODE CHANGES** to DocumentationService - verify it works transparently
- [ ] [setup] Commit: "test: verify DocumentationService works with Scala coordinates"

---

## Phase 3.5: Update Presentation Layer (15 min)

### Update Tool Descriptions

- [ ] [impl] Open `presentation/ToolDefinitions.scala`
- [ ] [impl] Update `getDocumentationTool` description with Scala examples
- [ ] [impl] Add text: "For Java libraries, use ':' separator: groupId:artifactId:version"
- [ ] [impl] Add text: "For Scala libraries, use '::' separator: groupId::artifactId:version"
- [ ] [impl] Add example: "Java: org.slf4j:slf4j-api:2.0.9"
- [ ] [impl] Add example: "Scala: org.typelevel::cats-effect:3.5.4"
- [ ] [impl] **NO SCHEMA CHANGES** - `GetDocInput` handles both formats already
- [ ] [setup] Commit: "docs(mcp): update tool descriptions to include Scala support"

---

## Phase 3.6: End-to-End Testing (30 min)

### E2E Test for Scala Documentation

- [ ] [test] Open `test/.../integration/EndToEndTest.scala`
- [ ] [test] Write E2E test: invoke `get_documentation` with `"org.typelevel::cats-effect:3.5.4"`
- [ ] [test] className = "cats.effect.IO"
- [ ] [impl] Start MCP server, send HTTP request with Scala coordinates
- [ ] [test] Assert HTTP 200 response
- [ ] [test] Assert response contains Scaladoc HTML for IO class
- [ ] [test] Assert HTML is non-empty and contains "IO"
- [ ] [test] Assert response time under 5 seconds

### E2E Error Tests for Scala

- [ ] [test] Write E2E test: request non-existent Scala artifact
- [ ] [test] Coordinates: `"com.fake::nonexistent:1.0.0"`
- [ ] [test] Assert error response "Artifact not found"
- [ ] [test] Write E2E test: valid Scala artifact but missing class
- [ ] [test] Coordinates: `"org.typelevel::cats-effect:3.5.4"`, className: "cats.effect.NonExistent"
- [ ] [test] Assert error response "Class not found"

### Regression E2E Tests

- [ ] [test] Run all existing E2E tests for Java documentation
- [ ] [test] Verify `get_documentation` with `"org.slf4j:slf4j-api:2.0.9"` still works
- [ ] [test] Verify both Java and Scala tools work in same server instance
- [ ] [setup] Commit: "test: add E2E tests for Scala documentation fetching"

---

## Phase 3.7: Polish and Verification (30 min)

### Code Quality Review

- [ ] [impl] Review all modified files for compiler warnings
- [ ] [impl] Fix any warnings found
- [ ] [impl] Verify `PURPOSE:` comments updated where logic changed
- [ ] [impl] Check: No "new" or "legacy" or "wrapper" in names or comments
- [ ] [impl] Ensure immutability throughout new code
- [ ] [impl] Verify domain naming: No implementation details in names

### Full Test Suite Verification

- [ ] [test] Run all unit tests - verify they pass
- [ ] [test] Run all integration tests - verify real Scala artifacts work
- [ ] [test] Run all E2E tests - verify both Java and Scala
- [ ] [test] Verify test output pristine (no warnings, no error logs for successful tests)
- [ ] [test] Check: Zero compiler warnings across entire codebase

### Error Message Review

- [ ] [impl] Test each Scala error case manually
- [ ] [impl] Verify error messages are helpful and include coordinates
- [ ] [impl] Ensure Scala artifacts have same quality error handling as Java

### Regression Testing

- [ ] [test] Test Java documentation fetching: `org.slf4j:slf4j-api:2.0.9` → `org.slf4j.Logger`
- [ ] [test] Test Java source fetching: still works from Phase 2
- [ ] [test] Verify no performance regression (both under 5s)
- [ ] [test] Verify no changes to Phase 1/2 functionality

### Documentation

- [ ] [impl] Add comments explaining `::` vs `:` distinction where relevant
- [ ] [impl] Document Coursier Scala resolution approach in comments
- [ ] [impl] Note any edge cases deferred (e.g., explicit `_2.13` versions)

### Final Commit

- [ ] [setup] Update implementation log with Phase 3 summary
- [ ] [setup] Note Coursier behavior findings from Phase 3.1
- [ ] [setup] Commit: "docs: complete Phase 3 - Scala documentation support"

---

## Acceptance Checklist

Before marking Phase 3 complete, verify ALL criteria:

### Functional Requirements

- [ ] Can parse Scala coordinates with `::` separator
- [ ] `get_documentation` tool works with Scala coordinates
- [ ] Can fetch Scaladoc for `org.typelevel::cats-effect:3.5.4` → `cats.effect.IO`
- [ ] Response contains valid Scaladoc HTML
- [ ] Response time under 5 seconds for first request
- [ ] Error handling works for non-existent Scala artifacts
- [ ] Error handling works for non-existent Scala classes
- [ ] **REGRESSION:** All Phase 1 tests still pass (Java docs)
- [ ] **REGRESSION:** All Phase 2 tests still pass (Java sources)

### Code Quality

- [ ] `ArtifactCoordinates` supports both `:` and `::`
- [ ] `scalaArtifact` field properly set during parsing
- [ ] Coursier resolution handles Scala artifacts correctly
- [ ] No changes to `DocumentationService` (transparent support)
- [ ] No changes to `JarFileReader` (HTML extraction same)
- [ ] No compiler warnings
- [ ] `PURPOSE:` comments updated where logic changed
- [ ] Domain naming clear (no implementation details)

### Testing

- [ ] All existing tests pass (regression verification)
- [ ] Unit tests for `::` coordinate parsing
- [ ] Unit tests for invalid Scala coordinate formats
- [ ] Integration tests with real cats-effect Scaladoc
- [ ] Integration tests with real zio Scaladoc
- [ ] E2E test with Scala coordinates via HTTP
- [ ] E2E tests for Scala error cases
- [ ] Test output pristine (no warnings/errors for successful tests)
- [ ] Tests use real Scala artifacts (no mocking)

### Documentation

- [ ] Tool descriptions updated with Scala examples
- [ ] Code comments explain `::` vs `:` distinction
- [ ] Error messages clear for Scala coordinate format
- [ ] Coursier behavior documented in comments

### Git Hygiene

- [ ] Incremental commits following TDD pattern
- [ ] Commit messages clear and descriptive
- [ ] Pre-commit hooks pass (no `--no-verify`)
- [ ] Working directory clean

---

## Notes

**TDD Discipline:**
- ALWAYS write the failing test BEFORE implementation
- Run the test to see it fail with the expected error
- Write ONLY enough code to make the test pass
- Refactor while keeping tests green
- Commit after each red-green-refactor cycle

**Testing with Real Scala Data:**
- Use `org.typelevel::cats-effect:3.5.4` as primary test artifact (stable, excellent Scaladoc)
- Use `dev.zio::zio:2.0.21` as secondary test artifact
- Let Coursier cache artifacts in `~/.cache/coursier` (persistent across runs)
- NO MOCKING of Maven Central or Coursier (per project guidelines)

**Key Technical Insight:**
- Scaladoc uses same `-javadoc` classifier as Javadoc
- HTML extraction is identical (no code changes to JarFileReader)
- Main difference is coordinate parsing and Coursier module construction
- Coursier likely handles Scala version suffix automatically

**Critical Decision Point:**
Phase 3.1 research will determine whether:
- Option A: Coursier accepts `::` directly (preferred - simpler)
- Option B: We must manually append `_3` or `_2.13` suffix

**Task Estimation:**
- Each task should take 15-30 minutes
- If a task takes longer, break it down further
- Commit frequently (every 2-3 tasks minimum)

**Expected Total Time:** 2-3 hours for complete Phase 3

---

**Next Steps After Phase 3:**
1. Mark all tasks complete
2. Update `project-management/issues/JMC-1/tasks.md` - mark Phase 3 complete
3. Run `/iterative-works:ag-implement JMC-1` to start Phase 4
