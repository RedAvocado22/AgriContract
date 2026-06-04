package com.agricontract.user.domain.event;

import com.agricontract.user.domain.model.vo.Role;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserRegisteredEvent {

    private final String eventId;
    private final String userId;
    private final String email;
    private final Role role;
    private final LocalDateTime occurredAt;

    private UserRegisteredEvent(String userId, String email, Role role) {
        // TODO
        this.eventId = null;
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.occurredAt = null;
    }

    public static UserRegisteredEvent of(String userId, String email, Role role) {
        return new UserRegisteredEvent(userId, email, role);
    }
}
