# AGENTS.md – Movies backend

Instructions for AI coding agents (and a quick reference for developers) working on this project.
For setup and usage see [README.md](README.md); for design decisions see
[documentation/plan.md](documentation/plan.md).

## Project in one paragraph

Spring Boot 4 (Java 21) REST API for listing, searching and adding movies, backed by PostgreSQL 17.
The API is **contract-first**: `src/main/resources/openapi/openapi.yaml` is the source of truth;
the API interface (`MoviesApi`) and DTOs (`*Dto`) are generated from it on every build. The schema
is managed by Liquibase; movies are imported from a JSON file on startup.

## Commands

Run from the `backend` directory. PowerShell: `.\mvnw`; bash: `./mvnw`.

| Task | Command |
|---|---|
| Start the database | `docker compose up -d` |
| Build (and regenerate API code) | `./mvnw compile` |
| Run the application (port 8080) | `./mvnw spring-boot:run` |
| All tests (needs Docker running) | `./mvnw verify` |
| Fast tests without Docker | `./mvnw test "-Dtest=MovieServiceTest,MovieImporterTest,MoviesControllerTest"` |
| Export the AI prompt log | `node backend/tools/export-prompts.mjs` (from the repository root) |

Always run `./mvnw verify` before finishing a change; all tests must pass.

## Code layout

| Package | Contents |
|---|---|
| `api` | `MoviesController` (implements generated `MoviesApi`), `ApiExceptionHandler` (Problem Details) |
| `service` | `MovieService` (use cases; **the transaction boundary**), exceptions |
| `service.mapper` | MapStruct `MovieMapper`, `PersonMapper`; hand-written `GenreMapper`, `TextMapper` |
| `domain` | JPA entities `Movie`, `Person`, `Genre` with Bean Validation rules |
| `repository` | Spring Data repositories, search query |
| `seed` | `MovieImporter` (JSON → `NewMovieDto` → `MovieService`), `MovieSeedProperties` |
| `config` | Serving `openapi.yaml` for Swagger UI |
| `generated` | **Generated** – `target/generated-sources/openapi`, never edit |

## Rules

### API changes (contract-first)

1. Change `openapi.yaml` **first**, then run `./mvnw compile` to regenerate.
2. **Never edit generated code** (`target/generated-sources`). To change what is generated, adjust
   the generator configuration in `pom.xml` or a template override in `src/main/openapi-templates`
   (each override documents why it exists).
3. The controller only delegates to the service; no business logic in `api`.
4. **No optional fields in response schemas.** List every field in `required`; a value that may be
   missing is `nullable: true` and sent as `null`, never left out (all generated models carry
   `@JsonInclude(ALWAYS)`). Request schemas may have optional fields (missing → `null` in Java,
   missing lists → empty list). Error responses are built as the generated `ProblemDto` in
   `ApiExceptionHandler`, never as Spring's `ProblemDetail` directly (it leaves fields out).
   Generated code uses JSpecify `@Nullable` (`useJspecify`).

### Adding a field to a movie

The build and startup enforce every step; follow the errors:

1. `openapi.yaml`: add the property to `NewMovie` and `Movie`, with validation constraints.
2. Entity (`domain/Movie`): add the field with the **same** Bean Validation rules as in the
   contract (keep the two in sync), and a constructor parameter/getter.
3. Liquibase: add a **new** changeset in `src/main/resources/db/changelog/changes/` and include it
   in `db.changelog-master.yaml`. Never edit an applied changeset. Hibernate runs with
   `ddl-auto: validate`, so the app won't start if entity and schema disagree.
4. MapStruct mappers use `unmappedSourcePolicy = ERROR` and `unmappedTargetPolicy = ERROR`: the
   build fails until the field is mapped. Declare deliberate exceptions explicitly
   (e.g. `@BeanMapping(ignoreUnmappedSourceProperties = "id")`), never relax the policies.
5. Tests: extend `MovieMapperTest` (all fields both ways) and, if relevant, `MoviesApplicationTests`.
6. If the import file format changes, update the test data in `src/test/resources/data/`.

### Service, transactions and mapping

- `MovieService` methods are the transaction boundary. Mapping happens **inside** them:
  `PersonMapper`/`GenreMapper` find or create rows (a rejected movie must roll them back), and
  entities load actors/genres lazily (`open-in-view` is disabled). Don't put `@Transactional` on
  controllers and don't map entities outside the service.
- The service validates the mapped entity (rules on the entities) for every caller, including the
  importer, because the contract's validation only applies to HTTP requests.
- Errors: throw `MovieNotFoundException` (404), `DuplicateMovieException` (409); validation errors
  become 400 with a field `errors` list (see `ApiExceptionHandler`).

### Database

- Uniqueness is case-insensitive (unique indexes on `lower(...)`): movie name + year, person first +
  last name, genre name.
- Search uses `LIKE '%term%'` with escaped wildcards (`MovieRepository.toContainsPattern`).

### Tests

- Tests never use the local `movies-postgres` database. Database tests use a shared PostgreSQL
  Testcontainers container (`TestcontainersConfiguration`); Docker must be running.
- `MoviesApplicationTests` shares one application and its data across tests: data added by one
  test must not affect another test's expectations (e.g. don't add movies by a director that a
  search test counts).
- `@DataJpaTest` classes (`MovieRepositoryTest`, `MovieMapperTest`) start from an empty database
  inside the rolled-back test transaction.
- Prefer real behaviour over mocks where it matters (real validator in `MovieServiceTest`, real
  database for mappers that use repositories).

### Code style

- Constructor injection (except MapStruct abstract mappers, which need field injection).
- No Lombok. Records where immutability fits.
- Javadoc/comments explain **why**, not what. Match the existing comment density.
- Keep `README.md` and `documentation/plan.md` up to date with every change in behaviour,
  configuration or design (the plan has a checklist per phase and records design decisions).

## Environment notes

- Windows development machine: PowerShell 5.1 and Git Bash. In PowerShell `curl` is an alias for
  `Invoke-WebRequest`; use `curl.exe` or `Invoke-RestMethod`.
- The developer may have the app running on port 8080. For manual checks start a separate instance
  on another port (`--server.port=8081`).
- Manual checks against the local database create real data: clean it up afterwards, and don't
  delete data you didn't create.
- `.env` (database credentials for Docker Compose) is git-ignored; never commit it.
- VS Code's Java extension compiles into the same `target/` folder as Maven. If a build fails with
  "Unresolved compilation problem" (an Eclipse compiler message) or a `NoClassDefFoundError` for one
  of our own classes, run `./mvnw clean verify`.
