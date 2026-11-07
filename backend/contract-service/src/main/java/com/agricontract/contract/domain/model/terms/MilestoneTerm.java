package com.agricontract.contract.domain.model.terms;

import java.math.BigDecimal;
import java.time.LocalDate;

/** committedQuantity is in kilograms. expectedDeliveryDate expires at 23:59:59.999 Asia/Ho_Chi_Minh. */
public record MilestoneTerm(
        Integer milestoneIndex,
        BigDecimal committedQuantity,
        LocalDate expectedDeliveryDate,
        Integer graceDays) {
}
