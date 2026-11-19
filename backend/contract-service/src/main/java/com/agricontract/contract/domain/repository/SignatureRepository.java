package com.agricontract.contract.domain.repository;

import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.signature.Signature;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.Map;

public interface SignatureRepository {

    /** Must run in the verify transaction; UNIQUE(contract_id, signer_role) refuses a second row for a party. */
    void insert(Signature signature);

    /** signedContentHash of each party that has signed this contract; empty when nobody has. */
    Map<SignerRole, String> signedHashes(String contractId);

    List<Signature> findByContractId(String contractId);

    /** The signature this code produced, if any (UNIQUE otp_id: one code signs at most once). */
    Optional<Signature> findByOtpId(UUID otpId);
}
