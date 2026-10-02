# Movies backend

REST API for listing, searching and adding movies. Built with Spring Boot and PostgreSQL. The API
is designed **contract-first**: the OpenAPI spec in
[`src/main/resources/openapi/openapi.yaml`](src/main/resources/openapi/openapi.yaml) is the source
of truth, and the server interfaces and DTOs are generated from it at build time.

**Features**

- List all movies (paged, sorted by name), search movies by a term, get one movie, add a movie.
- Search matches the movie name, synopsis, genres, actors and director (case-insensitive, partial).
- Movies from [`movies-compact.json`](src/main/resources/data/movies-compact.json) are loaded on
  startup; another JSON file can be loaded instead ([Movie data](#movie-data)).
- Validation and errors in the standard Problem Details format (RFC 9457).
- Swagger UI showing the API contract.

**Tech stack:** Java 21, Spring Boot 4, PostgreSQL 17 (Docker Compose), Spring Data JPA, Liquibase,
OpenAPI Generator, MapStruct, JUnit 5 + Mockito + Testcontainers, Maven.

The design decisions and how the project was built are documented in
[`documentation/plan.md`](documentation/plan.md); how AI was used in
[`documentation/ai-usage.md`](documentation/ai-usage.md).

## Requirements

| Requirement | Notes |
|---|---|
| **JDK 21** | Java 21 exactly or newer. Java 17 is **not** enough: the project is compiled for Java 21 (`java.version` in `pom.xml`). A JRE is not enough either; you need a JDK. |
| **`JAVA_HOME`** | Must point to the JDK 21 installation, e.g. `C:\Program Files\Java\jdk-21`. The Maven Wrapper and Maven use it to find Java. |
| **Docker** (with Docker Compose) | Runs the PostgreSQL database, and a temporary test database during the tests. On Windows/macOS, **Docker Desktop must be running.** |
| Maven 3.9+ *(optional)* | Not needed: the included Maven Wrapper (`mvnw` / `mvnw.cmd`) downloads the right Maven version automatically. |
| Free ports | `5432` (PostgreSQL) and `8080` (the application). |

Check your setup:

```shell
java -version          # should print version 21 or newer
echo $env:JAVA_HOME    # PowerShell; in bash: echo $JAVA_HOME
docker compose version
```

## Getting started

All commands are run in the `backend` directory.

The examples use the Maven Wrapper. In **PowerShell** run it as `.\mvnw`, in **bash / macOS /
Linux** as `./mvnw`. If you have Maven installed, you can use `mvn` instead.

### 1. Start the database

```shell
docker compose up -d
```

This starts PostgreSQL 17 in a container named `movies-postgres` with database, user and password
all set to `movies`. The data is kept in a Docker volume, so it survives restarts.

Check that it is running and `healthy`:

```shell
docker compose ps
```

### 2. Build

```shell
.\mvnw compile
```

Or if you have Maven installed in the PATH simply:

```shell
mvn compile
```

This also generates the API interfaces and DTOs from the OpenAPI spec into
`target/generated-sources/openapi`. Run it again after changing `openapi.yaml`. (Your IDE may need
this folder marked as a source folder; IntelliJ and VS Code usually pick it up automatically after
a Maven reload.)

### 3. Run the application

```shell
.\mvnw spring-boot:run
```

The application starts on **http://localhost:8080**. Stop it with `Ctrl+C`. On startup it creates
the database tables (first run only) and loads the movies from the bundled JSON file; see
[Movie data](#movie-data).

- Swagger UI (API documentation): http://localhost:8080/swagger-ui.html
- The raw API contract: http://localhost:8080/openapi.yaml

### 4. Run the tests

```shell
.\mvnw verify
```

The tests **don't use your local database**. Tests that need a database start their own
throwaway PostgreSQL 17 container with [Testcontainers](https://testcontainers.com/), so
**Docker must be running**, but the `movies-postgres` container doesn't need to be. The first run
downloads the `postgres:17-alpine` image; after that the container starts in a few seconds and is
removed when the tests finish.

| Test class | Kind | Database | What it covers |
|---|---|---|---|
| `MovieServiceTest` | unit (Mockito) | – | Validation rules, duplicate check, search vs. list, paging/sorting, not found |
| `MovieImporterTest` | unit (Mockito) | – | Import counts (imported / existing / invalid), genre typo fix, missing or invalid file, disabled import |
| `MoviesControllerTest` | web slice (`@WebMvcTest`) | – | Routing, contract validation, status codes, `Location` header, Problem Details for 400/404/409 |
| `MovieMapperTest` | integration | container | MapStruct mapping: trimming, reuse/creation of persons and genres, duplicates, all fields both ways, paging |
| `MovieRepositoryTest` | integration | container | Search query, paging, actor order, uniqueness; each test is rolled back |
| `MoviesApplicationTests` | full stack over HTTP | container | Startup import of [`src/test/resources/data/movies-test.json`](src/test/resources/data/movies-test.json), list, search, get, add, error responses, Swagger UI |

To run only the fast tests that don't need Docker:

```shell
.\mvnw test "-Dtest=MovieServiceTest,MovieImporterTest,MoviesControllerTest"
```

## Using the API

The easiest way to try the API is **Swagger UI** at http://localhost:8080/swagger-ui.html
("Try it out" on each endpoint). The contract itself is
[`openapi.yaml`](src/main/resources/openapi/openapi.yaml).

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/movies?search=&page=0&size=20` | List movies, optionally filtered by `search`. Returns a page: `{items, page, size, totalItems, totalPages}` |
| `GET` | `/api/movies/{id}` | Get one movie (`404` if not found) |
| `POST` | `/api/movies` | Add a movie: `201` + `Location` header; `400` invalid; `409` same name and year exists |

Examples with curl (bash / Git Bash; in PowerShell use `curl.exe`):

```shell
# List the first page of 5 movies
curl "http://localhost:8080/api/movies?page=0&size=5"

# Search (name, synopsis, genre, actor or director)
curl "http://localhost:8080/api/movies?search=harrison%20ford"

# Get one movie
curl "http://localhost:8080/api/movies/1"

# Add a movie
curl -i -X POST "http://localhost:8080/api/movies" \
  -H "Content-Type: application/json" \
  -d '{"name": "Jaws", "year": 1975, "genres": ["Thriller"], "ageLimit": 12, "rating": 5,
       "actors": [{"firstName": "Roy", "lastName": "Scheider"}],
       "director": {"firstName": "Steven", "lastName": "Spielberg"},
       "synopsis": "A giant shark terrorizes a beach town."}'
```

Adding a movie in PowerShell:

```powershell
$movie = @{
    name     = "Jaws"
    year     = 1975
    genres   = @("Thriller")
    ageLimit = 12
    rating   = 5
    director = @{ firstName = "Steven"; lastName = "Spielberg" }
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/movies" -ContentType "application/json" -Body $movie
```

Rules for a new movie: `name`, `year` (1888–2100), `genres` (at least one), `ageLimit` (0–21),
`rating` (1–5) and `director` are required; `actors` and `synopsis` are optional. Text is trimmed,
existing actors, directors and genres are reused (matched ignoring case), and repeated actors or
genres are stored once. Responses always contain every field of the contract; a field without a
value is `null` (also in error responses, e.g. `"errors": null` when the error is not a validation
failure). Invalid requests get a `400` with a list of field errors:

```json
{"type": "about:blank", "title": "Bad Request", "status": 400, "detail": "Request validation failed.",
 "instance": "/api/movies", "errors": [{"field": "rating", "message": "must be less than or equal to 5"}]}
```

## Architecture

```
api/         REST controller (implements the interface generated from openapi.yaml), error handling
service/     Use cases (list, search, get, add), one transaction each;
  mapper/    MapStruct mappers between API DTOs and entities (persons and genres are reused)
domain/      JPA entities with the validation rules
repository/  Spring Data repositories, search query
seed/        JSON import on startup
```

The API is **contract-first**: change `openapi.yaml` first; the build regenerates the API
interface and DTOs, and MapStruct fails the build until new fields are mapped. Details, and the
reasoning behind the design, are in [`documentation/plan.md`](documentation/plan.md) (section
"Layers and mapping"). Conventions for developers and AI agents are in [`AGENTS.md`](AGENTS.md).

## Movie data

On every startup the application imports movies from a JSON file, by default the bundled
[`src/main/resources/data/movies-compact.json`](src/main/resources/data/movies-compact.json).
The log shows a summary, e.g. `Movie import finished: 20 imported, 0 already existed, 0 invalid.`

- **Safe to run on every startup:** movies that already exist (same name and year, ignoring case)
  are skipped. Restarting doesn't create duplicates.
- **Same rules as the API:** each movie is cleaned up (names trimmed, repeated actors/genres
  removed) and validated exactly like `POST /api/movies`. Invalid movies are logged and skipped.
- **Known data fixes:** the genre typo `Familty` in the bundled file is corrected to `Family`.

### Loading a different JSON file

The file must be a JSON array of movies in the same format as `movies-compact.json` (the
`NewMovie` schema in the API contract). Point the application to it with `movies.seed.file`;
any Spring resource location works (`file:...`, `classpath:...`):

```shell
.\mvnw spring-boot:run "-Dspring-boot.run.arguments=--movies.seed.file=file:C:/data/other-movies.json"
```

or with an environment variable (PowerShell):

```powershell
$env:MOVIES_SEED_FILE = "file:C:/data/other-movies.json"
.\mvnw spring-boot:run
```

The movies from the new file are **added** to the existing ones. To load only the new file, reset
the database first (`docker compose down -v`, then `docker compose up -d`).

To skip the import, set `movies.seed.enabled=false` (env var `MOVIES_SEED_ENABLED=false`).

## Database schema

The schema is managed by [Liquibase](https://www.liquibase.com/). On startup the application
applies any new changesets from
[`src/main/resources/db/changelog`](src/main/resources/db/changelog) and records them in the
`databasechangelog` table, so each changeset runs only once per database. To change the schema,
add a new changeset file; never edit one that has already been applied.

## Stopping and resetting the database

```shell
docker compose stop      # stop the container, keep the data
docker compose down      # remove the container, keep the data
docker compose down -v   # remove the container AND delete all data
```

After `down -v`, start the database again with `docker compose up -d`; the next application start
recreates the tables and imports the movies again.

## Configuration

The defaults match `docker-compose.yml`, so no configuration is needed for local development.
To connect to another database, set these environment variables before starting the application:

| Variable | Default |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `5432` |
| `DB_NAME` | `movies` |
| `DB_USERNAME` | `movies` |
| `DB_PASSWORD` | `movies` |

The database container can be configured the same way with `POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD` and `POSTGRES_PORT`, either as environment variables or in a `.env` file next
to `docker-compose.yml` (`.env` is git-ignored). If you change these, set the `DB_*` variables to
match.

## Troubleshooting

| Problem | Fix |
|---|---|
| `failed to connect to the docker API ... dockerDesktopLinuxEngine` | Docker Desktop is not running. Start it and try again. |
| `release version 21 not supported` or `JAVA_HOME is not defined correctly` | `JAVA_HOME` points to an older Java or is not set. Point it to JDK 21 and open a new terminal. |
| `Connection to localhost:5432 refused` on startup | The database is not running: `docker compose up -d`. |
| `Port 8080 was already in use` | Another application uses port 8080. Stop it, or run with `.\mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"`. |
| `Cannot read movie file ... (check movies.seed.file)` on startup | The configured JSON file doesn't exist or isn't valid JSON. Check the path; on Windows use forward slashes, e.g. `file:C:/data/movies.json`. |
| `Bind for 0.0.0.0:5432 failed: port is already allocated` | Another PostgreSQL is running locally. Stop it, or set `POSTGRES_PORT=5433` and `DB_PORT=5433`. |

## Project documentation

- [`documentation/instructions.txt`](documentation/instructions.txt) – the original task
- [`documentation/plan.md`](documentation/plan.md) – implementation plan and design decisions
- [`documentation/ai-usage.md`](documentation/ai-usage.md) – how AI was used to build the project
- [`AGENTS.md`](AGENTS.md) – conventions and commands for developers and AI agents
  ([`CLAUDE.md`](CLAUDE.md) points Claude Code to it)
- [`documentation/ai-prompts.md`](documentation/ai-prompts.md) – all prompts given to the AI assistant (Claude Code), generated with `node backend/tools/export-prompts.mjs --until 2026-10-02T19:49:00Z` from the repository root
