package com.example.movies;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.example.movies.generated.model.MovieDto;
import com.example.movies.generated.model.MoviePageDto;
import com.example.movies.generated.model.NewMovieDto;
import com.example.movies.generated.model.PersonDto;
import com.example.movies.seed.MovieImporter;
import com.example.movies.seed.MovieImporter.ImportResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Full-stack tests: the whole application runs on a random port against a PostgreSQL test
 * container (see {@link TestcontainersConfiguration}) and is called over HTTP.
 * <p>
 * On startup the application imports {@code src/test/resources/data/movies-test.json} (20 movies).
 * The application context, and therefore the data, is shared by all tests in this class, so tests
 * that add movies use names that don't affect the other tests' expectations.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "movies.seed.file=classpath:data/movies-test.json")
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class MoviesApplicationTests {

    private static final int MOVIES_IN_TEST_FILE = 20;

    @Autowired
    private RestTestClient client;

    @Autowired
    private MovieImporter importer;

    @Value("${movies.seed.file}")
    private Resource testFile;

    // --- Import --------------------------------------------------------------------------------

    @Test
    void importsTestFileOnStartupAndSkipsExistingMoviesOnSecondRun() {
        ImportResult secondRun = importer.importMovies(testFile);

        assertThat(secondRun).isEqualTo(new ImportResult(0, MOVIES_IN_TEST_FILE, 0));
    }

    @Test
    void importCleansUpTheData() {
        MovieDto darkCrystal = search("dark crystal").getItems().getFirst();
        MovieDto avengers = search("avengers").getItems().getFirst();

        assertThat(darkCrystal.getGenres())
                .as("genre typo 'Familty' corrected, genres sorted by name")
                .containsExactly("Adventure", "Family", "Fantasy");
        assertThat(darkCrystal.getActors().getFirst())
                .as("director who also acts is the same person")
                .isEqualTo(darkCrystal.getDirector());
        assertThat(avengers.getActors())
                .as("names trimmed, billing order kept")
                .extracting(PersonDto::getLastName)
                .containsExactly("Downey Jr.", "Evans", "Johansson");
    }

    // --- List and search -----------------------------------------------------------------------

    @Test
    void listsMoviesSortedByNameOnePageAtATime() {
        MoviePageDto page = getPage("/api/movies?page=0&size=5");

        assertThat(page.getPage()).isZero();
        assertThat(page.getSize()).isEqualTo(5);
        assertThat(page.getItems()).hasSize(5);
        assertThat(page.getTotalItems()).isGreaterThanOrEqualTo(MOVIES_IN_TEST_FILE);
        assertThat(page.getTotalPages()).isEqualTo((int) Math.ceil(page.getTotalItems() / 5.0));
        assertThat(page.getItems()).extracting(MovieDto::getName)
                .isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    void searchMatchesSynopsisActorDirectorAndGenre() {
        assertThat(searchNames("WALLACE")).as("synopsis, case-insensitive")
                .containsExactly("The Curse of the Were-Rabbit");
        assertThat(searchNames("harrison ford")).as("actor full name")
                .containsExactly("Blade Runner 2049", "Raiders of the Lost Ark");
        assertThat(searchNames("spielberg")).as("director")
                .containsExactly("Raiders of the Lost Ark", "Saving Private Ryan");
        assertThat(searchNames("fantasy")).as("genre")
                .containsExactly("Gremlins", "The Dark Crystal", "The Shape of Water");
        assertThat(searchNames("no such movie")).isEmpty();
    }

    @Test
    void rejectsInvalidPagingParameters() {
        client.get().uri("/api/movies?size=500")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("size");
    }

    // --- Get -----------------------------------------------------------------------------------

    @Test
    void getsMovieById() {
        MovieDto vertigo = search("vertigo").getItems().getFirst();

        MovieDto fetched = client.get().uri("/api/movies/{id}", vertigo.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(MovieDto.class)
                .returnResult().getResponseBody();

        assertThat(fetched).isEqualTo(vertigo);
    }

    @Test
    void returnsNotFoundProblemForUnknownId() {
        client.get().uri("/api/movies/{id}", Long.MAX_VALUE)
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.instance").isEqualTo("/api/movies/" + Long.MAX_VALUE);
    }

    // --- Create --------------------------------------------------------------------------------

    @Test
    void createsMovieThatCanBeFetchedFromLocation() {
        NewMovieDto newMovie = new NewMovieDto("  The Abyss ", 1989, Set.of("Sci-fi"), 12, 4,
                new PersonDto("james", "cameron"))
                .actors(List.of(new PersonDto("Ed", "Harris")));

        var result = client.post().uri("/api/movies")
                .contentType(MediaType.APPLICATION_JSON)
                .body(newMovie)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(MovieDto.class)
                .returnResult();
        MovieDto created = result.getResponseBody();
        URI location = result.getResponseHeaders().getLocation();

        assertThat(created.getName()).isEqualTo("The Abyss");
        assertThat(created.getDirector()).as("existing director reused")
                .isEqualTo(new PersonDto("James", "Cameron"));
        assertThat(location).hasPath("/api/movies/" + created.getId());
        client.get().uri(location)
                .exchange()
                .expectStatus().isOk()
                .expectBody(MovieDto.class).isEqualTo(created);
    }

    @Test
    void rejectsDuplicateMovieWithConflict() {
        NewMovieDto duplicate = new NewMovieDto("VERTIGO", 1958, Set.of("Mystery"), 12, 5,
                new PersonDto("Alfred", "Hitchcock"));

        client.post().uri("/api/movies")
                .contentType(MediaType.APPLICATION_JSON)
                .body(duplicate)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("A movie named 'VERTIGO' from 1958 already exists.");
    }

    @Test
    void rejectsInvalidMovieWithFieldErrors() {
        NewMovieDto invalid = new NewMovieDto("Invalid", 1500, Set.of("Drama"), 12, 9,
                new PersonDto("Jane", "Doe"));

        client.post().uri("/api/movies")
                .contentType(MediaType.APPLICATION_JSON)
                .body(invalid)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.errors[*].field").isEqualTo(List.of("rating", "year"));
    }

    // --- API documentation ---------------------------------------------------------------------

    @Test
    void servesContractAndSwaggerUi() {
        client.get().uri("/openapi.yaml")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).value(body -> assertThat(body).contains("title: Movies API"));
        client.get().uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk();
    }

    // --- Helpers -------------------------------------------------------------------------------

    private MoviePageDto search(String term) {
        return toPage(client.get().uri("/api/movies?search={term}", term));
    }

    private List<String> searchNames(String term) {
        return search(term).getItems().stream().map(MovieDto::getName).toList();
    }

    private MoviePageDto getPage(String uri) {
        return toPage(client.get().uri(uri));
    }

    private MoviePageDto toPage(RestTestClient.RequestHeadersSpec<?> request) {
        return request.exchange()
                .expectStatus().isOk()
                .expectBody(MoviePageDto.class)
                .returnResult().getResponseBody();
    }
}
