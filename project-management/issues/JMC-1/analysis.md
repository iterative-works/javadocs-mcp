# Story-Driven Analysis: MVP: Implement core MCP server with documentation and source tools

**Issue:** JMC-1
**Created:** 2025-12-28
**Status:** Draft
**Classification:** Feature

## Problem Statement

**What user need are we addressing?**

When working with JVM libraries (Java/Scala), AI coding assistants like Claude Code need access to accurate, up-to-date documentation and source code to provide meaningful assistance. Currently, they lack a mechanism to look up Javadoc/Scaladoc or source files for arbitrary Maven artifacts on demand.

**What value does this provide?**

- **For AI assistants**: Enables context-aware code suggestions based on actual library APIs
- **For developers**: Faster, more accurate assistance when working with unfamiliar libraries
- **For projects**: Shared HTTP server means efficient caching across multiple development sessions

**Why HTTP-based MCP?**

Unlike stdio-based MCP servers (one per project), an HTTP-based server can be shared across all projects on a machine, providing efficient caching and resource utilization.

---

## Technology Decision: Chimp MCP Library

**Decision:** Use [Chimp](https://github.com/softwaremill/chimp) (SoftwareMill) for MCP server implementation.

**Rationale:**
- Native Scala 3 library with type-safe tool definitions
- Built on Tapir + Circe - familiar, well-maintained ecosystem
- Automatic JSON Schema generation from case classes via `derives`
- HTTP transport built-in (MCP spec 2025-03-26)
- Clean, minimal API focused on tools (exactly what we need)
- Commercial support available from SoftwareMill
- Active development (v0.1.6, 124 commits, 58 stars)

**What Chimp provides:**
- MCP HTTP server with JSON-RPC
- Tool registration and invocation
- Input schema auto-generation from Scala case classes
- Error handling per MCP spec

**What we still implement:**
- Coursier integration for artifact resolution
- JAR file extraction logic
- Domain logic (coordinate parsing, class name mapping)
- Caching layer

**Dependencies:**
```scala
//> using dep "com.softwaremill.chimp::chimp-core:0.1.6"
//> using dep "com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.11"
```

**Example tool definition with Chimp:**
```scala
case class GetDocInput(coordinates: String, className: String) derives Codec, Schema

val getDocTool = tool("get_documentation")
  .description("Fetch Javadoc/Scaladoc HTML for a class")
  .input[GetDocInput]
  .handle(input => documentationService.getDocumentation(input.coordinates, input.className))

val mcpEndpoint = mcpEndpoint(List(getDocTool), List("mcp"))
NettySyncServer().port(8080).addEndpoint(mcpEndpoint).startAndWait()
```

---

## User Stories

### Story 1: Fetch Javadoc HTML for a Java library class

```gherkin
Feature: Documentation lookup for Java libraries
  As an AI coding assistant
  I want to retrieve Javadoc HTML for a specific class
  So that I can provide accurate code suggestions based on the actual API

Scenario: Successfully fetch Javadoc for a standard Java library class
  Given the MCP server is running
  And Maven Central contains artifact "org.slf4j:slf4j-api:2.0.9" with javadoc JAR
  When I invoke tool "get_documentation" with coordinates "org.slf4j:slf4j-api:2.0.9" and className "org.slf4j.Logger"
  Then I receive status "success"
  And the response contains HTML javadoc for class "Logger"
  And the HTML includes method signatures like "void info(String msg)"
  And the response time is under 5 seconds for first request
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**

This is the foundational story that establishes the entire vertical slice. With Chimp handling MCP transport, complexity is reduced to:
- Integrating Coursier to resolve and download `-javadoc.jar` artifacts
- Parsing JAR files to extract specific class documentation
- Mapping class names to file paths within JARs (`org.slf4j.Logger` → `org/slf4j/Logger.html`)

**Key Technical Challenges:**
- Coursier API for fetching artifacts with specific classifiers (`-javadoc`)
- JAR file traversal and HTML extraction
- Error handling when artifact/class doesn't exist

**Handled by Chimp:**
- MCP HTTP transport (JSON-RPC over HTTP)
- Tool registration and schema generation
- Request/response serialization

**Acceptance:**
- Claude Code can connect to the running HTTP MCP server
- Can successfully invoke `get_documentation` tool
- Receives valid Javadoc HTML for `org.slf4j.Logger`
- Error handling works for non-existent class

---

### Story 2: Fetch source code for a Java library class

```gherkin
Feature: Source code lookup for Java libraries
  As an AI coding assistant
  I want to retrieve source code for a specific class
  So that I can understand implementation details when API docs are insufficient

Scenario: Successfully fetch source for a standard Java library class
  Given the MCP server is running
  And Maven Central contains artifact "org.slf4j:slf4j-api:2.0.9" with sources JAR
  When I invoke tool "get_source" with coordinates "org.slf4j:slf4j-api:2.0.9" and className "org.slf4j.Logger"
  Then I receive status "success"
  And the response contains Java source code for interface "Logger"
  And the source includes method declaration "void info(String msg);"
  And the response time is under 5 seconds for first request
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**

This story builds on Story 1's infrastructure but is simpler because:
- MCP server already exists from Story 1
- Coursier integration already works, just fetching `-sources.jar` instead of `-javadoc.jar`
- Source files are plain text (simpler than HTML parsing)

**Key Technical Challenges:**
- Mapping class names to `.java` file paths (`org.slf4j.Logger` → `org/slf4j/Logger.java`)
- Handling cases where sources JAR might not exist (some artifacts don't publish sources)
- Text encoding issues (UTF-8, but need to handle edge cases)

**Acceptance:**
- Can successfully invoke `get_source` tool
- Receives valid Java source code for `org.slf4j.Logger`
- Source code is properly formatted and readable
- Error handling works for missing sources JAR

---

### Story 3: Fetch Scaladoc HTML for a Scala library class

```gherkin
Feature: Documentation lookup for Scala libraries
  As an AI coding assistant
  I want to retrieve Scaladoc HTML for a Scala class
  So that I can assist with Scala-specific library usage

Scenario: Successfully fetch Scaladoc for a Scala library class
  Given the MCP server is running
  And Maven Central contains artifact "org.typelevel::cats-effect:3.5.4" with javadoc JAR (Scaladoc)
  When I invoke tool "get_documentation" with coordinates "org.typelevel::cats-effect:3.5.4" and className "cats.effect.IO"
  Then I receive status "success"
  And the response contains HTML Scaladoc for class "IO"
  And the HTML includes method signatures like "def flatMap"
  And the response time is under 5 seconds for first request
```

**Estimated Effort:** 3-4h
**Complexity:** Moderate

**Technical Feasibility:**

Builds directly on Story 1. Main differences:
- Scala artifact coordinates use `::` separator (handled by Coursier transparently)
- Scaladoc HTML has different structure than Javadoc HTML (but we're not parsing, just returning)
- Scala cross-version handling (e.g., `cats-effect_3` vs `cats-effect_2.13`)

**Key Technical Challenges:**
- Understanding Scala artifact naming conventions (`::` for Scala version, `:` for explicit)
- Coursier might resolve `org.typelevel::cats-effect:3.5.4` to `org.typelevel:cats-effect_3:3.5.4`
- Scaladoc file paths might differ from Javadoc (need to verify)

**Acceptance:**
- Can successfully fetch Scaladoc for `cats.effect.IO`
- Coordinates with `::` are correctly resolved
- HTML structure is preserved (even if different from Javadoc)

---

### Story 4: Fetch source code for a Scala library class

```gherkin
Feature: Source code lookup for Scala libraries
  As an AI coding assistant
  I want to retrieve source code for a Scala class
  So that I can understand Scala-specific implementation patterns

Scenario: Successfully fetch source for a Scala library class
  Given the MCP server is running
  And Maven Central contains artifact "org.typelevel::cats-effect:3.5.4" with sources JAR
  When I invoke tool "get_source" with coordinates "org.typelevel::cats-effect:3.5.4" and className "cats.effect.IO"
  Then I receive status "success"
  And the response contains Scala source code for class "IO"
  And the source includes Scala syntax like "sealed abstract class IO"
  And the response time is under 5 seconds for first request
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**

Builds on Stories 2 and 3. Very similar to Story 2 but with:
- Scala file extension (`.scala` instead of `.java`)
- Scala artifact coordinate handling (already solved in Story 3)

**Key Technical Challenges:**
- Minimal - mostly combining existing solutions
- Need to try both `.scala` and `.java` extensions (some Scala projects have mixed sources)

**Acceptance:**
- Can successfully fetch Scala source for `cats.effect.IO`
- Source code is valid Scala syntax
- Works with Scala artifact coordinates

---

### Story 5: Handle missing artifacts gracefully

```gherkin
Feature: Error handling for missing artifacts
  As an AI coding assistant
  I want clear error messages when artifacts don't exist
  So that I can inform the user about typos or unavailable libraries

Scenario: Artifact does not exist in any repository
  Given the MCP server is running
  When I invoke tool "get_documentation" with coordinates "com.nonexistent:fake-library:1.0.0" and className "com.fake.Class"
  Then I receive status "error"
  And the error message contains "Artifact not found"
  And the error message includes coordinates "com.nonexistent:fake-library:1.0.0"

Scenario: Artifact exists but javadoc JAR is not published
  Given the MCP server is running
  And Maven Central contains artifact "some.group:artifact:1.0.0" without javadoc JAR
  When I invoke tool "get_documentation" with coordinates "some.group:artifact:1.0.0" and className "some.Class"
  Then I receive status "error"
  And the error message contains "Javadoc not available"
  And the error message suggests trying "get_source" instead
```

**Estimated Effort:** 3-4h
**Complexity:** Straightforward

**Technical Feasibility:**

Error handling layer on top of existing infrastructure. Need to:
- Catch Coursier resolution errors
- Distinguish between "artifact doesn't exist" vs "classifier not available"
- Provide helpful error messages
- Ensure errors don't crash the server

**Key Technical Challenges:**
- Understanding Coursier's error types
- Creating user-friendly error messages from technical exceptions
- Logging errors for debugging without exposing internals

**Acceptance:**
- Clear error message when artifact doesn't exist
- Clear error message when javadoc/sources JAR missing
- Server remains stable after errors
- Errors logged appropriately

---

### Story 6: Handle missing classes within valid artifacts

```gherkin
Feature: Error handling for missing classes
  As an AI coding assistant
  I want clear error messages when a class doesn't exist in an artifact
  So that I can inform the user about typos or incorrect class names

Scenario: Class does not exist in the artifact
  Given the MCP server is running
  And Maven Central contains artifact "org.slf4j:slf4j-api:2.0.9"
  When I invoke tool "get_documentation" with coordinates "org.slf4j:slf4j-api:2.0.9" and className "org.slf4j.NonExistentClass"
  Then I receive status "error"
  And the error message contains "Class not found"
  And the error message includes className "org.slf4j.NonExistentClass"

Scenario: Class name has incorrect capitalization
  Given the MCP server is running
  And Maven Central contains artifact "org.slf4j:slf4j-api:2.0.9"
  When I invoke tool "get_documentation" with coordinates "org.slf4j:slf4j-api:2.0.9" and className "org.slf4j.logger"
  Then I receive status "error"
  And the error message contains "Class not found"
  And the error message suggests checking capitalization
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**

After fetching JAR, need to:
- Search for the expected file path within JAR
- Handle case sensitivity (JVM is case-sensitive)
- Provide helpful error messages with suggestions

**Key Technical Challenges:**
- JAR file traversal to verify class exists
- Heuristics for better error messages (did they mean `Logger` not `logger`?)
- Performance (don't list all classes, just report missing)

**Acceptance:**
- Clear error when class doesn't exist in JAR
- Error message suggests checking spelling/capitalization
- No false positives (correct class names work)

---

### Story 7: In-memory caching for repeated lookups

```gherkin
Feature: In-memory caching for performance
  As a developer using the MCP server
  I want repeated lookups to be fast
  So that the AI assistant doesn't slow down my workflow

Scenario: Second request for same class is much faster
  Given the MCP server is running
  And I have previously fetched documentation for "org.slf4j:slf4j-api:2.0.9" class "org.slf4j.Logger"
  When I invoke tool "get_documentation" again with the same coordinates and className
  Then I receive status "success"
  And the response time is under 100ms (served from cache)
  And the response content is identical to the first request

Scenario: Different class from same artifact reuses downloaded JAR
  Given the MCP server is running
  And I have previously fetched documentation for "org.slf4j:slf4j-api:2.0.9" class "org.slf4j.Logger"
  When I invoke tool "get_documentation" with coordinates "org.slf4j:slf4j-api:2.0.9" and className "org.slf4j.LoggerFactory"
  Then I receive status "success"
  And the response time is under 1 second (JAR already downloaded)
  And no network request is made to download the JAR again
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**

Two-level caching strategy:
1. **Result cache**: Store (coordinates, className) → result (fast)
2. **JAR cache**: Store downloaded JARs (Coursier might already do this)

**Key Technical Challenges:**
- Cache eviction strategy (LRU? Size limit? Time-based?)
- Thread safety (multiple concurrent requests)
- Memory management (JARs can be large, need limits)
- Testing cache behavior

**Acceptance:**
- Second request for same class is under 100ms
- Different class from same artifact doesn't re-download JAR
- Cache doesn't grow unbounded
- Cache is thread-safe

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 1: Fetch Javadoc HTML for a Java library class

**Domain Layer:**
- `ArtifactCoordinates` - Value object (groupId, artifactId, version)
- `ClassName` - Value object (fully qualified class name)
- `Documentation` - Entity (HTML content, metadata)
- `JavadocExtractor` - Domain service to extract docs from JAR

**Application Layer:**
- `DocumentationService.getDocumentation(coordinates, className)` - Use case
- `GetDocumentationCommand` - Command object
- `DocumentationResult` - Result object (success/error)

**Infrastructure Layer:**
- `ArtifactRepository` - Port for fetching artifacts
- `CoursierArtifactRepository` - Adapter implementation
- `JarFileReader` - Utility to read JAR contents
- `FileSystemCache` - Cache interface (Coursier's cache)

**Presentation Layer (via Chimp):**
- `GetDocInput` - Case class with `derives Codec, Schema` for auto-schema generation
- `getDocumentationTool` - Chimp tool definition using `tool().input[].handle()`
- `mcpEndpoint` - Chimp MCP endpoint aggregating tools
- `NettySyncServer` - Tapir Netty server hosting the MCP endpoint

---

### For Story 2: Fetch source code for a Java library class

**Domain Layer:**
- Reuse `ArtifactCoordinates` and `ClassName` from Story 1
- `SourceCode` - Entity (source text, language, metadata)
- `SourceExtractor` - Domain service to extract source from JAR

**Application Layer:**
- `SourceCodeService.getSource(coordinates, className)` - Use case
- `GetSourceCommand` - Command object
- `SourceResult` - Result object

**Infrastructure Layer:**
- Reuse `ArtifactRepository` and `CoursierArtifactRepository` from Story 1
- Reuse `JarFileReader` from Story 1
- Extend for `-sources` classifier

**Presentation Layer (via Chimp):**
- `GetSourceInput` - Case class with `derives Codec, Schema`
- `getSourceTool` - Chimp tool definition
- Added to existing `mcpEndpoint` tools list

---

### For Story 3: Fetch Scaladoc HTML for a Scala library class

**Domain Layer:**
- `ScalaArtifactCoordinates` - Value object (handles `::` separator)
- Reuse `ClassName` and `Documentation` from Story 1
- Potentially extend `JavadocExtractor` or create `ScaladocExtractor`

**Application Layer:**
- Reuse `DocumentationService` from Story 1 (should handle both)
- Coordinate parsing logic to handle Scala conventions

**Infrastructure Layer:**
- Extend `CoursierArtifactRepository` to handle Scala artifact resolution
- Reuse JAR reading infrastructure

**Presentation Layer (via Chimp):**
- Same `GetDocInput` case class handles both Java (`:`) and Scala (`::`) coordinates
- Tool description updated to document coordinate formats

---

### For Story 4: Fetch source code for a Scala library class

**Domain Layer:**
- Reuse components from Stories 2 and 3
- Extend `SourceExtractor` to try both `.scala` and `.java` extensions

**Application Layer:**
- Reuse `SourceCodeService` from Story 2
- Handle Scala file extension logic

**Infrastructure Layer:**
- Reuse all infrastructure from previous stories

**Presentation Layer (via Chimp):**
- Reuse `getSourceTool` from Story 2
- Update tool description with Scala examples

---

### For Story 5: Handle missing artifacts gracefully

**Domain Layer:**
- `ArtifactNotFoundError` - Domain error
- `ClassifierNotAvailableError` - Domain error (javadoc/sources missing)
- Error hierarchy for different failure modes

**Application Layer:**
- Error handling in `DocumentationService` and `SourceCodeService`
- Map domain errors to user-friendly messages
- `ErrorResult` - Structured error response

**Infrastructure Layer:**
- Catch Coursier resolution exceptions
- Map to domain errors
- Logging infrastructure

**Presentation Layer (via Chimp):**
- Chimp handles MCP error response formatting (returns `Left(errorMessage)`)
- Error message templates in domain layer
- Chimp translates to proper JSON-RPC error structure

---

### For Story 6: Handle missing classes within valid artifacts

**Domain Layer:**
- `ClassNotFoundError` - Domain error
- Class existence validation logic

**Application Layer:**
- Pre-validation: check class exists before extraction
- Enhanced error messages with suggestions

**Infrastructure Layer:**
- `JarFileReader` extension: list entries, check existence
- File path mapping validation

**Presentation Layer (via Chimp):**
- User-friendly error messages returned via `Left(message)`
- Suggestion logic (case-sensitivity hints) in domain/application layer

---

### For Story 7: In-memory caching for repeated lookups

**Domain Layer:**
- `Cache[K, V]` - Port for caching
- Cache key derivation from coordinates + className

**Application Layer:**
- Wrap `DocumentationService` and `SourceCodeService` with caching
- Cache invalidation strategy (if needed)

**Infrastructure Layer:**
- `InMemoryCache` - Adapter implementation
- LRU eviction policy
- Thread-safe concurrent access
- Memory limit enforcement

**Presentation Layer:**
- Cache statistics logging
- Caching is transparent to Chimp layer (applied in application/domain services)

---

## Technical Risks & Uncertainties

### ✅ RESOLVED: HTTP MCP Server Implementation

**Decision:** Use **Chimp** (SoftwareMill) - a Scala 3 MCP library built on Tapir.

**Research conducted:** Evaluated linkyard/scala-effect-mcp, indoorvivants/mcp, official Java SDK, and Chimp. Chimp was selected for its clean API, Tapir integration, and tools-focused design matching our needs.

**What this resolves:**
- HTTP transport: Handled by Chimp + Tapir Netty server
- JSON-RPC protocol: Handled by Chimp
- Tool schema generation: Auto-derived from case classes via `derives Codec, Schema`
- MCP protocol version: Chimp implements MCP spec 2025-03-26

**Impact on estimates:** Story 1 reduced from 8-12h to 4-6h.

---

### CLARIFY: Coursier JAR Caching Strategy

Coursier likely caches downloaded JARs to `~/.cache/coursier`. Do we rely on this or add our own layer?

**Questions to answer:**

1. Does Coursier's file-system cache give us enough performance?
2. Do we need in-memory JAR content caching beyond Coursier's cache?
3. Should we cache extracted content (HTML/source) separately from JARs?

**Options:**

- **Option A: Rely solely on Coursier's cache** - Simplest
  - Pros: No additional code, Coursier handles locking/concurrency
  - Cons: Still involves JAR extraction on each request for same class

- **Option B: Add in-memory result cache (Story 7)** - Balanced
  - Pros: Much faster repeated lookups, reasonable memory usage
  - Cons: Need cache management, memory limits, eviction

- **Option C: Persistent result cache (SQLite/file)** - Most performant
  - Pros: Survives server restarts, can be very large
  - Cons: Out of MVP scope, adds complexity

**Impact:** Affects Story 7 scope and overall performance characteristics.

**Recommendation:** **Option B** for MVP (Story 7), potentially Option C in future.

---

### CLARIFY: Class Name to File Path Mapping

How exactly do we map `cats.effect.IO` to file paths in javadoc/source JARs?

**Questions to answer:**

1. For Javadoc: Is it always `cats/effect/IO.html` or can structure vary?
2. For sources: Is it always `cats/effect/IO.scala` or can there be variations?
3. How do we handle inner classes? (`IO$Pure` or `IO.Pure`?)
4. What about package-info files and module-info?

**Options:**

- **Option A: Simple replacement algorithm** - Replace `.` with `/`, add extension
  - Pros: Fast, simple, works for 90% of cases
  - Cons: Might miss edge cases, inner classes tricky

- **Option B: JAR manifest/index inspection** - Read JAR metadata
  - Pros: More accurate, handles edge cases
  - Cons: More complex, not all JARs have indexes

- **Option C: Fuzzy search within JAR** - Try multiple path patterns
  - Pros: Most robust, handles all cases
  - Cons: Slower, more complex

**Impact:** Affects accuracy of class lookups in Stories 1-4 and error handling in Story 6.

**Recommendation:** Start with **Option A**, fall back to Option C if we hit issues during testing.

---

### CLARIFY: Scaladoc vs Javadoc Detection

How do we know whether to look for Scaladoc or Javadoc for a given artifact?

**Questions to answer:**

1. Can we determine language from coordinates alone (`::` = Scala)?
2. Should we try both and return whichever exists?
3. Do some Scala libraries publish both?

**Options:**

- **Option A: Infer from coordinates** - `::` means Scala, `:` means Java
  - Pros: Fast, no guessing
  - Cons: Might be wrong for edge cases (Scala with explicit version)

- **Option B: Try both formats** - Fetch javadoc JAR, check file extensions
  - Pros: Most accurate
  - Cons: Slower, more complex

- **Option C: Add explicit `language` parameter** - User specifies
  - Pros: No ambiguity
  - Cons: More burden on caller

**Impact:** Affects Stories 3-4 implementation and user experience.

**Recommendation:** **Option A** for MVP (simple heuristic), document edge cases.

---

### ✅ RESOLVED: MCP Tool Schema Design

**Decision:** Use simple string parameters with Chimp's auto-schema generation.

```scala
case class GetDocInput(coordinates: String, className: String) derives Codec, Schema
case class GetSourceInput(coordinates: String, className: String) derives Codec, Schema
```

**Generated JSON Schema:**
```json
{
  "coordinates": "org.typelevel::cats-effect:3.5.4",
  "className": "cats.effect.IO"
}
```

**Rationale:**
- Simple, familiar Maven/Coursier coordinate format
- Chimp auto-generates JSON Schema from case class
- Runtime validation in domain layer (coordinate parsing)
- No wildcards for MVP (exact class matches only)

---

### ✅ RESOLVED: Error Response Format

**Decision:** Use Chimp's built-in error handling with descriptive messages.

Chimp tool handlers return `Either[String, String]`:
- `Right(content)` → Success response with content
- `Left(errorMessage)` → MCP error response with message

**Error message format (in domain layer):**
```
Artifact not found: org.example:fake:1.0.0
Check spelling and verify the artifact exists on Maven Central.
```

Chimp translates this to proper JSON-RPC error structure per MCP spec.

**Rationale:**
- Simple, follows MCP standard
- Descriptive messages include suggestions inline
- No need for custom error code taxonomy for MVP

---

## Total Estimates

**Story Breakdown (revised with Chimp):**
- Story 1 (Fetch Javadoc for Java class): **4-6 hours** *(reduced from 8-12h)*
- Story 2 (Fetch source for Java class): 3-4 hours *(slightly reduced)*
- Story 3 (Fetch Scaladoc for Scala class): 2-3 hours *(slightly reduced)*
- Story 4 (Fetch source for Scala class): 1-2 hours *(slightly reduced)*
- Story 5 (Handle missing artifacts): 2-3 hours *(slightly reduced)*
- Story 6 (Handle missing classes): 2-3 hours
- Story 7 (In-memory caching): 4-6 hours

**Total Range:** 18-27 hours *(reduced from 26-38h)*

**Confidence:** Medium-High

**Reasoning:**

- **Story 1 complexity significantly reduced**: With Chimp handling MCP transport, JSON-RPC, and schema generation, Story 1 focuses only on Coursier integration and JAR extraction. Main unknowns are Coursier API and JAR file handling.

- **Stories 2-4 build on foundation**: Once Story 1 works, Stories 2-4 are mostly variations on the same pattern. Confidence is high.

- **Error handling is well-understood**: Stories 5-6 are straightforward exception handling and validation. Low risk.

- **Caching adds moderate complexity**: Story 7 involves concurrency and memory management, but patterns are well-known.

- **Most CLARIFY markers resolved**: HTTP library, tool schema, and error format decisions are made. Remaining CLARIFYs are lower-risk.

- **Testing time included**: Each story estimate includes unit tests, integration tests, and scenario tests per TDD guidelines.

**Remaining risk factors:**
- Coursier API learning curve (mitigated by good docs)
- Edge cases in JAR file structures (javadoc format variations)
- Scala version handling subtleties

**Mitigation:**
- Start with well-known artifacts (slf4j, cats-effect) for testing
- Defer edge cases to post-MVP backlog

---

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**: Pure domain logic, value objects, business rules
2. **Integration Tests**: Coursier integration, JAR reading, real artifacts
3. **E2E Scenario Tests**: Automated verification of the Gherkin scenario

---

**Story 1: Fetch Javadoc HTML for Java library class**

**Unit Tests:**
- `ArtifactCoordinates` parsing and validation
- `ClassName` parsing and validation
- File path mapping logic (`org.slf4j.Logger` → `org/slf4j/Logger.html`)
- Error construction for various failure modes

**Integration Tests:**
- Coursier fetches real `-javadoc.jar` from Maven Central
- JAR file reading extracts expected HTML file
- End-to-end: coordinates + className → HTML content
- Test with multiple well-known artifacts (slf4j, guava, commons-lang3)

**E2E Scenario Tests:**
- Start MCP server
- Connect as MCP client
- Invoke `get_documentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")`
- Assert response contains expected HTML
- Assert response time < 5s
- Verify HTML contains method signatures

**Test Data:**
- Use real Maven Central artifacts (slf4j, guava) - stable, well-known
- No mocking of Coursier or Maven Central (per guidelines)

---

**Story 2: Fetch source code for Java library class**

**Unit Tests:**
- File path mapping for sources (`.html` → `.java`)
- Source code extraction logic
- Text encoding handling

**Integration Tests:**
- Coursier fetches real `-sources.jar`
- JAR reading extracts expected `.java` file
- Multiple test artifacts with sources

**E2E Scenario Tests:**
- MCP server running from Story 1
- Invoke `get_source("org.slf4j:slf4j-api:2.0.9", "org.slf4j.Logger")`
- Assert response contains valid Java source
- Assert response time < 5s
- Verify source compiles (syntax check)

---

**Story 3: Fetch Scaladoc HTML for Scala library class**

**Unit Tests:**
- Scala coordinates parsing (`::` separator)
- Cross-version handling (`_3`, `_2.13`)
- File path mapping for Scaladoc (if different from Javadoc)

**Integration Tests:**
- Coursier resolves Scala artifacts correctly
- Fetch Scaladoc for cats-effect, zio, akka
- Verify HTML structure

**E2E Scenario Tests:**
- Invoke `get_documentation("org.typelevel::cats-effect:3.5.4", "cats.effect.IO")`
- Assert response contains Scaladoc HTML
- Verify Scala-specific syntax in docs

---

**Story 4: Fetch source code for Scala library class**

**Unit Tests:**
- Scala file extension logic (`.scala`, `.java` fallback)
- Mixed-source handling

**Integration Tests:**
- Fetch sources for Scala artifacts
- Test with pure Scala and mixed Java/Scala projects

**E2E Scenario Tests:**
- Invoke `get_source("org.typelevel::cats-effect:3.5.4", "cats.effect.IO")`
- Assert response contains valid Scala source
- Verify Scala syntax (sealed abstract class, etc.)

---

**Story 5: Handle missing artifacts gracefully**

**Unit Tests:**
- Error message formatting
- Error type classification
- Suggestion generation logic

**Integration Tests:**
- Request non-existent artifact → proper error
- Request artifact without javadoc JAR → proper error
- Coursier error mapping to domain errors

**E2E Scenario Tests:**
- Invoke `get_documentation("com.nonexistent:fake:1.0.0", "Fake")`
- Assert error response with "Artifact not found"
- Assert server remains stable (can handle subsequent requests)

**Error Log Verification:**
- Errors logged at appropriate level (WARN for client errors, ERROR for server issues)
- No stack traces for expected errors (artifact not found)
- Stack traces for unexpected errors

---

**Story 6: Handle missing classes within valid artifacts**

**Unit Tests:**
- Class existence checking logic
- Suggestion generation (case sensitivity hints)

**Integration Tests:**
- Request non-existent class in valid artifact
- Request class with wrong capitalization
- Verify helpful error messages

**E2E Scenario Tests:**
- Invoke `get_documentation("org.slf4j:slf4j-api:2.0.9", "org.slf4j.NonExistent")`
- Assert error response with "Class not found"
- Assert suggestion to check spelling

---

**Story 7: In-memory caching for repeated lookups**

**Unit Tests:**
- Cache key generation (coordinates + className)
- LRU eviction logic
- Thread safety (concurrent access)
- Memory limit enforcement

**Integration Tests:**
- First request downloads, second request cached
- Different class from same artifact reuses JAR
- Cache eviction when limit reached
- Concurrent requests don't corrupt cache

**E2E Scenario Tests:**
- Request same class twice, measure timing
- Assert second request < 100ms
- Verify no network call on cache hit (via logging)

**Performance Tests:**
- 100 concurrent requests for same class
- 1000 unique classes (cache eviction behavior)
- Memory usage monitoring

---

**Test Data Strategy:**

- **Real artifacts from Maven Central**: slf4j-api, guava, cats-effect, zio-core
- **No mocking of external services**: Test against real Maven Central
- **Fixtures for error cases**: Prepare known-bad coordinates for error testing
- **Factories for domain objects**: `ArtifactCoordinates.of(...)`, `ClassName.of(...)`

**Regression Coverage:**

- After each story, re-run all previous E2E scenarios
- Ensure new features don't break existing functionality
- CI pipeline runs full test suite on every commit

**Test Output Requirements:**

- Zero warnings in test output
- No error logs for successful tests (per guidelines)
- Expected errors in error-case tests must be captured and asserted

---

## Deployment Considerations

### Database Changes

**MVP: No database**
- In-memory caching only (Story 7)
- No persistent storage
- Server restart loses cache (acceptable for MVP)

**Future consideration:**
- If persistent cache needed, add SQLite or file-based storage
- Migration: none needed (additive feature)

---

### Configuration Changes

**Environment variables needed:**

```bash
# Server configuration
HTTP_PORT=8080                    # MCP server port (default: 8080)
HTTP_HOST=localhost               # Bind address (default: localhost)

# Coursier configuration (optional)
COURSIER_CACHE=$HOME/.cache/coursier  # Cache directory (default)
COURSIER_REPOSITORIES=central         # Additional repositories (optional)

# Caching (Story 7)
CACHE_MAX_SIZE_MB=100             # In-memory cache limit (default: 100MB)
CACHE_EVICTION_POLICY=LRU         # Eviction policy (default: LRU)

# Logging
LOG_LEVEL=INFO                    # Log level (default: INFO)
```

**Configuration file (optional):**
- For MVP, env vars sufficient
- Future: HOCON or JSON config file

---

### Rollout Strategy

**MVP deployment:**

1. **Local development first** (Stories 1-6)
   - Run server locally on `localhost:8080`
   - Configure Claude Code to connect via HTTP MCP
   - Test with real workflows

2. **Story-by-story deployment** (incremental)
   - Deploy Story 1 → test with Java artifacts
   - Deploy Story 2 → test source fetching
   - Deploy Story 3 → test Scala artifacts
   - Continue incrementally

3. **Feature flags** (optional for MVP, recommended for future)
   - `ENABLE_CACHING=true/false` for Story 7
   - `ENABLE_SCALA_SUPPORT=true/false` for Stories 3-4
   - Allows rolling back individual features without code changes

**Server lifecycle:**

- Start: `scala-cli run . -- --port 8080`
- Stop: `SIGTERM` (graceful shutdown)
- Restart: No data loss (cache rebuilt on demand)

---

### Rollback Plan

**If Story N fails in production:**

1. **Server-level rollback:**
   - Stop server: `kill <PID>`
   - Checkout previous commit: `git checkout <previous-tag>`
   - Restart server
   - Total downtime: ~30 seconds

2. **Feature-level rollback (if using feature flags):**
   - Set `ENABLE_FEATURE=false`
   - Restart server
   - Feature disabled, other functionality intact

3. **No data loss risk:**
   - MVP has no persistent state
   - Cache rebuild on next request
   - No database migrations to roll back

**Monitoring for issues:**

- Watch logs for errors: `tail -f server.log`
- Test basic functionality: `curl` to health endpoint
- Run smoke tests: E2E test suite against deployed server

---

## Dependencies

### Prerequisites

**Before starting Story 1:**

- ✅ Scala CLI installed and working
- ✅ JVM 21 available
- ✅ Coursier dependency added to `project.scala`
- ✅ **Chimp MCP library** - `com.softwaremill.chimp::chimp-core:0.1.6`
- ✅ **Tapir Netty server** - `com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.11`
- ⚠️ Test framework setup (MUnit recommended for Scala 3)

**External accounts/access:**

- Maven Central access (public, no credentials)
- Claude Code MCP configuration (need to document)

**Development environment:**

- Git repository initialized
- CI pipeline setup (GitHub Actions, GitLab CI, or local)
- Test data fixtures location

---

### Story Dependencies

**Sequential dependencies:**

- **Story 1 → Stories 2, 3, 4**: Story 1 establishes MCP server and Coursier integration; all others build on this foundation
- **Stories 1-4 → Story 5**: Error handling requires working success cases to contrast against
- **Stories 1-4 → Story 6**: Missing class errors require working artifact resolution
- **Stories 1-6 → Story 7**: Caching requires working retrieval to cache

**Can be parallelized:**

- **Story 2 || Story 3**: After Story 1, source and Scaladoc can be developed independently
- **Story 5 || Story 6**: Both are error handling, could be done in parallel if two developers
- None really - linear dependency chain is strong

**Recommended sequence:**

1. Story 1 (foundational - no alternatives)
2. Story 2 or Story 3 (choose based on primary use case)
3. Whichever of Story 2/3 wasn't done
4. Story 4 (completes CRUD matrix: Java/Scala × docs/source)
5. Story 5 (artifact-level errors)
6. Story 6 (class-level errors)
7. Story 7 (performance optimization)

---

### External Blockers

**Potential blockers:**

- ~~**MCP HTTP specification clarity**~~: Resolved - Chimp handles MCP protocol

- **Maven Central availability**: If Maven Central is down, testing blocked
  - *Mitigation*: Use local Coursier cache for testing, have offline fallback fixtures

- **Coursier API changes**: If Coursier 2.1.10 has bugs or API issues
  - *Mitigation*: Well-established library, unlikely; can downgrade if needed

- **Claude Code compatibility**: If Claude Code's MCP client has quirks
  - *Mitigation*: Test early with real Claude Code instance, not just raw HTTP

- **Chimp library issues**: If Chimp 0.1.6 has bugs or missing features
  - *Mitigation*: Library is from SoftwareMill (reputable), active development; can fall back to linkyard/scala-effect-mcp if needed

**No blockers from other teams:**
- Solo project, no dependencies on external teams

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Fetch Javadoc HTML for a Java library class** - *Foundational vertical slice*
   - Why first: Establishes entire architecture (MCP server, Coursier, JAR reading, tool registration)
   - This is the hardest story - tackles all major unknowns
   - Success here validates the entire approach
   - Delivers immediate value: can look up Java library docs

2. **Story 2: Fetch source code for a Java library class** - *Completes Java support*
   - Why second: Builds on Story 1 infrastructure, adds second tool
   - Simpler than Story 1 (no HTML complexity, just text files)
   - Validates that our architecture supports multiple tools cleanly
   - After this, Java library support is complete

3. **Story 3: Fetch Scaladoc HTML for a Scala library class** - *Adds Scala docs*
   - Why third: Extends to Scala ecosystem (critical for our use case)
   - Tests our coordinate parsing and Coursier integration robustness
   - Validates that Javadoc extraction works for Scaladoc too
   - High value: makes tool useful for Scala projects

4. **Story 4: Fetch source code for a Scala library class** - *Completes Scala support*
   - Why fourth: Rounds out the feature matrix (Java+Scala, docs+source)
   - Straightforward combination of Stories 2 and 3
   - After this, core functionality is complete

5. **Story 5: Handle missing artifacts gracefully** - *Production-readiness begins*
   - Why fifth: Makes the tool robust for real-world use
   - Up to now, we've tested happy paths; this handles errors
   - Critical for good UX when users typo coordinates

6. **Story 6: Handle missing classes within valid artifacts** - *Completes error handling*
   - Why sixth: Handles the other major error case (class not found)
   - Builds on Story 5's error infrastructure
   - After this, error handling is comprehensive

7. **Story 7: In-memory caching for repeated lookups** - *Performance optimization*
   - Why last: Optimization after functionality is complete
   - Not strictly necessary for MVP, but high impact on UX
   - Can be deployed independently as a performance enhancement
   - If time is short, could be deferred to post-MVP

---

**Iteration Plan (revised with Chimp):**

**Iteration 1 (Stories 1-2): Java library support - ~7-10 hours**
- Goal: Working MCP server that can fetch Java docs and sources
- Deliverable: Claude Code can look up `org.slf4j.Logger` docs and source
- Risk: Medium (Coursier API learning, JAR handling)
- Milestone: "Java MVP"

**Iteration 2 (Stories 3-4): Scala library support - ~3-5 hours**
- Goal: Extend to Scala ecosystem
- Deliverable: Claude Code can look up `cats.effect.IO` docs and source
- Risk: Low (builds on proven infrastructure)
- Milestone: "Full JVM MVP"

**Iteration 3 (Stories 5-7): Production hardening - ~8-12 hours**
- Goal: Error handling and performance
- Deliverable: Robust, fast, production-ready server
- Risk: Low (well-understood patterns)
- Milestone: "Production MVP"

**Total: 18-27 hours across 3 iterations** *(reduced from 26-38h thanks to Chimp)*

**Demo points:**
- After Iteration 1: Demo Java library lookup to validate approach
- After Iteration 2: Demo Scala library lookup to show completeness
- After Iteration 3: Demo error cases and caching to show polish

---

## Documentation Requirements

**Documentation to create/update:**

- [x] **README.md** - Already exists, update with:
  - [ ] Installation instructions (Scala CLI setup)
  - [ ] Server startup commands
  - [ ] Claude Code configuration (MCP client setup)
  - [ ] Example usage (how to invoke tools)
  - [ ] Troubleshooting guide

- [ ] **API Documentation** - Create `docs/API.md`:
  - [ ] MCP tool schemas (JSON schema for `get_documentation` and `get_source`)
  - [ ] Parameter descriptions (coordinates format, className format)
  - [ ] Response formats (success and error structures)
  - [ ] Example requests and responses

- [ ] **Architecture Documentation** - Create `docs/ARCHITECTURE.md`:
  - [ ] Component diagram (high-level)
  - [ ] Data flow (request → Coursier → JAR → response)
  - [ ] Caching strategy
  - [ ] Error handling approach

- [ ] **Development Guide** - Create `docs/DEVELOPMENT.md`:
  - [ ] Setting up dev environment
  - [ ] Running tests (unit, integration, E2E)
  - [ ] Adding new tools (extension guide)
  - [ ] Debugging tips

- [ ] **Coordinate Format Guide** - Create `docs/COORDINATES.md`:
  - [ ] Maven coordinate format (`group:artifact:version`)
  - [ ] Scala coordinate format (`group::artifact:version`)
  - [ ] Cross-version handling (`_3`, `_2.13`)
  - [ ] Examples for common libraries

- [ ] **Changelog** - Create `CHANGELOG.md`:
  - [ ] Track changes per iteration
  - [ ] Document breaking changes (if any)
  - [ ] Link to issues/stories

**Living documentation:**
- [ ] Gherkin scenarios serve as executable specification
- [ ] Test cases document expected behavior
- [ ] Code comments explain domain concepts

**User-facing documentation:**
- [ ] Claude Code users: How to configure MCP server
- [ ] Quick start guide: 5-minute setup to first successful lookup

---

**Analysis Status:** Ready for Implementation

**Key Decisions Made:**
- ✅ **MCP Library:** Chimp (SoftwareMill) - Tapir-based, type-safe tools
- ✅ **Tool Schema:** Auto-derived from case classes via `derives Codec, Schema`
- ✅ **Error Handling:** Chimp's `Either[String, String]` with descriptive messages

**Next Steps:**

1. Run `/iterative-works:ag-create-tasks JMC-1` to break down stories into implementation tasks

2. Run `/iterative-works:ag-implement JMC-1` for iterative story-by-story development

---

**Remaining Questions for Michal:**

1. **Testing framework** - What should we use?
   - MUnit (recommended for Scala 3, lighter)
   - ScalaTest (most popular, more verbose)

2. **Story prioritization** - Current order prioritizes Java first. Should Scala come first instead?
   - Current: Story 1 (Java docs) → Story 2 (Java source) → Story 3 (Scala docs) → Story 4 (Scala source)
   - Alternative: Story 1 → Story 3 → Story 2 → Story 4 (Scala docs before Java source)

3. **Scope** - With reduced estimates (18-27h), do all 7 stories still make sense?
   - All 7 stories: Full MVP with caching
   - Stories 1-6: Skip caching for now (saves 4-6h)
   - Stories 1-4: Core functionality only (saves 8-12h)
