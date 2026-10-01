package com.example.movies.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.example.movies.TestcontainersConfiguration;
import com.example.movies.domain.Genre;
import com.example.movies.domain.Movie;
import com.example.movies.domain.Person;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Runs against the real PostgreSQL schema created by Liquibase, in the PostgreSQL test container
 * (see {@link TestcontainersConfiguration}). Every test runs in a transaction that is rolled back,
 * so data added by other test classes is not changed.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // use the container, not an in-memory DB
@Import(TestcontainersConfiguration.class)
class MovieRepositoryTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20, Sort.by("name"));

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private EntityManager entityManager;

    private Person robert;
    private Person chris;
    private Genre adventure;
    private Genre drama;

    @BeforeEach
    void setUp() {
        // Start from an empty database inside the test transaction (rolled back afterwards).
        movieRepository.deleteAllInBatch();
        personRepository.deleteAllInBatch();
        genreRepository.deleteAllInBatch();

        robert = personRepository.save(new Person("Robert", "Downey Jr."));
        chris = personRepository.save(new Person("Chris", "Evans"));
        adventure = genreRepository.save(new Genre("Adventure"));
        drama = genreRepository.save(new Genre("Drama"));
    }

    @Test
    void searchMatchesNameCaseInsensitively() {
        saveMovie("Avengers: Endgame", 2019, "Heroes assemble.", "Anthony", "Russo", adventure);
        saveMovie("Ad Astra", 2019, "A trip to Neptune.", "James", "Gray", drama);

        assertThat(searchNames("ENDGAME")).containsExactly("Avengers: Endgame");
    }

    @Test
    void searchMatchesSynopsisDirectorActorAndGenre() {
        Movie endgame = saveMovie("Avengers: Endgame", 2019, "Heroes assemble.", "Anthony", "Russo", adventure);
        endgame.addActor(robert);
        saveMovie("Ad Astra", 2019, "A trip to Neptune.", "James", "Gray", drama);
        entityManager.flush();

        assertThat(searchNames("neptune")).as("synopsis").containsExactly("Ad Astra");
        assertThat(searchNames("anthony russo")).as("director full name").containsExactly("Avengers: Endgame");
        assertThat(searchNames("robert downey")).as("actor full name").containsExactly("Avengers: Endgame");
        assertThat(searchNames("drama")).as("genre").containsExactly("Ad Astra");
    }

    @Test
    void searchReturnsMovieOnceWhenSeveralActorsMatch() {
        Movie endgame = saveMovie("Avengers: Endgame", 2019, "Heroes assemble.", "Anthony", "Russo", adventure);
        endgame.addActor(robert);
        endgame.addActor(chris);
        entityManager.flush();

        Page<Movie> page = movieRepository.search(MovieRepository.toContainsPattern("r"), FIRST_PAGE);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(Movie::getName).containsExactly("Avengers: Endgame");
    }

    @Test
    void searchTreatsLikeWildcardsAsLiteralText() {
        saveMovie("Avengers: Endgame", 2019, "Heroes assemble.", "Anthony", "Russo", adventure);
        saveMovie("100% Wolf", 2020, "A werewolf story.", "Alexs", "Stadermann", adventure);

        assertThat(searchNames("%")).containsExactly("100% Wolf");
        assertThat(searchNames("_")).isEmpty();
    }

    @Test
    void searchPagesResults() {
        saveMovie("Movie A", 2001, null, "Jane", "Doe", drama);
        saveMovie("Movie B", 2002, null, "Jane", "Doe", drama);
        saveMovie("Movie C", 2003, null, "Jane", "Doe", drama);

        Page<Movie> page = movieRepository.search(MovieRepository.toContainsPattern("movie"),
                PageRequest.of(1, 2, Sort.by("name")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Movie::getName).containsExactly("Movie C");
    }

    @Test
    void keepsActorBillingOrderAndSortsGenresByName() {
        Movie endgame = saveMovie("Avengers: Endgame", 2019, "Heroes assemble.", "Anthony", "Russo", drama);
        endgame.addGenre(adventure);
        endgame.addActor(robert);
        endgame.addActor(chris);
        entityManager.flush();
        entityManager.clear();

        Movie loaded = movieRepository.findById(endgame.getId()).orElseThrow();

        assertThat(loaded.getActors()).extracting(Person::getFirstName).containsExactly("Robert", "Chris");
        assertThat(loaded.getGenres()).extracting(Genre::getName).containsExactly("Adventure", "Drama");
    }

    @Test
    void existsByNameIgnoreCaseAndYear() {
        saveMovie("Avengers: Endgame", 2019, null, "Anthony", "Russo", adventure);

        assertThat(movieRepository.existsByNameIgnoreCaseAndYear("avengers: endgame", 2019)).isTrue();
        assertThat(movieRepository.existsByNameIgnoreCaseAndYear("Avengers: Endgame", 2018)).isFalse();
    }

    @Test
    void databaseRejectsDuplicateNameAndYear() {
        saveMovie("Avengers: Endgame", 2019, null, "Anthony", "Russo", adventure);

        assertThatThrownBy(() -> saveMovie("AVENGERS: ENDGAME", 2019, null, "Anthony", "Russo", adventure))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Movie saveMovie(String name, int year, String synopsis,
                            String directorFirstName, String directorLastName, Genre genre) {
        Person director = personRepository
                .findByFirstNameIgnoreCaseAndLastNameIgnoreCase(directorFirstName, directorLastName)
                .orElseGet(() -> personRepository.save(new Person(directorFirstName, directorLastName)));
        Movie movie = new Movie(name, year, 12, 4, synopsis, director);
        movie.addGenre(genre);
        return movieRepository.saveAndFlush(movie);
    }

    private List<String> searchNames(String term) {
        return movieRepository.search(MovieRepository.toContainsPattern(term), FIRST_PAGE)
                .map(Movie::getName)
                .getContent();
    }
}
