package com.agricontract.calendar;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Vietnam business-day calendar shared by every service (milestone-escrow design §1.1, #295): a business day is
 * an ICT Monday to Friday that is not a configured public holiday. Holidays come from one configuration value,
 * {@code VIETNAM_HOLIDAYS} (comma-separated ISO dates), maintained every year; there is no built-in table.
 *
 * <p>A window of N business days starts the day after the starting instant's ICT date (day zero) and ends at the
 * end of the Nth business day, 23:59:59.999 ICT, returned as a UTC instant (the frozen date-only convention).
 */
public final class VietnamBusinessDays {

    public static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_DAYS = 30;

    private final Set<LocalDate> holidays;

    public VietnamBusinessDays(Set<LocalDate> holidays) {
        this.holidays = Set.copyOf(holidays);
    }

    /**
     * Builds the calendar from {@code VIETNAM_HOLIDAYS}. Fails fast when the value is missing, blank, holds no date
     * or holds a token that is not an ISO date, so a service never starts with a silently empty calendar. Spaces and
     * empty tokens between commas are ignored.
     */
    public static VietnamBusinessDays fromConfiguration(String holidays) {
        if (holidays == null || holidays.isBlank()) {
            throw new IllegalArgumentException("VIETNAM_HOLIDAYS is not configured: list the public holidays of the "
                    + "operating years as comma-separated YYYY-MM-DD dates");
        }
        Set<LocalDate> dates = Arrays.stream(holidays.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(VietnamBusinessDays::parse)
                .collect(Collectors.toSet());
        if (dates.isEmpty()) {
            throw new IllegalArgumentException("VIETNAM_HOLIDAYS holds no date");
        }
        return new VietnamBusinessDays(dates);
    }

    /** End of the Nth business day after {@code start}'s ICT date (1 to 30). */
    public Instant deadline(Instant start, int days) {
        if (start == null) {
            throw new IllegalArgumentException("start is required");
        }
        if (days < 1 || days > MAX_DAYS) {
            throw new IllegalArgumentException("Business days must be 1.." + MAX_DAYS + ", was " + days);
        }
        LocalDate date = start.atZone(ICT).toLocalDate();
        int remaining = days;
        while (remaining > 0) {
            date = date.plusDays(1);
            if (isBusinessDay(date)) {
                remaining--;
            }
        }
        return date.plusDays(1).atStartOfDay(ICT).toInstant().minusMillis(1);
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !holidays.contains(date);
    }

    private static LocalDate parse(String token) {
        try {
            return LocalDate.parse(token);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("VIETNAM_HOLIDAYS holds an invalid date: " + token, e);
        }
    }
}
