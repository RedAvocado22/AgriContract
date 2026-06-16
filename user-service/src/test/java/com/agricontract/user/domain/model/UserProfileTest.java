package com.agricontract.user.domain.model;

import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserProfileTest {

    private UserProfile buildProfile() {
        return UserProfile.create(
                "keycloak-sub-123",
                "Cong ty ABC",
                Role.SELLER,
                "seller@example.com",
                "0901234567",
                "123 Le Loi, HCM"
        );
    }

    @Test
    void create_shouldSetVerificationStatusToPending() {
        UserProfile profile = buildProfile();
        assertEquals(VerificationStatus.PENDING, profile.getVerificationStatus());
    }

    @Test
    void verify_whenPending_shouldTransitionToVerified() {
        UserProfile profile = buildProfile();
        profile.verify();
        assertEquals(VerificationStatus.VERIFIED, profile.getVerificationStatus());
    }

    @Test
    void verify_whenAlreadyVerified_shouldThrowIllegalStateException() {
        UserProfile profile = buildProfile();
        profile.verify();
        assertThrows(IllegalStateException.class, profile::verify);
    }

    @Test
    void reject_whenPending_shouldTransitionToRejected() {
        UserProfile profile = buildProfile();
        profile.reject();
        assertEquals(VerificationStatus.REJECTED, profile.getVerificationStatus());
    }

    @Test
    void updateContactInfo_shouldUpdatePhoneAndAddress() {
        UserProfile profile = buildProfile();
        var newInfo = new com.agricontract.user.domain.model.vo.ContactInfo(
                "seller@example.com", "0999999999", "456 Nguyen Hue"
        );
        profile.updateContactInfo(newInfo);
        assertEquals("0999999999", profile.getContactInfo().phone());
        assertEquals("456 Nguyen Hue", profile.getContactInfo().address());
    }
}
