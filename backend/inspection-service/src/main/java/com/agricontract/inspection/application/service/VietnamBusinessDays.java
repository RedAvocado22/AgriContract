package com.agricontract.inspection.application.service;

import java.time.*;
import java.util.Set;

/** Assignment date is day zero; the deadline is end of the Nth subsequent ICT business date. */
public class VietnamBusinessDays {
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Set<LocalDate> holidays;
    public VietnamBusinessDays(Set<LocalDate> holidays) { this.holidays = Set.copyOf(holidays); }

    public Instant deadline(Instant assignedAt, int days) {
        if (days < 1 || days > 30) throw new IllegalArgumentException("Business days must be 1..30");
        LocalDate date = assignedAt.atZone(ICT).toLocalDate();
        for (int remaining = days; remaining > 0;) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY
                    && !holidays.contains(date)) remaining--;
        }
        // Frozen date-only convention uses millisecond end-of-day, stored as UTC.
        return date.plusDays(1).atStartOfDay(ICT).toInstant().minusMillis(1);
    }
}
