package com.example.movies.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/**
 * A movie. A movie is identified by its name (case-insensitive) and release year.
 */
@Entity
@Table(name = "movie")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** Release year. */
    @Column(name = "release_year")
    private Integer year;

    private Integer ageLimit;

    /** Rating on a scale of 1 to 5. */
    private Integer rating;

    private String synopsis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "director_id")
    private Person director;

    /** Actors in billing order (lead actor first). */
    @ManyToMany
    @JoinTable(name = "movie_actor",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id"))
    @OrderColumn(name = "position")
    private List<Person> actors = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "movie_genre",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id"))
    @OrderBy("name")
    private Set<Genre> genres = new LinkedHashSet<>();

    protected Movie() {
        // for JPA
    }

    public Movie(String name, Integer year, Integer ageLimit, Integer rating, String synopsis, Person director) {
        this.name = name;
        this.year = year;
        this.ageLimit = ageLimit;
        this.rating = rating;
        this.synopsis = synopsis;
        this.director = director;
    }

    public void addActor(Person actor) {
        actors.add(actor);
    }

    public void addGenre(Genre genre) {
        genres.add(genre);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Integer getYear() {
        return year;
    }

    public Integer getAgeLimit() {
        return ageLimit;
    }

    public Integer getRating() {
        return rating;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public Person getDirector() {
        return director;
    }

    public List<Person> getActors() {
        return Collections.unmodifiableList(actors);
    }

    public Set<Genre> getGenres() {
        return Collections.unmodifiableSet(genres);
    }
}
