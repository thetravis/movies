# How AI was used – frontend

The frontend is an extra on top of the required backend exercise, so its AI usage is documented
separately; the backend's is in [backend/documentation/ai-usage.md](../../backend/documentation/ai-usage.md).
As with the backend, the code was written by prompting an AI coding agent; this document describes
the way of working, the developer's decisions, how the output was checked, and the issues found.

The prompts of the frontend work are in [ai-prompts.md](ai-prompts.md) (referred to below as **#n**).

## Tools

| Tool | Use |
|---|---|
| **Claude Code** (CLI, model Claude Opus 5.5, `claude-opus-5-5`) | Wrote all code, tests and documentation; ran builds, tests, the dev server and checks against the real backend |
| [`AGENTS.md`](../AGENTS.md) + [`CLAUDE.md`](../CLAUDE.md) | Frontend conventions for AI agents (contract-first rules, state, accessibility, tests) |
| [`plan.md`](plan.md) | Living plan: phases with checklists, technology choices, decisions and findings |
| [`export-prompts.mjs`](../../backend/tools/export-prompts.mjs) | Exports the prompts from Claude Code's transcripts; the `--since`/`--until` options were added in Phase 6 so the backend and frontend get separate logs |

## Way of working

The same as for the backend: plan first (#1), one phase per prompt (#3, #4, #11, #13, #14, #16),
options with a recommendation before decisions, verification in every phase, and the developer
reviewing the result in the browser and committing.

## Decisions made by the developer

| # | Decision |
|---|---|
| 1 | React + TypeScript + npm, types generated from the backend's OpenAPI contract; phases: setup, types, list with paging, search |
| 2 | TanStack Query (familiar from earlier work); generated types git-ignored; search as you type with a short delay; adding a movie included; frontend AI usage documented separately. Asked whether to use AG Grid (used before) → own table component, AG Grid noted as a later option |
| 4 | Script name `generateTypes` |
| 5–6 | Contract rule after the generated types revealed a mismatch: **response schemas have no optional fields**, empty values are `null`; request schemas may have optional fields. Also spotted the deprecated `@Nullable` in the backend's generated code |
| 7 | Asked whether the backend accepts a `NewMovie` without `synopsis` from other clients → covered by a new end-to-end backend test |
| 8–9 | Questioned the long `@JsonInclude` annotation; after the options, kept it (robust regardless of global Jackson settings) |
| 10 | Checked that the plan reflected the findings → it didn't fully; updated |
| 12 | Long synopses must be readable → click a row to expand it |
| 15 | Add-movie form as an overlay window, with a library → Radix UI Dialog |

## What the AI did

- Planned the frontend and proposed the stack: Vite, openapi-typescript + openapi-fetch, TanStack
  Query, Vitest + React Testing Library + MSW, CSS modules; dev-server proxy instead of CORS.
- Checked current versions and compatibility before adding packages, e.g. pinned TypeScript 5.9
  (openapi-typescript's peer dependency), chose MSW 2.15 over the three-day-old 3.0, checked
  jest-dom 7 and Radix Dialog's React 19 support.
- Implemented all phases, the backend contract changes requested during the work, and the tests.
- Kept the plans, READMEs and `AGENTS.md` files of both projects up to date.

## How the output was checked

- **Build, type check, lint and tests in every phase:** at the end 56 frontend tests; the backend
  suite (57 tests) after each backend change.
- **Type safety of the API client:** a temporary file with deliberately wrong calls
  (`@ts-expect-error`) proved that wrong paths, parameters and bodies don't compile.
- **Against the real backend:** the typed client and the Vite proxy were exercised against the
  running backend (list, search, `404`, `400` field errors, `409`); the real field path of an
  invalid genre (`genres[]`) was checked this way and the test data corrected.
- **Checking the checks:** in every phase the AI deliberately broke behaviours (e.g. keeping the
  previous page, the search delay, Back/Forward, focus on the first error, the dialog's guards) to
  confirm that the tests fail.
- **Visual check by the developer** in the browser after each phase (the AI can't see the page).

## Issues found and fixed along the way

| Issue | How it was found / fixed |
|---|---|
| The contract said `synopsis` was optional, but the backend sent `null` | The generated TypeScript types made it visible; fixed in the backend with the developer's "no optional response fields" rule |
| Spring's error responses left fields out and had no `type` | A strict test of the full `Problem` shape; all errors are now converted to the generated `ProblemDto` |
| Pinning TypeScript 5.9 would have silently turned off `strict` (TypeScript 6 enables it by default) | Noticed when copying the template; set explicitly |
| All tests failed with "fetch failed": openapi-fetch keeps the `fetch` from when the client is created, before the API mock replaces it | Debugged in the tests; the client now looks up `fetch` on every call |
| `movieForm.ts` and `MovieForm.tsx` differ only in letter case; on Windows the import found the wrong file | Type check error; logic module renamed to `movieFormModel.ts` |
| Tests shared state through the URL (jsdom keeps it) | Failing tests after adding URL state; the setup resets the URL |
| VS Code's Java extension and Maven both compile into `backend/target`, causing random build errors | Recognised from an Eclipse compiler message; documented in the backend's `AGENTS.md` |
| The plan wasn't updated after the later contract decisions | Developer's question (#10); both plans updated |

## Developer's notes

*To be written by the developer: own review findings, what was changed by hand, and reflections on
the frontend work.*
