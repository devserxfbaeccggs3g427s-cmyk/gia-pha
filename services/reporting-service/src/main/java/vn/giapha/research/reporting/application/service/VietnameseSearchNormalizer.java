package vn.giapha.research.reporting.application.service;

import java.text.Normalizer;
import java.util.Locale;

final class VietnameseSearchNormalizer {

    private VietnameseSearchNormalizer() {
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace('đ', 'd').replace('Đ', 'D');
        return decomposed.replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).trim();
    }
}
