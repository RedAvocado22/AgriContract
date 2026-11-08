package com.agricontract.contract.infrastructure.persistence.signature;

import com.agricontract.contract.domain.model.consent.ConsentActionType;
import com.agricontract.contract.domain.model.consent.SignerRole;
import com.agricontract.contract.domain.model.signature.OtpChallenge;
import com.agricontract.contract.domain.repository.OtpChallengeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcOtpChallengeRepository implements OtpChallengeRepository {
    private static final RowMapper<OtpChallenge> ROW = (rs, n) -> new OtpChallenge(
            UUID.fromString(rs.getString("otp_id")),
            UUID.fromString(rs.getString("consent_id")),
            rs.getString("contract_id"),
            SignerRole.valueOf(rs.getString("signer_role")),
            rs.getString("user_id"),
            rs.getTimestamp("auth_time").toInstant(),
            rs.getString("signed_content_hash"),
            rs.getString("otp_hash"),
            rs.getTimestamp("sent_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(),
            Optional.ofNullable(rs.getTimestamp("verified_at")).map(Timestamp::toInstant).orElse(null),
            rs.getInt("attempt_count"),
            ConsentActionType.valueOf(rs.getString("action_type")));

    private final JdbcTemplate jdbc;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int expireUsableChallenges(String contractId, String userId, SignerRole signerRole,
                                      ConsentActionType action, String signedContentHash, Instant now,
                                      int maxAttempts) {
        // Lock first: a concurrent initiate/resend on this contract waits here until this transaction commits,
        // then its UPDATE (a locking read, so it sees committed rows) kills the challenge this one inserts.
        jdbc.queryForList("SELECT contract_id FROM contracts WHERE contract_id = ? FOR UPDATE", contractId);
        Timestamp at = Timestamp.from(now);
        return jdbc.update("UPDATE signature_otp SET expires_at = ? WHERE contract_id = ? AND user_id = ?"
                        + " AND signer_role = ? AND action_type = ? AND signed_content_hash = ? AND verified_at IS NULL"
                        + " AND expires_at > ? AND attempt_count < ?",
                at, contractId, userId, signerRole.name(), action.name(), signedContentHash, at, maxAttempts);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(OtpChallenge c) {
        jdbc.update("INSERT INTO signature_otp (otp_id, consent_id, contract_id, signer_role, user_id, auth_time,"
                        + " signed_content_hash, otp_hash, sent_at, expires_at, verified_at, attempt_count, action_type)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                c.otpId().toString(), c.consentId().toString(), c.contractId(), c.signerRole().name(), c.userId(),
                Timestamp.from(c.authTime()), c.signedContentHash(), c.otpHash(), Timestamp.from(c.sentAt()),
                Timestamp.from(c.expiresAt()), c.verifiedAt() == null ? null : Timestamp.from(c.verifiedAt()),
                c.attemptCount(), c.actionType().name());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Instant> lastSentAt(String contractId, String userId, SignerRole signerRole,
                                        ConsentActionType action) {
        Timestamp last = jdbc.queryForObject("SELECT MAX(sent_at) FROM signature_otp"
                + " WHERE contract_id = ? AND user_id = ? AND signer_role = ? AND action_type = ?", Timestamp.class,
                contractId, userId, signerRole.name(), action.name());
        return Optional.ofNullable(last).map(Timestamp::toInstant);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int countSentSince(String contractId, String userId, SignerRole signerRole, ConsentActionType action,
                              Instant since) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM signature_otp WHERE contract_id = ?"
                        + " AND user_id = ? AND signer_role = ? AND action_type = ? AND sent_at > ?", Integer.class,
                contractId, userId, signerRole.name(), action.name(), Timestamp.from(since));
        return count == null ? 0 : count;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void expire(UUID otpId, Instant now) {
        Timestamp at = Timestamp.from(now);
        jdbc.update("UPDATE signature_otp SET expires_at = ? WHERE otp_id = ? AND expires_at > ?",
                at, otpId.toString(), at);
    }

    @Override
    public Optional<OtpChallenge> findById(UUID otpId) {
        return jdbc.query("SELECT * FROM signature_otp WHERE otp_id = ?", ROW, otpId.toString()).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<OtpChallenge> findByIdForUpdate(UUID otpId) {
        return jdbc.query("SELECT * FROM signature_otp WHERE otp_id = ? FOR UPDATE", ROW, otpId.toString())
                .stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int recordFailedAttempt(UUID otpId) {
        jdbc.update("UPDATE signature_otp SET attempt_count = attempt_count + 1 WHERE otp_id = ?", otpId.toString());
        Integer count = jdbc.queryForObject("SELECT attempt_count FROM signature_otp WHERE otp_id = ?",
                Integer.class, otpId.toString());
        return count == null ? 0 : count;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markVerified(UUID otpId, Instant verifiedAt) {
        int updated = jdbc.update("UPDATE signature_otp SET verified_at = ? WHERE otp_id = ? AND verified_at IS NULL",
                Timestamp.from(verifiedAt), otpId.toString());
        if (updated != 1) {
            throw new IllegalStateException("Challenge " + otpId + " was already verified");
        }
    }
}
