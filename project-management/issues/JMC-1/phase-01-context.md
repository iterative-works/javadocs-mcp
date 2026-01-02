# Phase 1 Context: Fetch Javadoc HTML for Java class

**Issue:** JMC-1
**Phase:** 1 of 7
**Story:** Fetch Javadoc HTML for a Java library class
**Estimated Effort:** 4-6 hours
**Complexity:** Moderate

---

## Goals

This is the **foundational phase** that establishes the entire vertical slice of the application. By the end of this phase, we will have:

1. **Working MCP HTTP server** using Chimp that responds to tool invocations
2. **Coursier integration** that resolves and downloads `-javadoc.jar` artifacts from Maven Central
3. **JAR extraction logic** that reads HTML files from downloaded JARs
4. **Class name mapping** that converts `org.slf4j.Logger` to `org/slf4j/Logger.html`
5. **End-to-end flow**: MCP tool request → Coursier → JAR → HTML response
6. **Complete test suite**: Unit, integration, and E2E tests following TDD

**Success Criteria:**
- Claude Code can connect to the running MCP server via HTTP
- Can invoke `get_documentation` tool with Java library coordinates
- Receives valid Javadoc HTML for `org.slf4j.Logger`
- Response time is under 5 seconds for first request
- Error handling works for non-existent classes

---

## Scope

### In Scope

**Core Functionality:**
- MCP server with single tool: `get_documentation`
- Chimp HTTP transport and JSON-RPC handling
- Coursier integration for resolving Maven coordinates
- JAR file reading and HTML extraction
- Mapping class names to file paths within JARs
- Basic error handling (artifact not found, class not found)

**Domain Components:**
- `ArtifactCoordinates` value object (groupId, artifactId, version)
- `ClassName` value object (fully qualified class name)
- `Documentation` entity (HTML content, metadata)
- `JavadocExtractor` domain service

**Infrastructure:**
- `CoursierArtifactRepository` - Fetches `-javadoc.jar` artifacts
- `JarFileReader` - Reads HTML files from JARs
- Chimp tool registration and MCP endpoint

**Testing:**
- Unit tests for domain logic (coordinate parsing, path mapping)
- Integration tests with real Maven Central artifacts (slf4j, guava)
- E2E tests with MCP client invoking tools

### Out of Scope (Future Phases)

- **Source code fetching** (Phase 2)
- **Scala library support** (Phases 3-4)
- **Advanced error handling** (Phases 5-6)
- **Caching** (Phase 7)
- **Multiple tool support** - Focus on one tool working perfectly
- **Performance optimization** - Will add caching in Phase 7
- **Persistent storage** - In-memory only for now
- **Configuration files** - Use hardcoded defaults

### Edge Cases Deferred

- Inner classes with complex names (`Foo$1$Bar`)
- Package-info and module-info documentation
- Multi-version JARs
- Shaded/relocated classes
- Non-standard javadoc structures

---

## Dependencies

### Prerequisites (Must Exist)

✅ **Development Environment:**
- Scala CLI installed and working
- JVM 21 available
- Git repository initialized
- Project structure created

✅ **Build Configuration:**
- `project.scala` with Scala 3.3 and JVM 21
- Coursier dependency already added (`io.get-coursier::coursier:2.1.10`)

### New Dependencies to Add

**Before starting implementation, add to `project.scala`:**

```scala
// MCP Server (Chimp)
//> using dep "com.softwaremill.chimp::chimp-core:0.1.6"
//> using dep "com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.11"

// Testing
//> using test.dep "org.scalameta::munit:1.0.0"
```

**External Dependencies:**
- Maven Central access (public, no auth required)
- No database or external services

### From Previous Phases

**None** - This is Phase 1, the foundation.

---

## Technical Approach

### Architecture Overview

We're implementing **vertical slice architecture** with functional core and DDD principles:

```
┌─────────────────────────────────────────────┐
│         Presentation Layer (Chimp)          │
│  - GetDocInput case class                   │
│  - getDocumentationTool definition          │
│  - NettySyncServer HTTP endpoint            │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│          Application Layer                   │
│  - DocumentationService.getDocumentation()  │
│  - Command/Result objects                   │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│           Domain Layer                       │
│  - ArtifactCoordinates (value object)       │
│  - ClassName (value object)                 │
│  - Documentation (entity)                   │
│  - JavadocExtractor (domain service)        │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│        Infrastructure Layer                  │
│  - CoursierArtifactRepository               │
│  - JarFileReader                            │
│  - Coursier API integration                 │
└─────────────────────────────────────────────┘
```

### Key Technical Decisions

**1. Chimp MCP Library**

Chimp provides:
- MCP HTTP transport (JSON-RPC over HTTP)
- Tool registration with auto-schema generation
- Request/response serialization
- Error handling per MCP spec

Example tool definition:
```scala
case class GetDocInput(coordinates: String, className: String) 
  derives Codec, Schema

val getDocTool = tool("get_documentation")
  .description("Fetch Javadoc HTML for a class")
  .input[GetDocInput]
  .handle { input =>
    documentationService
      .getDocumentation(input.coordinates, input.className)
      .fold(
        error => Left(error.message),
        doc => Right(doc.htmlContent)
      )
  }
```

**2. Coursier Integration**

Use Coursier's `Fetch` API to resolve and download artifacts:

```scala
import coursier.*

val fetch = Fetch()
  .addDependencies(
    Dependency(
      Module(Organization("org.slf4j"), ModuleName("slf4j-api")),
      "2.0.9"
    ).withClassifier(Classifier("javadoc"))
  )

val files: Seq[File] = fetch.run()  // Downloads to ~/.cache/coursier
```

**3. JAR File Reading**

Use Java's `java.util.jar.JarFile` to extract HTML:

```scala
import java.util.jar.JarFile
import scala.io.Source

def extractHtml(jarFile: File, htmlPath: String): Either[Error, String] = {
  val jar = JarFile(jarFile)
  try {
    Option(jar.getEntry(htmlPath)) match {
      case Some(entry) =>
        val inputStream = jar.getInputStream(entry)
        val html = Source.fromInputStream(inputStream).mkString
        Right(html)
      case None =>
        Left(ClassNotFound(htmlPath))
    }
  } finally {
    jar.close()
  }
}
```

**4. Class Name Mapping**

Algorithm to convert class name to HTML path:

```scala
def classNameToPath(className: String): String = {
  // Strip inner class suffix (e.g., Logger$Factory -> Logger)
  val outerClass = className.split('$').head
  // Convert package separators to path separators
  val path = outerClass.replace('.', '/')
  // Add HTML extension
  s"$path.html"
}

// Examples:
// org.slf4j.Logger -> org/slf4j/Logger.html
// com.google.common.collect.ImmutableList -> com/google/common/collect/ImmutableList.html
```

**5. Error Handling**

Return `Either[ErrorMessage, HtmlContent]` from domain services:

```scala
sealed trait DocumentationError {
  def message: String
}

case class ArtifactNotFound(coordinates: String) extends DocumentationError {
  def message = s"Artifact not found: $coordinates"
}

case class ClassNotFound(className: String) extends DocumentationError {
  def message = s"Class not found in javadoc: $className"
}
```

Chimp handlers translate to MCP error responses:
```scala
.handle { input =>
  service.getDocumentation(input.coordinates, input.className) match {
    case Right(doc) => Right(doc.htmlContent)
    case Left(error) => Left(error.message)
  }
}
```

---

## Files to Modify/Create

### Directory Structure

Create this structure under `/home/mph/Devel/projects/javadocs-mcp-JMC-1/`:

```
src/
  main/
    scala/
      com/
        example/            # TODO: Replace with actual package name
          javadocsmcp/
            domain/
              ArtifactCoordinates.scala
              ClassName.scala
              Documentation.scala
              JavadocExtractor.scala
              Errors.scala
            application/
              DocumentationService.scala
              Commands.scala
              Results.scala
            infrastructure/
              CoursierArtifactRepository.scala
              JarFileReader.scala
            presentation/
              McpServer.scala
              ToolDefinitions.scala
            Main.scala

  test/
    scala/
      com/
        example/
          javadocsmcp/
            domain/
              ArtifactCoordinatesTest.scala
              ClassNameTest.scala
              JavadocExtractorTest.scala
            application/
              DocumentationServiceTest.scala
            infrastructure/
              CoursierArtifactRepositoryTest.scala
              JarFileReaderTest.scala
            integration/
              EndToEndTest.scala
```

### Existing Files to Modify

1. **`project.scala`** - Add Chimp, Tapir, and MUnit dependencies
2. **`README.md`** - Update with Phase 1 progress (optional, can defer)

### New Files to Create (17 files)

**Domain Layer (5 files):**
1. `domain/ArtifactCoordinates.scala` - Parse and validate Maven coordinates
2. `domain/ClassName.scala` - Parse and validate class names
3. `domain/Documentation.scala` - Documentation entity with HTML content
4. `domain/JavadocExtractor.scala` - Extract docs from JAR (pure logic)
5. `domain/Errors.scala` - Domain error types

**Application Layer (3 files):**
6. `application/DocumentationService.scala` - Use case orchestration
7. `application/Commands.scala` - Command objects
8. `application/Results.scala` - Result types

**Infrastructure Layer (2 files):**
9. `infrastructure/CoursierArtifactRepository.scala` - Coursier integration
10. `infrastructure/JarFileReader.scala` - JAR file I/O

**Presentation Layer (2 files):**
11. `presentation/ToolDefinitions.scala` - Chimp tool registration
12. `presentation/McpServer.scala` - Chimp HTTP server setup

**Main (1 file):**
13. `Main.scala` - Application entry point

**Tests (4 files):**
14. `test/.../domain/ArtifactCoordinatesTest.scala`
15. `test/.../domain/ClassNameTest.scala`
16. `test/.../infrastructure/JarFileReaderTest.scala`
17. `test/.../integration/EndToEndTest.scala`

---

## Testing Strategy

### Test-Driven Development (TDD)

**CRITICAL:** Follow TDD cycle for ALL code:

1. **Red**: Write failing test that validates desired behavior
2. **Green**: Write minimal code to pass the test
3. **Refactor**: Clean up while keeping tests green

### Test Pyramid

**Unit Tests (Fast, Many):**
- Domain logic: coordinate parsing, path mapping, validation
- Pure functions with no I/O
- Test edge cases: empty strings, special characters, inner classes

**Integration Tests (Medium Speed, Some):**
- Coursier integration with real Maven Central
- JAR file reading with real artifacts
- Test with well-known libraries: `org.slf4j:slf4j-api:2.0.9`, `com.google.guava:guava:32.1.3-jre`

**E2E Tests (Slow, Few):**
- Full MCP server lifecycle: start → invoke tool → receive response → stop
- Test happy path: successful documentation fetch
- Test error path: non-existent class

### Specific Test Cases

**`ArtifactCoordinatesTest.scala`:**
```scala
class ArtifactCoordinatesTest extends munit.FunSuite {
  test("parse valid Maven coordinates") {
    val coords = ArtifactCoordinates.parse("org.slf4j:slf4j-api:2.0.9")
    assertEquals(coords.groupId, "org.slf4j")
    assertEquals(coords.artifactId, "slf4j-api")
    assertEquals(coords.version, "2.0.9")
  }

  test("reject coordinates with missing version") {
    val result = ArtifactCoordinates.parse("org.slf4j:slf4j-api")
    assert(result.isLeft)
  }

  test("reject coordinates with invalid format") {
    val result = ArtifactCoordinates.parse("invalid")
    assert(result.isLeft)
  }
}
```

**`ClassNameTest.scala`:**
```scala
class ClassNameTest extends munit.FunSuite {
  test("convert class name to HTML path") {
    val className = ClassName("org.slf4j.Logger")
    assertEquals(className.toHtmlPath, "org/slf4j/Logger.html")
  }

  test("strip inner class suffix") {
    val className = ClassName("org.slf4j.Logger$Factory")
    assertEquals(className.toHtmlPath, "org/slf4j/Logger.html")
  }

  test("reject empty class name") {
    val result = ClassName.parse("")
    assert(result.isLeft)
  }
}
```

**`JarFileReaderTest.scala`:**
```scala
class JarFileReaderTest extends munit.FunSuite {
  test("extract HTML from real slf4j javadoc JAR") {
    // Integration test - downloads real artifact
    val coords = ArtifactCoordinates.parse("org.slf4j:slf4j-api:2.0.9").toOption.get
    val repo = CoursierArtifactRepository()
    val jarFile = repo.fetchJavadocJar(coords).toOption.get

    val reader = JarFileReader()
    val html = reader.readEntry(jarFile, "org/slf4j/Logger.html")

    assert(html.isRight)
    assert(html.toOption.get.contains("public interface Logger"))
  }

  test("return error for non-existent HTML path") {
    val coords = ArtifactCoordinates.parse("org.slf4j:slf4j-api:2.0.9").toOption.get
    val repo = CoursierArtifactRepository()
    val jarFile = repo.fetchJavadocJar(coords).toOption.get

    val reader = JarFileReader()
    val result = reader.readEntry(jarFile, "org/slf4j/NonExistent.html")

    assert(result.isLeft)
  }
}
```

**`EndToEndTest.scala`:**
```scala
class EndToEndTest extends munit.FunSuite {
  test("fetch documentation for org.slf4j.Logger via MCP tool") {
    // Start server
    val server = McpServer.start(port = 8888)

    try {
      // Invoke tool (simulate MCP client)
      val request = GetDocInput(
        coordinates = "org.slf4j:slf4j-api:2.0.9",
        className = "org.slf4j.Logger"
      )

      val response = server.invokeTool("get_documentation", request)

      // Assert success
      assert(response.isRight)
      val html = response.toOption.get
      assert(html.contains("Logger"))
      assert(html.contains("void info(String msg)"))
    } finally {
      server.stop()
    }
  }
}
```

### Test Data Strategy

**Real Artifacts (No Mocking):**
- Use `org.slf4j:slf4j-api:2.0.9` for primary tests (stable, well-known)
- Use `com.google.guava:guava:32.1.3-jre` for secondary tests
- Let Coursier cache artifacts in `~/.cache/coursier` (persistent across test runs)

**Test Execution Order:**
1. Unit tests first (fast feedback)
2. Integration tests second (validate Coursier)
3. E2E tests last (full system validation)

**Expected Test Output:**
- Zero warnings (compile with `-Werror`)
- No error logs for successful tests
- Clean output: `All tests passed`

---

## Acceptance Criteria

Phase 1 is **complete** when ALL of the following are true:

### Functional Requirements

- [ ] MCP server starts on `localhost:8080` without errors
- [ ] Server responds to MCP protocol handshake
- [ ] `get_documentation` tool is registered and discoverable
- [ ] Can fetch Javadoc for `org.slf4j:slf4j-api:2.0.9` → `org.slf4j.Logger`
- [ ] Response contains valid HTML with method signatures
- [ ] Response time is under 5 seconds for first request (uncached)
- [ ] Error handling: Returns clear error for non-existent class
- [ ] Error handling: Returns clear error for non-existent artifact

### Code Quality

- [ ] All code follows DDD structure (domain/application/infrastructure/presentation)
- [ ] All functions are pure except at edges (I/O in infrastructure only)
- [ ] Immutable data structures throughout
- [ ] No compiler warnings (`-Werror` enabled)
- [ ] Code comments: All files start with 2-line `PURPOSE:` comments
- [ ] Naming: Domain concepts clear, no implementation details in names

### Testing

- [ ] All unit tests pass (domain logic)
- [ ] All integration tests pass (Coursier + JAR reading)
- [ ] All E2E tests pass (full MCP flow)
- [ ] Test coverage: All critical paths tested
- [ ] Test output pristine: No warnings, no error logs for successful tests
- [ ] Tests use real artifacts (no mocking of Maven Central)

### Documentation

- [ ] `project.scala` updated with all dependencies
- [ ] Code comments explain domain concepts
- [ ] Each file has clear `PURPOSE:` header

### Git Hygiene

- [ ] Changes committed incrementally (not one giant commit)
- [ ] Commit messages follow TDD pattern: "red: add test for X", "green: implement X"
- [ ] Pre-commit hooks run successfully (no `--no-verify`)
- [ ] Working directory clean (no uncommitted changes)

---

## Risk Assessment

### Medium Risks

**Coursier API Learning Curve:**
- **Risk**: Unfamiliarity with Coursier API for fetching specific classifiers
- **Mitigation**: Study Coursier docs, start with simple `Fetch` examples
- **Fallback**: Coursier CLI is well-documented, can translate CLI commands to API

**JAR File Structure Variations:**
- **Risk**: Some javadoc JARs might have non-standard structure
- **Mitigation**: Test with multiple well-known libraries (slf4j, guava, commons)
- **Fallback**: Document edge cases, defer to future phases

### Low Risks

**Chimp Library Issues:**
- **Risk**: Chimp v0.1.6 might have bugs or missing features
- **Mitigation**: SoftwareMill is reputable, library is actively maintained
- **Fallback**: Can switch to `linkyard/scala-effect-mcp` if needed

**Maven Central Availability:**
- **Risk**: Maven Central could be down during testing
- **Mitigation**: Coursier caches artifacts, use cached versions for tests
- **Fallback**: Run tests offline once artifacts are cached

### Unknowns to Discover

- How does Coursier handle artifacts without javadoc JARs? (Need to test)
- Are there javadoc HTML variations across different Java versions? (Test with JDK 8 vs 11 vs 17 artifacts)
- How does Chimp report errors to MCP clients? (Verify with real Claude Code)

---

## Implementation Checklist

### Phase 1.1: Setup (30 min)

- [ ] Add Chimp and Tapir dependencies to `project.scala`
- [ ] Add MUnit test dependency
- [ ] Create directory structure (`src/main/scala`, `src/test/scala`)
- [ ] Verify `scala-cli compile .` works
- [ ] Commit: "chore: add Chimp MCP dependencies and project structure"

### Phase 1.2: Domain Layer (1-1.5h)

**TDD Cycle for each component:**

- [ ] Red: Write test for `ArtifactCoordinates.parse`
- [ ] Green: Implement `ArtifactCoordinates` value object
- [ ] Red: Write test for `ClassName.toHtmlPath`
- [ ] Green: Implement `ClassName` value object
- [ ] Red: Write test for path mapping with inner classes
- [ ] Green: Implement inner class stripping logic
- [ ] Implement `Documentation` entity (simple case class)
- [ ] Implement domain error types (`ArtifactNotFound`, `ClassNotFound`)
- [ ] Commit: "feat(domain): add ArtifactCoordinates and ClassName value objects"

### Phase 1.3: Infrastructure Layer (1.5-2h)

**Coursier Integration:**

- [ ] Red: Write test for `CoursierArtifactRepository.fetchJavadocJar`
- [ ] Green: Implement Coursier `Fetch` API call with `-javadoc` classifier
- [ ] Red: Write test for error case (artifact not found)
- [ ] Green: Handle Coursier exceptions, map to domain errors
- [ ] Commit: "feat(infra): integrate Coursier for javadoc JAR fetching"

**JAR Reading:**

- [ ] Red: Write test for `JarFileReader.readEntry` with real JAR
- [ ] Green: Implement JAR extraction with `java.util.jar.JarFile`
- [ ] Red: Write test for missing entry in JAR
- [ ] Green: Handle missing entry, return domain error
- [ ] Commit: "feat(infra): add JAR file reader for HTML extraction"

### Phase 1.4: Application Layer (30-45 min)

- [ ] Implement `DocumentationService.getDocumentation` orchestration
- [ ] Red: Write test for successful documentation fetch (integration test)
- [ ] Green: Wire domain and infrastructure together
- [ ] Red: Write test for error propagation
- [ ] Green: Ensure errors flow through correctly
- [ ] Commit: "feat(app): add DocumentationService orchestration"

### Phase 1.5: Presentation Layer (1-1.5h)

**Chimp Tool Registration:**

- [ ] Define `GetDocInput` case class with `derives Codec, Schema`
- [ ] Red: Write test that tool is registered
- [ ] Green: Implement `getDocumentationTool` with Chimp API
- [ ] Create `mcpEndpoint` with tool list
- [ ] Commit: "feat(mcp): add get_documentation tool definition"

**Server Setup:**

- [ ] Implement `McpServer` with Netty server
- [ ] Add `Main.scala` entry point
- [ ] Red: Write E2E test for server startup
- [ ] Green: Start server on `localhost:8080`
- [ ] Commit: "feat(server): add MCP HTTP server with Netty"

### Phase 1.6: End-to-End Testing (1h)

- [ ] Write E2E test: fetch docs for `org.slf4j.Logger`
- [ ] Run E2E test, debug issues
- [ ] Test with Claude Code MCP client (manual verification)
- [ ] Test error cases: non-existent class, non-existent artifact
- [ ] Commit: "test: add E2E tests for get_documentation tool"

### Phase 1.7: Polish and Documentation (30 min)

- [ ] Add `PURPOSE:` comments to all files
- [ ] Review code for warnings, fix any issues
- [ ] Run full test suite, ensure all tests pass
- [ ] Verify test output is pristine (no warnings/errors)
- [ ] Update `README.md` with Phase 1 completion (optional)
- [ ] Final commit: "docs: add PURPOSE comments and polish Phase 1"

---

## Phase Transition

Upon completion of Phase 1, you will have:

**Deliverables:**
- Working MCP server with `get_documentation` tool
- Complete test suite (unit + integration + E2E)
- Clean, functional codebase following DDD principles

**Ready for Phase 2:**
- Server infrastructure reusable for `get_source` tool
- Coursier and JAR reading patterns established
- Testing patterns proven and replicable

**Next Steps:**
1. Mark Phase 1 complete in `tasks.md`
2. Run `/iterative-works:ag-implement JMC-1` to generate Phase 2 context
3. Begin Phase 2: Fetch source code for Java class (builds on this foundation)

---

**Estimated Total:** 4-6 hours
**Confidence:** Medium-High (Chimp reduces complexity, but Coursier API is new)

**Start implementation with:** `/iterative-works:ag-implement JMC-1`

---

## Refactoring Decisions

### R1: Extract Port Traits for Hexagonal Architecture (2025-12-29)

**Trigger:** Code review (`review-phase-01-20251228.md`) identified critical architecture issues:
- Application layer (`DocumentationService`) depends on concrete infrastructure classes
- Violates Dependency Inversion Principle
- No port interfaces defined for dependency injection
- Tests cannot use in-memory implementations because there are no interfaces to implement

**Decision:** Extract port traits and implement proper hexagonal architecture:
- Create `ArtifactRepository` trait (abstracts artifact fetching)
- Create `DocumentationReader` trait (abstracts content reading from sources)
- Update `DocumentationService` to depend on traits, not concrete classes
- Update `Main.scala` wiring to inject concrete implementations
- Create in-memory test implementations for unit testing

**Scope:**
- Files affected: `DocumentationService.scala`, `Main.scala`, new port traits, test files
- Components: Application layer, infrastructure layer, tests
- Boundaries: Do NOT change domain logic, presentation layer, or existing behavior

**Approach:** Two-step implementation:
1. **Step 1:** Extract port traits in `domain/ports/`, update `DocumentationService` and `Main` wiring
2. **Step 2:** Create in-memory test implementations, refactor tests to use them
