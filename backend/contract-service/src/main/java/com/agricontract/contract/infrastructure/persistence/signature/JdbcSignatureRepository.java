package com.agricontract.contract.infrastructure.persistence.signature;

import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.signature.Signature;
import com.agricontract.contract.domain.repository.SignatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcSignatureRepository implements SignatureRepository {
    private static final RowMapper<Signature> ROW = (rs, n) -> new Signature(
            UUID.fromString(rs.getString("signature_id")),
            rs.getString("contract_id"),
            SignerRole.valueOf(rs.getString("signer_role")),
            rs.getString("signer_user_id"),
            UUID.fromString(rs.getString("otp_id")),
            UUID.fromString(rs.getString("consent_id")),
            rs.getTimestamp("auth_time").toInstant(),
            rs.getTimestamp("signed_at").toInstant(),
            rs.getString("signed_content_hash"),
            rs.getString("ip_address"));

    private final JdbcTemplate jdbc;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Signature s) {
        jdbc.update("INSERT INTO signature (signature_id, contract_id, signer_role, signer_user_id, otp_id,"
                        + " consent_id, auth_time, signed_at, signed_content_hash, ip_address)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                s.signatureId().toString(), s.contractId(), s.signerRole().name(), s.signerUserId(),
                s.otpId().toString(), s.consentId().toString(), Timestamp.from(s.authTime()),
                Timestamp.from(s.signedAt()), s.signedContentHash(), s.ipAddress());
    }

    @Override
    public Map<SignerRole, String> signedHashes(String contractId) {
        Map<SignerRole, String> hashes = new EnumMap<>(SignerRole.class);
        jdbc.query("SELECT signer_role, signed_content_hash FROM signature WHERE contract_id = ?",
                rs -> {
                    hashes.put(SignerRole.valueOf(rs.getString("signer_role")), rs.getString("signed_content_hash"));
                }, contractId);
        return hashes;
    }

    @Override
    public List<Signature> findByContractId(String contractId) {
        return jdbc.query("SELECT * FROM signature WHERE contract_id = ? ORDER BY signed_at", ROW, contractId);
    }
}
