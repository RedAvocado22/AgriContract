package com.agricontract.product.infrastructure.messaging;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Field readers shared by the contract event consumers. They throw IllegalArgumentException naming the field only,
 * never its value, because the consumers log the message.
 */
final class ContractEventPayloads {

    private ContractEventPayloads() {
    }

    // The codec reads decimals as Double. Going through toString keeps the shortest decimal form, which is exact for
    // every value reserved_quantity DECIMAL(15,3) can hold; new BigDecimal(double) would not be.
    static BigDecimal quantity(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("reservedQuantity must be a positive number");
        }
        BigDecimal quantity = new BigDecimal(number.toString());
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("reservedQuantity must be a positive number");
        }
        return quantity;
    }

    static String text(Map<String, Object> payload, String field) {
        if (!(payload.get(field) instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + field);
        }
        return value;
    }

    // validated as a UUID but kept verbatim, so it matches the id the reserve command stored
    static String uuidText(Map<String, Object> payload, String field) {
        String value = text(payload, field);
        UUID.fromString(value);
        return value;
    }
}
