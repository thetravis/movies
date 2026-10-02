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
| Build tool / dev server | **Vite** (`react-ts` template) | Current standard for new React projects (Create React App is deprecated); gives `package.json`, `tsconfig`, ESLint and a fast dev server |
| Contract → types | **openapi-typescript** | Generates only TypeScript types (no runtime code) from `openapi.yaml` |
| HTTP client | **openapi-fetch** | Tiny typed `fetch` wrapper: paths, query parameters, request bodies and responses are checked against the generated types |
| Server state | **TanStack Query** | Loading/error states, caching, keeps the previous page visible while the next one loads; familiar to the developer |
| Tests | Vitest + React Testing Library (+ MSW to mock the API) | Vite-native test runner; tests through the UI the way a user uses it |
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

### Talking to the backend in development

The dev server runs on `http://localhost:5173`, the backend on `http://localhost:8080`. Instead of
enabling CORS in the backend, the **Vite dev server proxies `/api` to the backend**: the frontend
calls relative URLs (`/api/movies`) and needs no backend change.

## 3. Implementation steps

### Phase 0 – Prerequisites
- [ ] Node 24 / npm 11 (installed).
- [ ] Backend running (`docker compose up -d` and `./mvnw spring-boot:run` in `backend/`).

### Phase 1 – Project setup
- [ ] Scaffold with Vite (`react-ts` template) into `frontend/`: `package.json`, `tsconfig`, ESLint, `index.html`, `src/`.
- [ ] Pin TypeScript 5.9; add `openapi-typescript` (dev dependency) and `openapi-fetch`.
- [ ] Vite proxy `/api` → `http://localhost:8080`.
- [ ] `.gitignore` (`node_modules`, `dist`, generated `src/api/schema.d.ts`).
- [ ] Verify: `npm install`, `npm run dev`, `npm run build`, `npm run lint`.

### Phase 2 – Generate types from the contract
- [ ] npm script `generate:api`: `openapi-typescript ../backend/src/main/resources/openapi/openapi.yaml -o src/api/schema.d.ts`.
- [ ] Run it automatically before `dev`, `build` and `test` (`predev`/`prebuild`/`pretest`), like the backend regenerates on every build.
- [ ] Typed API client `src/api/client.ts` (openapi-fetch, base URL `/`).
- [ ] Convenient type aliases (`Movie`, `NewMovie`, `MoviePage`, `Problem`) exported from one module.
- [ ] Verify: a temporary call to `GET /api/movies` through the proxy compiles and returns data.

### Phase 3 – List movies with paging
- [ ] Movie list showing the relevant fields (name, year, genres, rating, age limit, director, actors; synopsis shortened).
- [ ] Paging controls: previous/next, "page x of y", total count; page size selector (optional).
- [ ] Loading, error (Problem Details `detail`) and empty states.
- [ ] Keep the current page visible while the next one loads.
- [ ] Tests for the list and paging.

### Phase 4 – Search
- [ ] Search box; search as you type with a short debounce (~300 ms).
- [ ] A new search term goes back to the first page.
- [ ] `search` and `page` in the URL query string, so reload, back/forward and sharing a link work.
- [ ] "No movies match" state.
- [ ] Tests for search.

### Phase 5 – Add a movie
- [ ] Form matching the contract's `NewMovie`: name, year, genres, age limit, rating, director, actors (add/remove rows), synopsis.
- [ ] Client-side checks mirroring the contract (required fields, ranges) for immediate feedback; the backend stays the authority.
- [ ] Show the backend's `400` field errors next to the fields (Problem Details `errors[].field`), and the `409` duplicate message.
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
| Generated `src/api/schema.d.ts` | Git-ignored; regenerated before every `dev`, `build` and `test` |
| Search trigger | As you type, with a short debounce |
| Add movie | Included (Phase 5) |
| AI documentation | Separate for the frontend, in `frontend/documentation/` (the backend was the required exercise; the frontend is extra) |
| Movie list component | Own table component instead of AG Grid (see "Why not AG Grid") |
