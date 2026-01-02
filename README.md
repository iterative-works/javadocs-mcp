# javadocs-mcp

An HTTP-based MCP (Model Context Protocol) server that provides on-demand documentation and source code for JVM dependencies.

## Overview

When working with AI coding assistants like Claude Code, having access to accurate, up-to-date library documentation is essential. This MCP server fetches Javadoc/Scaladoc and source files for any Maven artifact on demand.

## Phase 1 Complete ✅

**Working Features:**
- `get_documentation` tool fetches Javadoc HTML for Java classes
- Maven Central integration via Coursier
- Automatic download and caching of javadoc JARs
- Inner class support (e.g., `Logger$Factory` → `Logger.html`)
- Type-safe error handling with clear messages

## Quick Start

### Prerequisites

- JVM 21
- Scala CLI installed

### Running the Server

```bash
# Start on default port 8080
scala-cli run .

# Start on custom port
scala-cli run . -- 8888
```

The server will start and listen for MCP tool invocations on the specified port.

## MCP Tools

### `get_documentation` (Available Now)

Fetches Javadoc HTML for a Java class from a Maven artifact.

**Input:**
```json
{
  "coordinates": "org.slf4j:slf4j-api:2.0.9",
  "className": "org.slf4j.Logger"
}
```

**Output:** HTML content from the javadoc JAR

**Supported:**
- Any Maven artifact with `-javadoc.jar` classifier
- Inner classes (automatically strips `$Factory` suffix)
- Clear error messages for missing artifacts/classes

### `get_source` (Coming in Phase 2)

Returns source file content for classes.

## Testing

```bash
# Run all tests (22 passing)
scala-cli test .

# Run specific test suite
scala-cli test . --test-only javadocsmcp.domain.ArtifactCoordinatesTest
```

**Test Coverage:**
- ✅ Unit tests: Domain logic (coordinates parsing, path mapping)
- ✅ Integration tests: Real Maven Central downloads (slf4j, guava)
- ✅ End-to-end tests: Complete service orchestration

## Architecture

Following DDD and Functional Core/Imperative Shell principles:

```
Presentation Layer (Chimp/Tapir)
    ↓
Application Layer (DocumentationService)
    ↓
Domain Layer (Pure functional logic)
    ↓
Infrastructure Layer (Coursier, JAR reading)
```

## Dependencies

- **Chimp** (0.1.6): MCP server library
- **Tapir Netty** (1.11.11): HTTP server
- **Coursier** (2.1.10): Maven artifact resolution
- **Scala** (3.3): Programming language
- **MUnit** (1.0.0): Testing framework

## Performance & Caching

Phase 7 introduced in-memory caching for dramatic performance improvements:

- **First request**: 3-5 seconds (network + JAR download)
- **Cached request**: < 100ms (typically 10-20ms)
- **Memory limit**: Configurable via environment variable

### Cache Configuration

```bash
# Set maximum cache size (default: 100MB)
CACHE_MAX_SIZE_MB=200 scala-cli run .
```

**Cache Behavior:**
- LRU (Least Recently Used) eviction policy
- Thread-safe for concurrent requests
- Separate caches for documentation and source code
- Both successful and error results are cached
- Cache keys include coordinates, className, and scalaVersion

### Performance Tuning

**Recommended Settings:**
- Development: `CACHE_MAX_SIZE_MB=50` (conserves memory)
- Production: `CACHE_MAX_SIZE_MB=200` (better hit rate)
- Heavy usage: `CACHE_MAX_SIZE_MB=500` (maximum performance)

**Cache Statistics:**
- Hit rate typically > 80% for repeated lookups
- Average cache hit overhead: < 1ms
- Cache size self-regulates via LRU eviction

## Roadmap

- ✅ **Phase 1**: Javadoc HTML fetching
- ✅ **Phase 2**: Source code fetching
- ✅ **Phase 3-4**: Scala library support
- ✅ **Phase 5-6**: Advanced error handling
- ✅ **Phase 7**: In-memory caching

## License

TBD
