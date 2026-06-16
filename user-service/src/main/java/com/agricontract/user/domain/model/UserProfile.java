package com.agricontract.user.domain.model;

import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;
import lombok.Getter;

// Aggregate Root
@Getter
public class UserProfile {

    private UserId userId;
    private String organizationName;
    private Role role;
    private ContactInfo contactInfo;
    private VerificationStatus verificationStatus;

    private UserProfile() {}

    public static UserProfile create(UserId userId, String organizationName,
                                     Role role, ContactInfo contactInfo) {

        throw new UnsupportedOperationException("TODO");
    }

    public void verify() { /* TODO */ }

    public void reject() { /* TODO */ }

    public void updateContactInfo(ContactInfo newInfo) { /* TODO */ }

    public static UserProfile reconstitute(UserId userId, String organizationName,
                                           Role role, ContactInfo contactInfo,
                                           VerificationStatus verificationStatus) {
        UserProfile p = new UserProfile();
        p.userId = userId;
        p.organizationName = organizationName;
        p.role = role;
        p.contactInfo = contactInfo;
        p.verificationStatus = verificationStatus;
        return p;
    }
}
