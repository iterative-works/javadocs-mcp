# Phase 6 Context: Handle missing classes within artifacts

**Issue:** JMC-1  
**Phase:** 6 of 7  
**Story:** Story 6 - Error handling for missing classes  
**Estimated Effort:** 2-3 hours  
**Complexity:** Straightforward  

---

## Goals

This phase completes the error handling foundation by providing clear, actionable error messages when a class doesn't exist within a valid artifact JAR. The goal is to help AI assistants inform users about:

1. Class name typos or incorrect spelling
2. Incorrect capitalization (JVM is case-sensitive)
3. Wrong artifact (class might be in a transitive dependency)

After this phase, the MCP server will have **comprehensive error coverage** for all failure scenarios:
- ✅ Artifact not found (Phase 5)
- ✅ Javadoc/Sources JAR not available (Phase 5)
- ✅ Class not found in JAR (Phase 6) ← **THIS PHASE**

---

## Scope

### In Scope

1. **Pre-validate class existence before extraction:**
   - Check if expected file path exists in JAR entries
   - Distinguish "file missing" from "JAR read error"
   - Return helpful error when class not found

2. **Enhance `ClassNotFound` error message:**
   - Already exists in `DocumentationError` enum
   - Current message is basic - needs improvement
   - Add capitalization hints
   - Add dependency verification suggestions

3. **Test missing class scenarios comprehensively:**
   - Unit tests: Class existence checking logic
   - Integration tests: Real artifacts with non-existent classes
   - E2E tests: Error responses for wrong class names

### Out of Scope

- **Listing all classes in JAR** (performance concern)
  - Don't enumerate all entries to suggest alternatives
  - Simple existence check only

- **Fuzzy matching or suggestions** (complex heuristics)
  - Don't try to guess "Did you mean Logger not logger?"
  - Generic capitalization hint is sufficient for MVP

- **TASTy-based class search for Scala** (future enhancement)
  - Current approach (file path mapping) is sufficient
  - TASTy lookup deferred to post-MVP

- **Package-info or module-info special cases** (edge cases)
  - Focus on regular classes for MVP

---

## Dependencies

### Prerequisites from Previous Phases

**Phase 1-5 must be complete:**
- ✅ `JarFileReader.readEntry()` returns `Left(ClassNotFound(path))` when entry missing
- ✅ `ClassNotFound` error type exists in `DocumentationError` enum
- ✅ Error propagation through service layer works
- ✅ Phase 5 established enhanced error message pattern (multi-line `.stripMargin`)

### What Already Works

**Class existence checking is partially implemented:**

Current `JarFileReader.readEntry()`:
```scala
Option(jar.getEntry(htmlPath)) match {
  case Some(entry) => /* read and return content */
  case None => Left(ClassNotFound(htmlPath))
}
```

**This already returns `ClassNotFound` when entry missing!**

**However, the error message is basic:**
```scala
case ClassNotFound(className) => 
  s"""Class not found in JAR: $className
     |
     |Please check:
     |- Class name spelling and capitalization (case-sensitive)
     |- Class is part of this artifact (not a transitive dependency)""".stripMargin
```

**Current state assessment:**
- ✅ Error detection works (JAR entry not found)
- ✅ Error propagates correctly through layers
- ✅ Error message already enhanced in Phase 5
- ⚠️ Message could be more specific about capitalization

### What Needs Improvement

**The `ClassNotFound` error message was already improved in Phase 5, so the main work is:**

1. **Verify the error detection works correctly**
   - Existing tests might not cover "class not found" scenarios
   - Need integration tests with real artifacts and wrong class names

2. **Consider minor message enhancements** (optional)
   - Current message is already good (from Phase 5)
   - Could add: "Note: Class names are case-sensitive in Java/Scala"
   - Could add: "Verify the class exists in this artifact's javadoc/sources"

3. **Add comprehensive tests**
   - Integration tests: Real artifacts with non-existent classes
   - E2E tests: Verify MCP error responses for wrong class names

---

## Technical Approach

### 1. Verify Current Implementation Works

**The good news:** Most of the work is already done!

`JarFileReader.readEntry()` already:
- Checks if entry exists: `Option(jar.getEntry(htmlPath))`
- Returns `ClassNotFound` error when missing
- Error message was enhanced in Phase 5

**What we need to verify:**
- Does this work correctly for all file types? (HTML, Java, Scala)
- Are there any edge cases where the error isn't returned?
- Do existing tests cover this scenario?

### 2. Optional: Enhance `ClassNotFound` Message Further

**Current message (from Phase 5):**
```scala
case ClassNotFound(className) => 
  s"""Class not found in JAR: $className
     |
     |Please check:
     |- Class name spelling and capitalization (case-sensitive)
     |- Class is part of this artifact (not a transitive dependency)""".stripMargin
```

**Potential enhancement:**
```scala
case ClassNotFound(className) => 
  s"""Class not found in JAR: $className
     |
     |Please check:
     |- Spelling: Ensure the fully qualified class name is correct
     |- Capitalization: Class names are case-sensitive (Logger ≠ logger)
     |- Artifact: Verify this class is in this artifact (not a transitive dependency)
     |- Inner classes: Use outer class name (Foo$$Bar → Foo)""".stripMargin
```

**Decision point:** Is the enhancement worth it?
- Current message already mentions capitalization
- Inner class hint might be useful (from analysis.md research)
- But don't over-engineer - current message is probably sufficient

**Recommendation:** Keep current message, add tests to verify it works.

### 3. Test Strategy: Focus on Integration and E2E

**Unit tests are minimal** (logic already tested in Phase 5):
- `JarFileReader.readEntry()` returns `ClassNotFound` when entry missing
- Already covered by existing `JarFileReaderTest`

**Integration tests are the focus:**
- Use real artifacts from Maven Central
- Request non-existent class → verify `ClassNotFound` error
- Request class with wrong capitalization → verify `ClassNotFound` error
- Verify error message contains the class name

**E2E tests verify MCP protocol:**
- Invoke `get_documentation` with wrong class → error response
- Invoke `get_source` with wrong class → error response
- Verify error message includes suggestions

### 4. Implementation Plan

**Option A: Minimal approach (recommended)**
1. Add integration tests for "class not found" scenarios
2. Add E2E tests for MCP error responses
3. Verify existing error message is sufficient
4. Done - no code changes needed!

**Option B: Enhanced message approach**
1. Update `ClassNotFound.message` with minor improvements
2. Write unit test for new message
3. Add integration tests
4. Add E2E tests

**Recommendation:** Start with Option A. If manual testing reveals message is insufficient, upgrade to Option B.

---

## Files to Modify

### Option A: Minimal Approach (Tests Only)

**No production code changes needed!**

Test files:
- `src/test/scala/javadocsmcp/application/DocumentationServiceTest.scala` - Add class not found test
- `src/test/scala/javadocsmcp/application/SourceCodeServiceTest.scala` - Add class not found test
- `src/test/scala/javadocsmcp/infrastructure/JarFileReaderTest.scala` - Verify error behavior
- `src/test/scala/javadocsmcp/infrastructure/CoursierArtifactRepositoryTest.scala` - Integration tests
- `src/test/scala/javadocsmcp/integration/EndToEndTest.scala` - E2E tests

### Option B: Enhanced Message (Minor Code + Tests)

Production code:
- `src/main/scala/javadocsmcp/domain/Errors.scala` - Update `ClassNotFound.message` (~5 lines)

Test files: Same as Option A

---

## Testing Strategy

### Unit Tests

**Verify existing error behavior works:**

**`JarFileReaderTest.scala` (verify existing test or add):**
```scala
test("readEntry returns ClassNotFound when entry doesn't exist in JAR") {
  val jarFile = /* temp JAR with known entries */
  val reader = JarFileReader()
  
  val result = reader.readEntry(jarFile, "org/nonexistent/Class.html")
  
  assert(result.isLeft)
  result.left.foreach { error =>
    assert(error == DocumentationError.ClassNotFound("org/nonexistent/Class.html"))
  }
}
```

**`DocumentationServiceTest.scala` (add new test):**
```scala
test("getDocumentation returns ClassNotFound when class not in JAR") {
  val coords = ArtifactCoordinates.parse("org.slf4j:slf4j-api:2.0.9").toOption.get
  val className = ClassName.parse("org.slf4j.NonExistentClass").toOption.get
  
  // In-memory repository returns JAR without this class
  val jarFile = /* JAR with only Logger.html */
  inMemoryRepo.setJavadocJar(coords, jarFile)
  
  val result = service.getDocumentation(coords, className, None)
  
  assert(result.isLeft)
  result.left.foreach { error =>
    assert(error.isInstanceOf[DocumentationError.ClassNotFound])
  }
}
```

**Coverage:** ~3 new unit tests (if not already covered)

### Integration Tests

**Use real Maven Central artifacts with non-existent classes:**

**`CoursierArtifactRepositoryTest.scala` or new `DocumentationServiceIntegrationTest.scala`:**
```scala
test("get documentation for non-existent class returns ClassNotFound") {
  val coords = ArtifactCoordinates.parse("org.slf4j:slf4j-api:2.0.9").toOption.get
  val className = ClassName.parse("org.slf4j.NonExistentClass").toOption.get
  
  val repository = CoursierArtifactRepository()
  val reader = JarFileReader()
  val service = DocumentationService(repository, reader)
  
  val result = service.getDocumentation(coords, className, None)
  
  assert(result.isLeft)
  result.left.foreach { error =>
    assert(error.isInstanceOf[DocumentationError.ClassNotFound])
    assert(error.message.contains("org.slf4j.NonExistentClass"))
    assert(error.message.contains("capitalization"))
  }
}
```

**Test capitalization scenario:**
```scala
test("get documentation for lowercase class name returns ClassNotFound") {
  val coords = ArtifactCoordinates.parse("org.slf4j:slf4j-api:2.0.9").toOption.get
  val className = ClassName.parse("org.slf4j.logger").toOption.get  // lowercase 'l'
  
  val repository = CoursierArtifactRepository()
  val reader = JarFileReader()
  val service = DocumentationService(repository, reader)
  
  val result = service.getDocumentation(coords, className, None)
  
  assert(result.isLeft)
  result.left.foreach { error =>
    assert(error.isInstanceOf[DocumentationError.ClassNotFound])
    // Verify helpful message about case-sensitivity
  }
}
```

**Coverage:** ~4 new integration tests (2 for docs, 2 for source)

### E2E Tests

**Verify MCP error responses for missing classes:**

**`EndToEndTest.scala`:**
```scala
test("get_documentation with non-existent class returns error") {
  val serverHandle = McpServer.startAsync(8080)
  try {
    val request = Json.obj(
      "coordinates" := "org.slf4j:slf4j-api:2.0.9",
      "className" := "org.slf4j.NonExistentClass"
    )
    
    val response = invokeToolAndGetResponse("get_documentation", request)
    
    assert(response.isError)
    val errorMsg = extractErrorMessage(response)
    assert(errorMsg.contains("Class not found"))
    assert(errorMsg.contains("org.slf4j.NonExistentClass"))
    assert(errorMsg.contains("capitalization"))
  } finally {
    serverHandle.stop()
  }
}
```

**Test wrong capitalization:**
```scala
test("get_documentation with incorrect capitalization returns error") {
  val serverHandle = McpServer.startAsync(8080)
  try {
    val request = Json.obj(
      "coordinates" := "org.slf4j:slf4j-api:2.0.9",
      "className" := "org.slf4j.logger"  // lowercase 'l'
    )
    
    val response = invokeToolAndGetResponse("get_documentation", request)
    
    assert(response.isError)
    val errorMsg = extractErrorMessage(response)
    assert(errorMsg.contains("Class not found"))
    assert(errorMsg.contains("case-sensitive"))
  } finally {
    serverHandle.stop()
  }
}
```

**Coverage:** ~4 new E2E tests (2 for docs, 2 for source)

### Total New Tests

- Unit: ~3 tests (verify existing behavior)
- Integration: ~4 tests (real artifacts with wrong classes)
- E2E: ~4 tests (MCP error responses)
- **Total: ~11 new tests**

---

## Acceptance Criteria

**Story 6 is complete when:**

1. ✅ **Non-existent class returns clear error:**
   - Error message includes class name
   - Error message suggests checking spelling
   - Error message mentions case-sensitivity

2. ✅ **Capitalization errors are clear:**
   - `org.slf4j.logger` (wrong case) returns error
   - Error message explicitly mentions case-sensitivity
   - Works for both documentation and source tools

3. ✅ **Error message is actionable:**
   - Suggests checking artifact is correct
   - Mentions inner class handling (if enhanced)
   - Follows multi-line format from Phase 5

4. ✅ **All scenarios are tested:**
   - Integration tests with real non-existent classes
   - E2E tests via MCP protocol
   - Both `get_documentation` and `get_source` tools
   - All tests passing

5. ✅ **Server remains stable:**
   - Errors don't crash the server
   - Subsequent requests work normally
   - E2E tests verify stability

---

## Implementation Tasks

### Task 1: Verify Existing Implementation (30 min)

**Goal:** Confirm that `JarFileReader.readEntry()` already handles missing classes correctly.

**Steps:**
1. Review `JarFileReader.scala` implementation
2. Check existing `JarFileReaderTest.scala` for coverage
3. Manually test: fetch real artifact, request non-existent class
4. Confirm error propagates correctly

**Deliverable:** Confirmation that error handling works, or identify gaps.

### Task 2: Add Integration Tests for Missing Classes (45 min)

**Steps:**
1. Add test to `DocumentationServiceIntegrationTest.scala`:
   - Real artifact (org.slf4j:slf4j-api:2.0.9)
   - Non-existent class (org.slf4j.NonExistentClass)
   - Verify `ClassNotFound` error
2. Add test for capitalization error:
   - Same artifact
   - Wrong capitalization (org.slf4j.logger)
   - Verify error message
3. Repeat for `SourceCodeServiceIntegrationTest.scala`

**Tests:** 4 integration tests
**Files:** `DocumentationServiceIntegrationTest.scala`, `SourceCodeServiceIntegrationTest.scala`

### Task 3: Add E2E Tests for MCP Error Responses (30 min)

**Steps:**
1. Add test to `EndToEndTest.scala`:
   - Invoke `get_documentation` with non-existent class
   - Verify error response structure
   - Verify error message content
2. Add test for `get_source` with non-existent class
3. Add tests for capitalization errors

**Tests:** 4 E2E tests
**Files:** `EndToEndTest.scala`

### Task 4: (Optional) Enhance ClassNotFound Message (15 min)

**Only if manual testing reveals message is insufficient.**

**Steps:**
1. Update `Errors.scala` `ClassNotFound.message`
2. Add unit test for new message format
3. Verify integration tests still pass

**Tests:** 1 unit test (if changes made)
**Files:** `Errors.scala`, `ErrorsTest.scala`

### Task 5: Manual Verification (15 min)

**Steps:**
1. Start MCP server
2. Invoke with non-existent class via curl or MCP client
3. Verify error message is helpful and clear
4. Test capitalization error scenario
5. Try subsequent requests (stability check)

**Deliverable:** Confirmation that error messages help users debug class issues.

---

## Estimated Breakdown

| Task | Estimated Time |
|------|----------------|
| 1. Verify existing implementation | 30 min |
| 2. Add integration tests | 45 min |
| 3. Add E2E tests | 30 min |
| 4. (Optional) Enhance message | 15 min |
| 5. Manual verification | 15 min |
| **Total** | **2 hours 15 min** |

**Confidence:** Very High - most work is writing tests, not changing code.

---

## Risks and Mitigation

### Risk 1: Tests Already Exist

**Risk:** Integration/E2E tests for "class not found" might already exist.

**Impact:** Low - duplicate tests are easy to identify and merge.

**Mitigation:**
- Check existing tests first (Task 1)
- If tests exist, verify coverage and enhance if needed
- Remove any redundant new tests

**Likelihood:** Medium - Phase 5 might have added some coverage.

### Risk 2: Error Message Insufficient

**Risk:** Current `ClassNotFound` message might not be helpful enough.

**Impact:** Low - easy to enhance with Task 4.

**Mitigation:**
- Start with manual testing (Task 5)
- If message unclear, enhance per Option B
- Get user feedback if uncertain

**Likelihood:** Low - Phase 5 message is already quite good.

### Risk 3: Edge Cases in JAR Entry Lookup

**Risk:** Some JAR formats might not return `None` for missing entries.

**Impact:** Medium - errors might not be detected correctly.

**Mitigation:**
- Integration tests with real artifacts will catch this
- Test with both Java and Scala artifacts
- Test with both javadoc and sources JARs

**Likelihood:** Very Low - `jar.getEntry()` is standard JVM API.

---

## Success Metrics

**Phase 6 is successful if:**

1. ✅ Non-existent class returns `ClassNotFound` error (verified by tests)
2. ✅ Error message is clear and actionable (mentions capitalization, spelling)
3. ✅ All tests passing (~11 new tests)
4. ✅ Server stable after errors (E2E tests confirm)
5. ✅ Manual testing confirms messages help users debug

**User experience improvement:**
- Before: Generic "Class not found" (if even tested)
- After: Comprehensive tests ensure error is caught and message is helpful

---

## Next Steps After Phase 6

**Phase 7: In-memory caching**
- Cache both successful results and errors
- Error cache might have shorter TTL
- Don't re-fetch non-existent classes on every request

**Post-MVP enhancements:**
- Fuzzy class name matching ("Did you mean Logger?")
- TASTy-based class search for Scala artifacts
- Listing available classes in JAR (for debugging)

---

## Notes for Implementer

**Quick wins:**
- Most work is already done by Phase 5!
- Main task is adding comprehensive tests
- Error detection already works in `JarFileReader`

**Be careful with:**
- Check existing test coverage first (avoid duplicates)
- Use real Maven Central artifacts for integration tests
- Verify error message shows actual class name (not file path)

**Don't over-engineer:**
- Current error message is likely sufficient
- Don't add fuzzy matching (complex, low ROI for MVP)
- Don't enumerate all JAR entries (performance concern)
- Keep it simple: detect missing class, return clear error

**Testing focus:**
- Integration tests are most valuable (real artifacts)
- E2E tests verify MCP protocol works
- Unit tests probably already cover the basics

**Remember:**
- This completes error handling for MVP
- After Phase 6, all error scenarios are covered
- Phase 7 (caching) is final MVP phase

---

**Phase 6 Context Status:** Ready for Implementation

**Dependencies:** ✅ Phases 1-5 complete  
**Blockers:** None  
**Key Insight:** Most implementation already exists, focus on comprehensive testing  
**Next Action:** Start Task 1 (Verify existing implementation)
