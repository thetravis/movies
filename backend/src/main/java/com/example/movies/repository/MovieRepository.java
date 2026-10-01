package com.example.movies.repository;

import com.example.movies.domain.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Movies. Actors and genres are loaded lazily in batches (see {@code default_batch_fetch_size}),
 * so a page of movies needs only a few queries in total.
 */
public interface MovieRepository extends JpaRepository<Movie, Long> {

    /** Escape character used in {@link #search} LIKE patterns; see {@link #toContainsPattern}. */
    char LIKE_ESCAPE = '!';

    @Override
    @EntityGraph(attributePaths = "director")
    Page<Movie> findAll(Pageable pageable);

    /**
     * Finds movies whose name, synopsis, director, any actor or any genre contains the pattern.
     * Person names are matched as "first last", so "robert downey" matches Robert Downey Jr.
     *
     * @param pattern lower-case LIKE pattern; build it with {@link #toContainsPattern(String)}
     */
    @EntityGraph(attributePaths = "director")
    @Query("""
            select m from Movie m join m.director d
            where lower(m.name) like :pattern escape '!'
               or lower(m.synopsis) like :pattern escape '!'
               or lower(concat(d.firstName, ' ', d.lastName)) like :pattern escape '!'
               or exists (select 1 from m.actors a
                          where lower(concat(a.firstName, ' ', a.lastName)) like :pattern escape '!')
               or exists (select 1 from m.genres g
                          where lower(g.name) like :pattern escape '!')
            """)
    Page<Movie> search(@Param("pattern") String pattern, Pageable pageable);

    boolean existsByNameIgnoreCaseAndYear(String name, Integer year);

    /**
     * Turns a user search term into a case-insensitive "contains" pattern for {@link #search}.
     * LIKE wildcards in the term are escaped, so "100%" matches the literal text "100%".
     */
    static String toContainsPattern(String term) {
        String escaped = term.trim().toLowerCase()
                .replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + "" + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped + "%";
    }
}
