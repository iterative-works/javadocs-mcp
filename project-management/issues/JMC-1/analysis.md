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

**Estimated Effort:** 8-12h
**Complexity:** Complex

**Technical Feasibility:**

This is the foundational story that establishes the entire vertical slice. Complexity comes from:
- Setting up HTTP MCP server with SSE transport (new territory)
- Integrating Coursier to resolve and download `-javadoc.jar` artifacts
- Parsing JAR files to extract specific class documentation
- Mapping class names to file paths within JARs (`org.slf4j.Logger` → `org/slf4j/Logger.html`)
- Handling various Javadoc HTML structures (different tools generate different formats)

**Key Technical Challenges:**
- MCP HTTP transport protocol implementation (need to understand SSE, JSON-RPC over HTTP)
- Coursier API for fetching artifacts with specific classifiers (`-javadoc`)
- JAR file traversal and HTML extraction
- Error handling when artifact/class doesn't exist

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

**Presentation Layer:**
- `MCPServer` - HTTP server with SSE transport
- `MCPToolRegistry` - Registry of available tools
- `GetDocumentationTool` - MCP tool implementation
- Tool schema definition (JSON schema for parameters)

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

**Presentation Layer:**
- `GetSourceTool` - MCP tool implementation
- Register in existing `MCPToolRegistry`
- Tool schema definition

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

**Presentation Layer:**
- Extend `GetDocumentationTool` to accept both Java and Scala coordinates
- Update tool schema to document coordinate formats

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

**Presentation Layer:**
- Reuse `GetSourceTool` from Story 2
- Update documentation with Scala examples

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

**Presentation Layer:**
- MCP error response formatting (JSON-RPC error structure)
- Error message templates
- HTTP status codes for errors

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

**Presentation Layer:**
- User-friendly error messages
- Suggestion logic (case-sensitivity hints)

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
- Optional cache-control headers (for future)

---

## Technical Risks & Uncertainties

### CLARIFY: HTTP MCP Server Implementation

We need to implement HTTP-based MCP transport with SSE. This is relatively new territory.

**Questions to answer:**

1. Should we use a high-level framework (Tapir + http4s) or lower-level HTTP library?
2. What's the exact MCP HTTP transport specification? (SSE format, JSON-RPC structure)
3. Do we need full SSE bi-directional streaming or just server→client?
4. How should we handle MCP protocol versioning?

**Options:**

- **Option A: Tapir + http4s (cats-effect)** - Type-safe, functional, well-documented
  - Pros: Type safety, compositional, excellent Scala 3 support, streaming support
  - Cons: Heavier dependency tree, learning curve, might be overkill for simple MVP

- **Option B: Simple http4s + cats-effect** - Lower-level but still functional
  - Pros: Full control, lighter than Tapir, good streaming support
  - Cons: More manual JSON-RPC handling, less type safety

- **Option C: cask (Li Haoyi's HTTP library)** - Scala-CLI friendly, simple
  - Pros: Very simple, minimal dependencies, quick to get started
  - Cons: Less production-ready, weaker streaming support, less functional

**Impact:** Affects Story 1 implementation complexity and all subsequent stories. Decision needed before starting.

**Recommendation:** I lean toward **Option B (http4s + cats-effect)** for balance of simplicity and capability, but need your input Michal.

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

### CLARIFY: MCP Tool Schema Design

What should the exact JSON schema be for the two tools?

**Questions to answer:**

1. Should coordinates be a single string or separate fields (groupId, artifactId, version)?
2. Should className support wildcards or just exact matches?
3. Do we need optional parameters (e.g., `scalaVersion`, `repository`)?
4. What's the response format? (plain text, structured JSON, both?)

**Options:**

- **Option A: Simple string parameters**
  ```json
  {
    "coordinates": "org.typelevel::cats-effect:3.5.4",
    "className": "cats.effect.IO"
  }
  ```
  - Pros: Easy to use, familiar format
  - Cons: No validation until runtime

- **Option B: Structured coordinates**
  ```json
  {
    "groupId": "org.typelevel",
    "artifactId": "cats-effect_3",
    "version": "3.5.4",
    "className": "cats.effect.IO"
  }
  ```
  - Pros: Validated, unambiguous
  - Cons: More verbose, burden on caller

- **Option C: Hybrid - string with validation**
  ```json
  {
    "coordinates": "org.typelevel::cats-effect:3.5.4",  // validated format
    "className": "cats.effect.IO"
  }
  ```
  - Pros: Balance of simplicity and safety
  - Cons: Validation rules must be documented

**Impact:** Affects all stories, user experience, and API evolution.

**Recommendation:** **Option C** - string coordinates with documented format and runtime validation.

---

### CLARIFY: Error Response Format

What structure should error responses have?

**Questions to answer:**

1. Should errors include error codes or just messages?
2. Do we include suggestions in error responses?
3. Should errors include debug info (stack traces, timing)?
4. What's the MCP standard for error responses?

**Options:**

- **Option A: Simple message string** - MCP JSON-RPC error with message
  - Pros: Simple, standard
  - Cons: Less structured, harder to parse programmatically

- **Option B: Structured error object**
  ```json
  {
    "error": {
      "code": "ARTIFACT_NOT_FOUND",
      "message": "Artifact org.example:fake:1.0.0 not found",
      "suggestions": ["Check spelling", "Verify version exists"]
    }
  }
  ```
  - Pros: Parseable, actionable, user-friendly
  - Cons: More complex, need error code taxonomy

- **Option C: Hybrid - message + optional data**
  - Pros: Backwards compatible, extensible
  - Cons: Less consistent

**Impact:** Affects Stories 5-6 and overall UX.

**Recommendation:** **Option B** - structured errors with codes and suggestions.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Fetch Javadoc for Java class): 8-12 hours
- Story 2 (Fetch source for Java class): 4-6 hours
- Story 3 (Fetch Scaladoc for Scala class): 3-4 hours
- Story 4 (Fetch source for Scala class): 2-3 hours
- Story 5 (Handle missing artifacts): 3-4 hours
- Story 6 (Handle missing classes): 2-3 hours
- Story 7 (In-memory caching): 4-6 hours

**Total Range:** 26-38 hours

**Confidence:** Medium

**Reasoning:**

- **Story 1 complexity drives the estimate**: First vertical slice with MCP HTTP server, Coursier integration, JAR handling - this is where most unknowns live. Could be 8h if we hit no snags, could be 12h if HTTP MCP transport is tricky.

- **Stories 2-4 build on foundation**: Once Story 1 works, Stories 2-4 are mostly variations on the same pattern. Confidence is higher here.

- **Error handling is well-understood**: Stories 5-6 are straightforward exception handling and validation. Low risk.

- **Caching adds moderate complexity**: Story 7 involves concurrency and memory management, but patterns are well-known.

- **CLARIFY markers add uncertainty**: Several decisions need to be made before we can refine estimates. HTTP library choice (Option A vs B vs C) could swing Story 1 by ±3 hours.

- **Testing time included**: Each story estimate includes unit tests, integration tests, and scenario tests per TDD guidelines.

**Risk factors:**
- MCP HTTP specification interpretation (might need research)
- Coursier API learning curve (mitigated by good docs)
- Edge cases in JAR file structures (javadoc format variations)
- Scala version handling subtleties

**Mitigation:**
- Resolve CLARIFY markers before starting
- Spike on MCP HTTP transport before Story 1 (2-3h exploration not in estimates)
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

- Scala CLI installed and working
- JVM 21 available
- Coursier dependency added to `project.scala`
- HTTP library dependency (need to choose - see CLARIFY)
- MCP specification documentation (need to review)
- Test framework setup (ScalaTest, MUnit, or similar)

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

- **MCP HTTP specification clarity**: If spec is ambiguous, might need to ask Anthropic or community
  - *Mitigation*: Review existing HTTP MCP implementations, ask in MCP community

- **Maven Central availability**: If Maven Central is down, testing blocked
  - *Mitigation*: Use local Coursier cache for testing, have offline fallback fixtures

- **Coursier API changes**: If Coursier 2.1.10 has bugs or API issues
  - *Mitigation*: Well-established library, unlikely; can downgrade if needed

- **Claude Code compatibility**: If Claude Code's MCP client has quirks
  - *Mitigation*: Test early with real Claude Code instance, not just raw HTTP

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

**Iteration Plan:**

**Iteration 1 (Stories 1-2): Java library support - ~12-18 hours**
- Goal: Working MCP server that can fetch Java docs and sources
- Deliverable: Claude Code can look up `org.slf4j.Logger` docs and source
- Risk: High (first iteration, most unknowns)
- Milestone: "Java MVP"

**Iteration 2 (Stories 3-4): Scala library support - ~5-7 hours**
- Goal: Extend to Scala ecosystem
- Deliverable: Claude Code can look up `cats.effect.IO` docs and source
- Risk: Low (builds on proven infrastructure)
- Milestone: "Full JVM MVP"

**Iteration 3 (Stories 5-7): Production hardening - ~9-13 hours**
- Goal: Error handling and performance
- Deliverable: Robust, fast, production-ready server
- Risk: Low (well-understood patterns)
- Milestone: "Production MVP"

**Total: 26-38 hours across 3 iterations**

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

**Analysis Status:** Ready for Review

**Next Steps:**

1. **Resolve CLARIFY markers** - Michal, I need your input on:
   - HTTP library choice (Tapir+http4s vs http4s vs cask)
   - MCP tool schema design (string vs structured coordinates)
   - Error response format preferences
   - Caching strategy confirmation

2. **Review story prioritization** - Does the recommended sequence (1→2→3→4→5→6→7) align with your priorities?

3. **Validate estimates** - Do 26-38 hours total feel reasonable for this MVP scope?

4. **Confirm scope boundaries** - Anything in "Out of Scope" that should actually be in MVP?

5. **Once CLARIFY markers resolved**: Run `/iterative-works:ag-create-tasks JMC-1` to break down stories into implementation tasks

6. **Then start implementation**: Run `/iterative-works:ag-implement JMC-1` for iterative story-by-story development

---

**Critical Questions for Michal:**

1. **HTTP library choice** - This affects Story 1 complexity significantly. What's your preference?
   - http4s + cats-effect (my recommendation for balance)
   - Tapir + http4s (more type-safe, heavier)
   - cask (simplest, less production-ready)

2. **Testing framework** - What should I use?
   - ScalaTest (most popular)
   - MUnit (lighter, Scala-native-friendly)
   - Other?

3. **Primary use case** - Should I prioritize Java or Scala in Story 2 vs Story 3?
   - If mostly Scala projects → do Story 3 before Story 2
   - If mixed → current order is good

4. **Time pressure** - Is there a deadline? Should I:
   - Aim for all 7 stories (26-38h)
   - Stop after Story 4 (17-25h, full functionality, minimal error handling)
   - Stop after Story 6 (22-32h, skip caching for now)

Something strange is afoot at the Circle K on the CLARIFY markers - there are quite a few uncertainties here. Should we spike on MCP HTTP transport (2-3h exploration) before committing to Story 1 estimates?
