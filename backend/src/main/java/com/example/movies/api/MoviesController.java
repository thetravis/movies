package com.example.movies.api;

import java.net.URI;

import com.example.movies.generated.api.MoviesApi;
import com.example.movies.generated.model.MovieDto;
import com.example.movies.generated.model.MoviePageDto;
import com.example.movies.generated.model.NewMovieDto;
import com.example.movies.service.MovieService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST endpoints for movies. Paths, parameters and validation come from the generated
 * {@link MoviesApi} interface (see openapi.yaml); this class only delegates to {@link MovieService}.
 * Errors are turned into responses by {@link ApiExceptionHandler}.
 */
@RestController
public class MoviesController implements MoviesApi {

    private final MovieService movieService;

    public MoviesController(MovieService movieService) {
        this.movieService = movieService;
    }

    @Override
    public ResponseEntity<MoviePageDto> listMovies(String search, Integer page, Integer size) {
        return ResponseEntity.ok(movieService.listMovies(search, page, size));
    }

    @Override
    public ResponseEntity<MovieDto> getMovie(Long id) {
        return ResponseEntity.ok(movieService.getMovie(id));
    }

    @Override
    public ResponseEntity<MovieDto> createMovie(NewMovieDto newMovieDto) {
        MovieDto created = movieService.createMovie(newMovieDto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }
}
