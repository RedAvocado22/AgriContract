package com.agricontract.contract.domain.model.consent;

/**
 * One server-owned checkbox sentence. statementHash = SHA-256 of exactly the text shown; changing a word means a
 * new version, never editing an existing one.
 */
public record ConsentStatement(String statementCode, String statementVersion, String text, String statementHash) {
}
