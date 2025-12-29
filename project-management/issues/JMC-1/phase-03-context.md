# Phase 3 Context: Fetch Scaladoc HTML for Scala class

**Issue:** JMC-1
**Phase:** 3 of 7
**Story:** Fetch Scaladoc HTML for a Scala library class
**Estimated Effort:** 2-3 hours
**Complexity:** Low-Moderate

---

## Goals

This phase extends the existing `get_documentation` tool to support Scala libraries with Scaladoc. By the end of this phase, we will have:

1. **Scala coordinate parsing** supporting `::` separator (e.g., `org.typelevel::cats-effect:3.5.4`)
2. **Coursier resolution** that automatically handles Scala version suffix (e.g., `cats-effect_3`)
3. **Scaladoc HTML extraction** from javadoc-classified JARs (Scaladoc uses same JAR classifier)
4. **No changes to existing tools** - `get_documentation` works for both Java and Scala
5. **Complete test suite** with real Scala library artifacts

**Success Criteria:**
- Can invoke `get_documentation` with Scala coordinates using `::`
- Receives valid Scaladoc HTML for `cats.effect.IO`
- Response time is under 5 seconds for first request
- Existing Java documentation fetching still works (no regressions)
- Error handling works for non-existent Scala artifacts

---

## Scope

### In Scope

**Core Functionality:**
- Parse Scala coordinates with `::` separator
- Resolve Scala artifacts through Coursier (handles `_3`, `_2.13` suffixes automatically)
- Extract Scaladoc HTML from javadoc-classified JARs
- Maintain backward compatibility with Java coordinates

**Domain Components:**
- Extend `ArtifactCoordinates` to support both `:` (Java) and `::` (Scala)
- Add `ScalaVersion` information for cross-building context
- No changes to `ClassName` - works identically for Scala

**Infrastructure:**
- Update `CoursierArtifactRepository` to handle Scala module resolution
- No changes to `JarFileReader` - Scaladoc HTML extracted same way

**Application Layer:**
- No changes to `DocumentationService` - coordinate parsing handles the difference
- Service layer remains agnostic to Java vs Scala

**Presentation Layer:**
- Update tool descriptions to mention Scala support
- No schema changes - same `GetDocInput` handles both formats

**Testing:**
- Unit tests for Scala coordinate parsing
- Integration tests with real Scala libraries (cats-effect, zio)
- E2E tests with `::` coordinates
- Regression tests ensuring Java still works

### Out of Scope (Future Phases)

- **Scala source code fetching** (Phase 4) - Only Scaladoc HTML in this phase
- **Scala 2 vs Scala 3 version detection** - Coursier handles this automatically
- **Cross-version wildcard resolution** - Use explicit versions only
- **Scala.js or Scala Native artifacts** - JVM artifacts only

### Key Technical Insight

**Scaladoc uses the same JAR classifier as Javadoc:**
- Scala artifacts publish documentation in `-javadoc.jar` files
- The HTML inside is Scaladoc format, not Javadoc format
- No classifier change needed - `Classifier("javadoc")` works for both

**The `::` is syntactic sugar:**
- `org.typelevel::cats-effect:3.5.4` is shorthand
- Coursier resolves it to `org.typelevel:cats-effect_3:3.5.4` (Scala 3)
- Or `org.typelevel:cats-effect_2.13:3.5.4` (Scala 2.13)
- The suffix comes from build environment's Scala version

### Edge Cases Deferred

- Explicit Scala version specification (e.g., `org.typelevel:cats-effect_2.12:3.5.4`)
- Full cross-version syntax (`:::` for Scala.js/Native)
- Platform-specific suffixes beyond JVM
- Handling both `::` and `:` in same coordinate string

---

## Dependencies

### Prerequisites (Must Exist from Phases 1-2)

✅ **MCP Server Infrastructure:**
- `get_documentation` tool working for Java libraries
- Chimp + Tapir HTTP server operational
- JSON-RPC protocol handling proven

✅ **Coursier Integration:**
- `CoursierArtifactRepository.fetchJavadocJar()` working
- Coursier cache functioning
- Error handling for missing artifacts

✅ **JAR Reading Infrastructure:**
- `JarFileReader` extracts HTML from JARs
- Works for any HTML structure (Javadoc or Scaladoc)

✅ **Domain Foundations:**
- `ArtifactCoordinates` with `:` separator parsing
- `ClassName` path mapping
- Error type hierarchy

✅ **Testing Infrastructure:**
- MUnit test suite
- Integration test patterns
- E2E test patterns
- In-memory test implementations

### New Dependencies

**None** - All required libraries already present.

**Test Artifacts Needed:**
- `org.typelevel::cats-effect:3.5.4` - Cats Effect IO monad
- `dev.zio::zio:2.0.21` - ZIO runtime
- Both have excellent Scaladoc and are widely used

### From Previous Phases

**Reusable Components:**
- `DocumentationService` - No changes needed (works at higher level)
- `JarFileReader` - Extracts any HTML, regardless of Javadoc vs Scaladoc format
- `ClassName` - Scala classes use same package structure (e.g., `cats.effect.IO`)
- Test patterns - Same TDD approach

**Extension Points:**
- `ArtifactCoordinates.parse()` - Add `::` handling
- `CoursierArtifactRepository` - Use Coursier's Scala coordinate support

---

## Technical Approach

### Architecture Overview

Phase 3 **extends existing components** rather than adding parallel ones:

```
┌─────────────────────────────────────────────┐
│         Presentation Layer                   │
│  - get_documentation (unchanged)            │
│  - Description updated to mention Scala     │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│          Application Layer                   │
│  - DocumentationService (unchanged)         │
│  - Works transparently for Java/Scala       │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│           Domain Layer                       │
│  - ArtifactCoordinates.parse() EXTENDED    │
│  - Handles :: and : separators             │
│  - ClassName (unchanged)                    │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│        Infrastructure Layer                  │
│  - CoursierArtifactRepository EXTENDED     │
│  - Uses Coursier's Scala resolution        │
│  - JarFileReader (unchanged)               │
└─────────────────────────────────────────────┘
```

### Key Technical Decisions

**1. Coordinate Parsing: Support Both `:` and `::`**

**Decision:** Extend `ArtifactCoordinates.parse()` to detect and handle `::` separator.

**Approach:**
```scala
object ArtifactCoordinates {
  def parse(coordinates: String): Either[DocumentationError, ArtifactCoordinates] = {
    if (coordinates.isEmpty) {
      Left(InvalidCoordinates(coordinates))
    } else if (coordinates.contains("::")) {
      // Scala coordinates: org.typelevel::cats-effect:3.5.4
      parseScalaCoordinates(coordinates)
    } else {
      // Java coordinates: org.slf4j:slf4j-api:2.0.9
      parseJavaCoordinates(coordinates)
    }
  }
  
  private def parseScalaCoordinates(coordinates: String): Either[DocumentationError, ArtifactCoordinates] = {
    val parts = coordinates.split("::")
    if (parts.length != 2) {
      Left(InvalidCoordinates(coordinates))
    } else {
      val groupId = parts(0).trim
      val remaining = parts(1).split(":")
      if (remaining.length != 2) {
        Left(InvalidCoordinates(coordinates))
      } else {
        val artifactId = remaining(0).trim
        val version = remaining(1).trim
        if (groupId.isEmpty || artifactId.isEmpty || version.isEmpty) {
          Left(InvalidCoordinates(coordinates))
        } else {
          Right(ArtifactCoordinates(
            groupId = groupId,
            artifactId = artifactId,  // e.g., "cats-effect" (without _3 suffix)
            version = version,
            scalaArtifact = true  // NEW field to track Scala vs Java
          ))
        }
      }
    }
  }
  
  private def parseJavaCoordinates(coordinates: String): Either[DocumentationError, ArtifactCoordinates] = {
    // Existing implementation
    val parts = coordinates.split(":")
    if (parts.length != 3) {
      Left(InvalidCoordinates(coordinates))
    } else {
      val groupId = parts(0).trim
      val artifactId = parts(1).trim
      val version = parts(2).trim
      if (groupId.isEmpty || artifactId.isEmpty || version.isEmpty) {
        Left(InvalidCoordinates(coordinates))
      } else {
        Right(ArtifactCoordinates(
          groupId = groupId,
          artifactId = artifactId,
          version = version,
          scalaArtifact = false
        ))
      }
    }
  }
}
```

**2. Coursier Scala Resolution**

**Decision:** Let Coursier handle Scala version suffix automatically based on runtime Scala version.

**Key Insight:** Coursier has built-in Scala cross-version support. When we use `::`, Coursier automatically appends the appropriate Scala version suffix.

**Approach:**
```scala
def fetchJavadocJar(coords: ArtifactCoordinates): Either[DocumentationError, File] = {
  Try {
    val module = if (coords.scalaArtifact) {
      // Scala artifact - Coursier will add _3 or _2.13 suffix automatically
      Module(
        Organization(coords.groupId),
        ModuleName(coords.artifactId),
        attributes = Map("scalaVersion" -> Properties.versionNumberString)
      )
    } else {
      // Java artifact - no suffix
      Module(
        Organization(coords.groupId),
        ModuleName(coords.artifactId)
      )
    }
    
    val attributes = Attributes(Type.jar, Classifier("javadoc"))
    val dependency = Dependency(module, coords.version).withAttributes(attributes)
    
    val fetch = Fetch().addDependencies(dependency)
    val files = fetch.run()
    
    if (files.isEmpty) {
      throw new RuntimeException(s"No javadoc JAR found for ${coords}")
    }
    
    files.head
  } match {
    case Success(file) => Right(file)
    case Failure(exception) => Left(ArtifactNotFound(coords.toString))
  }
}
```

**IMPORTANT REALIZATION:** After researching Coursier API, we may not need special handling. Coursier's `Module` constructor might automatically handle `::` syntax. Need to verify during implementation.

**Simpler Alternative (Preferred):**
```scala
// Option A: Pass coordinate string directly to Coursier
// Coursier's Dependency.parse() might handle :: automatically
val dependency = Dependency.parse(
  s"${coords.groupId}::${coords.artifactId}:${coords.version}:classifier=javadoc"
)

// Option B: Use Coursier's ScalaVersion handling
// Research: coursier.core.ScalaVersion class
```

**3. No Changes to HTML Extraction**

**Decision:** `JarFileReader.readEntry()` works identically for Scaladoc.

**Rationale:**
- Scaladoc HTML is stored in same JAR structure: `cats/effect/IO.html`
- Path mapping is identical: `cats.effect.IO` → `cats/effect/IO.html`
- HTML format differences (Scaladoc vs Javadoc) don't matter - we return raw HTML

**4. Update Domain Model**

Extend `ArtifactCoordinates` case class:

```scala
case class ArtifactCoordinates(
  groupId: String,
  artifactId: String,
  version: String,
  scalaArtifact: Boolean = false  // NEW field
)
```

**Alternative:** Add a `coordinateType` enum:
```scala
enum CoordinateType:
  case Java
  case Scala

case class ArtifactCoordinates(
  groupId: String,
  artifactId: String,
  version: String,
  coordinateType: CoordinateType = CoordinateType.Java
)
```

**Decision:** Use boolean for simplicity in Phase 3. Can refactor to enum if Phase 4 needs more granularity.

**5. Tool Description Updates**

Update `ToolDefinitions.scala`:

```scala
def getDocumentationTool(service: DocumentationService) = {
  val docTool = tool("get_documentation")
    .description("""
      Fetch Javadoc/Scaladoc HTML documentation for a Java or Scala library class.
      
      For Java libraries, use ':' separator: groupId:artifactId:version
      For Scala libraries, use '::' separator: groupId::artifactId:version
      
      Examples:
        Java:  org.slf4j:slf4j-api:2.0.9
        Scala: org.typelevel::cats-effect:3.5.4
    """.trim)
    .input[GetDocInput]
  
  docTool.handle { input =>
    service.getDocumentation(input.coordinates, input.className) match {
      case Right(doc) => Right(doc.htmlContent)
      case Left(error) => Left(error.message)
    }
  }
}
```

---

## Files to Modify/Create

### Directory Structure

All files under `/home/mph/Devel/projects/javadocs-mcp-JMC-1/src/`:

```
src/
  main/
    scala/
      javadocsmcp/
        domain/
          ArtifactCoordinates.scala          # MODIFY - add :: parsing
        infrastructure/
          CoursierArtifactRepository.scala   # MODIFY - Scala resolution
        presentation/
          ToolDefinitions.scala              # MODIFY - update descriptions

  test/
    scala/
      javadocsmcp/
        domain/
          ArtifactCoordinatesTest.scala      # MODIFY - add :: tests
        infrastructure/
          CoursierArtifactRepositoryTest.scala  # MODIFY - Scala artifact tests
        application/
          DocumentationServiceIntegrationTest.scala  # MODIFY - Scala library tests
        integration/
          EndToEndTest.scala                 # MODIFY - Scala E2E tests
```

### Files to Modify (6 files)

**Domain Layer:**
1. `domain/ArtifactCoordinates.scala` - Add `scalaArtifact` field, implement `parseScalaCoordinates()`

**Infrastructure Layer:**
2. `infrastructure/CoursierArtifactRepository.scala` - Handle Scala module resolution

**Presentation Layer:**
3. `presentation/ToolDefinitions.scala` - Update tool descriptions with Scala examples

**Tests:**
4. `test/.../domain/ArtifactCoordinatesTest.scala` - Add tests for `::` parsing
5. `test/.../infrastructure/CoursierArtifactRepositoryTest.scala` - Test Scala artifact fetching
6. `test/.../integration/EndToEndTest.scala` - E2E test with Scala coordinates

### New Files to Create

**None** - This phase only extends existing files.

---

## Testing Strategy

### Test-Driven Development (TDD)

**Follow Phase 1/2 TDD cycle:**

1. **Red**: Write failing test for Scala coordinate parsing
2. **Green**: Implement `::` parsing logic
3. **Red**: Write failing integration test for cats-effect
4. **Green**: Implement Coursier Scala resolution
5. **Refactor**: Clean up while keeping tests green

### Test Coverage

**Unit Tests:**
- Parse valid Scala coordinates with `::` separator
- Reject invalid Scala coordinates (wrong format)
- Handle edge cases (empty parts, extra colons)
- Verify `scalaArtifact` field set correctly

**Integration Tests:**
- Fetch real Scaladoc JAR from Maven Central (cats-effect, zio)
- Verify JAR downloaded successfully
- Extract HTML from Scaladoc JAR
- Verify HTML contains Scala-specific documentation

**E2E Tests:**
- HTTP request to `get_documentation` with Scala coordinates
- Successful Scaladoc fetch for `cats.effect.IO`
- Verify response contains valid Scaladoc HTML
- Response time under 5 seconds

**Regression Tests:**
- Ensure Java coordinate parsing still works (`:` separator)
- Verify slf4j documentation still fetches correctly
- No changes to existing Java functionality

### Specific Test Cases

**`ArtifactCoordinatesTest.scala` (MODIFY):**
```scala
test("parse valid Scala coordinates with :: separator") {
  val coords = ArtifactCoordinates.parse("org.typelevel::cats-effect:3.5.4")
  assert(coords.isRight)
  val result = coords.toOption.get
  assertEquals(result.groupId, "org.typelevel")
  assertEquals(result.artifactId, "cats-effect")
  assertEquals(result.version, "3.5.4")
  assertEquals(result.scalaArtifact, true)
}

test("parse zio Scala coordinates") {
  val coords = ArtifactCoordinates.parse("dev.zio::zio:2.0.21")
  assert(coords.isRight)
  val result = coords.toOption.get
  assertEquals(result.groupId, "dev.zio")
  assertEquals(result.artifactId, "zio")
  assertEquals(result.version, "2.0.21")
  assertEquals(result.scalaArtifact, true)
}

test("reject Scala coordinates with missing version") {
  val result = ArtifactCoordinates.parse("org.typelevel::cats-effect")
  assert(result.isLeft)
}

test("reject Scala coordinates with wrong separator count") {
  val result = ArtifactCoordinates.parse("org.typelevel:::cats-effect:3.5.4")
  assert(result.isLeft)
}

test("Java coordinates still work (regression)") {
  val coords = ArtifactCoordinates.parse("org.slf4j:slf4j-api:2.0.9")
  assert(coords.isRight)
  val result = coords.toOption.get
  assertEquals(result.scalaArtifact, false)
}
```

**`CoursierArtifactRepositoryTest.scala` (MODIFY):**
```scala
test("fetch Scaladoc JAR for cats-effect") {
  val repository = CoursierArtifactRepository()
  val coords = ArtifactCoordinates(
    groupId = "org.typelevel",
    artifactId = "cats-effect",
    version = "3.5.4",
    scalaArtifact = true
  )
  
  val result = repository.fetchJavadocJar(coords)
  assert(result.isRight, "Should fetch cats-effect Scaladoc JAR")
  
  val file = result.toOption.get
  assert(file.exists(), "JAR file should exist")
  assert(file.getName.contains("cats-effect"), "Filename should contain artifact name")
  assert(file.getName.contains("javadoc"), "Should be javadoc JAR")
}

test("fetch Scaladoc JAR for ZIO") {
  val repository = CoursierArtifactRepository()
  val coords = ArtifactCoordinates(
    groupId = "dev.zio",
    artifactId = "zio",
    version = "2.0.21",
    scalaArtifact = true
  )
  
  val result = repository.fetchJavadocJar(coords)
  assert(result.isRight, "Should fetch ZIO Scaladoc JAR")
}
```

**`DocumentationServiceIntegrationTest.scala` (MODIFY):**
```scala
test("fetch Scaladoc for cats.effect.IO") {
  val repository = CoursierArtifactRepository()
  val reader = JarFileReader()
  val service = DocumentationService(repository, reader)
  
  val result = service.getDocumentation(
    "org.typelevel::cats-effect:3.5.4",
    "cats.effect.IO"
  )
  
  assert(result.isRight, "Should fetch IO Scaladoc")
  val doc = result.toOption.get
  assert(doc.htmlContent.contains("IO"), "HTML should contain IO class")
  assert(doc.className.fullyQualifiedName == "cats.effect.IO")
}
```

**`EndToEndTest.scala` (MODIFY):**
```scala
test("should fetch Scaladoc for cats.effect.IO via MCP tool") {
  val request = """
    {
      "jsonrpc": "2.0",
      "method": "tools/call",
      "params": {
        "name": "get_documentation",
        "arguments": {
          "coordinates": "org.typelevel::cats-effect:3.5.4",
          "className": "cats.effect.IO"
        }
      },
      "id": 5
    }
  """
  
  val response = httpClient.post(serverUrl).body(request).send()
  assertEquals(response.code.code, 200)
  
  val json = parse(response.body).toOption.get
  val result = json.hcursor.downField("result")
  
  assert(!result.downField("isError").as[Boolean].toOption.get)
  val html = result.downField("content").as[String].toOption.get
  
  assert(html.contains("IO"))
  assert(html.nonEmpty)
}

test("Java documentation still works (regression)") {
  // Existing test for org.slf4j.Logger should still pass
  val request = """
    {
      "jsonrpc": "2.0",
      "method": "tools/call",
      "params": {
        "name": "get_documentation",
        "arguments": {
          "coordinates": "org.slf4j:slf4j-api:2.0.9",
          "className": "org.slf4j.Logger"
        }
      },
      "id": 6
    }
  """
  
  val response = httpClient.post(serverUrl).body(request).send()
  assertEquals(response.code.code, 200)
  // ... assert success
}
```

### Test Data Strategy

**Real Scala Artifacts:**
- Primary: `org.typelevel::cats-effect:3.5.4` - Well-known, stable, excellent docs
- Secondary: `dev.zio::zio:2.0.21` - Alternative Scala library
- Use cached artifacts from Coursier

**Regression Testing:**
- Keep all existing Java tests (`org.slf4j:slf4j-api`, `com.google.guava:guava`)
- Ensure they continue to pass without modification

**Expected Test Output:**
- Zero compiler warnings
- All 35+ tests passing (existing + new Scala tests)
- No error logs for successful tests

---

## Acceptance Criteria

Phase 3 is **complete** when ALL of the following are true:

### Functional Requirements

- [ ] Can parse Scala coordinates with `::` separator
- [ ] `get_documentation` tool works with Scala coordinates
- [ ] Can fetch Scaladoc for `org.typelevel::cats-effect:3.5.4` → `cats.effect.IO`
- [ ] Response contains valid Scaladoc HTML
- [ ] Response time under 5 seconds for first request
- [ ] Error handling works for non-existent Scala artifacts
- [ ] **REGRESSION:** All existing Java tests still pass

### Code Quality

- [ ] `ArtifactCoordinates` supports both `:` and `::`
- [ ] `scalaArtifact` field properly set during parsing
- [ ] Coursier resolution handles Scala version suffix automatically
- [ ] No changes to application/presentation layers (transparent support)
- [ ] No compiler warnings
- [ ] `PURPOSE:` comments updated where logic changed
- [ ] Domain naming clear (no "new" or "scala version" in names)

### Testing

- [ ] All existing tests pass (regression verification)
- [ ] Unit tests for `::` coordinate parsing
- [ ] Integration tests with real cats-effect Scaladoc
- [ ] E2E test with Scala coordinates via HTTP
- [ ] Test output pristine
- [ ] Tests use real Scala artifacts (no mocking)

### Documentation

- [ ] Tool descriptions updated with Scala examples
- [ ] Code comments explain `::` vs `:` distinction
- [ ] Error messages clear for Scala coordinate format

### Git Hygiene

- [ ] Incremental commits following TDD
- [ ] Commit messages clear
- [ ] Pre-commit hooks pass
- [ ] Working directory clean

---

## Risk Assessment

### Low Risks

**Coursier Scala Resolution:**
- **Risk**: Coursier might not automatically handle `::` syntax as expected
- **Mitigation**: Research Coursier API docs, test with simple example first
- **Fallback**: Manually append `_3` suffix based on runtime Scala version
- **Impact**: Low - well-documented feature of Coursier

**Scaladoc JAR Availability:**
- **Risk**: Some Scala libraries might not publish Scaladoc JARs
- **Mitigation**: Test with well-known libraries (cats, zio) that definitely have docs
- **Impact**: Low - same error handling as Java (ArtifactNotFound)

**Scaladoc HTML Structure:**
- **Risk**: Scaladoc HTML might be structured differently than Javadoc
- **Mitigation**: We don't parse HTML, just extract and return it
- **Impact**: None - HTML differences don't matter to us

### Medium Risks

**Scala 2 vs Scala 3:**
- **Risk**: Different Scala major versions use different suffixes (`_2.13` vs `_3`)
- **Mitigation**: Coursier handles this based on runtime Scala version
- **Concern**: What if user wants Scala 2 docs but runtime is Scala 3?
- **Resolution for MVP:** Use runtime Scala version (document limitation)
- **Future:** Could add optional `scalaVersion` parameter

### Unknowns to Discover

- Does Coursier automatically detect `::` and add version suffix? (Need to verify)
- What Scala version suffix does Coursier use? (Check Properties.versionNumberString)
- Do all Scala libraries follow `artifactId_scalaVersion` naming? (Standard, but verify)

---

## Implementation Checklist

### Phase 3.1: Research Coursier Scala Support (30 min)

- [ ] Read Coursier documentation on Scala version handling
- [ ] Test Coursier with `::` coordinates in Scala REPL
- [ ] Verify: `Dependency.parse("org.typelevel::cats-effect:3.5.4:classifier=javadoc")`
- [ ] Document findings: Does Coursier handle `::` automatically?
- [ ] Determine: Do we need custom Module construction or can we use parsing?
- [ ] Note: No commit yet - just research

### Phase 3.2: Extend Domain (TDD) (45 min)

**Coordinate Parsing:**
- [ ] Red: Test for parsing `org.typelevel::cats-effect:3.5.4`
- [ ] Red: Test for parsing `dev.zio::zio:2.0.21`
- [ ] Green: Add `scalaArtifact: Boolean` field to `ArtifactCoordinates`
- [ ] Green: Implement `parseScalaCoordinates()` private method
- [ ] Green: Detect `::` in `parse()` and route to Scala parser
- [ ] Red: Test rejection of invalid Scala coordinates
- [ ] Green: Add validation logic
- [ ] Red: Regression test - Java coordinates still work
- [ ] Green: Verify existing logic unchanged
- [ ] Commit: "feat(domain): add Scala coordinate parsing with :: separator"

### Phase 3.3: Extend Infrastructure (TDD) (30-45 min)

**Coursier Scala Resolution:**
- [ ] Red: Integration test for cats-effect Scaladoc JAR fetch
- [ ] Green: Update `fetchJavadocJar()` to handle `scalaArtifact` field
- [ ] Implement Scala module construction (based on Phase 3.1 research)
- [ ] Red: Test ZIO Scaladoc fetch
- [ ] Green: Verify implementation works for multiple libraries
- [ ] Red: Test error case - non-existent Scala artifact
- [ ] Green: Ensure error handling works
- [ ] Commit: "feat(infra): add Scala artifact resolution to Coursier repository"

### Phase 3.4: Service Layer Verification (15 min)

**No Code Changes - Verification Only:**
- [ ] Red: Integration test for DocumentationService with Scala coordinates
- [ ] Green: Verify service works without modification (transparent)
- [ ] Test: `service.getDocumentation("org.typelevel::cats-effect:3.5.4", "cats.effect.IO")`
- [ ] Verify: HTML contains expected Scaladoc content
- [ ] Commit: "test: verify DocumentationService works with Scala coordinates"

### Phase 3.5: Update Presentation Layer (15 min)

**Tool Description Updates:**
- [ ] Update `getDocumentationTool` description with Scala examples
- [ ] Add usage notes for `:` vs `::` separators
- [ ] No schema changes (GetDocInput handles both)
- [ ] Commit: "docs(mcp): update tool descriptions to include Scala support"

### Phase 3.6: End-to-End Testing (30 min)

- [ ] Add E2E test for Scaladoc via HTTP MCP call
- [ ] Test: get_documentation with `org.typelevel::cats-effect:3.5.4`
- [ ] Verify: Response contains Scaladoc HTML
- [ ] Test: Error case for non-existent Scala artifact
- [ ] Regression: Verify existing Java E2E tests still pass
- [ ] Manual test: curl with Scala coordinates
- [ ] Commit: "test: add E2E tests for Scala documentation fetching"

### Phase 3.7: Polish and Regression (30 min)

- [ ] Run full test suite - all tests pass
- [ ] Verify: Zero compiler warnings
- [ ] Check: All Java tests still pass (no regressions)
- [ ] Review: Error messages helpful for Scala coordinates
- [ ] Update: `PURPOSE:` comments where code changed
- [ ] Update: implementation-log.md with Phase 3 summary
- [ ] Final commit: "docs: Phase 3 complete - Scala documentation support"

---

## Phase Transition

Upon completion of Phase 3, you will have:

**Deliverables:**
- `get_documentation` tool supports both Java (`:`) and Scala (`::`) coordinates
- Scala library Scaladoc fetching works seamlessly
- No regressions in Java functionality
- Clear documentation of coordinate format differences

**Ready for Phase 4:**
- Coordinate parsing handles both Java and Scala
- Infrastructure supports Scala artifact resolution
- Next: Extend `get_source` tool for Scala sources (`.scala` files)

**Technical Insights Gained:**
- How Coursier handles Scala cross-version resolution
- Whether `::` is handled automatically or needs special parsing
- Scaladoc JAR structure (likely identical to Javadoc)

**Next Steps:**
1. Mark Phase 3 complete in `tasks.md`
2. Update implementation log with Coursier findings
3. Run `/iterative-works:ag-implement JMC-1` for Phase 4 context
4. Begin Phase 4: Fetch source code for Scala class

---

**Estimated Total:** 2-3 hours
**Confidence:** Medium-High (Coursier likely handles `::` well, but needs verification)

**Key Unknown:** Coursier API for Scala coordinates - will discover in Phase 3.1

**Start implementation with:** `/iterative-works:ag-implement JMC-1`

---

## Refactoring Decisions

### R1: Replace hardcoded Scala suffix with coursier/dependency library (2025-12-29)

**Trigger:** Code review discussion identified that the hardcoded `_3` suffix in `CoursierArtifactRepository.resolveArtifactName()` is inflexible - it only supports Scala 3 artifacts and provides no way for users to fetch Scala 2.13 library documentation.

**Decision:** Replace manual suffix handling with the `coursier/dependency` library's `ScalaParameters` approach, plus add optional `scalaVersion` parameter to MCP tools.

**Scope:**
- Files affected: `project.scala`, `CoursierArtifactRepository.scala`, `ToolDefinitions.scala`, `ArtifactCoordinates.scala`
- Components: Infrastructure layer (Coursier), Presentation layer (tool schemas)
- Boundaries: DO NOT change `DocumentationService`, `SourceCodeService`, or `JarFileReader`

**Approach:**
1. Add `io.get-coursier::dependency` library to project.scala
2. Use `dep"..."` string interpolator for parsing coordinates
3. Use `ScalaParameters(version).applyParams()` to resolve concrete artifact names
4. Add optional `scalaVersion: Option[String]` parameter to `GetDocInput`/`GetSourceInput` (default: "3")
5. Users can still specify explicit suffixes (`org.typelevel:cats-effect_2.13:3.5.4`) for full control

**Benefits:**
- Proper `::` parsing via library (not custom code)
- Supports both Scala 2.x and Scala 3
- User can override default Scala version per request
- Explicit suffix coordinates still work (backward compatible)

---

## Notes for Implementation

### Critical Decision Point: Coursier Handling

**Before writing code in Phase 3.2, research in Phase 3.1 will determine:**

**Option A: Coursier Auto-Handles `::` (Preferred)**
```scala
// If Coursier's parse() understands ::, use this:
val depString = if (coords.scalaArtifact) {
  s"${coords.groupId}::${coords.artifactId}:${coords.version}"
} else {
  s"${coords.groupId}:${coords.artifactId}:${coords.version}"
}
val dependency = Dependency.parse(s"$depString:classifier=javadoc")
```

**Option B: Manual Scala Version Appending**
```scala
// If we need to manually construct Module with suffix:
val moduleName = if (coords.scalaArtifact) {
  val scalaVersion = scala.util.Properties.versionNumberString
  val scalaBinaryVersion = scalaVersion.split('.').take(2).mkString(".")
  s"${coords.artifactId}_$scalaBinaryVersion"
} else {
  coords.artifactId
}

val module = Module(
  Organization(coords.groupId),
  ModuleName(moduleName)
)
```

**Research will reveal which approach to use. Document findings in Phase 3.1.**

### Backward Compatibility Guarantee

**CRITICAL:** All existing Java tests MUST continue to pass without modification. If any Java test fails after Phase 3 changes, STOP and investigate before proceeding.

### Testing Philosophy

**For Scala support:**
- Test with REAL Scala libraries (cats-effect, zio)
- Verify HTML extraction works (even if Scaladoc format differs)
- Don't parse or validate Scaladoc HTML structure - just return it

**For regression:**
- Keep all existing Java tests
- Run them frequently during Phase 3
- Any failure is a breaking change

---
