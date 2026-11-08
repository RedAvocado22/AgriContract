package com.agricontract.contract.application.dto;

import java.util.UUID;

public record RetryDepositLockCommand(String contractId, String operatorUserId, UUID idempotencyKey) {
}
