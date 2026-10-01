package com.example.movies.service.mapper;

/**
 * Text cleanup shared by the mappers. MapStruct applies {@link #trimToNull} to every
 * {@code String → String} property in mappers that list this class in {@code uses}.
 */
public final class TextMapper {

    private TextMapper() {
    }

    /** Trims the value; blank values become {@code null}, so "required" validation catches them. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
