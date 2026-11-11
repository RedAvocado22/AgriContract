package com.agricontract.user.domain.model;

import com.agricontract.user.domain.event.UserRegisteredEvent;
import com.agricontract.user.domain.exception.InvalidVerificationTransitionException;
import com.agricontract.user.domain.exception.RoleSeparationViolationException;
import com.agricontract.user.domain.exception.SelfKycDecisionException;
import com.agricontract.user.domain.exception.SelfRoleChangeException;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Aggregate Root
// State machine (KYC):
//   PENDING  → VERIFIED   (verify)
//   PENDING  → REJECTED   (reject)
//   REJECTED → PENDING    (resubmit)
// verifiedByActorId / verifiedAt hold the latest decision, whether verify or reject.
@Getter
public class UserProfile {

    private final List<UserRegisteredEvent> domainEvents = new ArrayList<>();
    private UserId userId;
    private String organizationName;
    private Role role;
    private ContactInfo contactInfo;
    private VerificationStatus verificationStatus;
    private Instant authorizationExpiresAt;
    private String verifiedByActorId;
    private Instant verifiedAt;
    private String rejectionReason;
    // Projection of reputation-service lock state; only the lock event consumer may change it.
    private Instant lockedUntil;
    // The address the user proved to own with an emailed code (design §4.4). Only a proven address gets notifications.
    private String verifiedEmail;
    private Instant emailVerifiedAt;

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
                                           VerificationStatus verificationStatus,
                                           Instant authorizationExpiresAt,
                                           String verifiedByActorId,
                                           Instant verifiedAt,
                                           String rejectionReason,
                                           Instant lockedUntil,
                                           String verifiedEmail,
                                           Instant emailVerifiedAt) {
        UserProfile userProfile = new UserProfile();
        userProfile.userId = userId;
        userProfile.organizationName = organizationName;
        userProfile.role = role;
        userProfile.contactInfo = contactInfo;
        userProfile.verificationStatus = verificationStatus;
        userProfile.authorizationExpiresAt = authorizationExpiresAt;
        userProfile.verifiedByActorId = verifiedByActorId;
        userProfile.verifiedAt = verifiedAt;
        userProfile.rejectionReason = rejectionReason;
        userProfile.lockedUntil = lockedUntil;
        userProfile.verifiedEmail = verifiedEmail;
        userProfile.emailVerifiedAt = emailVerifiedAt;
        return userProfile;
    }

    public List<UserRegisteredEvent> pullDomainEvents() {
        List<UserRegisteredEvent> events = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }

    // authorizationExpiresAt == null means the source document has no expiry.
    public void verify(String actorId, Instant decidedAt, Instant authorizationExpiresAt) {
        if (this.verificationStatus != VerificationStatus.PENDING) {
            throw new InvalidVerificationTransitionException("verify", this.verificationStatus);
        }
        requireDecision(actorId, decidedAt);
        if (authorizationExpiresAt != null && !authorizationExpiresAt.isAfter(decidedAt)) {
            throw new IllegalArgumentException("authorizationExpiresAt must be after the decision time");
        }
        this.verificationStatus = VerificationStatus.VERIFIED;
        this.verifiedByActorId = actorId;
        this.verifiedAt = decidedAt;
        this.authorizationExpiresAt = authorizationExpiresAt;
        this.rejectionReason = null;
    }

    public void reject(String actorId, Instant decidedAt, String reason) {
        if (this.verificationStatus != VerificationStatus.PENDING) {
            throw new InvalidVerificationTransitionException("reject", this.verificationStatus);
        }
        requireDecision(actorId, decidedAt);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason must not be blank");
        }
        this.verificationStatus = VerificationStatus.REJECTED;
        this.verifiedByActorId = actorId;
        this.verifiedAt = decidedAt;
        this.rejectionReason = reason.trim();
    }

    // Keeps verifiedByActorId / verifiedAt as the latest decision history.
    public void resubmit() {
        if (this.verificationStatus != VerificationStatus.REJECTED) {
            throw new InvalidVerificationTransitionException("resubmit", this.verificationStatus);
        }
        this.verificationStatus = VerificationStatus.PENDING;
        this.rejectionReason = null;
    }

    // One role per account. A direct OPERATOR <-> ARBITRATOR move would let one person propose and then approve
    // the same maker-checker action, so it is refused. An actor never changes its own role, even to the role it
    // already holds (checked before the no-op). Returns false when the role is already set (no-op).
    public boolean assignRole(String actorId, Role newRole) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("Role change actor must not be blank");
        }
        if (actorId.equals(this.userId.value())) {
            throw new SelfRoleChangeException(actorId);
        }
        if (newRole == null) {
            throw new IllegalArgumentException("Role must not be null");
        }
        if (newRole == this.role) {
            return false;
        }
        if (newRole.isSeparatedFrom(this.role)) {
            throw new RoleSeparationViolationException(this.userId.value(), this.role, newRole);
        }
        this.role = newRole;
        return true;
    }

    public void updateContactInfo(ContactInfo newInfo) {
        if (newInfo == null) {
            return;
        }
        // A new address is not proven yet: the old proof does not carry over.
        if (!sameAddress(this.contactInfo.email(), newInfo.email())) {
            this.verifiedEmail = null;
            this.emailVerifiedAt = null;
        }
        this.contactInfo = newInfo;
    }

    // True only while the proven address is still the contact address.
    public boolean isEmailVerified() {
        return verifiedEmail != null && sameAddress(verifiedEmail, contactInfo.email());
    }

    // The contact address once proven, else null. Every notification recipient must come from here.
    public String verifiedContactEmail() {
        return isEmailVerified() ? contactInfo.email() : null;
    }

    // codeSentTo: the address the confirmed code went to; it must still be the contact address.
    public void markEmailVerified(String codeSentTo, Instant verifiedAt) {
        if (verifiedAt == null) {
            throw new IllegalArgumentException("Email verification time must not be null");
        }
        if (!sameAddress(codeSentTo, contactInfo.email())) {
            throw new IllegalArgumentException("The code was sent to an address that is no longer the contact email");
        }
        this.verifiedEmail = contactInfo.email();
        this.emailVerifiedAt = verifiedAt;
    }

    // Addresses compare case-insensitively, like the unique key on user_profiles.verified_email under MySQL collation.
    private static boolean sameAddress(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private void requireDecision(String actorId, Instant decidedAt) {
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("Decision actor must not be blank");
        }
        if (decidedAt == null) {
            throw new IllegalArgumentException("Decision time must not be null");
        }
        if (actorId.equals(this.userId.value())) {
            throw new SelfKycDecisionException(actorId);
        }
    }
}
