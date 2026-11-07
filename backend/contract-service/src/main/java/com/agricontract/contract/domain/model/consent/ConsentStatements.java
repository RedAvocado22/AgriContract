package com.agricontract.contract.domain.model.consent;

import com.agricontract.contract.domain.exception.ContractConflictException;

import java.util.Map;

/**
 * The active SIGN_CONTRACT statement for each signer role. One fixed sentence per role (D35): the contract's
 * figures are not embedded, the fingerprint shown under the checkbox binds the consent to the content.
 */
public final class ConsentStatements {
    private final Map<SignerRole, ConsentStatement> signContract;

    public ConsentStatements(Map<SignerRole, ConsentStatement> signContract) {
        for (SignerRole role : SignerRole.values()) {
            if (!signContract.containsKey(role)) {
                throw new IllegalArgumentException("Missing SIGN_CONTRACT statement for " + role);
            }
        }
        this.signContract = Map.copyOf(signContract);
    }

    public static String signContractCode(SignerRole role) {
        return "SIGN_CONTRACT_AS_" + role.name();
    }

    public ConsentStatement activeSignContract(SignerRole role) {
        return signContract.get(role);
    }

    /** The client may only tick the version currently in force; an old page must reload. */
    public ConsentStatement requireActiveSignContract(SignerRole role, String presentedVersion) {
        ConsentStatement active = signContract.get(role);
        if (!active.statementVersion().equals(presentedVersion)) {
            throw new ContractConflictException(ContractConflictException.CONSENT_STATEMENT_OUTDATED,
                    "Consent statement version " + presentedVersion + " is not in force; current is "
                            + active.statementVersion() + ". Reload the page.");
        }
        return active;
    }
}
