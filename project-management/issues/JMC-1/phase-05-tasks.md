# Phase 5 Tasks: Handle missing artifacts gracefully

**Issue:** JMC-1
**Phase:** 5 of 7
**Story:** Error handling for missing artifacts
**Estimated Effort:** 2-3 hours

---

## Setup

- [x] Review phase context and analysis documents
- [x] Verify Phases 1-4 are complete and tests pass

---

## Tests (Write First - TDD)

### Unit Tests - Error Message Formatting

- [ ] **Test `ArtifactNotFound` message format**
  - Write test: Verify message includes coordinates
  - Write test: Verify message includes Maven Central suggestion
  - Write test: Verify message includes spelling check suggestion
  - Expected: Multi-line formatted message with all suggestions

- [ ] **Test `JavadocNotAvailable` message format**
  - Add `JavadocNotAvailable` enum case to `DocumentationError`
  - Write test: Verify message includes coordinates
  - Write test: Verify message suggests using `get_source` instead
  - Write test: Verify message explains some libraries don't publish javadoc
  - Expected: Multi-line formatted message with alternative tool suggestion

- [ ] **Test `SourcesNotAvailable` message format**
  - Write test: Verify message includes coordinates
  - Write test: Verify message suggests using `get_documentation` instead
  - Write test: Verify message explains some libraries don't publish sources
  - Expected: Multi-line formatted message with alternative tool suggestion

- [ ] **Test `ClassNotFound` message format**
  - Write test: Verify message includes class name
  - Write test: Verify message suggests checking spelling and capitalization
  - Expected: Multi-line formatted message with helpful hints

- [ ] **Test `InvalidCoordinates` message format**
  - Write test: Verify message includes invalid input
  - Write test: Verify message shows expected format examples
  - Write test: Verify message distinguishes Java (`:`) vs Scala (`::`)
  - Expected: Multi-line formatted message with examples

- [ ] **Test `InvalidClassName` message format**
  - Write test: Verify message includes invalid input
  - Write test: Verify message shows expected format
  - Expected: Multi-line formatted message with example

### Integration Tests - Coursier Error Detection

- [ ] **Research Coursier exception types**
  - Write temporary test to trigger artifact not found
  - Catch exception and log `ex.getClass.getName`
  - Write temporary test to trigger classifier not found
  - Document findings in code comments
  - Expected: Know exact exception types to match against

- [ ] **Test non-existent artifact returns `ArtifactNotFound`**
  - Write test: Request `com.nonexistent:fake-library:1.0.0`
  - Assert: Returns `Left(ArtifactNotFound(...))`
  - Assert: Error message contains coordinates
  - Expected: Coursier error mapped to domain error correctly

- [ ] **Test missing javadoc classifier returns `JavadocNotAvailable`**
  - Write test: Request javadoc for artifact without javadoc JAR
  - Assert: Returns `Left(JavadocNotAvailable(...))`
  - Assert: Error message suggests `get_source`
  - Expected: Classifier-specific error, not generic artifact error

- [ ] **Test missing sources classifier returns `SourcesNotAvailable`**
  - Write test: Request sources for artifact without sources JAR
  - Assert: Returns `Left(SourcesNotAvailable(...))`
  - Assert: Error message suggests `get_documentation`
  - Expected: Classifier-specific error with helpful suggestion

### E2E Tests - Error Responses via MCP

- [ ] **Test MCP error response for non-existent artifact**
  - Write test: Invoke `get_documentation` with `com.nonexistent:fake:1.0.0`
  - Assert: Response has `isError: true`
  - Assert: Error message contains "Artifact not found"
  - Assert: Error message contains coordinates
  - Assert: Error message contains Maven Central suggestion
  - Expected: MCP protocol error with helpful user-facing message

- [ ] **Test MCP error response for missing javadoc**
  - Write test: Invoke `get_documentation` for artifact without javadoc
  - Assert: Response has `isError: true`
  - Assert: Error message suggests trying `get_source`
  - Expected: User receives actionable alternative

- [ ] **Test server stability after errors**
  - Write test: Send error-triggering request
  - Write test: Send valid request immediately after
  - Assert: Second request succeeds
  - Expected: Errors don't crash server or corrupt state

---

## Implementation (After Tests)

### Domain Layer - Error Messages

- [ ] **Add `JavadocNotAvailable` to `DocumentationError` enum**
  - Add case: `JavadocNotAvailable(coordinates: String)`
  - Run tests (should fail - no message implementation yet)
  - Expected: Enum compiles, tests fail on message

- [ ] **Implement enhanced `ArtifactNotFound` message**
  - Update `message` method with multi-line format
  - Include: coordinates, Maven Central link, spelling suggestion
  - Use `.stripMargin` for formatting
  - Run tests (should pass)
  - Expected: Tests pass, message is user-friendly

- [ ] **Implement `JavadocNotAvailable` message**
  - Write multi-line message with suggestions
  - Include: coordinates, explanation, `get_source` alternative
  - Run tests (should pass)
  - Expected: Tests pass, helpful alternative suggested

- [ ] **Implement enhanced `SourcesNotAvailable` message**
  - Update message with multi-line format
  - Include: coordinates, explanation, `get_documentation` alternative
  - Run tests (should pass)
  - Expected: Tests pass, clear guidance provided

- [ ] **Implement enhanced `ClassNotFound` message**
  - Update message with multi-line format
  - Include: class name, spelling suggestion, capitalization hint
  - Run tests (should pass)
  - Expected: Tests pass, debugging hints included

- [ ] **Implement enhanced `InvalidCoordinates` message**
  - Update message with multi-line format
  - Include: invalid input, Java format example, Scala format example
  - Run tests (should pass)
  - Expected: Tests pass, format examples clear

- [ ] **Implement enhanced `InvalidClassName` message**
  - Update message with multi-line format
  - Include: invalid input, expected format example
  - Run tests (should pass)
  - Expected: Tests pass, format guidance provided

### Infrastructure Layer - Error Detection

- [ ] **Update `fetchJar()` to distinguish Coursier errors**
  - Modify `match` on `Failure(ex)` to inspect exception type
  - Map resolution errors to `ArtifactNotFound`
  - Map classifier errors to passed error constructor
  - Add code comments documenting Coursier exception types
  - Run integration tests (should pass)
  - Expected: Correct error type returned for each failure scenario

- [ ] **Update `fetchJavadocJar()` to use `JavadocNotAvailable`**
  - Change error constructor from `ArtifactNotFound.apply` to `JavadocNotAvailable.apply`
  - Run integration tests (should pass)
  - Expected: Missing javadoc returns `JavadocNotAvailable`, not `ArtifactNotFound`

- [ ] **Verify `fetchSourcesJar()` uses `SourcesNotAvailable`**
  - Check error constructor is `SourcesNotAvailable.apply`
  - Run integration tests (should pass)
  - Expected: Missing sources returns correct error type

---

## Integration

- [ ] **Run all unit tests**
  - Execute: `scala-cli test . -- javadocsmcp.domain.ErrorsTest`
  - Expected: All error message tests pass

- [ ] **Run all integration tests**
  - Execute: `scala-cli test . -- javadocsmcp.infrastructure.CoursierArtifactRepositoryTest`
  - Expected: All Coursier error mapping tests pass

- [ ] **Run all E2E tests**
  - Execute: `scala-cli test . -- javadocsmcp.integration.EndToEndTest`
  - Expected: All MCP error response tests pass

- [ ] **Run full test suite**
  - Execute: `scala-cli test .`
  - Expected: All tests pass, no regressions from previous phases

- [ ] **Manual verification: Non-existent artifact**
  - Start MCP server
  - Invoke `get_documentation` with `com.nonexistent:fake:1.0.0`
  - Verify: Error message is helpful and user-friendly
  - Expected: Clear, actionable error message

- [ ] **Manual verification: Server stability**
  - Trigger error with non-existent artifact
  - Immediately invoke with valid artifact
  - Verify: Second request succeeds
  - Expected: Server remains stable after errors

- [ ] **Code review: Error messages**
  - Review all error messages for clarity
  - Check for jargon, technical terms, unhelpful wording
  - Ensure all messages include context (coordinates/className)
  - Expected: All messages pass readability test

---

## Completion Checklist

- [ ] All error messages enhanced with multi-line format
- [ ] `JavadocNotAvailable` error type added and working
- [ ] Coursier exceptions mapped to correct domain errors
- [ ] All tests passing (unit + integration + E2E)
- [ ] Manual testing confirms user-friendly error messages
- [ ] Server stable after error scenarios
- [ ] Code committed with descriptive message
- [ ] Phase 5 marked complete in task index

---

**Estimated Time Breakdown:**
- Setup: 5 min
- Test writing: 90 min (15 tests × 6 min each)
- Implementation: 60 min (error messages + Coursier detection)
- Integration & verification: 25 min
- **Total: ~3 hours**

**Next Phase:** Phase 6 - Handle missing classes within artifacts
