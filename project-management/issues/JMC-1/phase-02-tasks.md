# Phase 2 Implementation Tasks: Fetch source code for Java class

**Issue:** JMC-1
**Phase:** 2 of 7
**Story:** Fetch source code for a Java library class
**Estimated Effort:** 3-4 hours
**Status:** Not Started

---

## Task Overview

This phase builds on Phase 1's infrastructure to add source code fetching capability. Tasks follow strict TDD cycle: write failing test → implement minimal code → refactor.

**Key principle:** Each task should be completable in 15-30 minutes. Commit frequently.

**Strategy:** Mirror Phase 1 structure with parallel components for sources instead of javadoc.

---

## Phase 2.1: Port Refactoring (30 min)

### Rename DocumentationReader to JarContentReader

- [ ] [impl] Rename `domain/ports/DocumentationReader.scala` to `domain/ports/JarContentReader.scala`
- [ ] [impl] Update trait name from `DocumentationReader` to `JarContentReader` inside file
- [ ] [impl] Update `infrastructure/JarFileReader.scala` to implement `JarContentReader`
- [ ] [impl] Update `application/DocumentationService.scala` constructor parameter type
- [ ] [impl] Update `testkit/InMemoryDocumentationReader.scala` → rename to `InMemoryJarContentReader.scala`
- [ ] [impl] Update test files that reference the old name
- [ ] [test] Run all existing tests - ensure Phase 1 still works
- [ ] [setup] Commit: "refactor: rename DocumentationReader to JarContentReader for clarity"

---

## Phase 2.2: Extend Domain (30 min)

### ClassName Source Path Mapping

- [ ] [test] Write failing test for `ClassName.toSourcePath()` → returns `"org/slf4j/Logger.java"`
- [ ] [impl] Implement `toSourcePath()` method in `ClassName.scala`
- [ ] [test] Write failing test for inner class handling: `"org.slf4j.Logger$Factory"` → `"org/slf4j/Logger.java"`
- [ ] [impl] Verify existing inner class stripping logic works for source paths
- [ ] [test] Run tests, ensure both `toHtmlPath` and `toSourcePath` work correctly
- [ ] [setup] Commit: "feat(domain): add toSourcePath method to ClassName"

### SourceCode Entity

- [ ] [impl] Create `domain/SourceCode.scala` with case class
- [ ] [impl] Add fields: `sourceText: String`, `className: ClassName`, `coordinates: ArtifactCoordinates`
- [ ] [impl] Add `PURPOSE:` comment to `SourceCode.scala`
- [ ] [setup] Commit: "feat(domain): add SourceCode entity"

### Domain Errors

- [ ] [impl] Open `domain/Errors.scala`
- [ ] [impl] Add `case SourcesNotAvailable(coordinates: String)` to `DocumentationError` enum
- [ ] [impl] Add helpful error message: "Sources JAR not available for: {coordinates}. Try using get_documentation instead."
- [ ] [setup] Commit: "feat(domain): add SourcesNotAvailable error type"

---

## Phase 2.3: Extend Infrastructure (45 min)

### Extend ArtifactRepository Port

- [ ] [impl] Open `domain/ports/ArtifactRepository.scala`
- [ ] [impl] Add method signature: `def fetchSourcesJar(coords: ArtifactCoordinates): Either[DocumentationError, File]`
- [ ] [setup] Commit: "feat(ports): add fetchSourcesJar to ArtifactRepository"

### Implement fetchSourcesJar in CoursierArtifactRepository

- [ ] [test] Write failing integration test for `fetchSourcesJar("org.slf4j:slf4j-api:2.0.9")` returns `Right(File)`
- [ ] [impl] Open `infrastructure/CoursierArtifactRepository.scala`
- [ ] [impl] Implement `fetchSourcesJar()` method using `Classifier("sources")` instead of `"javadoc"`
- [ ] [impl] Run test, ensure it passes (downloads real sources JAR from Maven Central)
- [ ] [test] Write failing test for artifact without sources JAR returns `Left(SourcesNotAvailable)`
- [ ] [impl] Wrap Coursier exceptions, map to `SourcesNotAvailable` error
- [ ] [impl] Verify both tests pass
- [ ] [setup] Commit: "feat(infra): implement fetchSourcesJar in CoursierArtifactRepository"

### Test JAR Reader with Java Source Files

- [ ] [test] Write integration test: fetch slf4j sources JAR, then read `"org/slf4j/Logger.java"` from it
- [ ] [impl] Run test using existing `JarFileReader.readEntry()` - verify it works without code changes
- [ ] [test] Verify source content contains "public interface Logger"
- [ ] [test] Write test for missing source file returns `Left(ClassNotFound)`
- [ ] [impl] Ensure existing error handling works correctly
- [ ] [setup] Commit: "test: verify JAR reader works with Java source files"

### Update In-Memory Test Implementations

- [ ] [impl] Open `testkit/InMemoryArtifactRepository.scala`
- [ ] [impl] Add `fetchSourcesJar()` method implementation
- [ ] [impl] Add `addSourcesJar()` helper method for tests
- [ ] [impl] Update in-memory storage to handle both javadoc and sources JARs
- [ ] [setup] Commit: "test: extend InMemoryArtifactRepository with sources support"

---

## Phase 2.4: Application Layer (45 min)

### SourceCodeService Implementation

- [ ] [impl] Create `application/SourceCodeService.scala`
- [ ] [impl] Add constructor parameters: `repository: ArtifactRepository`, `reader: JarContentReader`
- [ ] [impl] Add `PURPOSE:` comment
- [ ] [impl] Define method signature: `def getSource(coordinatesStr: String, classNameStr: String): Either[DocumentationError, SourceCode]`
- [ ] [setup] Commit: "feat(app): create SourceCodeService skeleton"

### SourceCodeService Unit Tests

- [ ] [test] Create `test/.../application/SourceCodeServiceTest.scala`
- [ ] [test] Write failing test for successful source fetch using in-memory implementations
- [ ] [impl] Implement service orchestration: parse coordinates → fetch sources JAR → parse className → read source → create SourceCode
- [ ] [impl] Run test, ensure it passes
- [ ] [test] Write failing test for invalid coordinates, expect `Left(InvalidCoordinates)`
- [ ] [impl] Ensure error propagation works
- [ ] [test] Write failing test for valid artifact but sources JAR missing, expect `Left(SourcesNotAvailable)`
- [ ] [impl] Handle error case, verify test passes
- [ ] [test] Write failing test for valid artifact but missing class, expect `Left(ClassNotFound)`
- [ ] [impl] Ensure all error paths work correctly
- [ ] [setup] Commit: "feat(app): implement SourceCodeService with error handling"

### SourceCodeService Integration Tests

- [ ] [test] Create `test/.../application/SourceCodeServiceIntegrationTest.scala`
- [ ] [test] Write integration test: fetch real source for `org.slf4j:slf4j-api:2.0.9` → `org.slf4j.Logger`
- [ ] [impl] Wire service with real `CoursierArtifactRepository` and `JarFileReader`
- [ ] [impl] Run test, verify it downloads and extracts real Java source
- [ ] [test] Assert source text contains "public interface Logger"
- [ ] [test] Assert source text contains "void info(String msg)"
- [ ] [test] Write integration test for `com.google.guava:guava:32.1.3-jre` → `com.google.common.collect.ImmutableList`
- [ ] [impl] Verify guava source extraction works
- [ ] [setup] Commit: "test: add integration tests for SourceCodeService with real Maven artifacts"

---

## Phase 2.5: Presentation Layer (30 min)

### MCP Tool Definition

- [ ] [impl] Open `presentation/ToolDefinitions.scala`
- [ ] [impl] Add `case class GetSourceInput(coordinates: String, className: String) derives Codec, Schema`
- [ ] [impl] Create `getSourceTool` function mirroring `getDocumentationTool` structure
- [ ] [impl] Use Chimp's `tool("get_source").description(...).input[GetSourceInput].handle(...)`
- [ ] [impl] In handler: call `SourceCodeService.getSource`, map `Either` appropriately
- [ ] [impl] Map `Right(source)` to `Right(source.sourceText)`
- [ ] [impl] Map `Left(error)` to `Left(error.message)`
- [ ] [impl] Add tool description: "Fetch Java source code for a library class"
- [ ] [setup] Commit: "feat(mcp): add get_source tool definition"

### Register Source Tool in MCP Server

- [ ] [impl] Open `presentation/McpServer.scala`
- [ ] [impl] Update `mcpEndpoint` to include both `getDocumentationTool` and `getSourceTool`
- [ ] [impl] Verify list of tools: `List(getDocumentationTool, getSourceTool)`
- [ ] [setup] Commit: "feat(server): register get_source tool in MCP endpoint"

### Wire SourceCodeService in Main

- [ ] [impl] Open `Main.scala`
- [ ] [impl] Instantiate `SourceCodeService` with same repository and reader instances
- [ ] [impl] Pass `sourceCodeService` to tool definitions
- [ ] [impl] Verify dependency injection is correct
- [ ] [setup] Commit: "feat(main): wire up SourceCodeService in application entry point"

---

## Phase 2.6: End-to-End Testing (45 min)

### E2E Test for get_source Happy Path

- [ ] [test] Open `test/.../integration/EndToEndTest.scala`
- [ ] [test] Write E2E test: start server, invoke `get_source` tool with slf4j coordinates
- [ ] [test] Use `GetSourceInput(coordinates = "org.slf4j:slf4j-api:2.0.9", className = "org.slf4j.Logger")`
- [ ] [impl] Run E2E test, debug any wiring issues
- [ ] [test] Assert response is `Right(sourceText)` where sourceText contains "public interface Logger"
- [ ] [test] Assert source contains "void info(String msg)"
- [ ] [test] Assert response time is under 5 seconds
- [ ] [setup] Commit: "test: add E2E test for get_source happy path"

### E2E Test for get_source Error Cases

- [ ] [test] Write E2E test for non-existent artifact: expect error "Artifact not found"
- [ ] [impl] Verify error handling works end-to-end
- [ ] [test] Write E2E test for artifact without sources JAR: expect "Sources JAR not available"
- [ ] [impl] Ensure specific error message includes suggestion to try get_documentation
- [ ] [test] Write E2E test for valid artifact but missing class: expect "Class not found"
- [ ] [impl] Verify all error paths work correctly
- [ ] [setup] Commit: "test: add E2E error tests for get_source tool"

### Run Full Test Suite

- [ ] [test] Run all unit tests - verify they pass
- [ ] [test] Run all integration tests - verify they work with real Maven Central
- [ ] [test] Run all E2E tests - verify complete flow for both tools
- [ ] [impl] Fix any test failures discovered
- [ ] [test] Verify test output is pristine (no warnings, no error logs for successful tests)
- [ ] [setup] Commit any fixes: "fix: resolve test failures in Phase 2"

---

## Phase 2.7: Integration and Polish (30 min)

### Code Quality Review

- [ ] [impl] Review all new files for compiler warnings, fix any found
- [ ] [impl] Verify all new files have `PURPOSE:` comment headers
- [ ] [impl] Check for code duplication between DocumentationService and SourceCodeService
- [ ] [impl] Refactor common patterns if needed (while keeping tests green)
- [ ] [impl] Ensure immutability throughout new code
- [ ] [impl] Verify pure functions in domain/application layers

### Regression Testing

- [ ] [test] Run all Phase 1 tests - ensure get_documentation still works
- [ ] [test] Run all Phase 2 tests - ensure get_source works
- [ ] [test] Test both tools together in single E2E test
- [ ] [test] Verify no performance regression (both tools respond within 5s)

### Error Message Verification

- [ ] [impl] Test each error case manually, verify messages are helpful
- [ ] [impl] Ensure `SourcesNotAvailable` error suggests using `get_documentation`
- [ ] [impl] Verify all error messages include relevant context (coordinates, class name)

### Documentation

- [ ] [impl] Add comments explaining source vs javadoc differences where needed
- [ ] [impl] Document any edge cases deferred to future phases
- [ ] [impl] Update inline documentation for clarity

### Git Hygiene

- [ ] [setup] Review commit history - ensure incremental commits
- [ ] [setup] Verify commit messages are clear and follow TDD pattern
- [ ] [setup] Ensure pre-commit hooks passed on all commits (no --no-verify)
- [ ] [setup] Verify working directory is clean (git status)

### Final Commit

- [ ] [setup] Commit: "docs: complete Phase 2 polish and verification"

---

## Acceptance Checklist

Before marking Phase 2 complete, verify ALL criteria:

### Functional Requirements

- [ ] MCP server has `get_source` tool registered
- [ ] Can fetch source code for `org.slf4j:slf4j-api:2.0.9` → `org.slf4j.Logger`
- [ ] Response contains valid Java source code (interface definition)
- [ ] Source includes expected method signatures (e.g., `void info(String msg)`)
- [ ] Response time under 5 seconds for first request (uncached)
- [ ] Error handling: Returns clear error when sources JAR missing
- [ ] Error handling: Returns clear error when source file not in JAR
- [ ] Phase 1 functionality (`get_documentation`) still works correctly

### Code Quality

- [ ] Port traits properly abstracted (`ArtifactRepository`, `JarContentReader`)
- [ ] `SourceCodeService` mirrors `DocumentationService` structure
- [ ] All functions pure except at edges
- [ ] Immutable data structures
- [ ] No compiler warnings
- [ ] `PURPOSE:` comments on all new files
- [ ] Domain language used in naming

### Testing

- [ ] All existing tests still pass (Phase 1 regression)
- [ ] Unit tests for `ClassName.toSourcePath()`
- [ ] Unit tests for `SourceCodeService` with in-memory implementations
- [ ] Integration tests with real Maven Central sources JARs
- [ ] E2E tests for `get_source` tool via HTTP
- [ ] Test output pristine (no warnings/errors for successful tests)
- [ ] Tests use real artifacts (no mocking)

### Documentation

- [ ] Code comments explain source vs javadoc differences
- [ ] Error messages suggest alternatives (e.g., "try get_documentation")

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

**Testing with Real Data:**
- Use `org.slf4j:slf4j-api:2.0.9` as primary test artifact (stable, has sources)
- Use `com.google.guava:guava:32.1.3-jre` as secondary test artifact
- Let Coursier cache artifacts in `~/.cache/coursier` (persistent across runs)
- NO MOCKING of Maven Central or Coursier (per project guidelines)

**Pattern Reuse from Phase 1:**
- `SourceCodeService` mirrors `DocumentationService` exactly
- Same error handling patterns
- Same port-based architecture
- Same test structure (unit → integration → E2E)

**Task Estimation:**
- Each task should take 15-30 minutes
- If a task takes longer, break it down further
- Commit frequently (every 2-3 tasks minimum)

**Expected Total Time:** 3-4 hours for complete Phase 2

---

**Next Steps After Phase 2:**
1. Mark all tasks complete
2. Update `project-management/issues/JMC-1/tasks.md` - mark Phase 2 complete
3. Run `/iterative-works:ag-implement JMC-1` to start Phase 3
