package com.example.movies.service.mapper;

import com.example.movies.domain.Genre;
import com.example.movies.repository.GenreRepository;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

/**
 * Maps genres between the API (plain names) and the database. Used by {@link MovieMapper}.
 * <p>
 * Hand-written: a genre is just its name, so there is nothing for MapStruct to generate.
 * {@link #toEntity} reads and writes the database: call it only inside a transaction
 * ({@code MovieService}).
 */
@Component
public class GenreMapper {

    private final GenreRepository genreRepository;
    private final Validator validator;

    public GenreMapper(GenreRepository genreRepository, Validator validator) {
        this.genreRepository = genreRepository;
        this.validator = validator;
    }

    public String toName(Genre genre) {
        return genre.getName();
    }

    /**
     * Returns the existing genre with this name (case-insensitive), or saves and returns a new one.
     * The name is trimmed. An invalid genre (e.g. blank) is returned unsaved, so that validating the
     * movie reports the error.
     */
    public Genre toEntity(String name) {
        Genre genre = new Genre(TextMapper.trimToNull(name));
        if (!validator.validate(genre).isEmpty()) {
            return genre;
        }
        return genreRepository.findByNameIgnoreCase(genre.getName())
                .orElseGet(() -> genreRepository.save(genre));
    }
}
