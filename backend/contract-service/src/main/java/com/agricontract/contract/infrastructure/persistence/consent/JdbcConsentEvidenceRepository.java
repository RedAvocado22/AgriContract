package com.agricontract.contract.infrastructure.persistence.consent;

import com.agricontract.contract.domain.model.consent.ConsentEvidence;
import com.agricontract.contract.domain.repository.ConsentEvidenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

/** Plain INSERT; the table's triggers refuse UPDATE and DELETE (V8). */
@Repository
@RequiredArgsConstructor
public class JdbcConsentEvidenceRepository implements ConsentEvidenceRepository {
    private final JdbcTemplate jdbc;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(ConsentEvidence e) {
        jdbc.update("INSERT INTO consent_evidence (consent_id, action_type, subject_type, subject_id, actor_user_id,"
                        + " actor_role, statement_code, statement_version, statement_hash, presented_content_hash,"
                        + " consented_at, correlation_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                e.consentId().toString(), e.actionType().name(), e.subjectType().name(), e.subjectId(),
                e.actorUserId(), e.actorRole().name(), e.statementCode(), e.statementVersion(), e.statementHash(),
                e.presentedContentHash(), Timestamp.from(e.consentedAt()), e.correlationId());
    }
}
