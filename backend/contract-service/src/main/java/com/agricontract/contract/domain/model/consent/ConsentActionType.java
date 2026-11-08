package com.agricontract.contract.domain.model.consent;

/** The behaviour a checkbox statement is about (contract-document-consent design §4). */
public enum ConsentActionType {
    SIGN_CONTRACT,
    /** D42: a party asks to stop a SIGNED contract whose deposit lock failed. Same high-risk tier as signing. */
    CANCEL_ACTIVATION
}
