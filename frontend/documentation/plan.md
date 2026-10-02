# Movies frontend – implementation plan

## 1. Goal

A React single-page application for the [Movies backend](../../backend/README.md) that lets a user:

1. **List movies** with paging.
2. **Search movies** by a term.
3. **Add a movie** (the backend's third use case).

The frontend uses the backend's **OpenAPI contract**
([`backend/src/main/resources/openapi/openapi.yaml`](../../backend/src/main/resources/openapi/openapi.yaml))
as the single source of truth: TypeScript types are generated from it, so a contract change shows
up as a compile error in the frontend, just as it does in the backend.

## 2. Technology choices

| Area | Choice | Reason |
|---|---|---|
| Language | TypeScript | Requested; types generated from the contract |
| UI | React 19 | Requested |
| Package manager | npm | Requested (Node 24 / npm 11 installed) |
| Build tool / dev server | **Vite** 8 (`react-ts` template) | Current standard for new React projects (Create React App is deprecated); gives `package.json`, `tsconfig`, linting and a fast dev server |
| Linting | **oxlint** (the template's default) | Fast Rust-based linter with React hook rules; replaces ESLint in the current Vite template |
| Contract → types | **openapi-typescript** | Generates only TypeScript types (no runtime code) from `openapi.yaml` |
| HTTP client | **openapi-fetch** | Tiny typed `fetch` wrapper: paths, query parameters, request bodies and responses are checked against the generated types |
| Server state | **TanStack Query** | Loading/error states, caching, keeps the previous page visible while the next one loads; familiar to the developer |
| Tests | Vitest 5 + React Testing Library + **MSW 2.15** to mock the API | Vite-native test runner; tests through the UI the way a user uses it; requests go through the real API client. MSW 3.0 was released days before; 2.x is the mature version |
| Styling | Plain CSS (CSS modules) | No UI library needed for three screens |
| Movie list | **Own table component** (`MovieTable`), not AG Grid | See "Why not AG Grid" below |

### Why not AG Grid

The developer has used AG Grid before; it was considered for the movie list and left out:

- The backend does paging, search and sorting. AG Grid's server-side row model is an Enterprise
  (paid) feature; with the Community edition the grid would get one page of rows with its own
  pagination and sorting switched off, so the pager and search box would be custom anyway, and
  grid sorting/filtering would only act on the rows shown (misleading).
- Nested data (actors, genres, director) needs cell renderers; in a table it is one line of JSX.
- It is by far the largest dependency, and its virtualisation needs extra setup for tests.
- The list is a small, typed `MovieTable` component taking `Movie[]`, so it can be replaced by
  AG Grid later if the app needs inline editing, many columns, column resizing/pinning, export, or
  client-side sorting/filtering of large data sets.

### TypeScript version

The latest TypeScript is 7.0 and the Vite template uses 6.0, but `openapi-typescript` 7.13 declares
a peer dependency on TypeScript `^5.x`. npm refuses conflicting peer dependencies, so Phase 1
starts with **TypeScript 5.9** (supported by every tool here). Moving to a newer TypeScript can be
done once `openapi-typescript` supports it.

The Vite template is written for TypeScript 6, which turns `strict` and
`noUncheckedSideEffectImports` on **by default**; TypeScript 5.9 does not. Both are therefore set
explicitly in `tsconfig.app.json` and `tsconfig.node.json`, otherwise pinning 5.9 would silently
disable strict type checking.

### Talking to the backend in development

The dev server runs on `http://localhost:5173`, the backend on `http://localhost:8080`. Instead of
enabling CORS in the backend, the **Vite dev server proxies `/api` to the backend**: the frontend
calls relative URLs (`/api/movies`) and needs no backend change.

## 3. Implementation steps

### Phase 0 – Prerequisites
- [x] Node 24 / npm 11 (installed).
- [x] Backend running (`docker compose up -d` and `./mvnw spring-boot:run` in `backend/`) – needed from Phase 2 on.

### Phase 1 – Project setup
- [x] Scaffold with Vite 8 (`create-vite` 9.2.1, `react-ts` template) into `frontend/`: `package.json`, `tsconfig`, oxlint, `index.html`, `src/` (template README not copied; own README in Phase 6).
- [x] Pin TypeScript `~5.9.3` (with `strict` set explicitly); add `openapi-typescript` 7.13 (dev) and `openapi-fetch` 0.17. No peer dependency conflicts.
- [x] Vite proxy `/api` → `http://localhost:8080`.
- [x] `.gitignore` (`node_modules`, `dist`, generated `src/api/schema.d.ts`).
- [x] Verified: `npm install`, `npm run build`, `npm run lint`; dev server serves the app and proxies `/api/movies` to the backend (incl. `404` Problem Details); `openapi-typescript` reads the contract.

### Phase 2 – Generate types from the contract
- [x] npm script `generateTypes`: `openapi-typescript ../backend/src/main/resources/openapi/openapi.yaml -o src/api/schema.d.ts`.
- [x] Runs automatically after `npm install` (`postinstall`, so the editor has the types right after cloning) and before `dev` and `build` (`predev`/`prebuild`); `pretest` is added with the test runner in Phase 3.
- [x] Typed API client `src/api/client.ts` (openapi-fetch; base URL = the page's origin, so the same code works in tests).
- [x] Type aliases in `src/api/types.ts`: `Movie`, `NewMovie`, `MoviePage`, `Person`, `Problem`, `FieldError`, `ListMoviesParams`.
- [x] Verified: type checking rejects wrong paths, unknown/wrongly typed query parameters and bodies missing required fields (temporary file with `@ts-expect-error`); the client returns data, `404` detail and `400` field errors from the real backend; `npm run build` regenerates a deleted `schema.d.ts`; the generated file is git-ignored.
- [x] **Contract mismatch found and fixed in the backend:** the backend returned `"synopsis": null` although the contract declared `synopsis` optional and non-nullable. Fixed with the developer's rule (details in the backend plan, "Follow-up"):
  - **Response schemas have no optional fields.** Every field of `Movie`, `MoviePage`, `Person`, `Problem` and `FieldError` is required and always sent; empty values are `null` (`Movie.synopsis`, `Problem.detail`, `Problem.errors`). The backend's generated models carry `@JsonInclude(ALWAYS)` (a simpler global Jackson setting was considered; the developer kept the per-class annotation).
  - **Errors too:** every backend error, also Spring's own (malformed JSON, wrong parameter type, unknown path), is sent in the full `Problem` shape with `type` defaulting to `about:blank`.
  - **Request schema `NewMovie`:** `synopsis` and `actors` are optional and nullable; a client may leave them out or send `null` (verified end to end in the backend tests).
  - Generated types now: `Movie.synopsis: string | null`; `Problem.detail: string | null`; `Problem.errors: FieldError[] | null`; `NewMovie.synopsis?: string | null`; `NewMovie.actors?: Person[] | null`.
  - Also switched the backend's generated code to JSpecify `@Nullable` (Spring's own was deprecated).

### Phase 3 – List movies with paging
- [x] Movie list (`MovieTable`, typed `Movie[]` rows): name (year), genres, rating as stars (with an accessible "x out of 5" label), age limit ("All ages" for 0), director, actors, synopsis cut to three lines with CSS (full text in the DOM). No actors / `null` synopsis shown as "—".
- [x] `Pagination`: previous/next (disabled at the ends), "Page x of y", total count, page size selector (10/20/50; changing it goes back to the first page).
- [x] Loading, error and empty states. Error message from the Problem's `detail`, falling back to `title` (`detail` is nullable); also handle network errors (backend not running), which have no Problem body. Error box with "Try again". Failed requests are retried once, but only network/5xx errors (a 4xx would fail again).
- [x] Keep the current page visible while the next one loads (TanStack Query `placeholderData: keepPreviousData`; the table is marked `aria-busy` and dimmed).
- [x] Tests (Vitest + React Testing Library + MSW 2, 9 tests): details incl. `null` fields, loading, paging forward/back with the requested `page` values, page size change, previous page kept while loading, empty state, Problem `detail`, fallback to `title`, network error + "Try again". Test data builders are typed from the contract. Checked by breaking two behaviours on purpose (no `placeholderData`, page not reset on size change): each failed exactly the expected test.
- [x] API layer: `src/api/movies.ts` (`fetchMovies`, `useMovies`, query keys), `src/api/errors.ts` (`ApiError` with `status` and `problem`), `src/api/queryClient.ts`. `npm test` regenerates the types first (`pretest`); `npm run test:watch` for development.
- [x] **Finding:** openapi-fetch keeps the `fetch` function that exists when the client is created. The API mock replaces `fetch` later, so all requests went to the real network ("fetch failed"). The client now looks up `fetch` on every call (`src/api/client.ts`).

### Phase 4 – Search
- [ ] Search box; search as you type with a short debounce (~300 ms).
- [ ] A new search term goes back to the first page.
- [ ] `search` and `page` in the URL query string, so reload, back/forward and sharing a link work.
- [ ] "No movies match" state.
- [ ] Tests for search.

### Phase 5 – Add a movie
- [ ] Form matching the contract's `NewMovie`: name, year, genres, age limit, rating, director, actors (add/remove rows), synopsis.
- [ ] Client-side checks mirroring the contract (required fields, ranges) for immediate feedback; the backend stays the authority.
- [ ] Show the backend's `400` field errors next to the fields (`Problem.errors[].field`, e.g. `director.firstName`, `actors[0].lastName`; `errors` is `null` for non-validation errors), and the `409` duplicate message.
- [ ] Optional fields: an empty synopsis is sent as `null` and an empty actor list as `[]` (both accepted by the backend; it also trims text and stores blank text as `null`).
- [ ] After success: show the new movie and refresh the list.
- [ ] Tests for the form, including server-side errors.

### Phase 6 – Documentation & handover
- [ ] `frontend/README.md`: requirements, install, run (with the backend), test, build.
- [ ] `AGENTS.md` / `CLAUDE.md` for the frontend (contract-first: change `openapi.yaml`, regenerate, fix compile errors).
- [ ] `frontend/documentation/ai-usage.md`: how AI was used for the frontend (documented separately from the backend, which was the required exercise); prompt log for the frontend sessions (add a `--since` option to `backend/tools/export-prompts.mjs`, so the frontend log starts with the first frontend prompt and the backend log stays unchanged).

## 4. Possible extensions

- Movie details view (`GET /api/movies/{id}`).
- Serve the built frontend from the backend or add it to Docker Compose.
- End-to-end tests (Playwright) against the real backend.

## 5. Decisions

| Question | Decision |
|---|---|
| Server state | TanStack Query |
| Generated `src/api/schema.d.ts` | Git-ignored; regenerated after `npm install` and before every `dev`, `build` and `test`; script name `generateTypes` |
| Search trigger | As you type, with a short debounce |
| Add movie | Included (Phase 5) |
| AI documentation | Separate for the frontend, in `frontend/documentation/` (the backend was the required exercise; the frontend is extra) |
| Movie list component | Own table component instead of AG Grid (see "Why not AG Grid") |
| Nullable fields | Response schemas have no optional fields: every field is always present, empty values are `null` (contract `nullable: true`). The frontend checks for `null`, never for a missing field. Errors (`Problem`) follow the same rule. The request schema `NewMovie` may have optional fields (`synopsis`, `actors`), which the frontend may leave out or send as `null` |
