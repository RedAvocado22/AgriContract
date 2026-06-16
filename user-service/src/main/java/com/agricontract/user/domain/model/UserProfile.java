package com.agricontract.user.domain.model;

import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import lombok.Getter;

import java.time.Instant;

// Aggregate Root
@Getter
public class UserProfile {

    private UserId userId;
    private String organizationName;
    private Role role;
    private ContactInfo contactInfo;
    private VerificationStatus verificationStatus;
    private Instant createdAt;
    private Instant updatedAt;

    private UserProfile() {}

    public static UserProfile create(String userId, String organizationName,
                                     Role role, String email, String phone, String address) {
        UserProfile p = new UserProfile();
        p.userId = new UserId(userId);
        p.organizationName = organizationName;
        p.role = role;
        p.contactInfo = new ContactInfo(email, phone, address);
        p.verificationStatus = VerificationStatus.PENDING;
        p.createdAt = p.updatedAt = Instant.now();
        return p;
    }

    public static UserProfile reconstitute(String userId, String organizationName,
                                            Role role, ContactInfo contactInfo,
                                            VerificationStatus verificationStatus) {
        UserProfile p = new UserProfile();
        p.userId = new UserId(userId);
        p.organizationName = organizationName;
        p.role = role;
        p.contactInfo = contactInfo;
        p.verificationStatus = verificationStatus;
        return p;
    }

    public void verify() {
        if (this.verificationStatus != VerificationStatus.PENDING)
            throw new IllegalStateException("Cannot verify from status: " + verificationStatus);
        this.verificationStatus = VerificationStatus.VERIFIED;
        this.updatedAt = Instant.now();
    }

    public void reject() {
        if (this.verificationStatus != VerificationStatus.PENDING)
            throw new IllegalStateException("Cannot reject from status: " + verificationStatus);
        this.verificationStatus = VerificationStatus.REJECTED;
        this.updatedAt = Instant.now();
    }

    public void updateContactInfo(ContactInfo newInfo) {
        this.contactInfo = newInfo;
        this.updatedAt = Instant.now();
    }
}
