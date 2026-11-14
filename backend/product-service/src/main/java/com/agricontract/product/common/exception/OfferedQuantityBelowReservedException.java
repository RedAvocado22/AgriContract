package com.agricontract.product.common.exception;

import com.agricontract.web.error.ApiException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

/**
 * 409 (D48): an edit cannot offer less than what contracts already hold. Nothing is changed; the seller can lower the
 * offer to the reserved total at most, which closes the listing.
 */
public class OfferedQuantityBelowReservedException extends ApiException {
    public OfferedQuantityBelowReservedException(BigDecimal reservedQuantity) {
        super(HttpStatus.CONFLICT, "OFFERED_QUANTITY_BELOW_RESERVED",
                "offeredQuantity must be at least the reserved quantity " + reservedQuantity.toPlainString() + " kg");
    }
}
