# Phase 4 Tasks: Fetch source code for Scala class

**Issue:** JMC-1
**Phase:** 4 of 7
**Context:** phase-04-context.md

---

## Setup

- [x] [setup] Verify existing tests pass before changes

## Tests First (TDD)

- [x] [test] Add unit test: `ClassName.toScalaSourcePath` returns `.scala` extension
- [x] [test] Add unit test: `ClassName.toScalaSourcePath` strips inner class suffix
- [x] [test] Add unit test: `SourceCodeService` with Scala artifact tries `.scala` first
- [x] [test] Add unit test: `SourceCodeService` with Scala artifact falls back to `.java`
- [x] [test] Add unit test: `SourceCodeService` with Java artifact only tries `.java`
- [x] [test] Add integration test: Fetch cats-effect IO.scala from Maven Central
- [x] [test] Add integration test: Verify source contains Scala syntax
- [x] [test] Add E2E test: `get_source` with Scala coordinates returns Scala source

## Implementation

- [x] [impl] Add `toScalaSourcePath` method to `ClassName`
- [x] [impl] Update `SourceCodeService` to try `.scala` then `.java` for Scala artifacts

## Integration

- [x] [integ] Run all tests and verify no regressions
- [ ] [integ] Test manually with MCP server (optional)

## Verification

- [x] [verify] All acceptance criteria from phase-04-context.md met
- [x] [verify] Gherkin scenario passes

---

## Task Details

### Tests First

**`ClassName.toScalaSourcePath`:**
- Input: `cats.effect.IO` → Output: `cats/effect/IO.scala`
- Input: `cats.effect.IO$Pure` → Output: `cats/effect/IO.scala` (inner class stripped)

**`SourceCodeService` fallback logic:**
- Test with in-memory reader that has only `.scala` file → succeeds
- Test with in-memory reader that has only `.java` file → succeeds via fallback
- Test with in-memory reader that has neither → fails with ClassNotFound

**Integration tests:**
- Use `org.typelevel::cats-effect:3.5.4` and `cats.effect.IO`
- Verify source contains `sealed abstract class IO`

**E2E tests:**
- HTTP request to running MCP server
- JSON-RPC call to `tools/call` with `get_source` tool
- Verify response has Scala content

### Implementation

**`ClassName.toScalaSourcePath`:**
```scala
def toScalaSourcePath: String = toPath(".scala")
```

**`JarContentReader` port update:**
```scala
def entryExists(jarPath: Path, entryPath: String): Boolean
```

**`SourceCodeService` fallback:**
```scala
def getSource(...): Either[...] =
  val scalaPath = className.toScalaSourcePath
  val javaPath = className.toSourcePath

  if coordinates.scalaArtifact then
    jarReader.readEntry(jarPath, scalaPath) match
      case Right(content) => Right(SourceCode(...))
      case Left(_) =>
        jarReader.readEntry(jarPath, javaPath).map(content => SourceCode(...))
  else
    jarReader.readEntry(jarPath, javaPath).map(content => SourceCode(...))
```

---

## Estimated Effort

- Setup: 5 min
- Tests: 30-45 min
- Implementation: 20-30 min
- Integration: 10 min
- Verification: 5 min

**Total: 1-1.5 hours**
