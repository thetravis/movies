package com.example.movies.service;

import java.util.List;

import com.example.movies.domain.Genre;
import com.example.movies.domain.Movie;
import com.example.movies.domain.Person;
import com.example.movies.generated.model.MovieDto;
import com.example.movies.generated.model.MoviePageDto;
import com.example.movies.generated.model.PersonDto;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Maps entities to the API DTOs generated from the OpenAPI contract.
 * <p>
 * Must be called inside a transaction: it reads the lazily loaded actors and genres.
 * <p>
 * Only the entity → DTO direction lives here. Creating a movie from a {@code NewMovieDto} is not
 * plain field copying (names are trimmed, persons and genres are reused, duplicates are checked),
 * so that is done in {@link MovieService}.
 * <p>
 * Written by hand on purpose: the mapping is small and easy to read and debug. If the number of
 * DTOs or fields grows, consider replacing this class with
 * <a href="https://mapstruct.org/">MapStruct</a>, which generates the same kind of plain Java code
 * at compile time and fails the build when a target field is left unmapped. (Avoid
 * reflection-based mappers such as ModelMapper: mismatches only show up at runtime.)
 */
@Component
public class MovieMapper {

    public MovieDto toDto(Movie movie) {
        return new MovieDto()
                .id(movie.getId())
                .name(movie.getName())
                .year(movie.getYear())
                .genres(movie.getGenres().stream().map(Genre::getName).toList())
                .ageLimit(movie.getAgeLimit())
                .rating(movie.getRating())
                .actors(movie.getActors().stream().map(this::toDto).toList())
                .director(toDto(movie.getDirector()))
                .synopsis(movie.getSynopsis());
    }

    public PersonDto toDto(Person person) {
        return new PersonDto(person.getFirstName(), person.getLastName());
    }

    public MoviePageDto toPageDto(Page<Movie> page) {
        List<MovieDto> items = page.getContent().stream().map(this::toDto).toList();
        return new MoviePageDto(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
