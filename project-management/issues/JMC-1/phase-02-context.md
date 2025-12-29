# Phase 2 Context: Fetch source code for Java class

**Issue:** JMC-1
**Phase:** 2 of 7
**Story:** Fetch source code for a Java library class
**Estimated Effort:** 3-4 hours
**Complexity:** Low

---

## Goals

This phase builds on Phase 1's foundation to add source code fetching capability. By the end of this phase, we will have:

1. **New `get_source` MCP tool** that fetches Java source code from `-sources.jar` artifacts
2. **Source code extraction logic** that reads `.java` files from source JARs
3. **Class name to source path mapping** that converts `org.slf4j.Logger` to `org/slf4j/Logger.java`
4. **Reusable infrastructure** leveraging existing Coursier and JAR reading patterns
5. **Complete test suite** following Phase 1's TDD patterns

**Success Criteria:**
- Can invoke `get_source` tool with Java library coordinates
- Receives valid Java source code for `org.slf4j.Logger`
- Source code is properly formatted and readable
- Response time is under 5 seconds for first request
- Error handling works for missing sources JAR

---

## Scope

### In Scope

**Core Functionality:**
- New MCP tool: `get_source`
- Coursier integration for resolving `-sources.jar` artifacts
- JAR file reading for `.java` source files
- Mapping class names to `.java` file paths
- Basic error handling (sources JAR not found, source file not found)

**Domain Components:**
- New `SourceCode` entity (source text, metadata)
- Extension to port traits for source fetching
- Reuse existing `ArtifactCoordinates` and `ClassName`

**Infrastructure:**
- New port method: `ArtifactRepository.fetchSourcesJar()`
- Extend `DocumentationReader` or create `SourceReader` port
- Implementation reuses `JarFileReader` patterns

**Application Layer:**
- New `SourceCodeService.getSource()` use case
- Similar structure to `DocumentationService`

**Presentation Layer:**
- New `GetSourceInput` case class with Chimp schema
- New `getSourceTool` definition
- Add to existing `mcpEndpoint` tools list

**Testing:**
- Unit tests for source path mapping (`.java` instead of `.html`)
- Integration tests with real Maven Central sources JARs
- E2E tests invoking `get_source` tool via HTTP

### Out of Scope (Future Phases)

- **Scala source support** (Phase 4) - Only Java `.java` files in this phase
- **Advanced error suggestions** (Phase 6) - Basic error messages only
- **Caching** (Phase 7) - Direct fetching, no optimization yet
- **Source code syntax highlighting** - Return plain text
- **Source decompilation** - Only works if `-sources.jar` exists

### Edge Cases Deferred

- Mixed Java/Scala source JARs - try `.java` only
- Multi-release source JARs (Java 9+)
- Annotation processors or generated sources
- Source files in non-standard locations within JAR

---

## Dependencies

### Prerequisites (Must Exist from Phase 1)

✅ **MCP Server Infrastructure:**
- `McpServer` with Chimp + Tapir Netty server
- `ToolDefinitions` pattern established
- Tool registration and JSON-RPC handling working

✅ **Coursier Integration:**
- `CoursierArtifactRepository` successfully fetches JARs with classifiers
- Caching in `~/.cache/coursier` working
- Error handling for missing artifacts

✅ **JAR Reading Infrastructure:**
- `JarFileReader` successfully extracts files from JARs
- Error handling for missing entries
- UTF-8 encoding support

✅ **Domain Foundations:**
- `ArtifactCoordinates` value object with validation
- `ClassName` value object with path mapping
- Port traits (`ArtifactRepository`, `DocumentationReader`)
- Error type hierarchy

✅ **Testing Patterns:**
- MUnit test setup
- Integration test patterns with real Maven Central
- E2E test patterns with HTTP server lifecycle
- In-memory test implementations (testkit)

### New Dependencies

**None** - All required libraries already added in Phase 1:
- Chimp MCP library
- Tapir Netty server
- Coursier
- MUnit

### From Phase 1

**Reusable Components:**
- `ArtifactCoordinates` - Parse coordinates same way
- `ClassName` - Reuse for source path mapping (change `.html` to `.java`)
- `CoursierArtifactRepository` - Add `fetchSourcesJar()` method
- `JarFileReader` - Works for any JAR content type
- Port traits - Extend or add new methods
- Test utilities - In-memory implementations, test fixtures

---

## Technical Approach

### Architecture Overview

Phase 2 **mirrors Phase 1's architecture** with parallel components for sources:

```
┌─────────────────────────────────────────────┐
│         Presentation Layer (Chimp)          │
│  - GetSourceInput case class                │
│  - getSourceTool definition                 │
│  - Added to existing mcpEndpoint            │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│          Application Layer                   │
│  - SourceCodeService.getSource()            │
│  - Mirrors DocumentationService pattern     │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│           Domain Layer                       │
│  - SourceCode (entity) - NEW                │
│  - Reuse ArtifactCoordinates               │
│  - Reuse ClassName (modify toSourcePath)   │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│        Infrastructure Layer                  │
│  - Extend ArtifactRepository port           │
│  - Extend DocumentationReader or new port   │
│  - CoursierArtifactRepository.fetchSourcesJar()  │
│  - Reuse JarFileReader                      │
└─────────────────────────────────────────────┘
```

### Key Technical Decisions

**1. Extend Existing Ports vs Create New Ones**

**Decision:** Extend `ArtifactRepository` with `fetchSourcesJar()` method, rename `DocumentationReader` to generic `JarContentReader`.

**Rationale:**
- `ArtifactRepository` naturally handles all artifact types (javadoc, sources, etc.)
- `DocumentationReader.readEntry()` is already generic - just reads any file from JAR
- Renaming port to `JarContentReader` makes it clear it's not javadoc-specific
- Avoids duplication - same `JarFileReader` implementation works for both

**Approach:**
```scala
// domain/ports/ArtifactRepository.scala
trait ArtifactRepository:
  def fetchJavadocJar(coords: ArtifactCoordinates): Either[DocumentationError, File]
  def fetchSourcesJar(coords: ArtifactCoordinates): Either[DocumentationError, File]  // NEW

// domain/ports/JarContentReader.scala (renamed from DocumentationReader)
trait JarContentReader:
  def readEntry(jarFile: File, entryPath: String): Either[DocumentationError, String]
```

**2. Source Code Entity**

Create new `SourceCode` entity parallel to `Documentation`:

```scala
case class SourceCode(
  sourceText: String,
  className: ClassName,
  coordinates: ArtifactCoordinates
)
```

**3. Class Name to Source Path Mapping**

Extend `ClassName` with new method:

```scala
case class ClassName(fullyQualifiedName: String) {
  def toHtmlPath: String = {
    val outerClass = fullyQualifiedName.split('$').head
    outerClass.replace('.', '/') + ".html"
  }
  
  def toSourcePath: String = {  // NEW
    val outerClass = fullyQualifiedName.split('$').head
    outerClass.replace('.', '/') + ".java"
  }
}
```

**Examples:**
- `org.slf4j.Logger` → `org/slf4j/Logger.java`
- `org.slf4j.Logger$Factory` → `org/slf4j/Logger.java` (inner class)
- `com.google.common.collect.ImmutableList` → `com/google/common/collect/ImmutableList.java`

**4. Coursier Integration for Sources**

Add method to `CoursierArtifactRepository`:

```scala
def fetchSourcesJar(coords: ArtifactCoordinates): Either[DocumentationError, File] = {
  Try {
    val module = Module(
      Organization(coords.groupId),
      ModuleName(coords.artifactId)
    )
    
    val attributes = Attributes(Type.jar, Classifier("sources"))  // Changed from "javadoc"
    val dependency = Dependency(module, coords.version).withAttributes(attributes)
    
    val fetch = Fetch().addDependencies(dependency)
    val files = fetch.run()
    
    if (files.isEmpty) {
      throw new RuntimeException(s"No sources JAR found for ${coords}")
    }
    
    files.head
  } match {
    case Success(file) => Right(file)
    case Failure(exception) => Left(SourcesNotAvailable(coords.toString))
  }
}
```

**5. Source Code Service**

Mirror `DocumentationService` structure:

```scala
class SourceCodeService(
  repository: ArtifactRepository,
  reader: JarContentReader
) {
  def getSource(coordinatesStr: String, classNameStr: String): Either[DocumentationError, SourceCode] = {
    for {
      coords <- ArtifactCoordinates.parse(coordinatesStr)
      className <- ClassName.parse(classNameStr)
      sourcesJar <- repository.fetchSourcesJar(coords)
      sourcePath = className.toSourcePath
      sourceText <- reader.readEntry(sourcesJar, sourcePath)
    } yield SourceCode(sourceText, className, coords)
  }
}
```

**6. MCP Tool Definition**

Add to `ToolDefinitions.scala`:

```scala
case class GetSourceInput(
  coordinates: String,
  className: String
) derives Codec, Schema

def getSourceTool(service: SourceCodeService) = {
  val sourceTool = tool("get_source")
    .description("Fetch Java source code for a library class")
    .input[GetSourceInput]
  
  sourceTool.handle { input =>
    service.getSource(input.coordinates, input.className) match {
      case Right(source) => Right(source.sourceText)
      case Left(error) => Left(error.message)
    }
  }
}
```

**7. Error Handling**

Add new error type to `domain/Errors.scala`:

```scala
enum DocumentationError(val message: String):
  case ArtifactNotFound(coordinates: String) extends DocumentationError(
    s"Artifact not found: $coordinates. Check coordinates and verify it exists on Maven Central."
  )
  case ClassNotFound(path: String) extends DocumentationError(
    s"Class not found: $path"
  )
  case InvalidCoordinates(input: String) extends DocumentationError(
    s"Invalid coordinates: $input. Expected format: 'groupId:artifactId:version'"
  )
  case InvalidClassName(name: String) extends DocumentationError(
    s"Invalid class name: $name"
  )
  case SourcesNotAvailable(coordinates: String) extends DocumentationError(  // NEW
    s"Sources JAR not available for: $coordinates. Try using get_documentation instead."
  )
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
          SourceCode.scala                    # NEW
          ClassName.scala                     # MODIFY - add toSourcePath()
          Errors.scala                        # MODIFY - add SourcesNotAvailable
          ports/
            ArtifactRepository.scala          # MODIFY - add fetchSourcesJar()
            JarContentReader.scala            # RENAME from DocumentationReader
        application/
          SourceCodeService.scala             # NEW
        infrastructure/
          CoursierArtifactRepository.scala    # MODIFY - add fetchSourcesJar()
          JarFileReader.scala                 # UPDATE - implement renamed port
        presentation/
          ToolDefinitions.scala               # MODIFY - add getSourceTool
          McpServer.scala                     # MODIFY - register source tool
        Main.scala                            # MODIFY - wire up SourceCodeService

  test/
    scala/
      javadocsmcp/
        domain/
          ClassNameTest.scala                 # MODIFY - add toSourcePath tests
          SourceCodeTest.scala                # NEW (optional)
        application/
          SourceCodeServiceTest.scala         # NEW - unit tests
          SourceCodeServiceIntegrationTest.scala  # NEW - integration tests
        infrastructure/
          CoursierArtifactRepositoryTest.scala  # MODIFY - add sources tests
          JarFileReaderTest.scala             # MODIFY - add .java file tests
        integration/
          EndToEndTest.scala                  # MODIFY - add get_source E2E tests
        testkit/
          InMemoryArtifactRepository.scala    # MODIFY - add fetchSourcesJar()
          InMemoryJarContentReader.scala      # RENAME from InMemoryDocumentationReader
```

### Files to Modify (10 files)

**Domain Layer:**
1. `domain/ClassName.scala` - Add `toSourcePath()` method
2. `domain/Errors.scala` - Add `SourcesNotAvailable` error case
3. `domain/ports/ArtifactRepository.scala` - Add `fetchSourcesJar()` method
4. `domain/ports/DocumentationReader.scala` - Rename to `JarContentReader.scala`

**Infrastructure Layer:**
5. `infrastructure/CoursierArtifactRepository.scala` - Implement `fetchSourcesJar()`
6. `infrastructure/JarFileReader.scala` - Update to implement renamed `JarContentReader`

**Presentation Layer:**
7. `presentation/ToolDefinitions.scala` - Add `getSourceTool` and `GetSourceInput`
8. `presentation/McpServer.scala` - Register source tool in endpoint

**Application Layer:**
9. `Main.scala` - Wire up `SourceCodeService` and source tool

**Tests:**
10. All test files need minor updates for port renaming

### New Files to Create (5 files)

**Domain Layer:**
1. `domain/SourceCode.scala` - Source code entity

**Application Layer:**
2. `application/SourceCodeService.scala` - Source fetching use case

**Tests:**
3. `test/.../application/SourceCodeServiceTest.scala` - Unit tests with in-memory implementations
4. `test/.../application/SourceCodeServiceIntegrationTest.scala` - Real Maven Central tests
5. `test/.../testkit/InMemorySourceCodeService.scala` - Optional test helper

---

## Testing Strategy

### Test-Driven Development (TDD)

**Follow the same TDD cycle as Phase 1:**

1. **Red**: Write failing test for desired behavior
2. **Green**: Implement minimal code to pass
3. **Refactor**: Clean up while keeping tests green

### Test Coverage

**Unit Tests:**
- `ClassName.toSourcePath()` - Verify `.java` extension and path conversion
- Source path mapping with inner classes
- `SourceCodeService` with in-memory implementations (no I/O)
- Error propagation through service layer

**Integration Tests:**
- Fetch real sources JAR from Maven Central using Coursier
- Read `.java` files from real JARs (slf4j, guava)
- Verify source text contains expected Java syntax
- Error handling when sources JAR doesn't exist

**E2E Tests:**
- HTTP request to `get_source` tool
- Successful source fetch for `org.slf4j.Logger`
- Verify response contains valid Java source code
- Error response when sources not available
- Response time under 5 seconds

### Specific Test Cases

**`ClassNameTest.scala` (MODIFY):**
```scala
test("convert class name to source path") {
  val className = ClassName("org.slf4j.Logger")
  assertEquals(className.toSourcePath, "org/slf4j/Logger.java")
}

test("strip inner class suffix for source path") {
  val className = ClassName("org.slf4j.Logger$Factory")
  assertEquals(className.toSourcePath, "org/slf4j/Logger.java")
}
```

**`SourceCodeServiceTest.scala` (NEW):**
```scala
class SourceCodeServiceTest extends munit.FunSuite {
  test("successfully fetch source code") {
    val mockRepo = InMemoryArtifactRepository()
    mockRepo.addSourcesJar("org.slf4j:slf4j-api:2.0.9", testJarFile)
    
    val mockReader = InMemoryJarContentReader()
    mockReader.addEntry("org/slf4j/Logger.java", "public interface Logger { }")
    
    val service = SourceCodeService(mockRepo, mockReader)
    val result = service.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")
    
    assert(result.isRight)
    assertEquals(result.toOption.get.sourceText, "public interface Logger { }")
  }
  
  test("return error when sources JAR not found") {
    val mockRepo = InMemoryArtifactRepository()  // Empty
    val mockReader = InMemoryJarContentReader()
    
    val service = SourceCodeService(mockRepo, mockReader)
    val result = service.getSource("com.example:fake:1.0.0", "Fake")
    
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains("not available"))
  }
}
```

**`SourceCodeServiceIntegrationTest.scala` (NEW):**
```scala
class SourceCodeServiceIntegrationTest extends munit.FunSuite {
  val repository = CoursierArtifactRepository()
  val reader = JarFileReader()
  val service = SourceCodeService(repository, reader)
  
  test("fetch real source code for org.slf4j.Logger") {
    val result = service.getSource("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")
    
    assert(result.isRight, "Should successfully fetch Logger source")
    val source = result.toOption.get
    assert(source.sourceText.contains("public interface Logger"))
    assert(source.sourceText.contains("void info(String msg)"))
  }
  
  test("fetch real source code for guava ImmutableList") {
    val result = service.getSource(
      "com.google.guava:guava:32.1.3-jre",
      "com.google.common.collect.ImmutableList"
    )
    
    assert(result.isRight)
    val source = result.toOption.get
    assert(source.sourceText.contains("ImmutableList"))
    assert(source.sourceText.contains("class") || source.sourceText.contains("interface"))
  }
}
```

**`EndToEndTest.scala` (MODIFY):**
```scala
test("should fetch source code for org.slf4j.Logger") {
  val request = """
    {
      "jsonrpc": "2.0",
      "method": "tools/call",
      "params": {
        "name": "get_source",
        "arguments": {
          "coordinates": "org.slf4j:slf4j-api:2.0.9",
          "className": "org.slf4j.Logger"
        }
      },
      "id": 3
    }
  """
  
  val response = httpClient.post(serverUrl)
    .body(request)
    .send()
  
  assertEquals(response.code.code, 200)
  
  val json = parse(response.body).toOption.get
  val result = json.hcursor.downField("result")
  
  assert(!result.downField("isError").as[Boolean].toOption.get)
  val sourceText = result.downField("content").as[String].toOption.get
  
  assert(sourceText.contains("public interface Logger"))
  assert(sourceText.contains("void info(String msg)"))
}

test("should return error for artifact without sources JAR") {
  // Some artifacts don't publish sources - need to find example
  // Or test with known non-existent artifact
  val request = """
    {
      "jsonrpc": "2.0",
      "method": "tools/call",
      "params": {
        "name": "get_source",
        "arguments": {
          "coordinates": "com.example:no-sources:1.0.0",
          "className": "Fake"
        }
      },
      "id": 4
    }
  """
  
  val response = httpClient.post(serverUrl).body(request).send()
  val json = parse(response.body).toOption.get
  val result = json.hcursor.downField("result")
  
  assert(result.downField("isError").as[Boolean].toOption.get)
  val errorMsg = result.downField("content").as[String].toOption.get
  assert(errorMsg.contains("not available"))
}
```

### Test Data Strategy

**Real Artifacts (Same as Phase 1):**
- Primary: `org.slf4j:slf4j-api:2.0.9` - Has sources JAR
- Secondary: `com.google.guava:guava:32.1.3-jre` - Has sources JAR
- Let Coursier cache in `~/.cache/coursier`

**Expected Test Output:**
- Zero compiler warnings
- No error logs for successful tests
- All 30+ tests passing (existing + new)

---

## Acceptance Criteria

Phase 2 is **complete** when ALL of the following are true:

### Functional Requirements

- [ ] MCP server has `get_source` tool registered
- [ ] Can fetch source code for `org.slf4j:slf4j-api:2.0.9` → `org.slf4j.Logger`
- [ ] Response contains valid Java source code (interface definition)
- [ ] Source includes expected method signatures (e.g., `void info(String msg)`)
- [ ] Response time under 5 seconds for first request (uncached)
- [ ] Error handling: Returns clear error when sources JAR missing
- [ ] Error handling: Returns clear error when source file not in JAR

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

## Risk Assessment

### Low Risks

**Port Renaming Impact:**
- **Risk**: Renaming `DocumentationReader` to `JarContentReader` touches many files
- **Mitigation**: Do refactor in isolated commit before adding new functionality
- **Impact**: Low - simple rename, compiler will catch all usages

**Sources JAR Availability:**
- **Risk**: Some artifacts might not publish sources JARs
- **Mitigation**: Clear error message suggesting `get_documentation` instead
- **Impact**: Low - this is expected behavior, not a bug

**Source File Locations:**
- **Risk**: Source files might be in non-standard JAR locations
- **Mitigation**: Use same well-known test artifacts (slf4j, guava)
- **Impact**: Low - standard Maven artifacts follow conventions

### Unknowns to Discover

- Do all artifacts that have javadoc also have sources? (Likely not - some only have one)
- Are there Java source files in unusual encodings? (Test with UTF-8 only for now)
- Do source JARs ever include generated sources? (Accept as-is for Phase 2)

---

## Implementation Checklist

### Phase 2.1: Port Refactoring (30 min)

- [ ] Rename `DocumentationReader` → `JarContentReader`
- [ ] Update all imports in domain/infrastructure/application
- [ ] Update all test files
- [ ] Run tests - ensure Phase 1 still works
- [ ] Commit: "refactor: rename DocumentationReader to JarContentReader for clarity"

### Phase 2.2: Extend Domain (30 min)

**TDD Cycle:**
- [ ] Red: Test for `ClassName.toSourcePath()` → `.java` extension
- [ ] Green: Implement `toSourcePath()` method
- [ ] Red: Test inner class handling for source paths
- [ ] Green: Verify existing logic works
- [ ] Create `SourceCode` entity case class
- [ ] Add `SourcesNotAvailable` error to `Errors.scala`
- [ ] Commit: "feat(domain): add source path mapping and SourceCode entity"

### Phase 2.3: Extend Infrastructure (45 min)

**Extend ArtifactRepository Port:**
- [ ] Add `fetchSourcesJar()` method to `ArtifactRepository` trait
- [ ] Red: Test for `CoursierArtifactRepository.fetchSourcesJar()`
- [ ] Green: Implement using `Classifier("sources")`
- [ ] Red: Test error case (sources not found)
- [ ] Green: Return `SourcesNotAvailable` error
- [ ] Update `InMemoryArtifactRepository` test implementation
- [ ] Commit: "feat(infra): add sources JAR fetching to Coursier repository"

**Test JAR Reader with Sources:**
- [ ] Red: Test `JarFileReader.readEntry()` with `.java` file
- [ ] Green: Verify existing implementation works (no code changes needed)
- [ ] Commit: "test: verify JAR reader works with Java source files"

### Phase 2.4: Application Layer (45 min)

- [ ] Create `SourceCodeService.scala` (mirror `DocumentationService`)
- [ ] Red: Unit test for successful source fetch (in-memory)
- [ ] Green: Implement service orchestration
- [ ] Red: Test error propagation
- [ ] Green: Handle errors correctly
- [ ] Create integration test file
- [ ] Red: Integration test with real slf4j sources
- [ ] Green: Wire service, verify it works
- [ ] Commit: "feat(app): add SourceCodeService for fetching Java sources"

### Phase 2.5: Presentation Layer (30 min)

**Add MCP Tool:**
- [ ] Define `GetSourceInput` case class with `derives Codec, Schema`
- [ ] Create `getSourceTool` in `ToolDefinitions.scala`
- [ ] Update `McpServer.scala` to register source tool
- [ ] Update `Main.scala` to wire `SourceCodeService`
- [ ] Commit: "feat(mcp): add get_source tool for source code fetching"

### Phase 2.6: End-to-End Testing (45 min)

- [ ] Add E2E test for `get_source` happy path
- [ ] Add E2E test for sources not available error
- [ ] Run full test suite
- [ ] Debug any failures
- [ ] Manual test with curl or Postman
- [ ] Commit: "test: add E2E tests for get_source tool"

### Phase 2.7: Integration and Polish (30 min)

- [ ] Run all tests - ensure Phase 1 + Phase 2 both work
- [ ] Verify test output pristine
- [ ] Add/update `PURPOSE:` comments
- [ ] Review error messages - ensure they're helpful
- [ ] Update implementation log
- [ ] Final commit: "docs: update Phase 2 completion and polish"

---

## Phase Transition

Upon completion of Phase 2, you will have:

**Deliverables:**
- Working `get_source` tool alongside `get_documentation`
- Complete Java library support (docs + sources)
- Extended port traits for both javadoc and sources
- Proven patterns for adding new tools

**Ready for Phase 3:**
- Infrastructure supports any JAR classifier (javadoc, sources, etc.)
- Port traits are generic and extensible
- Tool registration pattern established
- Next: Add Scala coordinate handling (`::` separator)

**Next Steps:**
1. Mark Phase 2 complete in `tasks.md`
2. Update implementation log with Phase 2 summary
3. Run `/iterative-works:ag-implement JMC-1` for Phase 3 context
4. Begin Phase 3: Fetch Scaladoc HTML for Scala class

---

**Estimated Total:** 3-4 hours
**Confidence:** High (builds on proven Phase 1 patterns)

**Start implementation with:** `/iterative-works:ag-implement JMC-1`
