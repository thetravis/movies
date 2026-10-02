# How AI was used

This project was built almost entirely by prompting an AI coding agent, as allowed by the task.
This document describes the tools and the way of working, which decisions the developer made, how
the AI's output was checked, and the AI's mistakes that were caught along the way.

The complete prompt history is in [ai-prompts.md](ai-prompts.md) (prompts are referred to below as
**#n**). The git history shows the same steps; commits made after an AI step are marked
"by Claude Code".

## Tools

| Tool | Use |
|---|---|
| **Claude Code** (CLI, model Claude Opus 5.5, `claude-opus-5-5`) | Agent that read the repository, wrote all code, tests and documentation, ran builds, tests and manual checks (Docker, Maven, curl, psql) |
| [`AGENTS.md`](../AGENTS.md) + [`CLAUDE.md`](../CLAUDE.md) | Project conventions for AI agents (contract-first rules, how to add a field, transaction and test rules), so that a later session or another agent follows the same design |
| [`plan.md`](plan.md) | Living plan: phases with checklists, design decisions and their reasons. Created in the first prompt and updated in every step |
| [`tools/export-prompts.mjs`](../tools/export-prompts.mjs) | Script (written by the AI, #15–16) that exports the prompts from Claude Code's session transcripts into [ai-prompts.md](ai-prompts.md) |

## Way of working

1. **Plan first.** The first prompt (#1) gave the goal and the technical direction (Spring Boot,
   PostgreSQL, Maven, contract-first OpenAPI, Docker Compose) and asked for a plan before any code.
2. **One phase per prompt.** Each phase of the plan was started with a short prompt ("start
   Phase 3"). The AI implemented it, verified it, updated `plan.md`/`README.md` and summarised the
   result; the developer reviewed and committed.
3. **Questions before decisions.** Design questions were discussed before implementation: the AI
   explained options with a recommendation, the developer decided. Some decisions were asked as
   multiple-choice questions (recorded in ai-prompts.md, #8).
4. **Verification in every step** (see below), not only at the end.

## Decisions made by the developer

The AI proposed options; these are the points where the developer chose, questioned or changed the
direction:

| # | Decision |
|---|---|
| 1 | Technology direction: Spring Boot, PostgreSQL in Docker Compose, Maven, contract-first OpenAPI |
| 3 | Asked whether the Maven Wrapper, Flyway, Actuator and Testcontainers were needed → Phase 1 kept minimal (Actuator dropped, the others added only when needed) |
| 5–7 | **Liquibase** instead of the planned Flyway; initial data loaded by a **Java importer** with a configurable file (so a reviewer can load a different JSON) instead of a data changeset |
| 8 | **Paging** from the start; **409 Conflict** for duplicate movies |
| 10 | README content: requirements, how to start the database, build and run |
| 12–13 | Hand-written mapper first, with MapStruct mentioned as the option for later |
| 17–18 | Tests must **not use the local database** → Testcontainers; separate test data file `movies-test.json` |
| 19–20 | Questioned the service layer using API DTOs → refactoring, MapStruct introduced |
| 22 | Spotted that `NewMovie` → entity mapping was still unchecked (a new field could be silently lost) |
| 23–25 | Rejected extra service model classes; proposed MapStruct DTO ↔ entity with person/genre mappers using repositories. Asked about moving the transaction to the controller; chose option A (mapping inside the service transaction) after the trade-offs |
| 26 | Found the Hibernate Validator warning `HV000271` at runtime and reported it |

The design evolution in #19–25 is described in [plan.md](plan.md), section "Layers and mapping".

## What the AI did

- Analysed the task and the data (found the data quality issues: leading spaces in names, the genre
  typo `Familty`).
- Wrote the plan, `docker-compose.yml`, the Spring Boot project (via Spring Initializr), the
  OpenAPI contract, the Liquibase schema, entities, repositories, service, MapStruct mappers,
  controller, error handling, importer, all tests and all documentation.
- Researched current versions and APIs while working: latest Spring Boot (4.1.1), OpenAPI Generator
  options for Spring Boot 4 / Jackson 3, the new package names of Spring Boot 4 test annotations,
  Testcontainers 2.
- Solved problems found during the work, e.g. a template override for the OpenAPI generator to
  remove the deprecated container-level `@Valid` (#26).

## How the output was checked

- **Build and tests after every change:** `./mvnw verify`; at the end 55 tests (unit, web slice,
  repository, mapper, full stack over HTTP against a PostgreSQL test container).
- **Manual end-to-end checks** with curl against a separately started instance: every endpoint and
  error case, the importer with different files (new, existing, invalid, missing file), rollback of
  persons/genres created for a rejected movie. Test data was removed afterwards.
- **Checking the checks:** the AI deliberately broke production code (no trimming, duplicate actors
  allowed, no typo fix) to confirm that the tests fail; it also temporarily removed a mapping to
  confirm that MapStruct fails the build.
- **Refactoring safety net:** the HTTP-level tests were left unchanged through both refactorings
  (#21, #25) and passed before and after.
- **Reading generated code:** the generated DTOs and MapStruct implementations were inspected to
  confirm they do what was intended.
- **Database state:** the local database was compared before and after test runs to confirm the
  tests no longer touch it.

## AI mistakes that were caught

Being explicit about these is part of using AI responsibly:

| Mistake | How it was caught / fixed |
|---|---|
| `Set.copyOf` in an entity getter would have lost the genre ordering | Noticed by the AI when writing the code, fixed before running |
| A test created a movie by Spielberg that broke another test's expected search result (shared test data) | Failing test; changed the test data and documented the rule in the test class and AGENTS.md |
| Claimed that MapStruct's `unmappedTargetPolicy` would also catch new request fields; it only checks targets | Found while answering the developer's question #22; fixed with `unmappedSourcePolicy = ERROR` |
| A faulty manual test script created a stray movie in the developer's local database | Noticed from the row counts; data removed, check re-run correctly |
| `@Valid` on collection fields of the entity (deprecated form) | Found while fixing the warning the developer reported (#26) |

## Developer's notes

Honestly, I did nothing but considered few options, laid out the requirements for the application and let the AI do the heavy lifting while steering it at some points. It seems that Claude Code has improved since I last worked with it and we did not fall into the loop of making changes and then fixing new problems caused by the changes. The early stage of the project might have helped with this too, though I'm surprised by the amount of code that I could produce just in about 4 hours. Next, I will spend 4 hours reviewing the code. 
