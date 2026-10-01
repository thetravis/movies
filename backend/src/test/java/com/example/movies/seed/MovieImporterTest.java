package com.example.movies.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.example.movies.generated.model.NewMovieDto;
import com.example.movies.seed.MovieImporter.ImportResult;
import com.example.movies.service.DuplicateMovieException;
import com.example.movies.service.MovieService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import tools.jackson.databind.json.JsonMapper;

/** Unit tests for {@link MovieImporter}; the service is mocked. */
@ExtendWith(MockitoExtension.class)
class MovieImporterTest {

    private static final String VALID_MOVIE = """
            {"name": "Vertigo", "year": 1958, "genres": ["Mystery"], "ageLimit": 12, "rating": 5,
             "director": {"firstName": "Alfred", "lastName": "Hitchcock"}}""";

    @Mock
    private MovieService movieService;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void countsImportedExistingAndInvalidMovies() {
        when(movieService.createMovie(any()))
                .thenReturn(null)                                                   // 1st: imported
                .thenThrow(new DuplicateMovieException("Vertigo", 1958))            // 2nd: exists
                .thenThrow(new ConstraintViolationException("invalid", Set.of()));  // 3rd: invalid
        String json = "[" + VALID_MOVIE + "," + VALID_MOVIE + "," + VALID_MOVIE + ","
                + """
                {"name": "Wrong Types", "year": "soon"}""" + "]";

        ImportResult result = importer(true).importMovies(resource(json));

        assertThat(result).isEqualTo(new ImportResult(1, 1, 2));
        verify(movieService, times(3)).createMovie(any());
    }

    @Test
    void correctsKnownGenreTyposBeforeSaving() {
        String json = """
                [{"name": "The Dark Crystal", "year": 1982, "genres": ["Adventure", " familty", "Fantasy"],
                  "ageLimit": 7, "rating": 4, "director": {"firstName": "Jim", "lastName": "Henson"}}]""";

        importer(true).importMovies(resource(json));

        ArgumentCaptor<NewMovieDto> movie = ArgumentCaptor.forClass(NewMovieDto.class);
        verify(movieService).createMovie(movie.capture());
        assertThat(movie.getValue().getGenres()).containsExactly("Adventure", "Family", "Fantasy");
    }

    @Test
    void readsTheProvidedDataFile() {
        ImportResult result = importer(true).importMovies(new ClassPathResource("data/movies-test.json"));

        assertThat(result).isEqualTo(new ImportResult(20, 0, 0));
    }

    @Test
    void failsWhenFileIsMissing() {
        Resource missing = new ClassPathResource("data/does-not-exist.json");

        assertThatThrownBy(() -> importer(true).importMovies(missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot read movie file")
                .hasMessageContaining("movies.seed.file");
    }

    @Test
    void failsWhenFileIsNotAJsonArray() {
        assertThatThrownBy(() -> importer(true).importMovies(resource(VALID_MOVIE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must contain a JSON array");
    }

    @Test
    void doesNothingWhenDisabled() {
        importer(false).run(new DefaultApplicationArguments());

        verify(movieService, never()).createMovie(any());
    }

    private MovieImporter importer(boolean enabled) {
        MovieSeedProperties properties = new MovieSeedProperties(enabled, resource("[]"));
        return new MovieImporter(properties, movieService, jsonMapper);
    }

    private static Resource resource(String json) {
        return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8), "test JSON");
    }
}
