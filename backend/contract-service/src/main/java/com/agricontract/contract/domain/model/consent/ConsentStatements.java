package com.agricontract.contract.domain.model.consent;

import com.agricontract.contract.domain.exception.ContractConflictException;

import java.util.Map;

/**
 * The active SIGN_CONTRACT statement for each signer role. One fixed sentence per role (D35): the contract's
 * figures are not embedded, the fingerprint shown under the checkbox binds the consent to the content.
 */
public final class ConsentStatements {
    private final Map<SignerRole, ConsentStatement> signContract;
    private final Map<SignerRole, ConsentStatement> cancelActivation;

    public ConsentStatements(Map<SignerRole, ConsentStatement> signContract,
                             Map<SignerRole, ConsentStatement> cancelActivation) {
        for (SignerRole role : SignerRole.values()) {
            if (!signContract.containsKey(role)) {
                throw new IllegalArgumentException("Missing SIGN_CONTRACT statement for " + role);
            }
            if (!cancelActivation.containsKey(role)) {
                throw new IllegalArgumentException("Missing CANCEL_ACTIVATION statement for " + role);
            }
        }
        this.signContract = Map.copyOf(signContract);
        this.cancelActivation = Map.copyOf(cancelActivation);
    }

    public static String cancelActivationCode(SignerRole role) {
        return "CANCEL_ACTIVATION_AS_" + role.name();
    }

    /** D42 cancel request: same rule as signing, only the version in force may be ticked. */
    public ConsentStatement requireActiveCancelActivation(SignerRole role, String presentedVersion) {
        return requireActive(cancelActivation.get(role), presentedVersion);
    }

    public static String signContractCode(SignerRole role) {
        return "SIGN_CONTRACT_AS_" + role.name();
    }

    public ConsentStatement activeSignContract(SignerRole role) {
        return signContract.get(role);
    }

    /** The client may only tick the version currently in force; an old page must reload. */
    public ConsentStatement requireActiveSignContract(SignerRole role, String presentedVersion) {
        return requireActive(signContract.get(role), presentedVersion);
    }

    private static ConsentStatement requireActive(ConsentStatement active, String presentedVersion) {
        if (!active.statementVersion().equals(presentedVersion)) {
            throw new ContractConflictException(ContractConflictException.CONSENT_STATEMENT_OUTDATED,
                    "Consent statement version " + presentedVersion + " is not in force; current is "
                            + active.statementVersion() + ". Reload the page.");
        }
        return active;
    }
}
