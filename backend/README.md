# Movies backend

REST API for listing, searching and adding movies. Built with Spring Boot and PostgreSQL. The API
is designed **contract-first**: the OpenAPI spec in
[`src/main/resources/openapi/openapi.yaml`](src/main/resources/openapi/openapi.yaml) is the source
of truth, and the server interfaces and DTOs are generated from it at build time.

> **Status:** work in progress. The project builds and starts, and Swagger UI shows the API
> contract, but the endpoints are not implemented yet (calls return `404`). See
> [`documentation/plan.md`](documentation/plan.md) for progress.

## Requirements

| Requirement | Notes |
|---|---|
| **JDK 21** | Java 21 exactly or newer. Java 17 is **not** enough: the project is compiled for Java 21 (`java.version` in `pom.xml`). A JRE is not enough either; you need a JDK. |
| **`JAVA_HOME`** | Must point to the JDK 21 installation, e.g. `C:\Program Files\Java\jdk-21`. The Maven Wrapper and Maven use it to find Java. |
| **Docker** (with Docker Compose) | Runs the PostgreSQL database. On Windows/macOS, **Docker Desktop must be running.** |
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

The application starts on **http://localhost:8080**. Stop it with `Ctrl+C`.

- Swagger UI (API documentation): http://localhost:8080/swagger-ui.html
- The raw API contract: http://localhost:8080/openapi.yaml

### 4. Run the tests

```shell
.\mvnw verify
```

The tests use the PostgreSQL database from `docker-compose.yml`, so **the database must be
running**. Each repository test runs in a transaction that is rolled back, so the tests don't
change the data in the database.

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
| `Bind for 0.0.0.0:5432 failed: port is already allocated` | Another PostgreSQL is running locally. Stop it, or set `POSTGRES_PORT=5433` and `DB_PORT=5433`. |

## Project documentation

- [`documentation/instructions.txt`](documentation/instructions.txt) – the original task
- [`documentation/plan.md`](documentation/plan.md) – implementation plan and design decisions
