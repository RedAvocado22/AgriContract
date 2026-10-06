package com.agricontract.user.domain.event;

import com.agricontract.user.domain.model.vo.Role;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class UserRegisteredEvent {

    private final String eventId;
    private final String userId;
    private final String email;
    private final Role role;
    private final LocalDateTime occurredAt;

    private UserRegisteredEvent(String userId, String email, Role role) {
        this.eventId = UUID.randomUUID().toString();
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.occurredAt = LocalDateTime.now();
    }

    public static UserRegisteredEvent of(String userId, String email, Role role) {
        return new UserRegisteredEvent(userId, email, role);
    }
}
