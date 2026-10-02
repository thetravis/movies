# AGENTS.md – Movies frontend

Instructions for AI coding agents (and a quick reference for developers) working on the frontend.
For setup and usage see [README.md](README.md); for design decisions see
[documentation/plan.md](documentation/plan.md). The backend has its own
[AGENTS.md](../backend/AGENTS.md).

## Project in one paragraph

React 19 + TypeScript single-page app (Vite) for the Movies backend: list with paging, search,
expandable synopses, add-movie dialog. **Contract-first:** the API types are generated from the
backend's `openapi.yaml` (`npm run generateTypes` → `src/api/schema.d.ts`, git-ignored). Server state
with TanStack Query, HTTP with openapi-fetch, dialog with Radix UI, styling with CSS modules.

## Commands

Run from the `frontend` directory.

| Task | Command |
|---|---|
| Install (also generates the types) | `npm install` |
| Development server (needs the backend on :8080) | `npm run dev` |
| Tests (no backend needed) | `npm test` |
| Type check + build | `npm run build` |
| Lint | `npm run lint` |
| Regenerate API types | `npm run generateTypes` |
| Export the frontend prompt log | `node backend/tools/export-prompts.mjs --since 2026-10-02T19:49:00Z --out frontend/documentation/ai-prompts.md` (from the repository root) |

Before finishing a change, `npm test`, `npm run build` and `npm run lint` must all pass without
errors or warnings.

## Code layout

| Path | Contents |
|---|---|
| `src/api/client.ts` | Typed openapi-fetch client (`apiClient`) |
| `src/api/types.ts` | Short names for the generated types – **import API types from here** |
| `src/api/movies.ts` | `fetchMovies`/`useMovies`, `createMovie`/`useCreateMovie`, query keys (`movieKeys`) |
| `src/api/errors.ts` | `ApiError` (`status`, `problem`), conversion of failed calls |
| `src/components/` | UI components; `movieFormModel.ts` holds the form rules without React |
| `src/hooks/` | `useMovieListParams` (list state in the URL), `useDebouncedValue` |
| `src/test/` | Test setup, MSW server and handlers, typed test data builders |

## Rules

### API and contract

- The contract (`../backend/src/main/resources/openapi/openapi.yaml`) is changed in the backend
  first. Then regenerate the types and fix the compile errors; never edit `schema.d.ts`.
- All API calls go through `apiClient` and the functions in `src/api/`; components don't call
  `fetch` directly. Failed calls throw `ApiError`; its `message` is ready to show to the user.
- Responses have no optional fields: empty values are `null`. Check for `null`, not `undefined`.
- Validation errors come as `Problem.errors` with backend field paths (`name`,
  `director.firstName`, `actors[0].lastName`, `genres[]`); `toFormErrors` maps them to form fields.
- Form rules in `movieFormModel.ts` mirror the contract's limits; keep them in sync when the
  contract changes (the backend stays the authority).
- The client looks up `fetch` on every call (see `client.ts`); keep it that way, otherwise the
  API mock in tests is bypassed.

### State

- Server data: TanStack Query only (no copies in component state). Invalidate `movieKeys.all`
  after changes so lists reload.
- The list's search, page and size live in the URL (`useMovieListParams`): paging pushes history
  entries, search replaces the current one. Page is 1-based in the URL, 0-based in the API.

### UI and accessibility

- Every input has a label; errors are linked with `aria-invalid` / `aria-describedby`; status
  messages use `role="status"`, errors `role="alert"`.
- Dialogs use Radix UI (`@radix-ui/react-dialog`) with our own CSS module styling.
- Styling: CSS modules per component; colours come from the variables in `index.css` (light and
  dark).
- Components that are only presentational (`MovieTable`, `Pagination`) take typed props and know
  nothing about loading.

### Tests

- Test behaviour through the UI (React Testing Library: roles and labels), with the real API client
  and MSW for HTTP. Unhandled requests fail the test.
- Use the typed builders in `src/test/data.ts` and `serveMovies` in `src/test/handlers.ts`.
- jsdom does no layout: stub element sizes where layout matters (see `MovieTable.test.tsx`).
- The test setup resets the URL after each test; don't rely on state from another test.
- When adding a behaviour, check that its test fails without it.

### Code style

- TypeScript `strict` (set explicitly – TypeScript is pinned to 5.9, see the plan).
- No `export` of constants from component files (breaks hot reload; oxlint warns).
- Comments explain **why**; match the existing comment density.
- File names must not differ only in letter case (Windows ignores case): e.g. `MovieForm.tsx` and
  `movieFormModel.ts`, not `movieForm.ts`.
- Keep `README.md` and `documentation/plan.md` up to date with every change.

## Environment notes

- Windows development machine (PowerShell 5.1 and Git Bash). npm 11 asks before running
  dependencies' install scripts; MSW's is not needed.
- The developer may have the backend on port 8080 and the dev server on 5173 running. For manual
  checks against the backend, don't create data you don't clean up, and don't delete data you
  didn't create.
