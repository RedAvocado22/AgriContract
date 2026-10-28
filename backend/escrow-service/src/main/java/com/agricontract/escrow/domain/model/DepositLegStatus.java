package com.agricontract.escrow.domain.model;

// #101 only creates legs. Result states (locked, failed after retries, ...) come with #102.
public enum DepositLegStatus {
    REQUESTED
}
