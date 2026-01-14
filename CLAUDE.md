# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

javadocs-mcp is an HTTP-based MCP (Model Context Protocol) server that provides on-demand Javadoc/Scaladoc and source code for JVM dependencies. It fetches documentation from Maven Central via Coursier and exposes it through MCP tools.

## Build & Test Commands

```bash
# Run the server (default port 8080)
scala-cli run .

# Run on custom port
scala-cli run . -- 8888

# Run all tests
scala-cli test .

# Run a specific test suite
scala-cli test . --test-only javadocsmcp.domain.ArtifactCoordinatesTest

# Run with custom cache size
CACHE_MAX_SIZE_MB=200 scala-cli run .
```

## Architecture

The project follows DDD with Functional Core/Imperative Shell:

```
Presentation Layer (presentation/)
├── McpServer.scala         - HTTP server setup using Chimp/Tapir
└── ToolDefinitions.scala   - MCP tool definitions (get_documentation, get_source)
    ↓
Application Layer (application/)
├── DocumentationService.scala  - Orchestrates javadoc retrieval
└── SourceCodeService.scala     - Orchestrates source code retrieval
    ↓
Domain Layer (domain/)
├── ArtifactCoordinates.scala   - Maven coordinate parsing (groupId:artifactId:version)
├── ClassName.scala             - Class name validation and path mapping
├── Documentation.scala         - Documentation value object
├── SourceCode.scala           - Source code value object
├── Errors.scala               - DocumentationError enum with user-friendly messages
└── ports/                     - Port interfaces (ArtifactRepository, JarContentReader, SourcePathResolver)
    ↓
Infrastructure Layer (infrastructure/)
├── CoursierArtifactRepository.scala  - Maven artifact resolution via Coursier
├── JarFileReader.scala               - JAR file content extraction
├── TastySourceResolver.scala         - Scala source path resolution via TASTy
├── LRUCache.scala                    - Generic thread-safe cache with size-based eviction
├── CachedDocumentationService.scala  - Caching decorator for documentation
└── CachedSourceCodeService.scala     - Caching decorator for source code
```

## Key Patterns

- **Error handling**: Use `Either[DocumentationError, T]` throughout. The `DocumentationError` enum provides user-friendly error messages via `.message`.
- **Artifact coordinates**: Java uses single colon (`org.slf4j:slf4j-api:2.0.9`), Scala uses double colon (`org.typelevel::cats-effect:3.5.4`). Parsing happens in `ArtifactCoordinates.parse()`.
- **Caching**: Services are wrapped with caching decorators. Cache key is `CacheKey(coordinates, className, scalaVersion)`.
- **Scala version handling**: Default is "3". The `scalaVersion` parameter affects artifact suffix resolution for Scala libraries.

## Dependencies

- **Chimp** (0.1.6): MCP server library
- **Tapir Netty** (1.13.4): HTTP server
- **Coursier** (2.1.24): Maven artifact resolution
- **TASTy Query** (1.6.1): Scala TASTy analysis for source lookup
- **MUnit** (1.0.0): Testing framework

## Claude API Schema Compatibility

The Claude API has strict requirements for MCP tool JSON schemas. Two issues required workarounds:

1. **No `$schema` field**: Claude rejects schemas with the `$schema` meta field, even with correct draft version. Fixed by passing `showJsonSchemaMetadata = false` to `mcpEndpoint()`.

2. **No nullable type arrays**: Tapir generates `"type": ["string", "null"]` for `Option[String]`, but Claude rejects this. Fixed with a custom schema:
   ```scala
   given Schema[Option[String]] = Schema(SchemaType.SString(), isOptional = true)
   ```
   This produces `"type": "string"` with the field not in `required`.

When adding new optional fields to tool inputs, use this pattern to ensure Claude compatibility.

## Compiler Flags

The project uses strict compiler settings (`-Werror`, `-Wunused:all`). All warnings must be fixed.

## Network/Proxy Configuration (Claude Code Sandbox)

When running in proxy environments (like the Claude Code sandbox), native image binaries (scala-cli, coursier) may not properly read HTTP_PROXY environment variables. Additionally, TLS inspection proxies require trusted CA certificates.

**Solution: Use JVM scala-cli with local proxy**

1. Download the JVM version of scala-cli (not native image):
   ```bash
   curl -x http://127.0.0.1:13130 -L -o /tmp/scala-cli-jvm \
     https://github.com/VirtusLab/scala-cli/releases/download/v1.11.0/scala-cli.jar
   ```

2. Run a local proxy that handles upstream authentication (if needed):
   ```python
   # /tmp/simple_proxy.py - forwards to authenticated upstream proxy
   python3 /tmp/simple_proxy.py &  # Listens on port 13130
   ```

3. Run scala-cli with proxy settings:
   ```bash
   java -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=13130 \
        -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=13130 \
        -jar /tmp/scala-cli-jvm compile --server=false --jvm system .
   ```

The JVM version respects Java proxy system properties and uses the system's Java truststore (which includes the TLS inspection CA in the sandbox).
