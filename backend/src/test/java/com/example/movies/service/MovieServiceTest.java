package com.example.movies.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.example.movies.domain.Genre;
import com.example.movies.domain.Movie;
import com.example.movies.domain.Person;
import com.example.movies.generated.model.MovieDto;
import com.example.movies.generated.model.MoviePageDto;
import com.example.movies.generated.model.NewMovieDto;
import com.example.movies.repository.MovieRepository;
import com.example.movies.service.mapper.MovieMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for {@link MovieService}. Repository and mapper are mocked; validation uses a real
 * Bean Validation validator, so the rules declared on the entities are exercised.
 */
@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    private static ValidatorFactory validatorFactory;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MovieMapper movieMapper;

    private MovieService movieService;

    @BeforeAll
    static void createValidatorFactory() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        movieService = new MovieService(movieRepository, movieMapper, validatorFactory.getValidator());
    }

    @Test
    void validMovieHelperCreatesAValidMovie() {
        // Guards the helper, so that the other tests fail for the reason they test.
        assertThat(validatorFactory.getValidator().validate(validMovie())).isEmpty();
    }

    // --- createMovie ---------------------------------------------------------------------------

    @Test
    void createMovieSavesValidMovieAndReturnsIt() {
        NewMovieDto request = new NewMovieDto();
        Movie movie = validMovie();
        MovieDto saved = new MovieDto().id(1L).name("Vertigo");
        when(movieMapper.toEntity(request)).thenReturn(movie);
        when(movieRepository.existsByNameIgnoreCaseAndYear("Vertigo", 1958)).thenReturn(false);
        when(movieRepository.save(movie)).thenReturn(movie);
        when(movieMapper.toDto(movie)).thenReturn(saved);

        assertThat(movieService.createMovie(request)).isSameAs(saved);
    }

    @Test
    void createMovieRejectsDuplicateWithoutSaving() {
        NewMovieDto request = new NewMovieDto();
        when(movieMapper.toEntity(request)).thenReturn(validMovie());
        when(movieRepository.existsByNameIgnoreCaseAndYear("Vertigo", 1958)).thenReturn(true);

        assertThatThrownBy(() -> movieService.createMovie(request))
                .isInstanceOf(DuplicateMovieException.class)
                .hasMessage("A movie named 'Vertigo' from 1958 already exists.");
        verify(movieRepository, never()).save(any());
    }

    @Test
    void createMovieRejectsInvalidMovieWithoutSaving() {
        NewMovieDto request = new NewMovieDto();
        // Name null (e.g. only spaces, trimmed to null by the mapper), rating out of range,
        // director without last name, no genres.
        Movie invalid = new Movie(null, 1958, 12, 9, null, new Person("Alfred", null));
        when(movieMapper.toEntity(request)).thenReturn(invalid);

        assertThatThrownBy(() -> movieService.createMovie(request))
                .isInstanceOfSatisfying(ConstraintViolationException.class, e ->
                        assertThat(e.getConstraintViolations())
                                .extracting(v -> v.getPropertyPath().toString())
                                .containsExactlyInAnyOrder("name", "rating", "director.lastName", "genres"));
        verify(movieRepository, never()).existsByNameIgnoreCaseAndYear(anyString(), any());
        verify(movieRepository, never()).save(any());
    }

    @Test
    void createMovieValidatesActorsAndGenres() {
        NewMovieDto request = new NewMovieDto();
        Movie movie = validMovie();
        movie.addActor(new Person("James", ""));
        movie.addGenre(new Genre("x".repeat(51)));
        when(movieMapper.toEntity(request)).thenReturn(movie);

        assertThatThrownBy(() -> movieService.createMovie(request))
                .isInstanceOfSatisfying(ConstraintViolationException.class, e ->
                        assertThat(e.getConstraintViolations())
                                .extracting(v -> v.getPropertyPath().toString())
                                .containsExactlyInAnyOrder("actors[0].lastName", "genres[].name"));
        verify(movieRepository, never()).save(any());
    }

    // --- listMovies ----------------------------------------------------------------------------

    @Test
    void listMoviesWithoutSearchTermListsAllMoviesSortedByName() {
        Page<Movie> movies = new PageImpl<>(List.of());
        MoviePageDto dto = new MoviePageDto();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(movieRepository.findAll(pageable.capture())).thenReturn(movies);
        when(movieMapper.toPageDto(movies)).thenReturn(dto);

        assertThat(movieService.listMovies("   ", 2, 10)).isSameAs(dto);

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        Sort.Order byName = pageable.getValue().getSort().getOrderFor("name");
        assertThat(byName).isNotNull();
        assertThat(byName.isAscending()).isTrue();
        assertThat(byName.isIgnoreCase()).isTrue();
        verify(movieRepository, never()).search(anyString(), any());
    }

    @Test
    void listMoviesWithSearchTermSearchesWithLowerCaseEscapedPattern() {
        when(movieRepository.search(anyString(), any())).thenReturn(new PageImpl<>(List.of()));

        movieService.listMovies("  100% Wolf ", 0, 20);

        verify(movieRepository).search(eq("%100!% wolf%"), any());
        verify(movieRepository, never()).findAll(any(Pageable.class));
    }

    // --- getMovie ------------------------------------------------------------------------------

    @Test
    void getMovieReturnsMappedMovie() {
        Movie movie = validMovie();
        MovieDto dto = new MovieDto().name("Vertigo");
        when(movieRepository.findById(7L)).thenReturn(Optional.of(movie));
        when(movieMapper.toDto(movie)).thenReturn(dto);

        assertThat(movieService.getMovie(7L)).isSameAs(dto);
    }

    @Test
    void getMovieThrowsNotFoundForUnknownId() {
        when(movieRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieService.getMovie(7L))
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessage("Movie with id 7 was not found.");
    }

    private static Movie validMovie() {
        Movie movie = new Movie("Vertigo", 1958, 12, 5, "A synopsis.", new Person("Alfred", "Hitchcock"));
        movie.addGenre(new Genre("Mystery"));
        return movie;
    }
}
