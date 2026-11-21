package com.agricontract.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class VietnamBusinessDaysTest {

    private final VietnamBusinessDays noHolidays = new VietnamBusinessDays(Set.of());

    /** Day zero is the start's ICT date; the window ends at 23:59:59.999 ICT = 16:59:59.999Z. */
    @ParameterizedTest
    @CsvSource({
            "2026-10-19, 2026-10-20",   // Monday -> Tuesday
            "2026-10-23, 2026-10-26",   // Friday -> Monday
            "2026-10-24, 2026-10-26",   // Saturday -> Monday
            "2026-10-25, 2026-10-26"})  // Sunday -> Monday
    void oneBusinessDaySkipsTheWeekend(String start, String due) {
        assertThat(noHolidays.deadline(Instant.parse(start + "T03:00:00Z"), 1))
                .isEqualTo(Instant.parse(due + "T16:59:59.999Z"));
    }

    @Test
    void fiveBusinessDaysSkipAWeekendAndConsecutiveHolidays() {
        VietnamBusinessDays calendar = new VietnamBusinessDays(
                Set.of(LocalDate.parse("2026-10-27"), LocalDate.parse("2026-10-28")));

        // Fri 23 -> Mon 26 (1), Tue 27 + Wed 28 holidays, Thu 29 (2), Fri 30 (3), Mon 2 (4), Tue 3 (5).
        assertThat(calendar.deadline(Instant.parse("2026-10-23T08:00:00Z"), 5))
                .isEqualTo(Instant.parse("2026-11-03T16:59:59.999Z"));
    }

    @Test
    void theIctDateDecides_notTheJvmTimezone() {
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            // 23:59:59.999 ICT Thursday 22 -> Friday 23; one millisecond later is Friday 23 ICT -> Monday 26.
            assertThat(noHolidays.deadline(Instant.parse("2026-10-22T16:59:59.999Z"), 1))
                    .isEqualTo(Instant.parse("2026-10-23T16:59:59.999Z"));
            assertThat(noHolidays.deadline(Instant.parse("2026-10-22T17:00:00Z"), 1))
                    .isEqualTo(Instant.parse("2026-10-26T16:59:59.999Z"));
        } finally {
            TimeZone.setDefault(saved);
        }
    }

    @Test
    void crossesTheYearAndSkipsNewYearsDay() {
        VietnamBusinessDays calendar = new VietnamBusinessDays(Set.of(LocalDate.parse("2027-01-01")));

        // 18:00Z on 30 Dec is already Thursday 31 Dec ICT; Fri 1 Jan holiday, weekend -> Mon 4 Jan.
        assertThat(calendar.deadline(Instant.parse("2026-12-30T18:00:00Z"), 1))
                .isEqualTo(Instant.parse("2027-01-04T16:59:59.999Z"));
    }

    @Test
    void configurationIgnoresSpacesDuplicatesAndEmptyTokens() {
        VietnamBusinessDays calendar =
                VietnamBusinessDays.fromConfiguration(" 2026-10-26 ,, 2026-10-26, ");

        assertThat(calendar.isBusinessDay(LocalDate.parse("2026-10-26"))).isFalse();
        assertThat(calendar.deadline(Instant.parse("2026-10-23T08:00:00Z"), 1))
                .isEqualTo(Instant.parse("2026-10-27T16:59:59.999Z"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", ",,", "2026-13-01", "26-10-2026", "2026-10-26,tomorrow"})
    void missingOrInvalidConfigurationFailsFast(String value) {
        assertThatThrownBy(() -> VietnamBusinessDays.fromConfiguration(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VIETNAM_HOLIDAYS");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 31})
    void daysOutsideOneToThirtyAreRefused(int days) {
        assertThatThrownBy(() -> noHolidays.deadline(Instant.EPOCH, days))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thirtyBusinessDaysIsAccepted() {
        assertThat(noHolidays.deadline(Instant.parse("2026-10-19T03:00:00Z"), 30))
                .isEqualTo(Instant.parse("2026-11-30T16:59:59.999Z"));
    }

    // businessDaysBefore: the milestone funding schedule (D64).

    @ParameterizedTest
    @CsvSource({
            "2026-12-11, 1, 2026-12-10",   // Friday -> Thursday
            "2026-12-14, 1, 2026-12-11",   // Monday -> Friday, skips the weekend
            "2026-12-14, 7, 2026-12-03",   // Mon 14 back 7: 11, 10, 9, 8, 7, 4, 3
            "2026-12-13, 1, 2026-12-11"})  // Sunday target -> Friday
    void businessDaysBeforeCountsBackOverWeekends(String date, int days, String expected) {
        assertThat(noHolidays.businessDaysBefore(LocalDate.parse(date), days)).isEqualTo(LocalDate.parse(expected));
    }

    @Test
    void businessDaysBeforeSkipsHolidays() {
        VietnamBusinessDays calendar = new VietnamBusinessDays(Set.of(LocalDate.parse("2026-12-10")));

        assertThat(calendar.businessDaysBefore(LocalDate.parse("2026-12-11"), 1)).isEqualTo(LocalDate.parse("2026-12-09"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 367})
    void businessDaysBeforeRefusesAnOutOfRangeCount(int days) {
        assertThatThrownBy(() -> noHolidays.businessDaysBefore(LocalDate.parse("2026-12-11"), days))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // businessDaysBetween: fundingDelayBusinessDays (D64), counted on ICT dates.

    @ParameterizedTest
    @CsvSource({
            "2026-12-07T01:00:00Z, 2026-12-07T09:00:00Z, 0",   // same ICT day
            "2026-12-04T03:00:00Z, 2026-12-07T03:00:00Z, 1",   // Friday -> Monday
            "2026-12-04T03:00:00Z, 2026-12-05T03:00:00Z, 0",   // Friday -> Saturday
            "2026-12-07T16:30:00Z, 2026-12-07T17:30:00Z, 1",   // 23:30 ICT Monday -> 00:30 ICT Tuesday
            "2026-12-01T03:00:00Z, 2026-12-10T03:00:00Z, 7"})  // Tue 1 -> Thu 10
    void businessDaysBetweenCountsIctDatesAfterTheStart(String from, String to, int expected) {
        assertThat(noHolidays.businessDaysBetween(Instant.parse(from), Instant.parse(to))).isEqualTo(expected);
    }

    @Test
    void businessDaysBetweenSkipsHolidaysAndRefusesAReversedRange() {
        VietnamBusinessDays calendar = new VietnamBusinessDays(Set.of(LocalDate.parse("2026-12-08")));

        assertThat(calendar.businessDaysBetween(Instant.parse("2026-12-07T03:00:00Z"),
                Instant.parse("2026-12-09T03:00:00Z"))).isEqualTo(1);
        assertThatThrownBy(() -> calendar.businessDaysBetween(Instant.parse("2026-12-09T03:00:00Z"),
                Instant.parse("2026-12-07T03:00:00Z"))).isInstanceOf(IllegalArgumentException.class);
    }
}
