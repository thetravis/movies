package com.example.movies.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.example.movies.domain.Genre;
import com.example.movies.domain.Movie;
import com.example.movies.domain.Person;
import com.example.movies.generated.model.MovieDto;
import com.example.movies.generated.model.MoviePageDto;
import com.example.movies.generated.model.NewMovieDto;
import com.example.movies.generated.model.PersonDto;
import com.example.movies.repository.GenreRepository;
import com.example.movies.repository.MovieRepository;
import com.example.movies.repository.PersonRepository;
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
 * Use cases of the Movies API: list, search, get and add movies.
 * <p>
 * Methods return API DTOs because the mapping must happen inside the transaction
 * (actors and genres are loaded lazily and open-in-view is disabled).
 */
@Service
@Transactional(readOnly = true)
public class MovieService {

    /** Movies are listed by name (case-insensitive), then year; id makes the order stable for paging. */
    private static final Sort LIST_ORDER = Sort.by(
            Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("year"), Sort.Order.asc("id"));

    private final MovieRepository movieRepository;
    private final PersonRepository personRepository;
    private final GenreRepository genreRepository;
    private final MovieMapper movieMapper;
    private final Validator validator;

    public MovieService(MovieRepository movieRepository, PersonRepository personRepository,
                        GenreRepository genreRepository, MovieMapper movieMapper, Validator validator) {
        this.movieRepository = movieRepository;
        this.personRepository = personRepository;
        this.genreRepository = genreRepository;
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

    public MovieDto getMovie(Long id) {
        return movieRepository.findById(id)
                .map(movieMapper::toDto)
                .orElseThrow(() -> new MovieNotFoundException(id));
    }

    /**
     * Adds a new movie.
     * <ul>
     *   <li>Text values are trimmed, a blank synopsis is stored as empty (null), and repeated
     *       actors or genres (case-insensitive) are kept only once.</li>
     *   <li>The trimmed input is validated against the constraints of the API contract, so this
     *       method is safe to call also from places other than the REST controller (e.g. the data
     *       importer).</li>
     *   <li>Existing persons and genres are reused (matched case-insensitively).</li>
     * </ul>
     *
     * @throws ConstraintViolationException if the input is invalid
     * @throws DuplicateMovieException      if a movie with the same name and year already exists
     */
    @Transactional
    public MovieDto createMovie(NewMovieDto input) {
        NewMovieDto newMovie = normalize(input);
        validate(newMovie);

        if (movieRepository.existsByNameIgnoreCaseAndYear(newMovie.getName(), newMovie.getYear())) {
            throw new DuplicateMovieException(newMovie.getName(), newMovie.getYear());
        }

        Movie movie = new Movie(newMovie.getName(), newMovie.getYear(), newMovie.getAgeLimit(),
                newMovie.getRating(), newMovie.getSynopsis(), findOrCreatePerson(newMovie.getDirector()));
        newMovie.getActors().forEach(actor -> movie.addActor(findOrCreatePerson(actor)));
        newMovie.getGenres().forEach(genre -> movie.addGenre(findOrCreateGenre(genre)));

        return movieMapper.toDto(movieRepository.save(movie));
    }

    private Person findOrCreatePerson(PersonDto person) {
        return personRepository
                .findByFirstNameIgnoreCaseAndLastNameIgnoreCase(person.getFirstName(), person.getLastName())
                .orElseGet(() -> personRepository.save(new Person(person.getFirstName(), person.getLastName())));
    }

    private Genre findOrCreateGenre(String name) {
        return genreRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> genreRepository.save(new Genre(name)));
    }

    private void validate(NewMovieDto newMovie) {
        Set<ConstraintViolation<NewMovieDto>> violations = validator.validate(newMovie);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    /** Returns a trimmed copy of the input without repeated actors or genres. Null-safe. */
    private static NewMovieDto normalize(NewMovieDto input) {
        String synopsis = trim(input.getSynopsis());

        Map<String, PersonDto> actors = new LinkedHashMap<>();
        if (input.getActors() != null) {
            input.getActors().stream()
                    .filter(Objects::nonNull)
                    .map(MovieService::normalize)
                    .forEach(actor -> actors.putIfAbsent(personKey(actor), actor));
        }

        Map<String, String> genres = new LinkedHashMap<>();
        if (input.getGenres() != null) {
            input.getGenres().stream()
                    .filter(Objects::nonNull)
                    .map(MovieService::trim)
                    .forEach(genre -> genres.putIfAbsent(genre.toLowerCase(Locale.ROOT), genre));
        }

        return new NewMovieDto()
                .name(trim(input.getName()))
                .year(input.getYear())
                .genres(new LinkedHashSet<>(genres.values()))
                .ageLimit(input.getAgeLimit())
                .rating(input.getRating())
                .actors(new ArrayList<>(actors.values()))
                .director(input.getDirector() == null ? null : normalize(input.getDirector()))
                .synopsis(StringUtils.hasText(synopsis) ? synopsis : null);
    }

    private static PersonDto normalize(PersonDto person) {
        return new PersonDto(trim(person.getFirstName()), trim(person.getLastName()));
    }

    private static String personKey(PersonDto person) {
        return (person.getFirstName() + " " + person.getLastName()).toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
