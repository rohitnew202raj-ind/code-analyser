package org.example.analyser.analyzer;

import java.util.regex.Pattern;

/**
 * Acronym-aware camelCase -> snake_case conversion.
 *
 * The naive "insert underscore before every uppercase letter"
 * approach mangles acronym runs, e.g.:
 *
 * HTTPOrderEntity -> h_t_t_p_order_entity   (wrong)
 * URLMapping      -> u_r_l_mapping          (wrong)
 *
 * This uses the standard two-pass regex approach (same idea
 * as Jackson's SnakeCaseStrategy): first split an acronym run
 * from the capitalized word that follows it, then split a
 * lowercase/digit boundary from the next uppercase letter.
 *
 * HTTPOrderEntity -> http_order_entity
 * URLMapping      -> url_mapping
 */
public final class SnakeCaseConverter {

    private static final Pattern ACRONYM_BOUNDARY =
            Pattern.compile("([A-Z]+)([A-Z][a-z])");

    private static final Pattern WORD_BOUNDARY =
            Pattern.compile("([a-z0-9])([A-Z])");

    private SnakeCaseConverter() {
    }

    public static String toSnakeCase(String value) {

        if (value == null || value.isEmpty()) {
            return value;
        }

        String withAcronymBoundaries =
                ACRONYM_BOUNDARY
                        .matcher(value)
                        .replaceAll("$1_$2");

        String withWordBoundaries =
                WORD_BOUNDARY
                        .matcher(withAcronymBoundaries)
                        .replaceAll("$1_$2");

        return withWordBoundaries.toLowerCase();
    }
}
