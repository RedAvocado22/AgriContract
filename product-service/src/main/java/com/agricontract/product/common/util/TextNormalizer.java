package com.agricontract.product.common.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Strips Vietnamese diacritics + lowercases, so e.g. "Cà Phê" and "ca phe" compare equal.
 */
public final class TextNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private TextNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String decomposed = Normalizer.normalize(input.trim().toLowerCase(), Normalizer.Form.NFD);
        String stripped = DIACRITICS.matcher(decomposed).replaceAll("");
        return stripped.replace('đ', 'd').replace('Đ', 'd');
    }
}
