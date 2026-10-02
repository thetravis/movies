# Movies frontend

React single-page application for the [Movies backend](../backend/README.md): list, search and add
movies. Built with TypeScript, Vite and TanStack Query. The backend's **OpenAPI contract** is the
single source of truth: the TypeScript types for the API are generated from it, so a contract change
shows up as a compile error here too.

**Features**

- Movie list with paging (previous/next, "page x of y", page size). The current page stays visible
  while the next one loads.
- Search as you type (sent once typing pauses) over name, synopsis, genres, actors and director.
- Search term, page and page size in the URL (`?search=war&page=2&size=50`): reload, Back/Forward
  and shared links work.
- Long synopses are cut to three lines; click a row (or "Show more") to read the whole text.
- "Add a movie" dialog with checks in the browser and the backend's errors shown next to the fields.
- Loading, empty and error states, including "backend not running".

How the frontend was built and why: [documentation/plan.md](documentation/plan.md). How AI was
used: [documentation/ai-usage.md](documentation/ai-usage.md).

## Requirements

| Requirement | Notes |
|---|---|
| **Node.js 22.12+ or 24** | Required by Vitest 5 (`engines` in `package.json`). Comes with npm. |
| **The backend** | Running on `http://localhost:8080` for development; see the [backend README](../backend/README.md). Not needed for the tests. |
| The repository layout | The API types are generated from `../backend/src/main/resources/openapi/openapi.yaml`, so the `frontend` and `backend` folders must stay side by side. |

## Getting started

All commands are run in the `frontend` directory.

```shell
npm install        # also generates the API types (src/api/schema.d.ts)
npm run dev        # http://localhost:5173
```

Start the backend first (in `backend/`: `docker compose up -d` and `.\mvnw spring-boot:run`). If it
isn't running, the page says "Could not connect to the server. Is the backend running?".

In development the Vite dev server forwards requests to `/api` to the backend (see
`vite.config.ts`), so the backend needs no CORS configuration.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Development server with hot reload on http://localhost:5173 |
| `npm test` | Runs all tests once (Vitest) |
| `npm run test:watch` | Runs the tests on every change |
| `npm run build` | Type check and production build into `dist/` |
| `npm run preview` | Serves the production build locally |
| `npm run lint` | Lints with oxlint |
| `npm run generateTypes` | Generates `src/api/schema.d.ts` from the OpenAPI contract |

`generateTypes` runs automatically after `npm install` and before `dev`, `build` and `test`, so the
types always match the contract. The generated file is git-ignored.

## API contract and types

The API types come from the backend's contract. When the contract changes:

1. `npm run generateTypes` (or just run `dev`, `build` or `test`).
2. Fix the compile errors: they point to every place that uses the changed part of the API.

Import API types from `src/api/types.ts` (`Movie`, `NewMovie`, `MoviePage`, `Problem`, ...), not from
the generated file. Calls go through the typed client in `src/api/client.ts` (openapi-fetch): paths,
parameters, request bodies and responses are all checked against the contract.

The contract has no optional fields in responses: a value without content is `null`, never a
missing field. Check for `null`, not for `undefined`.

## Project structure

```
src/
  api/          API client, generated types (schema.d.ts), type aliases, queries and mutations,
                error type (ApiError)
  components/   MovieList (search, paging, states), MovieTable, Synopsis, Pagination, SearchBox,
                AddMovieDialog, MovieForm (+ movieFormModel: form rules, no React)
  hooks/        useMovieListParams (state in the URL), useDebouncedValue
  test/         Test setup, mock API server (MSW), test data builders
```

## Tests

```shell
npm test
```

Tests use Vitest, React Testing Library and MSW. They render the real components and go through the
real API client; only HTTP is mocked, so **no backend is needed**. Test data is typed from the
contract.

| Test file | Covers |
|---|---|
| `MovieList.test.tsx` | Movie details, loading, paging, page size, previous page kept while loading, empty state, errors |
| `MovieList.search.test.tsx` | Search delay, first page on a new search, no matches, state in the URL, Back/Forward |
| `MovieTable.test.tsx` | Expanding a row's synopsis (click, button, keyboard, text selection) |
| `MovieForm.test.tsx` | Adding a movie: request body, checks, backend errors, `409`, dialog behaviour |
| `movieFormModel.test.ts` | Form rules, conversion to the API request, mapping of backend field errors |

## Troubleshooting

| Problem | Fix |
|---|---|
| "Could not connect to the server. Is the backend running?" | Start the backend on port 8080. |
| The editor shows errors for `./schema` | The generated types are missing: `npm run generateTypes`. |
| `Port 5173 is in use` | Vite picks the next free port; use the URL it prints. |
| `npm warn install-scripts ... msw` during `npm install` | Harmless: npm 11 asks before running dependencies' install scripts; MSW's script is only for mocking in the browser, which isn't used. |
| `EBADENGINE` warning | Node is older than 22.12; install Node 22 or 24. |

## Documentation

- [documentation/plan.md](documentation/plan.md) – plan, technology choices and decisions
- [documentation/ai-usage.md](documentation/ai-usage.md) – how AI was used for the frontend
- [documentation/ai-prompts.md](documentation/ai-prompts.md) – all prompts of the frontend work
- [AGENTS.md](AGENTS.md) – conventions for developers and AI agents
