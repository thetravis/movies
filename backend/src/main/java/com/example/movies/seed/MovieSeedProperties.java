package com.example.movies.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/**
 * Settings for loading movies from a JSON file on startup ({@code movies.seed.*}).
 *
 * @param enabled whether to import on startup (env var {@code MOVIES_SEED_ENABLED})
 * @param file    the JSON file to import; any Spring resource location, e.g.
 *                {@code classpath:data/movies-compact.json} or {@code file:C:/data/movies.json}
 *                (env var {@code MOVIES_SEED_FILE})
 */
@ConfigurationProperties("movies.seed")
public record MovieSeedProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("classpath:data/movies-compact.json") Resource file) {
}
