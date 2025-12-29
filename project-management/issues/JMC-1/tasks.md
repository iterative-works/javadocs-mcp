# Implementation Tasks: MVP: Implement core MCP server with documentation and source tools

**Issue:** JMC-1
**Created:** 2025-12-28
**Status:** 5/7 phases complete (71%)

## Phase Index

- [x] Phase 1: Fetch Javadoc HTML for Java class (Est: 4-6h) → `phase-01-context.md`
- [x] Phase 2: Fetch source code for Java class (Est: 3-4h) → `phase-02-context.md`
- [x] Phase 3: Fetch Scaladoc HTML for Scala class (Est: 2-3h) → `phase-03-context.md`
- [x] Phase 4: Fetch source code for Scala class (Est: 1-2h) → `phase-04-context.md`
- [x] Phase 5: Handle missing artifacts gracefully (Est: 2-3h) → `phase-05-context.md`
- [ ] Phase 6: Handle missing classes within artifacts (Est: 2-3h) → `phase-06-context.md`
- [ ] Phase 7: In-memory caching for repeated lookups (Est: 4-6h) → `phase-07-context.md`

## Progress Tracker

**Completed:** 5/7 phases
**Estimated Total:** 18-27 hours
**Time Spent:** 0 hours

## Technology Stack

- **MCP Library:** Chimp (SoftwareMill) v0.1.6
- **HTTP Server:** Tapir Netty Server Sync
- **Artifact Resolution:** Coursier 2.1.10
- **Testing:** MUnit
- **Language:** Scala 3.3, JVM 21

## Phase Summary

| Phase | Story | Key Deliverable | Complexity |
|-------|-------|-----------------|------------|
| 1 | Javadoc for Java | MCP server + Coursier + JAR extraction | Moderate |
| 2 | Source for Java | get_source tool + .java extraction | Low |
| 3 | Scaladoc for Scala | :: coordinate handling | Low |
| 4 | Source for Scala | .scala extension + fallback | Low |
| 5 | Missing artifacts | Coursier error handling | Low |
| 6 | Missing classes | JAR search + suggestions | Low |
| 7 | Caching | LRU in-memory cache | Moderate |

## Notes

- Phase context files generated just-in-time during implementation
- Use `/iterative-works:ag-implement` to start next phase automatically
- Estimates are rough and will be refined during implementation
- Phase 1 is foundational - establishes architecture for all subsequent phases
- Phases 2-4 build incrementally on Phase 1
- Phases 5-6 add error handling robustness
- Phase 7 is performance optimization (can be deferred if needed)
