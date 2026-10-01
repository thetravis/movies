package com.example.movies.service;

/** Thrown when a movie with the requested id does not exist. Mapped to HTTP 404. */
public class MovieNotFoundException extends RuntimeException {

    public MovieNotFoundException(Long id) {
        super("Movie with id " + id + " was not found.");
    }
}
