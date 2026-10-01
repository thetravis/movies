package com.example.movies.service;

/** Thrown when a movie with the same name (case-insensitive) and year already exists. Mapped to HTTP 409. */
public class DuplicateMovieException extends RuntimeException {

    public DuplicateMovieException(String name, Integer year) {
        super("A movie named '" + name + "' from " + year + " already exists.");
    }
}
