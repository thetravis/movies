# Movies backend – implementation plan

## 1. Goal

An API service (Java) that lets a client:

1. **List all movies** (with the relevant details).
2. **Search movies** matching a client-provided search term.
3. **Add a new movie** with its details to the database.

Additional requirements from `instructions.txt`:

- Use a common protocol → **REST** (JSON over HTTP).
- Solve persistence → **PostgreSQL**.
- Initial data comes from `src/main/resources/data/movies-compact.json`.
- At least **one unit test**.
- Code should be easy for **someone else to continue developing**.
- Be ready to explain **where and how AI was used** (AI-specific practices such as `AGENTS.md`/`CLAUDE.md`, skills and conversation tracking are a plus).

## 2. Technology choices

| Area | Choice | Reason |
|---|---|---|
| Language | Java 21 (LTS) | Required by current Spring Boot; modern language features (records etc.) |
| Framework | Spring Boot 4.1.1 | De-facto standard, fast to set up |
| Build | Maven + Maven Wrapper (`mvnw`) | Requested; wrapper means no local Maven install needed |
| API style | REST, **contract-first** with OpenAPI 3 | Spec is the single source of truth for the API |
| Code generation | `openapi-generator-maven-plugin` (`spring` generator, `interfaceOnly`) | Generates API interfaces + DTOs at build time into `target/generated-sources` |
| Database | PostgreSQL 17 (Docker Compose) | Requested |
| DB access | Spring Data JPA (Hibernate) | Low boilerplate, standard |
| Mapping | MapStruct (DTO ↔ entity, inside the service transaction) | Generated plain Java; unmapped fields in either direction fail the build |
| Schema management | Liquibase (SQL-formatted changesets) | Versioned, reviewable migrations; each changeset runs exactly once per database |
| Initial data | Java importer at startup, configurable JSON file | Reviewer can load a different JSON file without touching migrations (see 4.1) |
| Validation | Jakarta Bean Validation (constraints generated from the spec) | Input validation driven by the contract |
| Errors | RFC 7807 `ProblemDetail` | Standard error format |
| Tests | JUnit 5, Mockito, AssertJ, `@WebMvcTest`, Testcontainers (PostgreSQL) | Unit + slice + integration tests |
| API docs | Swagger UI (springdoc) showing the contract file itself (`/swagger-ui.html`, spec at `/openapi.yaml`) | Easy manual testing for reviewers; docs never drift from the contract |

### Prerequisites on the dev machine

- Docker (installed: Docker 29, Compose v5).
- **JDK 21** – installed (Oracle JDK 21.0.12), `JAVA_HOME` set.
- Maven 3.10 installed locally. The project still ships the Maven Wrapper (`mvnw` / `mvnw.cmd`) so reviewers can build without installing Maven.

## 3. Data analysis (`movies-compact.json`)

20 movies, every record has the same shape:

```json
{
  "name": "Avengers: Endgame",
  "year": 2018,
  "genres": ["Adventure", "Sci-fi"],
  "ageLimit": 12,
  "rating": 4,
  "actors": [{ "firstName": "Robert", "lastName": " Downey Jr." }],
  "director": { "firstName": "Anthony", "lastName": "Russo" },
  "synopsis": "After the devastating events of ..."
}
```

Observations:

- `year`: 1958–2019. `ageLimit` values: 0, 7, 8, 12, 15, 16, 18. `rating`: 2–5 (assume scale 1–5).
- Longest `synopsis` is 242 characters → use `TEXT` / generous length.
- No duplicate movie names.
- **Data quality issues** (handle in import, mention in docs):
  - Some names have leading whitespace, e.g. `" Downey Jr."`, `" Sallis"` → trim on import.
  - Genre typo `"Familty"` next to `"Family"` → normalize on import (or document as a known issue).
  - Some factual errors (e.g. Endgame year 2018) → keep as-is; not our concern.

## 4. Domain & database model

Normalized model (shows proper modelling and makes search over actors/genres possible):

```
movie (id PK, name, release_year, age_limit, rating, synopsis, director_id FK -> person)
person (id PK, first_name, last_name)          -- actors and directors
genre (id PK, name)
movie_actor (movie_id FK, position, person_id FK, PK(movie_id, position), UNIQUE(movie_id, person_id))
movie_genre (movie_id FK, genre_id FK, PK(movie_id, genre_id))
```

Notes:

- `year` is a reserved-ish word → column `release_year`.
- Check constraints mirror the API contract: `release_year` 1888–2100, `age_limit` 0–21, `rating` 1–5.
- Uniqueness is case-insensitive (unique indexes on `lower(...)`): movie `(name, release_year)`,
  person `(first_name, last_name)`, genre `name`. The movie key is used by the importer to skip
  existing movies and by `POST` to return `409`.
- `movie_actor.position` keeps the actors' billing order (`@OrderColumn`); genres are returned sorted by name.
- Persons and genres are reused (find-or-create, case-insensitive) when adding movies.
- `synopsis` is `VARCHAR(2000)` (matches the contract's max length).
- Search uses `LIKE '%term%'`, which can't use normal indexes; fine for this data size
  (`pg_trgm` or full-text search is a possible extension).
- Fetching: `director` is fetched with the movie (entity graph); actors and genres are loaded
  lazily in batches (`hibernate.default_batch_fetch_size: 50`) to avoid N+1 queries.

### 4.1 Schema vs. data responsibilities

**Liquibase owns the schema, the Java importer owns the data.**

- **Schema** – Liquibase changelog `src/main/resources/db/changelog/db.changelog-master.yaml`
  including SQL-formatted changesets (e.g. `001-create-schema.sql`). The initial schema is
  written by hand from the model above; future schema changes are new changesets, never edits
  to applied ones. Hibernate never changes the schema (`ddl-auto: validate`).
- **Data** – a Java importer (`ApplicationRunner`) runs after Liquibase on startup:
  - Reads the JSON file from a configurable location:
    `movies.seed.file` (default `classpath:data/movies-compact.json`; any Spring resource
    location works, e.g. `file:C:/data/other-movies.json`, or env var `MOVIES_SEED_FILE`).
  - **Idempotent**: always runs, skips movies that already exist (same name + year). Restarts
    don't create duplicates; a different file adds its movies to the existing ones.
  - Normalizes data (trim names, fix known genre typo `Familty` → `Family`).
  - Saves through the same service method as `POST /api/movies`, so the same validation rules
    apply. Invalid records are logged and skipped; they don't stop the application.
  - Can be disabled with `movies.seed.enabled=false` (e.g. tests, production).
  - Logs a summary: imported / skipped (existing) / rejected (invalid).
- **Why not import data as a Liquibase changeset?** Data in a changeset would be fixed SQL;
  loading a different JSON file would require generating SQL and adding a migration. Running
  Java code inside a changeset (`customChange`) would tie old migrations to current entity
  classes, which breaks when they evolve.

## 5. API contract

Spec: `src/main/resources/openapi/openapi.yaml` (OpenAPI 3.0.3).

| Method | Path | Description | Responses |
|---|---|---|---|
| `GET` | `/api/movies` | List movies ordered by name. Optional `search` filters results; `page` (default 0) and `size` (default 20, max 100) for paging. | `200` `MoviePage`, `400` |
| `GET` | `/api/movies/{id}` | Get a single movie | `200`, `404` |
| `POST` | `/api/movies` | Add a new movie | `201` + `Location` header, `400`, `409` (same name + year exists) |

Search semantics (`?search=term`): case-insensitive partial match against movie name, synopsis,
genre, actor names and director name. Combining with other filters (genre, year) is a possible
extension.

Schemas: `Movie` (response, includes `id`), `NewMovie` (request, no `id`), `MoviePage` (`items`, `page`, `size`, `totalItems`, `totalPages`), `Person`, `Problem` (RFC 9457, with optional field-level `errors`), `FieldError`.
Genres as plain strings (not enum) so new genres can be added without a contract change.

Generated code (`target/generated-sources/openapi`, regenerated on every build, never edited):

- `com.example.movies.generated.api.MoviesApi` – interface implemented by a hand-written controller.
- `com.example.movies.generated.model.*Dto` – DTOs (`Dto` suffix avoids clashes with JPA entities).
- Generator options: `interfaceOnly`, `skipDefaultInterface` (compiler forces every operation to be
  implemented), `useSpringBoot4` + `useJackson3`, Bean Validation annotations with Spring built-in
  method validation, no swagger annotations.

### Layers and mapping

| Layer | Package | Responsibility |
|---|---|---|
| Web (REST) | `api` – `MoviesController`, `ApiExceptionHandler` | Implements the generated `MoviesApi`, delegates to the service, error responses |
| Import | `seed` – `MovieImporter` | JSON file → `NewMovieDto` (the file format *is* the contract's `NewMovie` schema) → service |
| Service | `service` – `MovieService`; `service/mapper` – `MovieMapper`, `PersonMapper`, `GenreMapper`, `TextMapper` | Use cases, one transaction each; DTO ↔ entity mapping inside the transaction |
| Persistence | `domain`, `repository` | JPA entities (with the validation rules), Spring Data repositories |

- **The service works with the generated DTOs** (`NewMovieDto` in, `MovieDto`/`MoviePageDto` out). A
  deliberate trade-off for an application of this size: no separate service models, and one
  transactional method used by both the REST API and the importer.
- **Mapping is generated by MapStruct**, with `unmappedSourcePolicy = ERROR` and
  `unmappedTargetPolicy = ERROR`. A field added to `openapi.yaml` is therefore enforced end to end:

  | Step | Enforced by |
  |---|---|
  | `openapi.yaml` → `NewMovieDto` / `MovieDto` | code generation |
  | `NewMovieDto` → `Movie` entity | MapStruct: build fails (unmapped source) |
  | `Movie` entity → database column | Hibernate `ddl-auto: validate`: startup fails |
  | `Movie` entity → `MovieDto` | MapStruct: build fails (unmapped target) |

  Deliberate exceptions are declared explicitly, e.g. `Person.id` is not exposed in the API.
- **Persons and genres** are resolved by `PersonMapper` / `GenreMapper` (find existing, case-insensitive,
  or save new). They write to the database, so mapping must run inside the service transaction:
  if the movie is then rejected (`400`/`409`), the new persons and genres are rolled back
  (verified manually).
- **Normalization:** `TextMapper.trimToNull` is applied by MapStruct to every text field; actors and
  genres are added via `Movie.addActor`/`addGenre` (`ADDER_PREFERRED`), which ignore duplicates.
- **Validation in two places:** the contract validates requests at the edge (immediate `400`); the
  entities carry the same rules as Bean Validation annotations, checked by `MovieService` for every
  caller (so values that are blank after trimming are caught too). Keep them in sync.
- **History:** (1) hand-written mapper, service using DTOs; (2) refactored to separate service
  models + MapStruct for DTO ↔ model; (3) found that `NewMovie` → entity was still unchecked, so
  replaced the service models with MapStruct DTO ↔ entity mapping inside the service transaction
  (option A). Moving the transaction to the controller was considered and rejected: the importer
  would have to duplicate the use case. The HTTP-level tests stayed unchanged through all steps.

## 6. Project structure

```
backend/
├── docker-compose.yml           # PostgreSQL for local development
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
├── documentation/
│   ├── instructions.txt         # the task
│   ├── plan.md                  # this file
│   └── ai-usage.md              # how AI was used (for the interview)
├── AGENTS.md / CLAUDE.md        # instructions for AI agents working on this repo
├── README.md                    # how to build, run, test
└── src/
    ├── main/
    │   ├── java/.../movies/
    │   │   ├── MoviesApplication.java
    │   │   ├── api/             # controller implementing generated interface, exception handler
    │   │   ├── domain/          # JPA entities
    │   │   ├── repository/      # Spring Data repositories
    │   │   ├── service/         # business logic (list, search, create), exceptions; mapper/ = MapStruct mappers
    │   │   └── seed/            # JSON data importer
    │   └── resources/
    │       ├── application.yml
    │       ├── openapi/openapi.yaml
    │       ├── data/movies-compact.json
    │       └── db/changelog/
    │           ├── db.changelog-master.yaml
    │           └── changes/001-create-schema.sql
    └── test/java/.../movies/
```

## 7. Implementation steps

### Phase 0 – Environment
- [x] `docker-compose.yml` with PostgreSQL 17 (healthcheck, named volume, overridable credentials).
- [x] Install JDK 21, verify `java -version`.
- [x] `docker compose up -d` and verify connection.

### Phase 1 – Project skeleton
- [x] Generate Spring Boot project (Spring Initializr): Web, Validation, Data JPA, PostgreSQL driver. (Liquibase is added in Phase 3, Testcontainers in Phase 6; Actuator dropped as not needed.)
- [x] Add Maven Wrapper.
- [x] Move `resources/openapi` and `resources/data` under `src/main/resources`.
- [x] `application.yml` with datasource pointing to the compose DB (values overridable via env vars).
- [x] App starts and connects to the database.

### Phase 2 – Contract first
- [x] Write `openapi.yaml` (endpoints + schemas + validation constraints + examples).
- [x] Configure `openapi-generator-maven-plugin` (`interfaceOnly`, Spring Boot 4 support, `useTags`, `openApiNullable=false`, Jakarta validation).
- [x] `mvnw compile` generates interfaces/DTOs.
- [x] Serve Swagger UI for the contract.

### Phase 3 – Persistence
- [x] Add Liquibase (`spring-boot-starter-liquibase`); master changelog + `001-create-schema.sql`.
- [x] Set `spring.jpa.hibernate.ddl-auto=validate` (app fails fast if entities and schema disagree).
- [x] JPA entities + repositories.
- [x] Search query (JPQL, `lower(...) like` with `exists` subqueries for actors/genres, so no duplicates); director via `@EntityGraph`, collections via batch fetching.
- [x] Repository tests (`MovieRepositoryTest`, `@DataJpaTest` against the compose PostgreSQL, rolled back).

### Phase 4 – Data import
Design in 4.1. The importer reuses `MovieService.create`, so build it together with or right after Phase 5.
- [x] `MovieSeedProperties` (`@ConfigurationProperties`) for `movies.seed.enabled` and `movies.seed.file`.
- [x] `MovieImporter` (`ApplicationRunner`) reading the configured JSON file record by record (a broken record doesn't stop the rest); missing/unreadable file or non-array stops startup.
- [x] Trim names, normalize genre typo, reuse persons/genres.
- [x] Skip existing movies (name + year), log and skip invalid records, log a summary.
- [x] Import disabled in `MoviesApplicationTests` so tests don't write to the dev database.
- [x] Manually verified: first start (20 imported), restart (20 existing), other file (new/existing/invalid mix), missing file.
- [x] README: "Movie data" section (how to load a different file, reset).

### Phase 5 – Business logic & API
- [x] `MovieService`: `listMovies(search, page, size)`, `getMovie(id)`, `createMovie(newMovie)` (trims input, removes repeated actors/genres, validates, checks duplicates, reuses persons/genres). Works with the generated DTOs; mapping happens inside its transactions.
- [x] `service/mapper`: MapStruct `MovieMapper` (DTO ↔ entity) using `PersonMapper`/`GenreMapper` (find-or-create) and `TextMapper` (trimming); validation rules on the entities. See "Layers and mapping" for how this evolved.
- [x] Controller implementing generated `MoviesApi`.
- [x] `ApiExceptionHandler` → `ProblemDetail` (400 with field `errors`, 404, 409 duplicate, 409 for DB constraint violations as a safety net).
- [x] Manually tested all endpoints and error cases with curl.

### Phase 6 – Tests
- [x] Unit tests (Mockito): `MovieServiceTest` (validation with a real validator, duplicate, list vs. search, paging/sort, not found), `MovieImporterTest` (counts, typo fix, missing/non-array file, disabled).
- [x] `MovieMapperTest` against the test container (mappers use repositories): trimming, blank → null, reuse vs. create of persons/genres, duplicates, invalid persons/genres left unsaved, all fields both ways, paging.
- [x] `MoviesControllerTest` (`@WebMvcTest`, `MockMvcTester`, service mocked): routing, default paging, contract validation, 201 + Location, 400/404/409 Problem Details, DB details hidden.
- [x] Sanity check of the tests by breaking production code (no trimming, duplicate actors allowed, no typo fix): each break failed the expected tests. Total: 55 tests.
- [x] Testcontainers: shared PostgreSQL 17 test container (`TestcontainersConfiguration`, `@ServiceConnection`); tests no longer use the local compose database.
- [x] Test data: `src/test/resources/data/movies-test.json` (copy of the original file).
- [x] `MoviesApplicationTests`: full stack over HTTP (`RestTestClient`, random port) – import + idempotent re-import, data cleanup (typo fix, trimming, actor order), paging + sorting, search by synopsis/actor/director/genre, get, 404, create + Location, 409, 400 with field errors, invalid paging, contract + Swagger UI.
- [x] `MovieRepositoryTest` moved to the test container.

### Phase 7 – Documentation & handover
- [x] `README.md`: features, tech stack, prerequisites, run DB/app/tests, **Using the API** (curl + PowerShell examples, verified against a running instance), architecture overview, loading a different JSON file, reset, troubleshooting.
- [x] `AGENTS.md` (tool-neutral conventions: commands, layout, contract-first rules, "adding a field" checklist, transaction/mapping/test rules, environment notes) + `CLAUDE.md` importing it.
- [x] `documentation/ai-usage.md`: tools, way of working, decisions made by the developer (with prompt numbers), what the AI did, how output was checked, AI mistakes caught. Section "Developer's notes" left for the developer.
- [x] `tools/export-prompts.mjs` → `documentation/ai-prompts.md`: all prompts, answers to Claude's questions, results and files per step. **Re-run at the end** (`node backend/tools/export-prompts.mjs`); session transcripts are deleted by Claude Code after 30 days.
- [ ] Optional (not done): Dockerize the app itself and add it to compose.

### Follow-up – changes made during frontend development

- [x] **Every response field is always present.** The generated TypeScript types showed that the
  backend sent `"synopsis": null` although the contract declared `synopsis` optional. Decision
  (developer): response schemas have **no optional fields** – the contract lists the fields that
  are there, and a value that may be missing is `null`, never a missing field (missing fields break
  other clients). Request schemas may have optional fields (missing → `null` in Java; missing lists
  → empty list).
  - Contract: all fields of `Movie`, `MoviePage`, `Person`, `Problem`, `FieldError` are `required`;
    `Movie.synopsis`, `Problem.detail` and `Problem.errors` are `nullable`. `NewMovie.synopsis` and
    `NewMovie.actors` are optional and `nullable`. The rule is stated in the contract's description.
  - Generator: one class-level `@JsonInclude(ALWAYS)` on every generated model
    (`additionalModelTypeAnnotations`; `generateJsonIncludeAnnotations=false`). The per-field
    annotations of `generateJsonIncludeAnnotations=true` were tried first, but they give required
    non-nullable fields `NON_NULL`. Verified: with `spring.jackson.default-property-inclusion=non_null`
    the responses still contain every field. A single global setting
    (`spring.jackson.default-property-inclusion: always` in `application.yml`) was considered as a
    shorter alternative; the developer kept the per-class annotation because it holds regardless of
    the global configuration (the long annotation only appears in `pom.xml` and generated code).
  - Errors: Spring's `ProblemDetail` leaves empty fields out (and in Spring 7 has no `type` unless
    set), so `ApiExceptionHandler.handleExceptionInternal` converts every error – also Spring's own
    (type mismatch, malformed JSON, unknown path) – to the generated `ProblemDto`; `type` defaults to
    `about:blank` (RFC 9457).
  - Tests: `getMovieAlwaysIncludesNullableFields`, strict check of the full `Problem` shape for a
    `404`, Problem shape for Spring's own errors, and `acceptsMissingOrNullOptionalFields` (full
    stack, raw JSON: a `NewMovie` without `synopsis`/`actors` and one with explicit `null`s are both
    accepted and returned with `"synopsis": null`, `"actors": []`).
- [x] **JSpecify instead of deprecated `org.springframework.lang.Nullable`** in generated code:
  generator option `useJspecify=true`. No deprecation warnings left in the build. 57 tests.

## 8. Possible extensions (out of scope unless time allows)

- Update/delete movies. Developer's plan: partial updates with `PATCH` and an `UpdateMovie` schema where only the fields present in the request are changed. That needs "field missing" and "field set to null" to be distinguishable in Java, e.g. with the generator's `openApiNullable` (`JsonNullable<T>`) for that schema.
- Filters (genre, year range, age limit) and sorting.
- Search that scales to large data (`pg_trgm`, full-text search, search engine): see 8.2.
- Authentication for write operations.
- CI pipeline (GitHub Actions: build + tests).
- Frontend client.

### 8.1 Managing genres

Today **a genre's name is its identity**: the API uses plain strings, `uq_genre_name` makes names
unique (case-insensitive), and `GenreMapper` finds or creates by name. That is also why
`GenreMapper` is hand-written while `PersonMapper` is MapStruct: there is no `GenreDto`, and a
`String` has no fields for MapStruct to copy.

- **List genres** (`GET /api/genres` → `string[]`): fits the current design;
  `findAll(Sort.by("name"))` + `GenreMapper.toName`.
- **Add a genre to a movie** (e.g. `PUT /api/movies/{id}/genres/{name}`): fits too; reuse
  `GenreMapper.toEntity` + `Movie.addGenre`. Decide whether unknown names are still created on the
  fly or, with a curated list, rejected with `400` (look up only).
- **Edit (rename) genres** breaks the name-as-identity assumption: the URL changes on rename,
  clients holding the old name break, and renaming to an existing name is really a merge. Give
  genres an id in the API (`Genre {id, name}`, movies return genre objects). With a `GenreDto`,
  `GenreMapper` becomes a MapStruct mapper like `PersonMapper`. Breaking API change – cheaper before the API has users.

### 8.2 Search at scale

The current search is instant for 20 movies but would not scale to IMDb size (~10M titles, ~15M
people, 50M+ cast rows):

- `LIKE '%term%'` with a leading wildcard can't use a B-tree index, and `lower(...)`/`concat(...)`
  on the columns prevents index use anyway, so every branch scans every row.
- `OR` across five conditions, two of them `EXISTS` subqueries over actors and genres, can't use a
  separate index per branch: in practice movies are scanned and the subqueries run per movie.
- Paging: `MoviePage.totalItems`/`totalPages` makes Spring Data run a count query over **all**
  matches (millions for "the"), and `order by name` + `offset` reads and discards every row before
  the requested page. At that size results should be ordered by relevance anyway.

Steps, in order (measure first: load the IMDb TSV dumps and compare with `EXPLAIN ANALYZE`):

1. **Trigram indexes (`pg_trgm`)** – same "contains" semantics, no API change. GIN indexes on
   `lower(name)` and `lower(first_name || ' ' || last_name)` support `LIKE '%term%'`. Rewrite the
   query so each branch can use its index: search `person` once, reach movies via
   `ix_movie_actor_person` and the director FK, and combine branches with `UNION` instead of
   `OR`/`EXISTS`. Use `||`, not `concat()` (index expressions must be `IMMUTABLE`; `concat` isn't),
   and the query must repeat the indexed expression exactly. Trigrams don't help terms shorter than
   3 characters: add a minimum search length to the contract. The synopsis is better left to
   full-text search.
2. **Full-text search (`tsvector`)** – a weighted search document per movie (name highest, then
   director/actors, genres, synopsis) with a GIN index, ordered by `ts_rank`. Matches words (with
   stemming), not substrings. A generated column can't read other tables, so the document is
   maintained by the service or a trigger, or kept in a materialized view.
3. **Search engine** (OpenSearch/Elasticsearch, Meilisearch) – when typo tolerance, autocomplete,
   facets or tuned relevance are needed. Costs a second system kept in sync with PostgreSQL
   (outbox table or change data capture).

Paging at any step: replace the exact count with an estimate or a "has next page" flag (Spring
Data `Slice`, no count query) and use keyset pagination instead of `offset`. Both change the
contract (`MoviePage`).

## 9. Open questions / decisions to confirm

- ~~Base package name~~ → `com.example.movies` (Initializr default; groupId `com.example`).
- ~~Paging on the list endpoint~~ → yes, `page` + `size` with a `MoviePage` response.
- ~~Duplicate movies on `POST`~~ → rejected with `409 Conflict` (the importer skips them; the DB unique constraint enforces it either way).
- ~~Flyway vs. Liquibase, how to load initial data~~ → Liquibase for schema, configurable Java importer for data (see 4.1).
