package com.agricontract.user.infrastructure.web.dto;

import com.agricontract.user.domain.model.vo.Role;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;

import java.io.IOException;

// Accepts only an exact Role name string (JSON null is handled by Jackson before this runs, then @NotNull rejects it).
// The default enum deserializer maps integers and numeric strings to ordinals, so {"role":2} or {"role":"2"}
// would silently assign ADMIN.
public class StrictRoleDeserializer extends StdScalarDeserializer<Role> {

    public StrictRoleDeserializer() {
        super(Role.class);
    }

    @Override
    public Role deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (!p.hasToken(JsonToken.VALUE_STRING)) {
            return (Role) ctxt.handleUnexpectedToken(Role.class, p);
        }
        String text = p.getText();
        for (Role role : Role.values()) {
            if (role.name().equals(text)) {
                return role;
            }
        }
        return (Role) ctxt.handleWeirdStringValue(Role.class, text, "expected one of the Role names");
    }
}
