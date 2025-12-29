# Refactoring R1: Replace hardcoded Scala suffix with coursier/dependency library

**Phase:** 3
**Created:** 2025-12-29
**Status:** Complete

## Decision Summary

The current implementation uses a hardcoded `_3` suffix for Scala artifacts in `CoursierArtifactRepository.resolveArtifactName()`. This is inflexible:
- Only supports Scala 3 artifacts
- No way for users to fetch Scala 2.13 library documentation
- Custom parsing code that duplicates what coursier/dependency library does better

We will replace this with the `coursier/dependency` library which provides:
- Native `::` parsing via `dep"..."` string interpolator
- `ScalaParameters.applyParams()` for proper version suffix resolution
- Support for both Scala 2.x and Scala 3

Additionally, we'll add an optional `scalaVersion` parameter to MCP tools so users can override the default.

## Current State

**File: `infrastructure/CoursierArtifactRepository.scala`**
```scala
// Hardcoded _3 suffix - only works for Scala 3
private def resolveArtifactName(coords: ArtifactCoordinates): String =
  if coords.scalaArtifact then s"${coords.artifactId}_3"
  else coords.artifactId
```

**File: `presentation/ToolDefinitions.scala`**
```scala
case class GetDocInput(coordinates: String, className: String) derives Codec, Schema
case class GetSourceInput(coordinates: String, className: String) derives Codec, Schema
```

**File: `domain/ArtifactCoordinates.scala`**
- Custom `::` parsing in `parseScalaCoordinates()`
- `scalaArtifact: Boolean` field

## Target State

**File: `infrastructure/CoursierArtifactRepository.scala`**
```scala
import dependency._

private def resolveArtifact(
  coords: ArtifactCoordinates,
  scalaVersion: String
): (String, String, String) =
  if coords.scalaArtifact then
    // Use coursier/dependency library for proper resolution
    val dep = DependencyParser.parse(
      s"${coords.groupId}::${coords.artifactId}:${coords.version}"
    ).toOption.get
    val resolved = dep.applyParams(ScalaParameters(scalaVersion))
    (resolved.module.organization.value,
     resolved.module.name.value,
     resolved.version)
  else
    (coords.groupId, coords.artifactId, coords.version)
```

**File: `presentation/ToolDefinitions.scala`**
```scala
case class GetDocInput(
  coordinates: String,
  className: String,
  scalaVersion: Option[String] = None  // Default: "3"
) derives Codec, Schema

case class GetSourceInput(
  coordinates: String,
  className: String,
  scalaVersion: Option[String] = None  // Default: "3"
) derives Codec, Schema
```

**File: `domain/ArtifactCoordinates.scala`**
- Keep `scalaArtifact: Boolean` for detection
- Keep `::` parsing (still needed to detect Scala vs Java)
- Remove any suffix computation logic

## Constraints

- PRESERVE: All existing tests must pass
- PRESERVE: Java coordinate handling unchanged (`org.slf4j:slf4j-api:2.0.9`)
- PRESERVE: Explicit suffix coordinates work (`org.typelevel:cats-effect_2.13:3.5.4`)
- DO NOT TOUCH: `DocumentationService`, `SourceCodeService` (transparent to them)
- DO NOT TOUCH: `JarFileReader`, `JarContentReader`
- DO NOT TOUCH: Domain error types

## Tasks

### Setup

- [x] [impl] Add `io.get-coursier::dependency:0.2.3` to `project.scala`
- [x] [impl] Verify compilation with new dependency

### Infrastructure Refactoring

- [x] [test] Write test: resolve `org.typelevel::cats-effect:3.5.4` with scalaVersion="3" → `cats-effect_3`
- [x] [test] Write test: resolve `org.typelevel::cats-effect:3.5.4` with scalaVersion="2.13" → `cats-effect_2.13`
- [x] [test] Write test: Java coordinates unchanged regardless of scalaVersion
- [x] [impl] Update `CoursierArtifactRepository` to accept `scalaVersion` parameter
- [x] [impl] Replace `resolveArtifactName()` with `coursier/dependency` based resolution
- [x] [impl] Update `fetchJavadocJar()` signature to accept `scalaVersion: String = "3"`
- [x] [impl] Update `fetchSourcesJar()` signature to accept `scalaVersion: String = "3"`
- [x] [test] Run existing Coursier tests - verify they pass

### Port Trait Updates

- [x] [impl] Update `ArtifactRepository` trait to include `scalaVersion` parameter in fetch methods
- [x] [impl] Update `InMemoryArtifactRepository` test implementation
- [x] [test] Run service tests - verify they pass

### Application Layer Updates

- [x] [impl] Update `DocumentationService.getDocumentation()` to accept optional `scalaVersion`
- [x] [impl] Update `SourceCodeService.getSource()` to accept optional `scalaVersion`
- [x] [impl] Pass scalaVersion through to repository calls
- [x] [test] Run service tests - verify they pass

### Presentation Layer Updates

- [x] [impl] Add `scalaVersion: Option[String] = None` to `GetDocInput`
- [x] [impl] Add `scalaVersion: Option[String] = None` to `GetSourceInput`
- [x] [impl] Update tool handlers to pass scalaVersion (default "3") to services
- [x] [impl] Update tool descriptions to document new parameter
- [x] [test] Run E2E tests - verify they pass

### New Tests

- [x] [test] E2E test: fetch Scala 2.13 artifact docs with explicit scalaVersion="2.13"
- [x] [test] E2E test: fetch Scala 3 artifact docs with default scalaVersion
- [x] [test] E2E test: explicit suffix coordinate works without scalaVersion parameter

### Cleanup

- [x] [impl] Remove any dead code from old implementation
- [x] [impl] Update comments to reflect new approach
- [x] [impl] Update implementation-log.md with refactoring summary

## Verification

- [x] All 46+ existing tests pass (56 total now)
- [x] New scalaVersion tests pass
- [x] Can fetch `org.typelevel::cats-effect:3.5.4` (Scala 3, default)
- [x] Can fetch Scala 2.13 artifact with `scalaVersion: "2.13"`
- [x] Explicit suffix `org.typelevel:cats-effect_2.13:3.5.4` still works
- [x] Java artifacts unaffected
- [x] No compiler warnings
- [x] Test output pristine

## Notes

**Why coursier/dependency library?**
- Maintained by Coursier team
- Proper `::` parsing without custom code
- `ScalaParameters` handles version suffix computation correctly
- Supports platforms (Scala.js/Native) for future extension

**Default Scala version: "3"**
- Most users are on Scala 3 now
- Scala 3 uses simple `_3` suffix (not `_3.x.y`)
- Users needing 2.13 can specify explicitly

**Explicit suffix escape hatch:**
- `org.typelevel:cats-effect_2.13:3.5.4` (single colon, explicit suffix)
- This bypasses all Scala version handling
- Useful for edge cases or legacy artifacts
