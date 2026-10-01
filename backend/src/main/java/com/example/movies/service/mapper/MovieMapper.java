package com.example.movies.service.mapper;

import com.example.movies.domain.Movie;
import com.example.movies.generated.model.MovieDto;
import com.example.movies.generated.model.MoviePageDto;
import com.example.movies.generated.model.NewMovieDto;
import org.mapstruct.BeanMapping;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

/**
 * Maps movies between the API DTOs generated from openapi.yaml and the JPA entities.
 * <p>
 * The implementation is generated at compile time by <a href="https://mapstruct.org/">MapStruct</a>
 * ({@code target/generated-sources/annotations/.../MovieMapperImpl.java}). Fields with the same name
 * are copied automatically; differently named ones are declared with {@code @Mapping}.
 * <p>
 * <b>Unmapped fields in either direction fail the build.</b> This fits the contract-first approach:
 * when a field is added to openapi.yaml, the build fails until the entity has it too (and Hibernate
 * schema validation fails on startup until the database has a column for it).
 * <ul>
 *   <li>Text is trimmed ({@link TextMapper}).</li>
 *   <li>Persons and genres are resolved by {@link PersonMapper} and {@link GenreMapper}, which reuse
 *       existing rows or save new ones. Therefore {@link #toEntity} must be called inside a
 *       transaction ({@code MovieService}).</li>
 *   <li>Actors and genres are added through {@code Movie.addActor} / {@code addGenre}
 *       ({@code ADDER_PREFERRED}), which ignore duplicates.</li>
 *   <li>{@link #toDto} reads the lazily loaded actors and genres: also call it inside a
 *       transaction.</li>
 * </ul>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {TextMapper.class, PersonMapper.class, GenreMapper.class},
        collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
        unmappedSourcePolicy = ReportingPolicy.ERROR, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MovieMapper {

    Movie toEntity(NewMovieDto dto);

    MovieDto toDto(Movie movie);

    /** Spring's {@code Page} has many properties the API doesn't use, so unused sources are ignored here. */
    @BeanMapping(unmappedSourcePolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "items", source = "content")
    @Mapping(target = "page", source = "number")
    @Mapping(target = "totalItems", source = "totalElements")
    MoviePageDto toPageDto(Page<Movie> page);
}
