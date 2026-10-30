package com.agricontract.calendar;

import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class VietnamBusinessDaysTest {
    @ParameterizedTest
    @CsvSource({
            "2026-10-18,2026-10-19", "2026-10-19,2026-10-20",
            "2026-10-20,2026-10-21", "2026-10-21,2026-10-22",
            "2026-10-22,2026-10-23", "2026-10-23,2026-10-26",
            "2026-10-24,2026-10-26", "2026-10-25,2026-10-26"})
    void countsWeekdaysAndSkipsBothWeekendDays(String source, String due) {
        var calendar = new VietnamBusinessDays(Set.of());
        assertThat(calendar.deadline(Instant.parse(source + "T03:00:00Z"), 1))
                .isEqualTo(Instant.parse(due + "T16:59:59.999Z"));
    }

    @ParameterizedTest
    @CsvSource({"1,2026-10-28", "2,2026-10-29", "3,2026-10-30", "4,2026-11-02"})
    void countsMultipleDaysAcrossWeekendAndConsecutiveHolidays(int days, String due) {
        var calendar = VietnamBusinessDays.fromConfiguration("2026-10-26,2026-10-27");
        assertThat(calendar.deadline(Instant.parse("2026-10-23T08:00:00Z"), days))
                .isEqualTo(Instant.parse(due + "T16:59:59.999Z"));
    }

    @Test void ictMidnightBoundaryIsIndependentOfSystemTimezone() {
        var original = java.util.TimeZone.getDefault();
        try {
            var calendar = new VietnamBusinessDays(Set.of());
            for (String zone : new String[]{"UTC", "America/Los_Angeles", "Asia/Tokyo"}) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone));
                assertThat(calendar.deadline(Instant.parse("2026-10-22T16:59:59.999Z"), 1))
                        .isEqualTo(Instant.parse("2026-10-23T16:59:59.999Z"));
                assertThat(calendar.deadline(Instant.parse("2026-10-22T17:00:00Z"), 1))
                        .isEqualTo(Instant.parse("2026-10-26T16:59:59.999Z"));
            }
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", ",,,", " , , ", "not-a-date", "2026-02-30",
            "2026-1-01", "2026-10-26,invalid", "2026-10-26,2026-02-30"})
    void rejectsMissingOrInvalidHolidayConfiguration(String value) {
        assertThatThrownBy(() -> VietnamBusinessDays.fromConfiguration(value))
                .isInstanceOfAny(IllegalArgumentException.class, java.time.format.DateTimeParseException.class);
    }

    @Test void retainsWhitespaceDuplicateAndEmptyTokenCompatibility() {
        var calendar = VietnamBusinessDays.fromConfiguration(" , 2026-10-26,,2026-10-26, ");
        assertThat(calendar.deadline(Instant.parse("2026-10-23T08:00:00Z"), 1))
                .isEqualTo(Instant.parse("2026-10-27T16:59:59.999Z"));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 31})
    void rejectsDaysOutsideExistingRange(int days) {
        assertThatThrownBy(() -> new VietnamBusinessDays(Set.of()).deadline(Instant.EPOCH, days))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void acceptsMaximumExistingDuration() {
        assertThat(new VietnamBusinessDays(Set.of()).deadline(Instant.parse("2026-10-19T03:00:00Z"), 30))
                .isEqualTo(Instant.parse("2026-11-30T16:59:59.999Z"));
    }
    @Test void skipsWeekendAndConfiguredHolidayUsingIctDate() {
        var calendar = new VietnamBusinessDays(Set.of(LocalDate.parse("2026-10-26")));
        assertThat(calendar.deadline(Instant.parse("2026-10-23T08:00:00Z"), 1))
                .isEqualTo(Instant.parse("2026-10-27T16:59:59.999Z"));
    }
    @Test void utcEveningAlreadyBelongsToNextIctDateAndCrossesYear() {
        var calendar = new VietnamBusinessDays(Set.of(LocalDate.parse("2027-01-01")));
        assertThat(calendar.deadline(Instant.parse("2026-12-30T18:00:00Z"), 1))
                .isEqualTo(Instant.parse("2027-01-04T16:59:59.999Z"));
        assertThatThrownBy(() -> calendar.deadline(Instant.EPOCH, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
