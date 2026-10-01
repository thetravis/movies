# Movies backend – implementation plan

## 1. Goal

An API service (Java) that lets a client:

1. **List all movies** (with the relevant details).
2. **Search movies** matching a client-provided search term.
3. **Add a new movie** with its details to the database.

Additional requirements from `instructions.txt`:

- Use a common protocol → **REST** (JSON over HTTP).
- Solve persistence → **PostgreSQL**.
- Initial data comes from `resources/data/movies-compact.json`.
- At least **one unit test**.
- Code should be easy for **someone else to continue developing**.
- Be ready to explain **where and how AI was used** (AI-specific practices such as `AGENTS.md`/`CLAUDE.md`, skills and conversation tracking are a plus).

## 2. Technology choices

| Area | Choice | Reason |
|---|---|---|
| Language | Java 21 (LTS) | Required by current Spring Boot; modern language features (records etc.) |
| Framework | Spring Boot (latest stable, pinned in `pom.xml`) | De-facto standard, fast to set up |
| Build | Maven + Maven Wrapper (`mvnw`) | Requested; wrapper means no local Maven install needed |
| API style | REST, **contract-first** with OpenAPI 3 | Spec is the single source of truth for the API |
| Code generation | `openapi-generator-maven-plugin` (`spring` generator, `interfaceOnly`) | Generates API interfaces + DTOs at build time into `target/generated-sources` |
| Database | PostgreSQL 17 (Docker Compose) | Requested |
| DB access | Spring Data JPA (Hibernate) | Low boilerplate, standard |
| Schema management | Flyway | Versioned, reviewable SQL migrations |
| Validation | Jakarta Bean Validation (constraints generated from the spec) | Input validation driven by the contract |
| Errors | RFC 7807 `ProblemDetail` | Standard error format |
| Tests | JUnit 5, Mockito, AssertJ, `@WebMvcTest`, Testcontainers (PostgreSQL) | Unit + slice + integration tests |
| API docs | Swagger UI serving the contract (springdoc or static) | Easy manual testing for reviewers |

### Prerequisites on the dev machine

- Docker (installed: Docker 29, Compose v5).
- **JDK 21** – currently only a Java 8 JRE is installed, so a JDK 21 (e.g. Eclipse Temurin 21) must be installed and `JAVA_HOME` pointed to it.
- Maven is **not** installed – the project will use the Maven Wrapper (`mvnw` / `mvnw.cmd`).

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
genre (id PK, name UNIQUE)
movie_actor (movie_id FK, person_id FK, PK(movie_id, person_id))
movie_genre (movie_id FK, genre_id FK, PK(movie_id, genre_id))
```

Notes:

- `year` is a reserved-ish word → column `release_year`.
- Check constraints for `rating` (1–5) and `age_limit` (>= 0).
- Persons/genres are reused (find-or-create) when adding movies.
- Indexes for search: `lower(name)` on movie (later possibly `pg_trgm` for `ILIKE '%term%'`).

## 5. API contract (draft)

Spec lives in `src/main/resources/openapi/openapi.yaml` (moved from `resources/openapi/`).

| Method | Path | Description | Responses |
|---|---|---|---|
| `GET` | `/api/movies` | List movies. Optional `search` query param filters results. Optional `page`/`size` for paging. | `200` list of movies |
| `GET` | `/api/movies/{id}` | Get a single movie | `200`, `404` |
| `POST` | `/api/movies` | Add a new movie | `201` + `Location` header, `400` |

Search semantics (`?search=term`): case-insensitive partial match against movie name, synopsis,
genre, actor names and director name. Combining with other filters (genre, year) is a possible
extension.

Schemas: `Movie` (response, includes `id`), `NewMovie` (request, no `id`), `Person`, `Problem`.
Genres as plain strings (not enum) so new genres can be added without a contract change.

Generated code: API interface (e.g. `MoviesApi`) + model DTOs in package
`...generated` – implemented by a hand-written controller. Mapping DTO ↔ entity with a
small mapper class (or MapStruct).

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
    │   │   ├── api/             # controller implementing generated interface, exception handler, mapper
    │   │   ├── domain/          # JPA entities
    │   │   ├── repository/      # Spring Data repositories
    │   │   ├── service/         # business logic (list, search, create)
    │   │   └── seed/            # JSON data importer
    │   └── resources/
    │       ├── application.yml
    │       ├── openapi/openapi.yaml
    │       ├── data/movies-compact.json
    │       └── db/migration/V1__create_schema.sql
    └── test/java/.../movies/
```

## 7. Implementation steps

### Phase 0 – Environment
- [x] `docker-compose.yml` with PostgreSQL 17 (healthcheck, named volume, overridable credentials).
- [ ] Install JDK 21, verify `java -version`.
- [ ] `docker compose up -d` and verify connection.

### Phase 1 – Project skeleton
- [ ] Generate Spring Boot project (Spring Initializr): Web, Validation, Data JPA, PostgreSQL driver, Flyway, Actuator, Testcontainers.
- [ ] Add Maven Wrapper.
- [ ] Move `resources/openapi` and `resources/data` under `src/main/resources`.
- [ ] `application.yml` with datasource pointing to the compose DB (values overridable via env vars).
- [ ] App starts and `/actuator/health` is UP.

### Phase 2 – Contract first
- [ ] Write `openapi.yaml` (endpoints + schemas + validation constraints + examples).
- [ ] Configure `openapi-generator-maven-plugin` (`interfaceOnly`, `useSpringBoot3`, `useTags`, `openApiNullable=false`, Jakarta validation).
- [ ] `mvnw compile` generates interfaces/DTOs.
- [ ] Serve Swagger UI for the contract.

### Phase 3 – Persistence
- [ ] Flyway migration `V1__create_schema.sql`.
- [ ] JPA entities + repositories.
- [ ] Search query (JPQL/Criteria, `lower(...) like`, `distinct`), avoid N+1 (`@EntityGraph`/fetch join).

### Phase 4 – Data import
- [ ] Importer (`ApplicationRunner`) that loads `movies-compact.json` when the `movie` table is empty.
- [ ] Trim names, normalize genre typo, reuse persons/genres.
- [ ] Toggle via property (e.g. `movies.seed.enabled=true`).

### Phase 5 – Business logic & API
- [ ] `MovieService`: `findAll`, `search(term)`, `create(newMovie)`, `findById`.
- [ ] Controller implementing generated `MoviesApi`.
- [ ] Global exception handler → `ProblemDetail` (validation errors 400, not found 404).

### Phase 6 – Tests
- [ ] Unit tests: `MovieService` (Mockito), mapper, importer normalization.
- [ ] Web slice tests: `@WebMvcTest` for controller (status codes, validation).
- [ ] Integration test: Testcontainers PostgreSQL, full flow (seed → list → search → create).

### Phase 7 – Documentation & handover
- [ ] `README.md`: prerequisites, run DB, run app, run tests, example `curl` calls.
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

- Base package name (e.g. `fi.<name>.movies`).
- Paging on the list endpoint: include from the start or return a plain list (20 rows)?
- Should duplicate movies (same name + year) be rejected with `409 Conflict`?
