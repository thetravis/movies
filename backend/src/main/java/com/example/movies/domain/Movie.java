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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A movie. A movie is identified by its name (case-insensitive) and release year.
 * <p>
 * The validation annotations are the rules for a valid movie; {@code MovieService} checks them
 * before saving. The same rules are declared in the {@code NewMovie} schema of openapi.yaml so API
 * clients see them in the contract (<b>keep the two in sync</b>), and the most important ones are
 * also enforced by the database (001-create-schema.sql).
 */
@Entity
@Table(name = "movie")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    private String name;

    /** Release year. */
    @NotNull
    @Min(1888)
    @Max(2100)
    @Column(name = "release_year")
    private Integer year;

    @NotNull
    @Min(0)
    @Max(21)
    private Integer ageLimit;

    /** Rating on a scale of 1 to 5. */
    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    @Size(max = 2000)
    private String synopsis;

    @NotNull
    @Valid
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "director_id")
    private Person director;

    /** Actors in billing order (lead actor first). */
    @ManyToMany
    @JoinTable(name = "movie_actor",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id"))
    @OrderColumn(name = "position")
    private List<@Valid Person> actors = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "movie_genre",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id"))
    @OrderBy("name")
    @NotEmpty
    private Set<@Valid Genre> genres = new LinkedHashSet<>();

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

    /** Adds an actor to the end of the billing order. Adding the same person again has no effect. */
    public void addActor(Person actor) {
        if (!actors.contains(actor)) {
            actors.add(actor);
        }
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
