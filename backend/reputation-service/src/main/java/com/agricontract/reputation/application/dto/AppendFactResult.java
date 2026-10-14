package com.agricontract.reputation.application.dto;
public record AppendFactResult(Status status) {
    public enum Status { INSERTED, DUPLICATE }
}
