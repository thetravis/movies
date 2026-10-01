package com.example.movies.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;

import com.example.movies.domain.Genre;
import com.example.movies.domain.Movie;
import com.example.movies.domain.Person;
import com.example.movies.generated.model.MovieDto;
import com.example.movies.generated.model.MoviePageDto;
import com.example.movies.generated.model.NewMovieDto;
import com.example.movies.generated.model.PersonDto;
import com.example.movies.service.DuplicateMovieException;
import com.example.movies.service.MovieNotFoundException;
import com.example.movies.service.MovieService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Web layer tests for {@link MoviesController} and {@link ApiExceptionHandler}: routing, request
 * validation from the OpenAPI contract, status codes and Problem Details responses. The service is
 * mocked, so no database is needed.
 */
@WebMvcTest(MoviesController.class)
class MoviesControllerTest {

    private static final String VALID_NEW_MOVIE = """
            {"name": "Vertigo", "year": 1958, "genres": ["Mystery"], "ageLimit": 12, "rating": 5,
             "director": {"firstName": "Alfred", "lastName": "Hitchcock"}}""";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MovieService movieService;

    // --- GET /api/movies -----------------------------------------------------------------------

    @Test
    void listMoviesPassesSearchAndPagingToService() {
        when(movieService.listMovies("war", 1, 5)).thenReturn(page(vertigo()));

        assertThat(mvc.get().uri("/api/movies?search=war&page=1&size=5"))
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$.items[0].name").isEqualTo("Vertigo");
    }

    @Test
    void listMoviesUsesDefaultPaging() {
        when(movieService.listMovies(null, 0, 20)).thenReturn(page());

        assertThat(mvc.get().uri("/api/movies")).hasStatusOk();

        verify(movieService).listMovies(null, 0, 20);
    }

    @Test
    void listMoviesRejectsInvalidPagingWithFieldErrors() {
        assertThat(mvc.get().uri("/api/movies?page=-1&size=101"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.errors[*].field").asArray().containsExactly("page", "size");
        verifyNoInteractions(movieService);
    }

    @Test
    void listMoviesRejectsNonNumericPage() {
        assertThat(mvc.get().uri("/api/movies?page=abc"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        verifyNoInteractions(movieService);
    }

    // --- GET /api/movies/{id} ------------------------------------------------------------------

    @Test
    void getMovieReturnsMovie() {
        when(movieService.getMovie(1L)).thenReturn(vertigo());

        assertThat(mvc.get().uri("/api/movies/1"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"id": 1, "name": "Vertigo", "year": 1958, "genres": ["Mystery"],
                         "director": {"firstName": "Alfred", "lastName": "Hitchcock"}}""");
    }

    @Test
    void getMovieReturnsNotFoundProblem() {
        when(movieService.getMovie(99L)).thenThrow(new MovieNotFoundException(99L));

        assertThat(mvc.get().uri("/api/movies/99"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 404, "title": "Not Found", "detail": "Movie with id 99 was not found.",
                         "instance": "/api/movies/99"}""");
    }

    // --- POST /api/movies ----------------------------------------------------------------------

    @Test
    void createMovieReturnsCreatedWithLocation() {
        when(movieService.createMovie(any())).thenReturn(vertigo());

        assertThat(mvc.post().uri("/api/movies").contentType(MediaType.APPLICATION_JSON).content(VALID_NEW_MOVIE))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/api/movies/1")
                .bodyJson()
                .extractingPath("$.id").isEqualTo(1);
        verify(movieService).createMovie(new NewMovieDto("Vertigo", 1958,
                new LinkedHashSet<>(List.of("Mystery")), 12, 5, new PersonDto("Alfred", "Hitchcock")));
    }

    @Test
    void createMovieRejectsMissingRequiredFieldsWithoutCallingService() {
        assertThat(mvc.post().uri("/api/movies").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.errors[*].field").asArray()
                .containsExactly("ageLimit", "director", "genres", "name", "rating", "year");
        verifyNoInteractions(movieService);
    }

    @Test
    void createMovieRejectsMalformedJson() {
        assertThat(mvc.post().uri("/api/movies").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        verifyNoInteractions(movieService);
    }

    @Test
    void createMovieReturnsConflictForDuplicate() {
        when(movieService.createMovie(any())).thenThrow(new DuplicateMovieException("Vertigo", 1958));

        assertThat(mvc.post().uri("/api/movies").contentType(MediaType.APPLICATION_JSON).content(VALID_NEW_MOVIE))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail").isEqualTo("A movie named 'Vertigo' from 1958 already exists.");
    }

    @Test
    void createMovieReturnsFieldErrorsFromServiceValidation() {
        // E.g. a name of only spaces: passes the contract's minLength, but is blank after trimming.
        when(movieService.createMovie(any())).thenThrow(serviceValidationError());

        assertThat(mvc.post().uri("/api/movies").contentType(MediaType.APPLICATION_JSON).content(VALID_NEW_MOVIE))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 400, "detail": "Request validation failed.",
                         "errors": [{"field": "name", "message": "must not be blank"}]}""");
    }

    @Test
    void createMovieHidesDatabaseDetailsOnConstraintViolation() {
        when(movieService.createMovie(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThat(mvc.post().uri("/api/movies").contentType(MediaType.APPLICATION_JSON).content(VALID_NEW_MOVIE))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail").isEqualTo("The request conflicts with existing data.");
    }

    // --- Helpers -------------------------------------------------------------------------------

    private static MovieDto vertigo() {
        return new MovieDto(1L, "Vertigo", 1958, List.of("Mystery"), 12, 5, List.of(),
                new PersonDto("Alfred", "Hitchcock"));
    }

    private static MoviePageDto page(MovieDto... movies) {
        return new MoviePageDto(List.of(movies), 0, 20, (long) movies.length, 1);
    }

    /** A real violation as the service would throw it: a movie whose name is blank. */
    private static ConstraintViolationException serviceValidationError() {
        Movie movie = new Movie(null, 1958, 12, 5, null, new Person("Alfred", "Hitchcock"));
        movie.addGenre(new Genre("Mystery"));
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            return new ConstraintViolationException(factory.getValidator().validate(movie));
        }
    }
}
