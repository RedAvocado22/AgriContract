package com.agricontract.user.domain.model;

import com.agricontract.user.domain.event.UserRegisteredEvent;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserProfileTest {

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
    void create_addsUserRegisteredEvent() {
        UserProfile profile = UserProfile.create(userId, "Cong ty A", Role.SELLER, contactInfo);

        List<UserRegisteredEvent> events = profile.pullDomainEvents();
        assertEquals(1, events.size());
        assertEquals("user-123", events.get(0).getUserId());
    }

    @Test
    void reconstitute_doesNotAddDomainEvents() {
        UserProfile profile = UserProfile.reconstitute(userId, "Cong ty A", Role.SELLER, contactInfo, VerificationStatus.VERIFIED);

        assertTrue(profile.pullDomainEvents().isEmpty());
    }

    @Test
    void pullDomainEvents_clearsListAfterPull() {
        UserProfile profile = UserProfile.create(userId, "Cong ty A", Role.SELLER, contactInfo);

        profile.pullDomainEvents();
        assertTrue(profile.pullDomainEvents().isEmpty());
    }

    @Test
    void verify_pendingToVerified() {
        UserProfile profile = UserProfile.create(userId, "Cong ty A", Role.SELLER, contactInfo);

        profile.verify();

        assertEquals(VerificationStatus.VERIFIED, profile.getVerificationStatus());
    }

    @Test
    void verify_throwsIfNotPending() {
        UserProfile profile = UserProfile.create(userId, "Cong ty A", Role.SELLER, contactInfo);
        profile.verify();

        assertThrows(IllegalStateException.class, profile::verify);
    }

    @Test
    void reject_pendingToRejected() {
        UserProfile profile = UserProfile.create(userId, "Cong ty A", Role.SELLER, contactInfo);

        profile.reject();

        assertEquals(VerificationStatus.REJECTED, profile.getVerificationStatus());
    }

    @Test
    void reject_throwsIfNotPending() {
        UserProfile profile = UserProfile.create(userId, "Cong ty A", Role.SELLER, contactInfo);
        profile.reject();

        assertThrows(IllegalStateException.class, profile::reject);
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
}
