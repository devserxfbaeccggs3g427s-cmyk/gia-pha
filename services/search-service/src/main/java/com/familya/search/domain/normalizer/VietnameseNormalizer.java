package com.familya.search.domain.normalizer;

import java.text.Normalizer;

/**
 * Bộ chuẩn hoá (normalizer) có nhận thức tiếng Việt, dùng chung cho cả phía
 * ghi projection lẫn phía truy vấn của tầng ứng dụng.
 *
 * <p>Mục tiêu là đưa mọi chuỗi về cùng một dạng so sánh được:</p>
 * <ul>
 *   <li>Bỏ toàn bộ dấu thanh, dấu mũ, dấu nặng... bằng cách tách theo
 *       {@link Normalizer.Form#NFD} rồi loại bỏ các ký tự
 *       {@link Character#NON_SPACING_MARK}.</li>
 *   <li>Đổi {@code đ}/{@code Đ} thành {@code d}/{@code D} - vì ký tự
 *       này không có dạng tách rời dấu nên cần xử lý riêng.</li>
 *   <li>Chuyển về chữ thường để so sánh không phân biệt hoa/thường.</li>
 *   <li>Gom nhiều khoảng trắng liên tiếp thành một, rồi cắt khoảng trắng
 *       đầu/cuối.</li>
 * </ul>
 *
 * <p>Dạng "bề mặt" (surface) - tức chuỗi gốc có dấu - được giữ nguyên để
 * hiển thị; chỉ dạng đã chuẩn hoá mới dùng để đối chiếu truy vấn.</p>
 */
public final class VietnameseNormalizer {

    private VietnameseNormalizer() { }

    /**
     * Chuẩn hoá chuỗi đầu vào theo các bước mô tả ở Javadoc lớp.
     *
     * @param input chuỗi cần chuẩn hoá, có thể {@code null}.
     * @return chuỗi đã bỏ dấu, đổi {@code đ/Đ}, chữ thường, gọn khoảng trắng;
     *         trả về chuỗi rỗng khi đầu vào là {@code null}.
     */
    public static String normalize(String input) {
        // Bảo vệ an toàn: null được coi như chuỗi rỗng để phía truy vấn
        // không phải kiểm tra riêng.
        if (input == null) return "";
        // Bước 1: Tách chuỗi thành dạng NFD - tách ký tự gốc và các dấu
        // (combining diacritical marks) thành các codepoint riêng biệt.
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(decomposed.length());
        decomposed.chars().forEach(c -> {
            // Bước 2: Chỉ giữ lại những ký tự KHÔNG phải dấu kết hợp
            // (NON_SPACING_MARK). Nhờ vậy các dấu thanh/đầu bị loại bỏ.
            if (Character.getType(c) != Character.NON_SPACING_MARK) {
                sb.append((char) c);
            }
        });
        // Bước 3: Chuẩn hoá các ký tự đặc thù tiếng Việt còn sót lại
        // (đ/Đ không có dạng tách dấu riêng), chuyển về chữ thường và
        // gọn khoảng trắng trước khi trả về.
        String stripped = sb.toString()
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .trim();
        return stripped;
    }
}
