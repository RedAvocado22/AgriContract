package com.agricontract.inspection.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

/** Validation never rewrites committed strings or rounds decimal measurements. */
final class HashMaterialValidation {
    private static final Pattern DATE_TIME =
            Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z");
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final BigDecimal MAX_QUANTITY = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(100);

    private HashMaterialValidation() {}

    static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }

    static void quantity(BigDecimal value) {
        decimal(value, MAX_QUANTITY, 3, "Quantity");
    }

    static void percent(BigDecimal value) {
        decimal(value, MAX_PERCENT, 2, "Percentage");
    }

    private static void decimal(BigDecimal value, BigDecimal maximum, int scale, String field) {
        require(
                value != null && value.signum() >= 0 && value.compareTo(maximum) <= 0,
                field + " outside permitted range");
        require(value.stripTrailingZeros().scale() <= scale, field + " has excess precision");
    }

    static void timestamp(String value) {
        require(
                value != null && DATE_TIME.matcher(value).matches(),
                "UTC timestamp with exactly three fractional digits and literal Z required");
        try {
            LocalDateTime.parse(value, TIMESTAMP_FORMAT);
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException("Invalid canonical timestamp", invalid);
        }
    }

    static void uuid(String value) {
        require(
                value != null && value.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"),
                "Canonical lowercase UUID required");
    }

    static void hash(String value) {
        require(
                value != null && value.matches("[a-f0-9]{64}"),
                "Lowercase SHA-256 commitment required");
    }
}
