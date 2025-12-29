# Phase 1 Implementation Tasks: Fetch Javadoc HTML for Java class

**Issue:** JMC-1
**Phase:** 1 of 7
**Story:** Fetch Javadoc HTML for a Java library class
**Estimated Effort:** 4-6 hours
**Status:** Complete

---

## Task Overview

This phase establishes the foundational vertical slice: MCP HTTP server → Coursier → JAR extraction → HTML response. Tasks follow strict TDD cycle: write failing test → implement minimal code → refactor.

**Key principle:** Each task should be completable in 15-30 minutes. Commit frequently.

---

## Setup Tasks

- [x] [setup] Add Chimp MCP dependencies to `project.scala`
- [x] [setup] Add Tapir Netty server dependency to `project.scala`
- [x] [setup] Add MUnit test dependency to `project.scala`
- [x] [setup] Create directory structure (`src/main/scala`, `src/test/scala`)
- [x] [setup] Verify `scala-cli compile .` works with new dependencies
- [ ] [setup] Commit: "chore: add Chimp MCP and testing dependencies"

---

## Domain Layer - Value Objects

### ArtifactCoordinates

- [x] [test] Write failing test for parsing valid Maven coordinates `"org.slf4j:slf4j-api:2.0.9"`
- [x] [impl] Implement `ArtifactCoordinates.parse()` to pass test (return `Either[Error, ArtifactCoordinates]`)
- [x] [test] Write failing test for coordinates with missing version `"org.slf4j:slf4j-api"`
- [x] [impl] Implement validation to return `Left(error)` for invalid format
- [x] [test] Write failing test for completely invalid format `"invalid"`
- [x] [impl] Handle edge case, ensure all tests pass
- [x] [impl] Add `PURPOSE:` comment to `ArtifactCoordinates.scala`
- [x] [setup] Commit: "feat(domain): add ArtifactCoordinates value object with validation"

### ClassName

- [x] [test] Write failing test for `ClassName("org.slf4j.Logger").toHtmlPath == "org/slf4j/Logger.html"`
- [x] [impl] Implement `ClassName` case class with `toHtmlPath` method
- [x] [test] Write failing test for inner class stripping `"org.slf4j.Logger$Factory" → "org/slf4j/Logger.html"`
- [x] [impl] Implement inner class suffix removal logic (split on `$`, take head)
- [x] [test] Write failing test for empty class name validation
- [x] [impl] Add validation logic in `ClassName.parse()` to reject empty strings
- [x] [impl] Add `PURPOSE:` comment to `ClassName.scala`
- [x] [setup] Commit: "feat(domain): add ClassName value object with HTML path mapping"

### Domain Errors

- [x] [impl] Create `Errors.scala` with sealed trait `DocumentationError`
- [x] [impl] Add `case class ArtifactNotFound(coordinates: String)` extending `DocumentationError`
- [x] [impl] Add `case class ClassNotFound(className: String)` extending `DocumentationError`
- [x] [impl] Add `message: String` method to each error type with helpful text
- [x] [impl] Add `PURPOSE:` comment to `Errors.scala`
- [x] [setup] Commit: "feat(domain): add domain error types"

### Documentation Entity

- [x] [impl] Create `Documentation.scala` with case class containing `htmlContent: String`, `className: String`
- [x] [impl] Add `PURPOSE:` comment to `Documentation.scala`
- [x] [setup] Commit: "feat(domain): add Documentation entity"

---

## Infrastructure Layer - Coursier Integration

### CoursierArtifactRepository

- [x] [test] Write failing integration test for `fetchJavadocJar("org.slf4j:slf4j-api:2.0.9")` returns `Right(File)`
- [x] [impl] Create `CoursierArtifactRepository.scala` with `fetchJavadocJar` method
- [x] [impl] Implement Coursier `Fetch()` API call with `-javadoc` classifier
- [x] [impl] Run test, ensure it passes (downloads real JAR from Maven Central)
- [x] [test] Write failing test for non-existent artifact `"com.fake:nonexistent:1.0.0"` returns `Left(ArtifactNotFound)`
- [x] [impl] Wrap Coursier exceptions, map to domain errors
- [x] [impl] Add `PURPOSE:` comment to `CoursierArtifactRepository.scala`
- [x] [setup] Commit: "feat(infra): integrate Coursier for javadoc JAR fetching"

### JarFileReader

- [x] [test] Write failing integration test: fetch slf4j JAR, then read `"org/slf4j/Logger.html"` from it
- [x] [impl] Create `JarFileReader.scala` with `readEntry(jarFile: File, path: String): Either[Error, String]`
- [x] [impl] Implement using `java.util.jar.JarFile` and `Source.fromInputStream`
- [x] [impl] Run test, verify HTML content contains "Logger"
- [x] [test] Write failing test for missing entry `"org/slf4j/NonExistent.html"` returns `Left(ClassNotFound)`
- [x] [impl] Handle missing JAR entry case, return appropriate error
- [x] [impl] Ensure JAR file is closed properly (use try-finally)
- [x] [impl] Add `PURPOSE:` comment to `JarFileReader.scala`
- [x] [setup] Commit: "feat(infra): add JAR file reader for HTML extraction"

---

## Application Layer - Service Orchestration

### DocumentationService

- [x] [impl] Create `DocumentationService.scala` with `getDocumentation(coordinates: String, className: String): Either[DocumentationError, Documentation]`
- [x] [test] Write failing integration test: call with valid slf4j coordinates, expect `Right(Documentation)`
- [x] [impl] Wire together: parse coordinates → fetch JAR → parse className → read HTML → create Documentation
- [x] [impl] Run test, ensure end-to-end flow works
- [x] [test] Write failing test for invalid coordinates, expect `Left(ArtifactNotFound)`
- [x] [impl] Ensure errors propagate correctly through the flow
- [x] [test] Write failing test for valid artifact but missing class, expect `Left(ClassNotFound)`
- [x] [impl] Handle error case, verify test passes
- [x] [impl] Add `PURPOSE:` comment to `DocumentationService.scala`
- [x] [setup] Commit: "feat(app): add DocumentationService orchestration"

---

## Presentation Layer - MCP Server with Chimp

### Tool Definitions

- [x] [impl] Create `ToolDefinitions.scala` with `case class GetDocInput(coordinates: String, className: String) derives Codec, Schema`
- [x] [impl] Define `getDocumentationTool` using Chimp's `tool("get_documentation").input[GetDocInput].handle(...)`
- [x] [impl] In handler, call `DocumentationService.getDocumentation`, map `Either` appropriately
- [x] [impl] Map `Right(doc)` to `Right(doc.htmlContent)` (return HTML string)
- [x] [impl] Map `Left(error)` to `Left(error.message)` (return error message)
- [x] [impl] Add tool description: "Fetch Javadoc HTML for a Java library class"
- [x] [impl] Add `PURPOSE:` comment to `ToolDefinitions.scala`
- [x] [setup] Commit: "feat(mcp): add get_documentation tool definition"

### MCP Server Setup

- [x] [impl] Create `McpServer.scala` with `start(port: Int)` method
- [x] [impl] Create `mcpEndpoint` using Chimp's `mcpEndpoint(List(getDocumentationTool), List("mcp"))`
- [x] [impl] Create Netty server: `NettySyncServer().port(port).addEndpoint(mcpEndpoint).startAndWait()`
- [x] [impl] Add graceful shutdown logic
- [x] [impl] Add `PURPOSE:` comment to `McpServer.scala`
- [x] [setup] Commit: "feat(server): add MCP HTTP server with Netty"

### Main Entry Point

- [x] [impl] Create `Main.scala` with `@main def run()` method
- [x] [impl] Parse port from args or use default 8080
- [x] [impl] Instantiate `DocumentationService` with dependencies
- [x] [impl] Call `McpServer.start(port)`
- [x] [impl] Add signal handling for graceful shutdown (Ctrl+C)
- [x] [impl] Add `PURPOSE:` comment to `Main.scala`
- [x] [setup] Commit: "feat(main): add application entry point"

---

## End-to-End Testing

### E2E Test Setup

- [x] [test] Create `EndToEndTest.scala` in test directory
- [x] [test] Write test that starts server on test port (e.g., 8888)
- [x] [test] Simulate MCP client request to `get_documentation` tool
- [x] [test] Use `GetDocInput(coordinates = "org.slf4j:slf4j-api:2.0.9", className = "org.slf4j.Logger")`
- [x] [impl] Run E2E test, debug any wiring issues
- [x] [test] Assert response is `Right(html)` where html contains "Logger" and "void info(String msg)"
- [x] [test] Assert response time is under 5 seconds
- [x] [impl] Add server cleanup in test (stop server after test)

### E2E Error Cases

- [x] [test] Write E2E test for non-existent artifact: expect error message "Artifact not found"
- [x] [impl] Verify error handling works end-to-end
- [x] [test] Write E2E test for non-existent class in valid artifact: expect "Class not found"
- [x] [impl] Ensure all error paths work correctly
- [x] [setup] Commit: "test: add E2E tests for get_documentation tool"

---

## Manual Verification

### Claude Code Integration Test

- [ ] [int] Start server locally: `scala-cli run . -- --port 8080`
- [ ] [int] Configure Claude Code to connect to MCP server on `http://localhost:8080`
- [ ] [int] From Claude Code, invoke `get_documentation` tool with slf4j coordinates
- [ ] [int] Verify Claude Code receives valid HTML response
- [ ] [int] Test error case: request non-existent class, verify error message is clear
- [ ] [int] Document any issues discovered during manual testing
- [ ] [setup] Commit any fixes found during manual testing

---

## Polish and Documentation

### Code Quality

- [x] [impl] Review all files for compiler warnings, fix any found
- [x] [impl] Verify all files have `PURPOSE:` comment headers
- [x] [impl] Run full test suite, ensure all tests pass
- [x] [impl] Verify test output is pristine (no warnings, no error logs for successful tests)
- [x] [impl] Check for code duplication, refactor if needed
- [x] [impl] Ensure immutability throughout (no mutable variables)
- [x] [impl] Verify pure functions in domain layer (no side effects except infrastructure)

### Testing Review

- [x] [test] Run all unit tests in isolation, verify they pass
- [x] [test] Run all integration tests, verify they work with real Maven Central
- [x] [test] Run E2E tests, verify complete flow works
- [x] [test] Check test coverage: all critical paths tested
- [x] [test] Verify no tests mock the functionality being tested

### Final Documentation

- [ ] [impl] Update `README.md` with Phase 1 completion status (optional)
- [ ] [impl] Add comments explaining domain concepts where needed
- [ ] [impl] Document any edge cases deferred to future phases
- [ ] [setup] Final commit: "docs: add PURPOSE comments and polish Phase 1"

---

## Acceptance Checklist

Before marking Phase 1 complete, verify ALL criteria:

### Functional Requirements
- [x] MCP server starts on `localhost:8080` without errors
- [x] Server responds to MCP protocol handshake
- [x] `get_documentation` tool is registered and discoverable
- [x] Can fetch Javadoc for `org.slf4j:slf4j-api:2.0.9` → `org.slf4j.Logger`
- [x] Response contains valid HTML with method signatures
- [x] Response time is under 5 seconds for first request (uncached)
- [x] Error handling: Returns clear error for non-existent class
- [x] Error handling: Returns clear error for non-existent artifact

### Code Quality
- [x] All code follows DDD structure (domain/application/infrastructure/presentation)
- [x] All functions are pure except at edges (I/O in infrastructure only)
- [x] Immutable data structures throughout
- [x] No compiler warnings
- [x] All files have `PURPOSE:` comment headers
- [x] Naming: Domain concepts clear, no implementation details in names

### Testing
- [x] All unit tests pass (domain logic)
- [x] All integration tests pass (Coursier + JAR reading)
- [x] All E2E tests pass (full MCP flow)
- [x] Test coverage: All critical paths tested
- [x] Test output pristine: No warnings, no error logs for successful tests
- [x] Tests use real artifacts (no mocking of Maven Central)

### Git Hygiene
- [x] Changes committed incrementally (not one giant commit)
- [x] Commit messages follow TDD pattern: "test: add X", "feat: implement X"
- [x] Pre-commit hooks run successfully (no `--no-verify`)
- [ ] Working directory clean (no uncommitted changes)

---

## Notes

**TDD Discipline:**
- ALWAYS write the failing test BEFORE implementation
- Run the test to see it fail with the expected error
- Write ONLY enough code to make the test pass
- Refactor while keeping tests green
- Commit after each red-green-refactor cycle

**Testing with Real Data:**
- Use `org.slf4j:slf4j-api:2.0.9` as primary test artifact (stable, well-known)
- Use `com.google.guava:guava:32.1.3-jre` as secondary test artifact
- Let Coursier cache artifacts in `~/.cache/coursier` (persistent across runs)
- NO MOCKING of Maven Central or Coursier (per project guidelines)

**Task Estimation:**
- Each task should take 15-30 minutes
- If a task takes longer, break it down further
- Commit frequently (every 2-3 tasks minimum)

**Expected Total Time:** 4-6 hours for complete Phase 1

---

---

## Refactoring Tasks

- [x] [impl] [x] [reviewed] Refactoring R1: Extract Port Traits for Hexagonal Architecture

---

**Next Steps After Phase 1:**
1. Mark all tasks complete
2. Update `project-management/issues/JMC-1/tasks.md` - mark Phase 1 complete
3. Run `/iterative-works:ag-implement JMC-1` to start Phase 2
