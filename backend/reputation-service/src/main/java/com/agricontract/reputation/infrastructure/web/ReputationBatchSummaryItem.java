package com.agricontract.reputation.infrastructure.web;

import java.math.BigDecimal;
import java.util.UUID;

public record ReputationBatchSummaryItem(UUID userId, BigDecimal score, boolean locked) {}
