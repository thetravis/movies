package com.example.movies.seed;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.movies.generated.model.NewMovieDto;
import com.example.movies.service.DuplicateMovieException;
import com.example.movies.service.MovieService;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Imports movies from a JSON file on application startup (after Liquibase has created the schema).
 * <p>
 * The file is a JSON array of movies in the same format as the {@code NewMovie} schema of the API
 * contract, and each entry is read into the generated {@link NewMovieDto}. The import is safe to
 * run on every startup:
 * <ul>
 *   <li>Movies that already exist (same name and year) are skipped, so restarts don't create
 *       duplicates and a different file adds its movies to the existing ones.</li>
 *   <li>Each movie is saved through {@link MovieService#createMovie}, so the same cleanup and
 *       validation rules apply as for {@code POST /api/movies}. Invalid movies are logged and
 *       skipped; they don't stop the application.</li>
 * </ul>
 * A missing or unreadable file, or a file that is not a JSON array, stops the application: that is
 * a configuration error that should be noticed right away.
 * <p>
 * Configured with {@link MovieSeedProperties}.
 */
@Component
@EnableConfigurationProperties(MovieSeedProperties.class)
public class MovieImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MovieImporter.class);

    /**
     * Known typos in genre names of the provided data file, keyed by lower-case name.
     * Add new entries here if other data files contain similar mistakes.
     */
    private static final Map<String, String> GENRE_CORRECTIONS = Map.of("familty", "Family");

    private final MovieSeedProperties properties;
    private final MovieService movieService;
    private final JsonMapper jsonMapper;

    public MovieImporter(MovieSeedProperties properties, MovieService movieService, JsonMapper jsonMapper) {
        this.properties = properties;
        this.movieService = movieService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("Movie import is disabled (movies.seed.enabled=false).");
            return;
        }
        importMovies(properties.file());
    }

    /**
     * Imports all movies from the given JSON file.
     *
     * @return how many movies were imported, skipped as existing and rejected as invalid
     * @throws IllegalStateException if the file can't be read or is not a JSON array
     */
    public ImportResult importMovies(Resource file) {
        log.info("Importing movies from {}", file.getDescription());
        JsonNode movies = readArray(file);

        int imported = 0;
        int existing = 0;
        int rejected = 0;
        for (int i = 0; i < movies.size(); i++) {
            JsonNode json = movies.get(i);
            String label = "#" + (i + 1) + " '" + json.path("name").asString("?") + "'";
            try {
                movieService.createMovie(correctKnownTypos(jsonMapper.treeToValue(json, NewMovieDto.class)));
                imported++;
            } catch (DuplicateMovieException e) {
                log.debug("Skipping movie {}: already exists", label);
                existing++;
            } catch (ConstraintViolationException e) {
                log.warn("Skipping invalid movie {}: {}", label, describe(e));
                rejected++;
            } catch (JacksonException e) {
                log.warn("Skipping invalid movie {}: {}", label, e.getOriginalMessage());
                rejected++;
            }
        }

        ImportResult result = new ImportResult(imported, existing, rejected);
        log.info("Movie import finished: {} imported, {} already existed, {} invalid.",
                result.imported(), result.existing(), result.rejected());
        return result;
    }

    private JsonNode readArray(Resource file) {
        JsonNode root;
        try (InputStream in = file.getInputStream()) {
            root = jsonMapper.readTree(in);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Cannot read movie file " + file.getDescription()
                    + " (check movies.seed.file): " + e.getMessage(), e);
        }
        if (root == null || !root.isArray()) {
            throw new IllegalStateException("Movie file " + file.getDescription() + " must contain a JSON array.");
        }
        return root;
    }

    private static NewMovieDto correctKnownTypos(NewMovieDto movie) {
        if (movie.getGenres() == null) {
            return movie;
        }
        Set<String> genres = new LinkedHashSet<>();
        for (String genre : movie.getGenres()) {
            String key = genre == null ? null : genre.trim().toLowerCase(Locale.ROOT);
            genres.add(GENRE_CORRECTIONS.getOrDefault(key, genre));
        }
        return movie.genres(genres);
    }

    private static String describe(ConstraintViolationException e) {
        return e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    /** Outcome of an import. */
    public record ImportResult(int imported, int existing, int rejected) {
    }
}
