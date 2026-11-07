package com.agricontract.contract.domain.model.consent;

/** Which side of the contract the signer claims to be; must match the contract's buyer or seller. */
public enum SignerRole {
    BUYER,
    SELLER
}
