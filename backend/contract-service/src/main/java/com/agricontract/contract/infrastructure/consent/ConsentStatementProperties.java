package com.agricontract.contract.infrastructure.consent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Which version of the SIGN_CONTRACT statements is in force. The text lives in
 * classpath:consent-statement/{code}-{version}.txt; the hash is computed from that file at startup.
 */
@ConfigurationProperties(prefix = "contract.consent")
public record ConsentStatementProperties(String signContractVersion, String cancelActivationVersion) {
}
