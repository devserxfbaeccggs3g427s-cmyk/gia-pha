package com.familya.platform.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

/**
 * Bộ mã hóa/giải mã mật khẩu sử dụng thuật toán BCrypt.
 *
 * <p>Lớp này được thiết kế để tương thích với hệ thống cũ vốn sử dụng tiền tố
 * {@code 2a} cho chuỗi hash. BCrypt verifier chấp nhận đồng thời cả {@code 2a}
 * và {@code 2y} và luôn rehash sang {@code 2y} khi đăng nhập thành công để
 * hiện đại hóa dần kho mật khẩu mà không cần một đợt migration riêng biệt.</p>
 *
 * <p><b>Các đặc điểm chính:</b></p>
 * <ul>
 *   <li>Cost factor mặc định là {@code 10}, đảm bảo thời gian tính toán khoảng
 *       60-100ms trên phần cứng phổ biến.</li>
 *   <li>{@link #needsRehash(String)} luôn trả về {@code true} để đáp ứng
 *       chính sách bắt buộc rehash của ADR-009 — mọi lần đăng nhập thành
 *       công đều nâng cấp hash.</li>
 *   <li>Sử dụng thư viện {@code at.favre.lib:bcrypt} — một triển khai BCrypt
 *       thuần Java, không phụ thuộc vào JNI.</li>
 * </ul>
 *
 * @author Family Tree Platform Team
 */
@Component
public class PasswordEncoder {

    /**
     * Cost factor của BCrypt. Giá trị 10 được chọn để cân bằng giữa tốc độ
     * phản hồi và khả năng chống brute-force, phù hợp với phần cứng phổ biến
     * của môi trường cloud hiện nay.
     */
    private static final int COST = 10;

    /**
     * Băm mật khẩu thô thành chuỗi BCrypt {@code 2y} với cost factor đã cấu hình.
     *
     * @param raw mật khẩu thô cần băm; giá trị được chuyển sang mảng {@code char}
     *            để giảm thời gian tồn tại trong bộ nhớ heap (so với {@code String}).
     * @return chuỗi BCrypt đã băm, bao gồm cả tiền tố phiên bản thuật toán.
     */
    public String hash(String raw) {
        // Bước 1: Sử dụng BCrypt với cấu hình mặc định (random salt, cost 10).
        // Thư viện sẽ tự sinh salt ngẫu nhiên cho mỗi lần hash để đảm bảo
        // hai mật khẩu giống nhau cho ra hai chuỗi hash khác nhau.
        return BCrypt.withDefaults().hashToString(COST, raw.toCharArray());
    }

    /**
     * Xác minh mật khẩu thô có khớp với chuỗi hash đã lưu hay không.
     *
     * @param raw    mật khẩu thô do người dùng nhập
     * @param stored chuỗi BCrypt đã lưu trong cơ sở dữ liệu
     * @return {@code true} nếu mật khẩu khớp, {@code false} trong trường hợp
     *         ngược lại (bao gồm cả khi {@code stored} rỗng hoặc {@code null}).
     */
    public boolean matches(String raw, String stored) {
        // Bước 1: Kiểm tra nhanh — nếu chuỗi hash không tồn tại thì chắc chắn
        // không khớp. Điều này cũng tránh cho BCrypt ném exception khi gặp
        // đầu vào null hoặc chuỗi rỗng.
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        // Bước 2: Ủy quyền xác minh cho BCrypt, chuyển mật khẩu thô sang
        // mảng char để hạn chế thời gian sống trong bộ nhớ.
        BCrypt.Result r = BCrypt.verifyer().verify(raw.toCharArray(), stored);
        // Bước 3: Trả về kết quả xác minh từ thư viện.
        return r.verified;
    }

    /**
     * Cho biết chuỗi hash đã lưu có cần được rehash khi người dùng đăng nhập
     * thành công hay không.
     *
     * <p>Theo chính sách của ADR-009, hệ thống luôn rehash khi đăng nhập để
     * dần chuẩn hóa mọi chuỗi hash sang thuật toán và cost factor mới nhất.
     * Vì vậy phương thức này luôn trả về {@code true}.</p>
     *
     * @param stored chuỗi hash đang được kiểm tra (tham số không được sử dụng
     *               nhưng giữ lại để tương thích với các API kiểu {@code UserDetailsService}).
     * @return luôn luôn {@code true}.
     */
    public boolean needsRehash(String stored) {
        // Luôn trả về true để thỏa mãn chính sách rehash bắt buộc của ADR-009.
        return true;
    }
}
