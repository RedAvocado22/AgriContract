package com.agricontract.reputation.domain.model;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
public final class FactValues {
    private FactValues() {}
    public static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("Invalid UUID");
        return id;
    }
    public static Instant timestamp(Instant value) {
        return Objects.requireNonNull(value, "Fact timestamp is required").truncatedTo(ChronoUnit.MICROS);
    }
}
