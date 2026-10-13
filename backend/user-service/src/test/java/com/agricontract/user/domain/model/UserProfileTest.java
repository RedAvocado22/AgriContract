package com.agricontract.user.domain.model;

import com.agricontract.user.domain.event.UserRegisteredEvent;
import com.agricontract.user.domain.exception.InvalidVerificationTransitionException;
import com.agricontract.user.domain.exception.RoleSeparationViolationException;
import com.agricontract.user.domain.exception.SelfKycDecisionException;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserProfileTest {

    private static final String ACTOR_ID = "admin-1";
    private static final Instant DECIDED_AT = Instant.parse("2026-10-07T03:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2027-06-30T16:59:59.999Z");

    private UserId userId;
    private ContactInfo contactInfo;

    @BeforeEach
    void setUp() {
        userId = new UserId("user-123");
        contactInfo = new ContactInfo("test@example.com", "0901234567", "Ha Noi");
    }

    @Test
    void create_setsFieldsAndStatusPending() {
        UserProfile profile = UserProfile.create(userId, "Cong ty A", Role.SELLER, contactInfo);

        assertEquals("user-123", profile.getUserId().value());
        assertEquals("Cong ty A", profile.getOrganizationName());
        assertEquals(Role.SELLER, profile.getRole());
        assertEquals(VerificationStatus.PENDING, profile.getVerificationStatus());
    }

    @Test
    void create_hasNoKycDecision() {
        UserProfile profile = pending();

        assertNull(profile.getVerifiedByActorId());
        assertNull(profile.getVerifiedAt());
        assertNull(profile.getAuthorizationExpiresAt());
        assertNull(profile.getRejectionReason());
    }

    @Test
    void create_addsUserRegisteredEvent() {
        UserProfile profile = UserProfile.create(userId, "Cong ty A", Role.SELLER, contactInfo);

        List<UserRegisteredEvent> events = profile.pullDomainEvents();
        assertEquals(1, events.size());
        assertEquals("user-123", events.get(0).getUserId());
    }

    @Test
    void reconstitute_doesNotAddDomainEvents() {
        UserProfile profile = UserProfile.reconstitute(userId, "Cong ty A", Role.SELLER, contactInfo,
                VerificationStatus.VERIFIED, EXPIRES_AT, ACTOR_ID, DECIDED_AT, null);

        assertTrue(profile.pullDomainEvents().isEmpty());
    }

    @Test
    void reconstitute_restoresKycDecision() {
        UserProfile profile = UserProfile.reconstitute(userId, "Cong ty A", Role.SELLER, contactInfo,
                VerificationStatus.REJECTED, null, ACTOR_ID, DECIDED_AT, "Thieu giay uy quyen");

        assertEquals(VerificationStatus.REJECTED, profile.getVerificationStatus());
        assertEquals(ACTOR_ID, profile.getVerifiedByActorId());
        assertEquals(DECIDED_AT, profile.getVerifiedAt());
        assertEquals("Thieu giay uy quyen", profile.getRejectionReason());
    }

    @Test
    void pullDomainEvents_clearsListAfterPull() {
        UserProfile profile = UserProfile.create(userId, "Cong ty A", Role.SELLER, contactInfo);

        profile.pullDomainEvents();
        assertTrue(profile.pullDomainEvents().isEmpty());
    }

    @Test
    void verify_pendingToVerified_recordsDecision() {
        UserProfile profile = pending();

        profile.verify(ACTOR_ID, DECIDED_AT, EXPIRES_AT);

        assertEquals(VerificationStatus.VERIFIED, profile.getVerificationStatus());
        assertEquals(ACTOR_ID, profile.getVerifiedByActorId());
        assertEquals(DECIDED_AT, profile.getVerifiedAt());
        assertEquals(EXPIRES_AT, profile.getAuthorizationExpiresAt());
        assertNull(profile.getRejectionReason());
    }

    @Test
    void verify_allowsNullExpiryForIndefiniteAuthorization() {
        UserProfile profile = pending();

        profile.verify(ACTOR_ID, DECIDED_AT, null);

        assertEquals(VerificationStatus.VERIFIED, profile.getVerificationStatus());
        assertNull(profile.getAuthorizationExpiresAt());
    }

    @Test
    void verify_rejectsExpiryNotAfterDecision() {
        UserProfile profile = pending();

        assertThrows(IllegalArgumentException.class, () -> profile.verify(ACTOR_ID, DECIDED_AT, DECIDED_AT));
        assertEquals(VerificationStatus.PENDING, profile.getVerificationStatus());
    }

    @Test
    void verify_requiresActorAndDecisionTime() {
        UserProfile profile = pending();

        assertThrows(IllegalArgumentException.class, () -> profile.verify(null, DECIDED_AT, EXPIRES_AT));
        assertThrows(IllegalArgumentException.class, () -> profile.verify(" ", DECIDED_AT, EXPIRES_AT));
        assertThrows(IllegalArgumentException.class, () -> profile.verify(ACTOR_ID, null, EXPIRES_AT));
        assertEquals(VerificationStatus.PENDING, profile.getVerificationStatus());
    }

    @Test
    void reject_pendingToRejected_recordsDecision() {
        UserProfile profile = pending();

        profile.reject(ACTOR_ID, DECIDED_AT, "  Giay phep het han  ");

        assertEquals(VerificationStatus.REJECTED, profile.getVerificationStatus());
        assertEquals(ACTOR_ID, profile.getVerifiedByActorId());
        assertEquals(DECIDED_AT, profile.getVerifiedAt());
        assertEquals("Giay phep het han", profile.getRejectionReason());
    }

    @Test
    void reject_requiresNonBlankReason() {
        UserProfile profile = pending();

        assertThrows(IllegalArgumentException.class, () -> profile.reject(ACTOR_ID, DECIDED_AT, null));
        assertThrows(IllegalArgumentException.class, () -> profile.reject(ACTOR_ID, DECIDED_AT, ""));
        assertThrows(IllegalArgumentException.class, () -> profile.reject(ACTOR_ID, DECIDED_AT, "   "));
        assertEquals(VerificationStatus.PENDING, profile.getVerificationStatus());
    }

    @Test
    void reject_requiresActorAndDecisionTime() {
        UserProfile profile = pending();

        assertThrows(IllegalArgumentException.class, () -> profile.reject(" ", DECIDED_AT, "reason"));
        assertThrows(IllegalArgumentException.class, () -> profile.reject(ACTOR_ID, null, "reason"));
        assertEquals(VerificationStatus.PENDING, profile.getVerificationStatus());
    }

    @Test
    void verifyAndReject_actorCannotDecideOwnProfile() {
        UserProfile profile = pending();

        assertThrows(SelfKycDecisionException.class, () -> profile.verify("user-123", DECIDED_AT, EXPIRES_AT));
        assertThrows(SelfKycDecisionException.class, () -> profile.reject("user-123", DECIDED_AT, "reason"));
        assertEquals(VerificationStatus.PENDING, profile.getVerificationStatus());
        assertNull(profile.getVerifiedByActorId());
    }

    @Test
    void resubmit_rejectedToPending_clearsReasonKeepsDecisionHistory() {
        UserProfile profile = rejected();

        profile.resubmit();

        assertEquals(VerificationStatus.PENDING, profile.getVerificationStatus());
        assertNull(profile.getRejectionReason());
        assertEquals(ACTOR_ID, profile.getVerifiedByActorId());
        assertEquals(DECIDED_AT, profile.getVerifiedAt());
    }

    @Test
    void resubmittedProfile_canBeVerified() {
        UserProfile profile = rejected();
        profile.resubmit();
        Instant secondDecision = DECIDED_AT.plusSeconds(3600);

        profile.verify("operator-2", secondDecision, EXPIRES_AT);

        assertEquals(VerificationStatus.VERIFIED, profile.getVerificationStatus());
        assertEquals("operator-2", profile.getVerifiedByActorId());
        assertEquals(secondDecision, profile.getVerifiedAt());
    }

    @Test
    void verified_cannotTransitionFurther() {
        UserProfile profile = pending();
        profile.verify(ACTOR_ID, DECIDED_AT, EXPIRES_AT);

        assertThrows(InvalidVerificationTransitionException.class,
                () -> profile.verify(ACTOR_ID, DECIDED_AT, EXPIRES_AT));
        assertThrows(InvalidVerificationTransitionException.class,
                () -> profile.reject(ACTOR_ID, DECIDED_AT, "reason"));
        assertThrows(InvalidVerificationTransitionException.class, profile::resubmit);
        assertEquals(VerificationStatus.VERIFIED, profile.getVerificationStatus());
    }

    @Test
    void rejected_cannotBeVerifiedOrRejectedAgain() {
        UserProfile profile = rejected();

        assertThrows(InvalidVerificationTransitionException.class,
                () -> profile.verify(ACTOR_ID, DECIDED_AT, EXPIRES_AT));
        assertThrows(InvalidVerificationTransitionException.class,
                () -> profile.reject(ACTOR_ID, DECIDED_AT, "again"));
        assertEquals(VerificationStatus.REJECTED, profile.getVerificationStatus());
    }

    @Test
    void pending_cannotBeResubmitted() {
        UserProfile profile = pending();

        assertThrows(InvalidVerificationTransitionException.class, profile::resubmit);
        assertEquals(VerificationStatus.PENDING, profile.getVerificationStatus());
    }

    @Test
    void updateContactInfo_updatesSuccessfully() {
        UserProfile profile = UserProfile.create(userId, "Cong ty A", Role.SELLER, contactInfo);
        ContactInfo newInfo = new ContactInfo("new@example.com", "0999999999", "HCM");

        profile.updateContactInfo(newInfo);

        assertEquals("new@example.com", profile.getContactInfo().email());
    }

    @Test
    void updateContactInfo_ignoresNull() {
        UserProfile profile = UserProfile.create(userId, "Cong ty A", Role.SELLER, contactInfo);

        profile.updateContactInfo(null);

        assertEquals("test@example.com", profile.getContactInfo().email());
    }

    // --- role assignment ---

    @Test
    void assignRole_newRole_changesRole() {
        UserProfile profile = withRole(Role.BUYER);

        assertTrue(profile.assignRole(Role.OPERATOR));
        assertEquals(Role.OPERATOR, profile.getRole());
    }

    @Test
    void assignRole_sameRole_noOp() {
        UserProfile profile = withRole(Role.ARBITRATOR);

        assertFalse(profile.assignRole(Role.ARBITRATOR));
        assertEquals(Role.ARBITRATOR, profile.getRole());
    }

    @Test
    void assignRole_operatorToArbitrator_throwsAndKeepsRole() {
        UserProfile profile = withRole(Role.OPERATOR);

        assertThrows(RoleSeparationViolationException.class, () -> profile.assignRole(Role.ARBITRATOR));
        assertEquals(Role.OPERATOR, profile.getRole());
    }

    @Test
    void assignRole_arbitratorToOperator_throwsAndKeepsRole() {
        UserProfile profile = withRole(Role.ARBITRATOR);

        assertThrows(RoleSeparationViolationException.class, () -> profile.assignRole(Role.OPERATOR));
        assertEquals(Role.ARBITRATOR, profile.getRole());
    }

    @Test
    void assignRole_adminToArbitrator_allowed() {
        UserProfile profile = withRole(Role.ADMIN);

        assertTrue(profile.assignRole(Role.ARBITRATOR));
        assertEquals(Role.ARBITRATOR, profile.getRole());
    }

    @Test
    void assignRole_null_throws() {
        UserProfile profile = withRole(Role.BUYER);

        assertThrows(IllegalArgumentException.class, () -> profile.assignRole(null));
        assertEquals(Role.BUYER, profile.getRole());
    }

    private UserProfile withRole(Role role) {
        return UserProfile.create(userId, "Cong ty A", role, contactInfo);
    }

    private UserProfile pending() {
        return UserProfile.create(userId, "Cong ty A", Role.SELLER, contactInfo);
    }

    private UserProfile rejected() {
        UserProfile profile = pending();
        profile.reject(ACTOR_ID, DECIDED_AT, "Thieu giay uy quyen");
        return profile;
    }
}
