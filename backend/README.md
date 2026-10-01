# Movies backend

REST API for listing, searching and adding movies. Built with Spring Boot and PostgreSQL. The API
is designed **contract-first**: the OpenAPI spec in
[`src/main/resources/openapi/openapi.yaml`](src/main/resources/openapi/openapi.yaml) is the source
of truth, and the server interfaces and DTOs are generated from it at build time.

> **Status:** work in progress. All endpoints work (list, search, get, add), and the movies from
> `movies-compact.json` are loaded on startup. See
> [`documentation/plan.md`](documentation/plan.md) for progress.

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

- `MoviesApplicationTests` starts the whole application against the container, imports the test
  data from [`src/test/resources/data/movies-test.json`](src/test/resources/data/movies-test.json)
  and calls the API over HTTP (list, search, get, add, error responses, Swagger UI).
- `MovieRepositoryTest` tests the database queries; each test is rolled back.

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
- [`documentation/ai-prompts.md`](documentation/ai-prompts.md) – all prompts given to the AI assistant (Claude Code), generated with `node backend/tools/export-prompts.mjs` from the repository root
