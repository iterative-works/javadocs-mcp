# javadocs-mcp

An HTTP-based MCP (Model Context Protocol) server that provides on-demand documentation and source code for JVM dependencies.

## Overview

When working with AI coding assistants like Claude Code, having access to accurate, up-to-date library documentation is essential. This MCP server fetches Javadoc/Scaladoc and source files for any Maven artifact on demand.

## Features (Planned)

- **On-demand lookup** - Fetch documentation for any Maven coordinates
- **Javadoc & Scaladoc support** - Works with both Java and Scala libraries
- **Source code access** - Retrieve source files for classes
- **Shared HTTP server** - One instance serves multiple projects efficiently
- **Coursier-powered** - Supports Maven Central and any repository Coursier can access

## MCP Tools

- `get_documentation(coordinates, className)` - Returns Javadoc/Scaladoc for a class
- `get_source(coordinates, className)` - Returns source file content for a class

## Development

This project uses Scala CLI.

```bash
# Run the server
scala-cli run .

# Run tests
scala-cli test .
```

## License

TBD
