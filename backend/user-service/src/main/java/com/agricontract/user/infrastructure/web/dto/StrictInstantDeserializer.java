package com.agricontract.user.infrastructure.web.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

// Accepts only an RFC 3339 date-time string (or JSON null, which Jackson handles before this runs).
// The default Instant deserializer silently turns "" into null and numbers into epoch instants,
// which for an authorization expiry would mean an unintended "no expiry".
public class StrictInstantDeserializer extends StdScalarDeserializer<Instant> {

    public StrictInstantDeserializer() {
        super(Instant.class);
    }

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            return (Instant) ctxt.handleUnexpectedToken(Instant.class, p);
        }
        String text = p.getText();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return (Instant) ctxt.handleWeirdStringValue(Instant.class, text, "expected an RFC 3339 date-time");
        }
    }
}
