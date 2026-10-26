package com.agricontract.inspection.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.regex.Pattern;

/** Validation never rewrites committed strings or rounds decimal measurements. */
final class HashMaterialValidation {
    private static final Pattern DATE_TIME = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})[Tt](\\d{2}):(\\d{2}):(\\d{2})(\\.\\d+)?([Zz]|([+-])(\\d{2}):(\\d{2}))");
    private HashMaterialValidation() { }

    static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }

    static void quantity(BigDecimal value) {
        require(value != null && value.signum() >= 0, "Nonnegative quantity required");
    }

    static void percent(BigDecimal value) {
        quantity(value);
        require(value.compareTo(BigDecimal.valueOf(100)) <= 0, "Percentage exceeds 100");
    }

    static void timestamp(String value) {
        require(value != null, "RFC3339 date-time required");
        var match = DATE_TIME.matcher(value);
        require(match.matches(), "RFC3339 date-time required");
        try { LocalDate.of(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3))); }
        catch (DateTimeException invalid) { throw new IllegalArgumentException("Invalid date-time"); }
        int hour = Integer.parseInt(match.group(4)), minute = Integer.parseInt(match.group(5)), second = Integer.parseInt(match.group(6));
        require(hour <= 23 && minute <= 59 && second <= 60, "Invalid date-time");
        int offsetMinutes = 0;
        if (match.group(9) != null) {
            int offsetHour = Integer.parseInt(match.group(10)), offsetMinute = Integer.parseInt(match.group(11));
            require(offsetHour <= 23 && offsetMinute <= 59, "Invalid date-time offset");
            offsetMinutes = (offsetHour * 60 + offsetMinute) * (match.group(9).equals("-") ? -1 : 1);
        }
        // RFC3339 permits leap-second spelling; no timestamp normalization or leap-second registry here.
        require(second != 60 || Math.floorMod(hour * 60 + minute - offsetMinutes, 1440) == 1439, "Invalid leap-second time");
    }

    static void uuid(String value) {
        require(value != null && value.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"), "UUID required");
    }

    static void hash(String value) {
        require(value != null && value.matches("[a-f0-9]{64}"), "Lowercase SHA-256 commitment required");
    }
}
