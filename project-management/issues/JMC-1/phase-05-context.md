# Phase 5 Context: Handle missing artifacts gracefully

**Issue:** JMC-1  
**Phase:** 5 of 7  
**Story:** Story 5 - Error handling for missing artifacts  
**Estimated Effort:** 2-3 hours  
**Complexity:** Straightforward  

---

## Goals

This phase enhances error handling to provide clear, actionable error messages when:
1. Artifact does not exist in any Maven repository
2. Artifact exists but javadoc JAR is not published
3. Artifact exists but sources JAR is not published

The goal is **production readiness** - transforming technical Coursier exceptions into user-friendly messages that help AI assistants inform users about typos or unavailable libraries.

---

## Scope

### In Scope

1. **Improve error messages in `DocumentationError` enum:**
   - Add helpful suggestions to `ArtifactNotFound.message`
   - Add alternative tool suggestion to `SourcesNotAvailable.message`
   - Ensure messages include the artifact coordinates

2. **Distinguish Coursier errors in `CoursierArtifactRepository`:**
   - Detect "artifact doesn't exist" vs "classifier not available"
   - Map Coursier exceptions to correct `DocumentationError` types
   - Add logging for debugging without exposing internals

3. **Test error scenarios comprehensively:**
   - Unit tests: Error message formatting
   - Integration tests: Real non-existent artifacts
   - E2E tests: Error responses via MCP protocol

### Out of Scope

- Class-level error handling (Phase 6)
- Retry logic or fallback repositories
- Custom error codes (MCP error messages are sufficient for MVP)
- Network timeout handling (Coursier defaults are acceptable)
- Javadoc JAR availability detection (only handle error when fetch fails)

---

## Dependencies

### Prerequisites from Previous Phases

**Phase 1-4 must be complete:**
- ✅ `DocumentationError` enum exists with basic error types
- ✅ `CoursierArtifactRepository.fetchJavadocJar()` returns `Either[DocumentationError, File]`
- ✅ `CoursierArtifactRepository.fetchSourcesJar()` returns `Either[DocumentationError, File]`
- ✅ Error propagation through service layer via `Either` monads
- ✅ MCP tools convert `Left(error)` to error responses

### What Already Works

**Error handling foundation is solid:**
- Domain errors defined as Scala 3 enum with `message` method
- Infrastructure catches `Throwable` and maps to domain errors
- Services propagate errors through `for-comprehension` chains
- Presentation layer converts `Left(error.message)` to MCP error responses

**Current error coverage (from existing tests):**
- ✅ Invalid coordinates format → `InvalidCoordinates`
- ✅ Invalid class name format → `InvalidClassName`
- ✅ Artifact not found → `ArtifactNotFound` (basic message)
- ✅ Sources not available → `SourcesNotAvailable` (basic message)
- ✅ Class not found in JAR → `ClassNotFound`

### What Needs Improvement

**Error messages are too terse:**

Current `ArtifactNotFound`:
```scala
case ArtifactNotFound(coordinates) => 
  s"Artifact not found: $coordinates"
```

**Should suggest next steps:**
```scala
case ArtifactNotFound(coordinates) => 
  s"""Artifact not found: $coordinates
     |
     |Please check:
     |- Spelling of groupId, artifactId, and version
     |- Artifact exists on Maven Central: https://search.maven.org/
     |- Version number is correct""".stripMargin
```

Current `SourcesNotAvailable`:
```scala
case SourcesNotAvailable(coordinates) => 
  s"Sources JAR not available for: $coordinates. Try using get_documentation instead."
```

**Should be more helpful:**
```scala
case SourcesNotAvailable(coordinates) => 
  s"""Sources JAR not available for: $coordinates
     |
     |Some libraries don't publish sources to Maven Central.
     |Try using get_documentation instead to view the API documentation.""".stripMargin
```

**Coursier error distinction is missing:**

Current implementation catches all `Throwable`:
```scala
case Failure(_) => Left(errorConstructor(s"$org:$name:$ver"))
```

**Should distinguish error types:**
- Artifact resolution failed → `ArtifactNotFound`
- Artifact exists but classifier missing → Classifier-specific error (`SourcesNotAvailable`, new `JavadocNotAvailable`)

---

## Technical Approach

### 1. Add `JavadocNotAvailable` Error Type

**Problem:** Currently `ArtifactNotFound` is used for both "artifact doesn't exist" and "javadoc classifier missing".

**Solution:** Add new error type to `DocumentationError` enum:

```scala
enum DocumentationError:
  case ArtifactNotFound(coordinates: String)
  case JavadocNotAvailable(coordinates: String)  // NEW
  case SourcesNotAvailable(coordinates: String)
  case ClassNotFound(className: String)
  case InvalidCoordinates(input: String)
  case InvalidClassName(input: String)
```

**Message:**
```scala
case JavadocNotAvailable(coordinates) => 
  s"""Javadoc JAR not available for: $coordinates
     |
     |Some libraries don't publish javadoc to Maven Central.
     |Try using get_source instead to view the source code.""".stripMargin
```

### 2. Improve Coursier Error Detection

**Current code:**
```scala
Try {
  // ... coursier fetch ...
} match
  case Success(file) => Right(file)
  case Failure(_) => Left(errorConstructor(s"$org:$name:$ver"))
```

**Problem:** All failures return same error constructor (loses information).

**Solution:** Inspect Coursier exception types and map appropriately:

```scala
Try {
  // ... coursier fetch ...
} match
  case Success(file) => Right(file)
  case Failure(ex) => 
    // Coursier throws different exceptions for different failures
    val coordsStr = s"$org:$name:$ver"
    ex match
      case _: coursier.error.ResolutionError => 
        // Artifact doesn't exist in repository
        Left(DocumentationError.ArtifactNotFound(coordsStr))
      case _ if ex.getMessage.contains("not found") =>
        // Classifier not found (artifact exists, but javadoc/sources missing)
        Left(errorConstructor(coordsStr))
      case _ =>
        // Unknown error - log for debugging
        // TODO: Add logging here
        Left(errorConstructor(coordsStr))
```

**Research needed:** Identify exact Coursier exception types (see implementation tasks below).

### 3. Update `fetchJavadocJar()` Signature

**Current:**
```scala
def fetchJavadocJar(coords: ArtifactCoordinates, scalaVersion: String = "3"): Either[DocumentationError, File] =
  fetchJar(coords, scalaVersion, Classifier("javadoc"), ArtifactNotFound.apply)
```

**Problem:** Passes `ArtifactNotFound.apply` but should pass `JavadocNotAvailable.apply`.

**Solution:**
```scala
def fetchJavadocJar(coords: ArtifactCoordinates, scalaVersion: String = "3"): Either[DocumentationError, File] =
  fetchJar(coords, scalaVersion, Classifier("javadoc"), JavadocNotAvailable.apply)
```

### 4. Enhance Error Messages

**Update all error messages in `DocumentationError.message`:**

```scala
def message: String = this match
  case ArtifactNotFound(coordinates) => 
    s"""Artifact not found: $coordinates
       |
       |Please check:
       |- Spelling of groupId, artifactId, and version
       |- Artifact exists on Maven Central: https://search.maven.org/
       |- Version number is correct""".stripMargin
       
  case JavadocNotAvailable(coordinates) => 
    s"""Javadoc JAR not available for: $coordinates
       |
       |Some libraries don't publish javadoc to Maven Central.
       |Try using get_source instead to view the source code.""".stripMargin
       
  case SourcesNotAvailable(coordinates) => 
    s"""Sources JAR not available for: $coordinates
       |
       |Some libraries don't publish sources to Maven Central.
       |Try using get_documentation instead to view the API documentation.""".stripMargin
       
  case ClassNotFound(className) => 
    s"""Class not found in JAR: $className
       |
       |Please check:
       |- Class name spelling and capitalization (case-sensitive)
       |- Class is part of this artifact (not a transitive dependency)""".stripMargin
       
  case InvalidCoordinates(input) => 
    s"""Invalid Maven coordinates format: $input
       |
       |Expected formats:
       |- Java: groupId:artifactId:version (e.g., org.slf4j:slf4j-api:2.0.9)
       |- Scala: groupId::artifactId:version (e.g., org.typelevel::cats-effect:3.5.4)""".stripMargin
       
  case InvalidClassName(input) => 
    s"""Invalid class name: $input
       |
       |Expected format: fully qualified class name (e.g., org.slf4j.Logger)""".stripMargin
```

### 5. Add Logging (Optional for MVP)

**Consideration:** Should we log errors for debugging?

**Options:**
- **A:** Add structured logging with `slf4j` (future-proof)
- **B:** Use `System.err.println` for MVP (simple, no dependencies)
- **C:** No logging for MVP (defer to Phase 7 or later)

**Recommendation:** Option C for MVP - error messages already contain enough info.  
If debugging needed, can add logging in post-MVP iteration.

---

## Files to Modify

### Domain Layer

**`src/main/scala/javadocsmcp/domain/Errors.scala`:**
- Add `JavadocNotAvailable` error case
- Enhance all error messages with multi-line suggestions
- Ensure messages include coordinates/className for debugging

**Changes:**
- ~10 lines changed (enum case + message logic)
- High impact: All tools benefit from better error messages

### Infrastructure Layer

**`src/main/scala/javadocsmcp/infrastructure/CoursierArtifactRepository.scala`:**
- Update `fetchJar()` to distinguish Coursier error types
- Change `fetchJavadocJar()` to use `JavadocNotAvailable` error constructor
- Inspect Coursier exception types for proper mapping

**Changes:**
- ~15 lines changed (error matching logic)
- Research task: Identify Coursier exception types (see below)

### No Changes Needed

- ✅ Application layer: Already propagates errors via `Either`
- ✅ Presentation layer: Already converts `error.message` to MCP response
- ✅ Test infrastructure: In-memory repositories already return correct errors

---

## Testing Strategy

### Unit Tests

**`src/test/scala/javadocsmcp/domain/ErrorsTest.scala` (NEW FILE):**
- Test each error case message formatting
- Verify coordinates/className included in messages
- Verify suggestions present in messages

**Example test:**
```scala
test("ArtifactNotFound message includes coordinates and suggestions") {
  val error = DocumentationError.ArtifactNotFound("org.example:fake:1.0.0")
  val msg = error.message
  
  assert(msg.contains("org.example:fake:1.0.0"))
  assert(msg.contains("Maven Central"))
  assert(msg.contains("spelling"))
}
```

**Coverage:** ~6 tests (one per error type)

### Integration Tests

**`src/test/scala/javadocsmcp/infrastructure/CoursierArtifactRepositoryTest.scala` (MODIFY):**
- Add test for non-existent artifact → `ArtifactNotFound`
- Add test for missing javadoc classifier → `JavadocNotAvailable`
- Add test for missing sources classifier → `SourcesNotAvailable`

**Example test:**
```scala
test("fetchJavadocJar returns JavadocNotAvailable when javadoc JAR not published") {
  // Find a real artifact that doesn't publish javadoc
  // (Note: This might be hard to find - may need to mock)
  val coords = ArtifactCoordinates.parse("some.group:artifact:1.0.0").toOption.get
  val result = repository.fetchJavadocJar(coords)
  
  assert(result.isLeft)
  result.left.foreach { error =>
    assert(error.isInstanceOf[DocumentationError.JavadocNotAvailable])
    assert(error.message.contains("get_source"))
  }
}
```

**Challenge:** Finding real artifacts for error scenarios.

**Options:**
- A: Use real non-existent coordinates (e.g., `com.nonexistent:fake:1.0.0`)
- B: Mock Coursier (violates "no mocking external services" rule)
- C: Skip integration tests, rely on E2E tests

**Recommendation:** Option A - real coordinates ensure authentic error behavior.

**Coverage:** ~3 new tests

### E2E Tests

**`src/test/scala/javadocsmcp/integration/EndToEndTest.scala` (MODIFY):**
- Add test: Non-existent artifact returns error with message
- Add test: Error response includes suggestions
- Verify MCP error structure (`isError: true`)

**Example test:**
```scala
test("get_documentation with non-existent artifact returns helpful error") {
  val request = Json.obj(
    "coordinates" := "com.nonexistent:fake-library:1.0.0",
    "className" := "com.fake.Class"
  )
  
  val response = invokeToolAndGetResponse("get_documentation", request)
  
  assert(response.isError)
  val errorMsg = extractErrorMessage(response)
  assert(errorMsg.contains("Artifact not found"))
  assert(errorMsg.contains("com.nonexistent:fake-library:1.0.0"))
  assert(errorMsg.contains("Maven Central"))
}
```

**Coverage:** ~3 new tests

### Total New Tests

- Unit: ~6 tests (error message formatting)
- Integration: ~3 tests (Coursier error mapping)
- E2E: ~3 tests (MCP error responses)
- **Total: ~12 new tests**

---

## Acceptance Criteria

**Story 5 is complete when:**

1. ✅ **Artifact not found error is clear:**
   - Error message includes artifact coordinates
   - Error message suggests checking Maven Central
   - Error message suggests checking spelling

2. ✅ **Javadoc not available error is distinct:**
   - Different from "artifact not found"
   - Suggests trying `get_source` instead
   - Includes artifact coordinates

3. ✅ **Sources not available error is helpful:**
   - Suggests trying `get_documentation` instead
   - Explains that some libraries don't publish sources

4. ✅ **All error messages are user-friendly:**
   - Multi-line format with clear suggestions
   - Include relevant context (coordinates/className)
   - No technical jargon or stack traces

5. ✅ **Error handling is tested:**
   - Unit tests verify message formatting
   - Integration tests verify Coursier error mapping
   - E2E tests verify MCP error responses
   - All tests passing

6. ✅ **Server remains stable after errors:**
   - Errors don't crash the server
   - Subsequent requests work normally
   - E2E tests verify server continues running

---

## Implementation Tasks

### Task 1: Research Coursier Exception Types (15 min)

**Goal:** Identify exact exception types Coursier throws for different failures.

**Approach:**
1. Write temporary integration test with intentional failures
2. Catch exceptions and print `ex.getClass.getName`
3. Document findings

**Expected findings:**
- `coursier.error.ResolutionError` - artifact doesn't exist
- Something else for classifier not found?

**Deliverable:** Comment in code documenting Coursier exceptions.

### Task 2: Add `JavadocNotAvailable` Error (30 min)

**Steps:**
1. Add enum case to `DocumentationError`
2. Write message with suggestions
3. Write unit tests for message
4. Update `fetchJavadocJar()` to use new error

**Tests:** 2 unit tests
**Files:** `Errors.scala`

### Task 3: Enhance All Error Messages (45 min)

**Steps:**
1. Rewrite all `message` cases with multi-line format
2. Add suggestions to each error
3. Write unit tests for each message
4. Verify formatting looks good in E2E tests

**Tests:** 6 unit tests (one per error type)
**Files:** `Errors.scala`, new `ErrorsTest.scala`

### Task 4: Improve Coursier Error Detection (45 min)

**Steps:**
1. Update `fetchJar()` to inspect exception types
2. Map exceptions to correct error constructors
3. Add integration tests for error scenarios
4. Test with real non-existent artifacts

**Tests:** 3 integration tests
**Files:** `CoursierArtifactRepository.scala`, `CoursierArtifactRepositoryTest.scala`

### Task 5: Add E2E Error Tests (30 min)

**Steps:**
1. Test non-existent artifact via MCP
2. Verify error response structure
3. Verify error messages include suggestions
4. Verify server remains stable

**Tests:** 3 E2E tests
**Files:** `EndToEndTest.scala`

### Task 6: Manual Verification (15 min)

**Steps:**
1. Start MCP server
2. Invoke with non-existent artifact
3. Verify error message is helpful
4. Try subsequent requests (stability check)

**Deliverable:** Confirmation that error messages are user-friendly.

---

## Estimated Breakdown

| Task | Estimated Time |
|------|----------------|
| 1. Research Coursier exceptions | 15 min |
| 2. Add `JavadocNotAvailable` error | 30 min |
| 3. Enhance all error messages | 45 min |
| 4. Improve Coursier error detection | 45 min |
| 5. Add E2E error tests | 30 min |
| 6. Manual verification | 15 min |
| **Total** | **3 hours** |

**Confidence:** High - straightforward error handling improvements.

---

## Risks and Mitigation

### Risk 1: Coursier Exception Types Unknown

**Risk:** Might not be able to distinguish "artifact missing" from "classifier missing".

**Impact:** Medium - error messages might be less specific.

**Mitigation:**
- Start with research task (Task 1)
- If distinction impossible, use context clues (error message text)
- Worst case: Keep current approach with better messages

**Likelihood:** Low - Coursier should have different exceptions.

### Risk 2: Finding Test Artifacts

**Risk:** Hard to find real artifacts that don't publish javadoc/sources.

**Impact:** Low - can use non-existent coordinates instead.

**Mitigation:**
- Use `com.nonexistent:fake:1.0.0` for "artifact not found"
- Research artifacts without javadoc (might exist)
- E2E tests verify end-to-end behavior

**Likelihood:** Medium - javadoc/sources are usually published.

### Risk 3: Error Message Length

**Risk:** Multi-line error messages might be too verbose for AI assistants.

**Impact:** Low - clarity is more important than brevity.

**Mitigation:**
- Keep suggestions concise
- Test with Claude Code to verify readability
- Can shorten messages in future iteration if needed

**Likelihood:** Low - users prefer helpful over terse.

---

## Success Metrics

**Phase 5 is successful if:**

1. ✅ Error messages are actionable (include next steps)
2. ✅ Errors distinguish between "not found" and "not available"
3. ✅ All tests passing (12 new tests)
4. ✅ Server stable after errors (E2E tests confirm)
5. ✅ Manual testing confirms messages are helpful

**User experience improvement:**
- Before: "Artifact not found: com.typo:lib:1.0.0"
- After: Multi-line message with spelling suggestions and Maven Central link

---

## Next Steps After Phase 5

**Phase 6: Handle missing classes within artifacts**
- Builds on this phase's error handling patterns
- Adds class-level error detection
- Includes capitalization suggestions

**Phase 7: In-memory caching**
- Error results should also be cached (avoid re-fetching non-existent artifacts)
- Cache errors with shorter TTL than successes

---

## Notes for Implementer

**Quick wins:**
- Error messages are low-risk, high-impact changes
- Most work is in `Errors.scala` (single file)
- Tests are straightforward (string assertions)

**Be careful with:**
- Coursier exception types (research first)
- Multi-line string formatting (use `.stripMargin`)
- Test artifact selection (must be reliably non-existent)

**Don't over-engineer:**
- No custom error codes needed (messages are sufficient)
- No logging needed for MVP (defer to later)
- No retry logic (out of scope)

**Remember:**
- User is an AI assistant, not end user
- Error messages should help AI inform the human
- Clarity > brevity

---

**Phase 5 Context Status:** Ready for Implementation

**Dependencies:** ✅ Phases 1-4 complete  
**Blockers:** None  
**Next Action:** Start Task 1 (Research Coursier exceptions)
