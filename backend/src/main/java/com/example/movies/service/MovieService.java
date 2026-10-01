package com.example.movies.service;

import java.util.Set;

import com.example.movies.domain.Movie;
import com.example.movies.generated.model.MovieDto;
import com.example.movies.generated.model.MoviePageDto;
import com.example.movies.generated.model.NewMovieDto;
import com.example.movies.repository.MovieRepository;
import com.example.movies.service.mapper.MovieMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Use cases for movies: list, search, get and add. Used by the REST controller and the data
 * importer.
 * <p>
 * Each method is one transaction, and mapping between the API DTOs and the entities happens
 * inside it ({@link MovieMapper}): adding a movie may create persons and genres, which must be
 * rolled back if the movie is rejected, and the entities' actors and genres are loaded lazily
 * (open-in-view is disabled).
 */
@Service
@Transactional(readOnly = true)
public class MovieService {

    /** Movies are listed by name (case-insensitive), then year; id makes the order stable for paging. */
    private static final Sort LIST_ORDER = Sort.by(
            Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("year"), Sort.Order.asc("id"));

    private final MovieRepository movieRepository;
    private final MovieMapper movieMapper;
    private final Validator validator;

    public MovieService(MovieRepository movieRepository, MovieMapper movieMapper, Validator validator) {
        this.movieRepository = movieRepository;
        this.movieMapper = movieMapper;
        this.validator = validator;
    }

    /**
     * Lists movies one page at a time. A blank or missing search term lists all movies; otherwise
     * see {@link MovieRepository#search} for what is matched.
     */
    public MoviePageDto listMovies(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, LIST_ORDER);
        Page<Movie> movies = StringUtils.hasText(search)
                ? movieRepository.search(MovieRepository.toContainsPattern(search), pageable)
                : movieRepository.findAll(pageable);
        return movieMapper.toPageDto(movies);
    }

    /**
     * @throws MovieNotFoundException if there is no movie with the id
     */
    public MovieDto getMovie(Long id) {
        return movieRepository.findById(id)
                .map(movieMapper::toDto)
                .orElseThrow(() -> new MovieNotFoundException(id));
    }

    /**
     * Adds a new movie.
     * <ul>
     *   <li>Text is trimmed; repeated actors and genres (case-insensitive) are kept only once;
     *       existing persons and genres are reused (see {@link MovieMapper}).</li>
     *   <li>The movie is validated against the rules declared on the {@link Movie} entity, so this
     *       is safe to call from places other than the REST controller (e.g. the data importer).</li>
     * </ul>
     * If the movie is rejected, the transaction is rolled back, including persons and genres
     * created for it.
     *
     * @throws ConstraintViolationException if the movie is invalid
     * @throws DuplicateMovieException      if a movie with the same name and year already exists
     */
    @Transactional
    public MovieDto createMovie(NewMovieDto newMovie) {
        Movie movie = movieMapper.toEntity(newMovie);
        validate(movie);

        if (movieRepository.existsByNameIgnoreCaseAndYear(movie.getName(), movie.getYear())) {
            throw new DuplicateMovieException(movie.getName(), movie.getYear());
        }
        return movieMapper.toDto(movieRepository.save(movie));
    }

    private void validate(Movie movie) {
        Set<ConstraintViolation<Movie>> violations = validator.validate(movie);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
