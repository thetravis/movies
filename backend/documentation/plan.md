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

Mapping entity → DTO with a small hand-written `MovieMapper` (MapStruct would be the choice if mappings grow). DTO → entity is business logic (trimming, find-or-create, duplicate check) and lives in `MovieService`.

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
    │   │   ├── service/         # business logic (list, search, create), entity → DTO mapper, exceptions
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
- [x] `MovieService`: `listMovies(search, page, size)`, `getMovie(id)`, `createMovie(newMovie)` (trims input, removes repeated actors/genres, validates, checks duplicates, reuses persons/genres). Returns DTOs because mapping needs the transaction.
- [x] `MovieMapper` (entity → DTO, hand-written; MapStruct noted as the option if mappings grow).
- [x] Controller implementing generated `MoviesApi`.
- [x] `ApiExceptionHandler` → `ProblemDetail` (400 with field `errors`, 404, 409 duplicate, 409 for DB constraint violations as a safety net).
- [x] Manually tested all endpoints and error cases with curl.

### Phase 6 – Tests
- [ ] Unit tests: `MovieService` (Mockito), mapper, importer (normalization, skipping existing, skipping invalid).
- [ ] Web slice tests: `@WebMvcTest` for controller (status codes, validation).
- [ ] Optional integration test: add Testcontainers PostgreSQL, full flow (seed → list → search → create).

### Phase 7 – Documentation & handover
- [ ] `README.md`: prerequisites, run DB, run app, run tests, example `curl` calls, how to load a different JSON file, how to reset the DB (`docker compose down -v`).
- [ ] `AGENTS.md`/`CLAUDE.md`: conventions and commands for AI agents (contract-first rule: change spec first, then regenerate).
- [ ] `documentation/ai-usage.md`: which tools were used, how, and what was reviewed/changed manually.
- [ ] Optional: Dockerize the app itself and add it to compose.

## 8. Possible extensions (out of scope unless time allows)

- Update/delete movies (`PUT`/`DELETE`).
- Filters (genre, year range, age limit) and sorting.
- Full-text search (`tsvector`) or `pg_trgm` index.
- Authentication for write operations.
- CI pipeline (GitHub Actions: build + tests).
- Frontend client.

## 9. Open questions / decisions to confirm

- ~~Base package name~~ → `com.example.movies` (Initializr default; groupId `com.example`).
- ~~Paging on the list endpoint~~ → yes, `page` + `size` with a `MoviePage` response.
- ~~Duplicate movies on `POST`~~ → rejected with `409 Conflict` (the importer skips them; the DB unique constraint enforces it either way).
- ~~Flyway vs. Liquibase, how to load initial data~~ → Liquibase for schema, configurable Java importer for data (see 4.1).
