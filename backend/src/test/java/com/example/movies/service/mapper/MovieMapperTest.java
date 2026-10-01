package com.example.movies.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.example.movies.TestcontainersConfiguration;
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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Tests the MapStruct mappers (generated {@code MovieMapperImpl} and {@code PersonMapperImpl}
 * together with {@link GenreMapper}). {@link PersonMapper} and {@link GenreMapper} look up and save
 * rows, so this runs against the PostgreSQL test container; every test is rolled back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, MovieMapperImpl.class, PersonMapperImpl.class, GenreMapper.class})
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
class MovieMapperTest {

    @Autowired
    private MovieMapper movieMapper;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void startFromEmptyDatabase() {
        // Inside the test transaction, so data imported by other test classes is restored afterwards.
        movieRepository.deleteAllInBatch();
        personRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();
    }

    // --- NewMovieDto → Movie -------------------------------------------------------------------

    @Test
    void toEntityCopiesAllFieldsAndTrimsText() {
        NewMovieDto dto = newMovie("  Vertigo ", "Alfred", " Hitchcock ", "Mystery")
                .synopsis("  A detective with vertigo.  ")
                .actors(List.of(new PersonDto(" James", "Stewart ")));

        Movie movie = movieMapper.toEntity(dto);

        assertThat(movie.getName()).isEqualTo("Vertigo");
        assertThat(movie.getYear()).isEqualTo(1958);
        assertThat(movie.getAgeLimit()).isEqualTo(12);
        assertThat(movie.getRating()).isEqualTo(5);
        assertThat(movie.getSynopsis()).isEqualTo("A detective with vertigo.");
        assertThat(movie.getDirector().getLastName()).isEqualTo("Hitchcock");
        assertThat(movie.getActors()).extracting(Person::getFirstName, Person::getLastName)
                .containsExactly(tuple("James", "Stewart"));
        assertThat(movie.getGenres()).extracting(Genre::getName).containsExactly("Mystery");
    }

    @Test
    void toEntityTurnsBlankTextIntoNull() {
        Movie movie = movieMapper.toEntity(newMovie("   ", "Alfred", "Hitchcock", "Mystery").synopsis("  "));

        assertThat(movie.getName()).isNull();
        assertThat(movie.getSynopsis()).isNull();
    }

    @Test
    void toEntityReusesExistingPersonsAndGenresIgnoringCase() {
        Person hitchcock = personRepository.save(new Person("Alfred", "Hitchcock"));
        Genre mystery = genreRepository.save(new Genre("Mystery"));

        Movie movie = movieMapper.toEntity(newMovie("Vertigo", "ALFRED", "hitchcock", "mystery"));

        assertThat(movie.getDirector()).isSameAs(hitchcock);
        assertThat(movie.getGenres()).containsExactly(mystery);
        assertThat(personRepository.count()).isEqualTo(1);
        assertThat(genreRepository.count()).isEqualTo(1);
    }

    @Test
    void toEntitySavesNewPersonsAndGenres() {
        Movie movie = movieMapper.toEntity(newMovie("Vertigo", "Alfred", "Hitchcock", "Mystery")
                .actors(List.of(new PersonDto("James", "Stewart"))));

        assertThat(movie.getDirector().getId()).isNotNull();
        assertThat(movie.getActors().getFirst().getId()).isNotNull();
        assertThat(movie.getGenres().iterator().next().getId()).isNotNull();
        assertThat(personRepository.count()).isEqualTo(2);
        assertThat(genreRepository.count()).isEqualTo(1);
    }

    @Test
    void toEntityKeepsRepeatedActorsAndGenresOnlyOnce() {
        NewMovieDto dto = newMovie("Vertigo", "Alfred", "Hitchcock", "Mystery")
                .genres(new LinkedHashSet<>(List.of("Mystery", "mystery ", "Thriller")))
                .actors(List.of(new PersonDto("James", "Stewart"), new PersonDto("james ", " STEWART"),
                        new PersonDto("Kim", "Novak")));

        Movie movie = movieMapper.toEntity(dto);

        assertThat(movie.getActors()).extracting(Person::getFirstName).containsExactly("James", "Kim");
        assertThat(movie.getGenres()).extracting(Genre::getName).containsExactlyInAnyOrder("Mystery", "Thriller");
    }

    @Test
    void toEntityDoesNotSaveInvalidPersonsOrGenres() {
        Movie movie = movieMapper.toEntity(newMovie("Vertigo", "  ", "Hitchcock", "   "));

        assertThat(movie.getDirector().getId()).as("invalid person left unsaved").isNull();
        assertThat(movie.getDirector().getFirstName()).isNull();
        assertThat(movie.getGenres()).extracting(Genre::getId).as("invalid genre left unsaved").containsOnlyNulls();
        assertThat(personRepository.count()).isZero();
        assertThat(genreRepository.count()).isZero();
    }

    // --- Movie → MovieDto ----------------------------------------------------------------------

    @Test
    void toDtoCopiesAllFieldsFromStoredMovie() {
        Movie saved = movieRepository.save(movieMapper.toEntity(
                newMovie("Vertigo", "Alfred", "Hitchcock", "Thriller")
                        .genres(new LinkedHashSet<>(List.of("Thriller", "Mystery")))
                        .synopsis("A detective with vertigo.")
                        .actors(List.of(new PersonDto("James", "Stewart"), new PersonDto("Kim", "Novak")))));
        entityManager.flush();
        entityManager.clear();

        MovieDto dto = movieMapper.toDto(movieRepository.findById(saved.getId()).orElseThrow());

        assertThat(dto).isEqualTo(new MovieDto()
                .id(saved.getId())
                .name("Vertigo")
                .year(1958)
                .genres(List.of("Mystery", "Thriller"))
                .ageLimit(12)
                .rating(5)
                .actors(List.of(new PersonDto("James", "Stewart"), new PersonDto("Kim", "Novak")))
                .director(new PersonDto("Alfred", "Hitchcock"))
                .synopsis("A detective with vertigo."));
    }

    @Test
    void toPageDtoCopiesPagingFields() {
        Movie movie = movieRepository.save(movieMapper.toEntity(newMovie("Vertigo", "Alfred", "Hitchcock", "Mystery")));

        MoviePageDto page = movieMapper.toPageDto(new PageImpl<>(List.of(movie), PageRequest.of(1, 2), 5));

        assertThat(page.getItems()).extracting(MovieDto::getName).containsExactly("Vertigo");
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getTotalItems()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    @Test
    void toPageDtoReturnsEmptyItemsForEmptyPage() {
        MoviePageDto page = movieMapper.toPageDto(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getTotalItems()).isZero();
    }

    private static NewMovieDto newMovie(String name, String directorFirstName, String directorLastName, String genre) {
        return new NewMovieDto(name, 1958, new LinkedHashSet<>(Set.of(genre)), 12, 5,
                new PersonDto(directorFirstName, directorLastName));
    }
}
