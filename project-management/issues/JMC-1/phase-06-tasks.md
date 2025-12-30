# Phase 6 Tasks: Handle missing classes within artifacts

**Issue:** JMC-1
**Phase:** 6 of 7
**Story:** Error handling for missing classes
**Estimated Effort:** 2-3 hours

---

## Setup

- [ ] Review phase context document (phase-06-context.md)
- [ ] Review analysis document for Story 6 details
- [ ] Verify Phases 1-5 are complete and tests pass
- [ ] Confirm error message format from Phase 5 works

---

## Tests (Write First - TDD)

### Unit Tests - Class Existence Checking

- [ ] **[test] Verify `JarFileReader.readEntry()` returns `ClassNotFound` when entry missing**
  - Write test: Create temp JAR with known entries
  - Write test: Request non-existent entry path
  - Assert: Returns `Left(ClassNotFound(path))`
  - Expected: Existing behavior verified by test

- [ ] **[test] Verify `ClassNotFound` error message format**
  - Write test: Verify message includes class name
  - Write test: Verify message mentions case-sensitivity
  - Write test: Verify message suggests checking spelling
  - Write test: Verify message suggests checking artifact
  - Expected: Message already enhanced in Phase 5, tests confirm format

### Integration Tests - Real Artifacts with Missing Classes

- [ ] **[test] Documentation service returns `ClassNotFound` for non-existent class**
  - Write test: Use org.slf4j:slf4j-api:2.0.9 (real artifact)
  - Write test: Request org.slf4j.NonExistentClass
  - Assert: Returns `Left(ClassNotFound(...))`
  - Assert: Error message contains "NonExistentClass"
  - Assert: Error message contains "capitalization"
  - Expected: Integration test with real Maven Central artifact

- [ ] **[test] Documentation service returns `ClassNotFound` for wrong capitalization**
  - Write test: Use org.slf4j:slf4j-api:2.0.9
  - Write test: Request org.slf4j.logger (lowercase 'l')
  - Assert: Returns `Left(ClassNotFound(...))`
  - Assert: Error message mentions case-sensitivity
  - Expected: Helps users debug capitalization errors

- [ ] **[test] Source code service returns `ClassNotFound` for non-existent class**
  - Write test: Use org.slf4j:slf4j-api:2.0.9
  - Write test: Request non-existent class
  - Assert: Returns `Left(ClassNotFound(...))`
  - Expected: Sources handle missing classes same as documentation

- [ ] **[test] Source code service returns `ClassNotFound` for wrong capitalization**
  - Write test: Use org.slf4j:slf4j-api:2.0.9
  - Write test: Request wrong capitalization
  - Assert: Returns `Left(ClassNotFound(...))`
  - Expected: Sources detect capitalization errors

### E2E Tests - MCP Error Responses for Missing Classes

- [ ] **[test] MCP `get_documentation` with non-existent class returns error**
  - Write test: Start MCP server
  - Write test: Invoke get_documentation with org.slf4j:slf4j-api:2.0.9
  - Write test: Request org.slf4j.NonExistentClass
  - Assert: Response has `isError: true`
  - Assert: Error message contains "Class not found"
  - Assert: Error message contains class name
  - Assert: Error message contains "capitalization"
  - Expected: MCP protocol error with helpful message

- [ ] **[test] MCP `get_documentation` with wrong capitalization returns error**
  - Write test: Invoke with org.slf4j.logger (lowercase)
  - Assert: Response has `isError: true`
  - Assert: Error message mentions case-sensitivity
  - Expected: User-friendly capitalization hint

- [ ] **[test] MCP `get_source` with non-existent class returns error**
  - Write test: Invoke get_source with non-existent class
  - Assert: Response has `isError: true`
  - Assert: Error message contains "Class not found"
  - Expected: Source tool handles missing classes correctly

- [ ] **[test] MCP `get_source` with wrong capitalization returns error**
  - Write test: Invoke with wrong capitalization
  - Assert: Response has `isError: true`
  - Assert: Error message helpful
  - Expected: Consistent error handling across tools

- [ ] **[test] Server remains stable after class not found errors**
  - Write test: Send request with non-existent class
  - Write test: Send valid request immediately after
  - Assert: Second request succeeds
  - Expected: Errors don't corrupt server state

---

## Implementation (After Tests)

### Domain Layer - Verify Error Message

- [ ] **[impl] Review current `ClassNotFound` error message**
  - Check: Message format from Phase 5
  - Check: Includes class name, spelling hint, capitalization hint
  - Decision: Enhance message OR keep as-is
  - Expected: Message already sufficient from Phase 5

- [ ] **[impl] (Optional) Enhance `ClassNotFound` message if needed**
  - Only if review shows message insufficient
  - Add: Inner class hint if useful
  - Add: More specific capitalization example
  - Run tests (should pass)
  - Expected: Enhanced message if needed, otherwise skip

### Infrastructure Layer - Verify Error Detection

- [ ] **[impl] Review `JarFileReader.readEntry()` implementation**
  - Check: `Option(jar.getEntry(path)) match { case None => Left(ClassNotFound(...)) }`
  - Verify: Works for all file types (.html, .java, .scala)
  - Run unit tests (should pass)
  - Expected: Existing implementation works correctly

- [ ] **[impl] Verify error propagates through service layer**
  - Check: `DocumentationService.getDocumentation()` propagates `ClassNotFound`
  - Check: `SourceCodeService.getSource()` propagates `ClassNotFound`
  - Run integration tests (should pass)
  - Expected: Error flows correctly to presentation layer

---

## Integration

- [ ] **Run all unit tests**
  - Execute: `scala-cli test . -- javadocsmcp.infrastructure.JarFileReaderTest`
  - Expected: Class existence checking tests pass

- [ ] **Run all integration tests**
  - Execute: `scala-cli test . -- javadocsmcp.application.*Test`
  - Expected: All missing class scenarios tested with real artifacts

- [ ] **Run all E2E tests**
  - Execute: `scala-cli test . -- javadocsmcp.integration.EndToEndTest`
  - Expected: All MCP error response tests pass

- [ ] **Run full test suite**
  - Execute: `scala-cli test .`
  - Expected: All tests pass, no regressions

- [ ] **Manual verification: Non-existent class**
  - Start MCP server
  - Invoke `get_documentation` with valid artifact, invalid class
  - Verify: Error message is clear and actionable
  - Expected: User understands the issue and how to fix it

- [ ] **Manual verification: Capitalization error**
  - Invoke with org.slf4j.logger (lowercase)
  - Verify: Error message mentions case-sensitivity
  - Expected: User realizes capitalization mistake

- [ ] **Manual verification: Server stability**
  - Trigger class not found error
  - Immediately invoke with valid class
  - Verify: Second request succeeds
  - Expected: Server handles errors gracefully

- [ ] **Code review: Test coverage**
  - Review: All missing class scenarios covered
  - Review: Both documentation and source tools tested
  - Review: Both unit, integration, and E2E levels
  - Expected: Comprehensive test coverage

---

## Completion Checklist

- [ ] Existing `ClassNotFound` error behavior verified by tests
- [ ] Integration tests with real artifacts pass
- [ ] E2E tests via MCP protocol pass
- [ ] Error message is user-friendly and actionable
- [ ] Server stable after class not found errors
- [ ] Both `get_documentation` and `get_source` handle missing classes
- [ ] Full test suite passes (unit + integration + E2E)
- [ ] Manual testing confirms good UX
- [ ] Code committed with descriptive message
- [ ] Phase 6 marked complete in task index

---

**Estimated Time Breakdown:**
- Setup: 5 min
- Test writing: 90 min (11 tests × ~8 min each)
- Implementation review/verification: 30 min (mostly confirming existing code works)
- Optional message enhancement: 15 min (if needed)
- Integration & verification: 30 min
- **Total: ~2.5 hours**

**Key Insight:** Most implementation already exists from Phase 5. This phase focuses on comprehensive testing to verify the error handling works correctly for missing classes.

**Next Phase:** Phase 7 - In-memory caching for repeated lookups

---

**Notes:**
- Phase 6 is primarily a testing phase
- The error detection (JAR entry not found) already works
- The error message was already enhanced in Phase 5
- Main deliverable is comprehensive test coverage
- Optional enhancement if manual testing reveals message gaps
