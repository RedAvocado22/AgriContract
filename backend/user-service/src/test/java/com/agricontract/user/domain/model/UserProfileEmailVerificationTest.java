package com.agricontract.user.domain.model;

import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileEmailVerificationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private static UserProfile registered(String email) {
        return UserProfile.create(new UserId("user-1"), "HTX Ca Phe", Role.SELLER, new ContactInfo(email, null, null));
    }

    @Test
    void create_bodyEmailIsUnverified() {
        UserProfile profile = registered("seller@example.com");

        assertThat(profile.isEmailVerified()).isFalse();
        assertThat(profile.verifiedContactEmail()).isNull();
    }

    @Test
    void markEmailVerified_contactEmailBecomesRecipient() {
        UserProfile profile = registered("seller@example.com");

        profile.markEmailVerified("SELLER@example.com", NOW);

        assertThat(profile.isEmailVerified()).isTrue();
        assertThat(profile.verifiedContactEmail()).isEqualTo("seller@example.com");
        assertThat(profile.getVerifiedEmail()).isEqualTo("seller@example.com");
        assertThat(profile.getEmailVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void markEmailVerified_codeForAnotherAddress_refused() {
        UserProfile profile = registered("seller@example.com");

        assertThatThrownBy(() -> profile.markEmailVerified("old@example.com", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(profile.isEmailVerified()).isFalse();
    }

    @Test
    void updateContactInfo_newAddress_dropsProof() {
        UserProfile profile = registered("seller@example.com");
        profile.markEmailVerified("seller@example.com", NOW);

        profile.updateContactInfo(new ContactInfo("new@example.com", null, null));

        assertThat(profile.isEmailVerified()).isFalse();
        assertThat(profile.verifiedContactEmail()).isNull();
        assertThat(profile.getVerifiedEmail()).isNull();
    }

    @Test
    void updateContactInfo_sameAddress_keepsProof() {
        UserProfile profile = registered("seller@example.com");
        profile.markEmailVerified("seller@example.com", NOW);

        profile.updateContactInfo(new ContactInfo("seller@example.com", "0900000000", "Dak Lak"));

        assertThat(profile.isEmailVerified()).isTrue();
    }
}
