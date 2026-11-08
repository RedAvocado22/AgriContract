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
}
