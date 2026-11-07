package com.agricontract.contract.domain.model.terms;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** VND amount with exactly two decimals. Travels as a string ("12345.00") so no float rounding touches money. */
public record VndAmount(BigDecimal value) implements Comparable<VndAmount> {
    private static final Pattern WIRE = Pattern.compile("^[0-9]+\\.[0-9]{2}$");

    public VndAmount {
        if (value == null || value.signum() < 0) {
            throw new InvalidContractTermsException("VND amount must be zero or positive");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new InvalidContractTermsException("VND amount has more than 2 decimals: " + value.toPlainString());
        }
        value = value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static VndAmount parse(String wire) {
        if (wire == null || !WIRE.matcher(wire).matches()) {
            throw new InvalidContractTermsException("VND amount must look like 12345.00: " + wire);
        }
        return new VndAmount(new BigDecimal(wire));
    }

    public String wire() {
        return value.toPlainString();
    }

    @Override
    public int compareTo(VndAmount other) {
        return value.compareTo(other.value);
    }
}
