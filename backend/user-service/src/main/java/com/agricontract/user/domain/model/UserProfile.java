package com.agricontract.user.domain.model;

import com.agricontract.user.domain.event.UserRegisteredEvent;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

// Aggregate Root
// State machine:
//   PENDING → VERIFIED
//   PENDING  → REJECTED
@Getter
public class UserProfile {

    private final List<UserRegisteredEvent> domainEvents = new ArrayList<>();
    private UserId userId;
    private String organizationName;
    private Role role;
    private ContactInfo contactInfo;
    private VerificationStatus verificationStatus;

    private UserProfile() {
    }

    public static UserProfile create(UserId userId, String organizationName,
                                     Role role, ContactInfo contactInfo) {
        UserProfile userProfile = new UserProfile();
        userProfile.userId = userId;
        userProfile.organizationName = organizationName;
        userProfile.role = role;
        userProfile.contactInfo = contactInfo;
        userProfile.verificationStatus = VerificationStatus.PENDING;
        userProfile.domainEvents.add(UserRegisteredEvent.of(
                userId.value(),
                contactInfo.email(),
                role
        ));
        return userProfile;
    }

    public static UserProfile reconstitute(UserId userId, String organizationName,
                                           Role role, ContactInfo contactInfo,
                                           VerificationStatus verificationStatus) {
        UserProfile userProfile = new UserProfile();
        userProfile.userId = userId;
        userProfile.organizationName = organizationName;
        userProfile.role = role;
        userProfile.contactInfo = contactInfo;
        userProfile.verificationStatus = verificationStatus;
        return userProfile;
    }

    public List<UserRegisteredEvent> pullDomainEvents() {
        List<UserRegisteredEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }

    public void verify() {
        if (this.verificationStatus != VerificationStatus.PENDING) {
            throw new IllegalStateException("Cannot verify from status: " + this.verificationStatus);
        }
        this.verificationStatus = VerificationStatus.VERIFIED;
    }

    public void reject() {
        if (this.verificationStatus != VerificationStatus.PENDING) {
            throw new IllegalStateException("Cannot rejected from status: " + this.verificationStatus);
        }
        this.verificationStatus = VerificationStatus.REJECTED;
    }

    public void updateContactInfo(ContactInfo newInfo) {
        if (newInfo == null) {
            return;
        }
        this.contactInfo = newInfo;
    }
}
