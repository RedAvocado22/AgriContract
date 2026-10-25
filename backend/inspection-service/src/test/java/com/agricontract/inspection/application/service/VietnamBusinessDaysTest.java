package com.agricontract.inspection.application.service;

import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VietnamBusinessDaysTest {
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
