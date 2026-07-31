package com.familya.search.domain.normalizer;

import java.text.Normalizer;

/**
 * Vietnamese-aware normaliser used by the search projection writers
 * and the application-layer query builder. Diacritics are stripped,
 * {@code đ}/{@code Đ} map to {@code d}/{@code D}, and whitespace is
 * collapsed. The surface form is preserved unchanged for display.
 */
public final class VietnameseNormalizer {

    private VietnameseNormalizer() { }

    public static String normalize(String input) {
        if (input == null) return "";
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(decomposed.length());
        decomposed.chars().forEach(c -> {
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                sb.append((char) c);
            }
        });
        String stripped = sb.toString()
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .trim();
        return stripped;
    }
}
