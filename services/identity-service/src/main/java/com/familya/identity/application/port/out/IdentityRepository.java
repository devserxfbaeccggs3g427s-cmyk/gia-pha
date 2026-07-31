package com.familya.identity.application.port.out;

import com.familya.identity.domain.model.EmailVerificationToken;
import com.familya.identity.domain.model.OAuthLink;
import com.familya.identity.domain.model.Session;
import com.familya.identity.domain.model.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (cổng) ra của tầng application dùng để truy cập dữ liệu identity.
 *
 * <p>Đây là hợp đồng giữa tầng use case và tầng persistence. Mọi thao
 * tác đọc/ghi dữ liệu người dùng, phiên, liên kết OAuth và token xác
 * minh email đều đi qua interface này. Triển khai mặc định trong
 * service là
 * {@link com.familya.identity.adapter.out.persistence.JdbcIdentityRepository}
 * (sử dụng JDBC).
 *
 * <p>Thiết kế này tuân thủ nguyên tắc Dependency Inversion – use case
 * không phụ thuộc vào chi tiết cơ sở dữ liệu.
 *
 * @author Familya Platform Team
 * @since 1.0.0
 */
public interface IdentityRepository {
    /**
     * Tìm người dùng theo {@code id}.
     *
     * @param id UUID người dùng.
     * @return {@link Optional} chứa {@link User} nếu tồn tại, ngược lại
     *         trả về {@link Optional#empty()}.
     */
    Optional<User> findById(UUID id);

    /**
     * Tìm người dùng theo email đã chuẩn hóa (lowercase, trim).
     *
     * @param normalizedEmail email chuẩn hóa.
     * @return {@link Optional} chứa {@link User} nếu tồn tại.
     */
    Optional<User> findByNormalizedEmail(String normalizedEmail);

    /**
     * Chèn mới một người dùng kèm danh sách liên kết OAuth.
     *
     * @param user        aggregate người dùng.
     * @param oAuthLinks  danh sách liên kết OAuth (có thể rỗng).
     */
    void insert(User user, List<OAuthLink> oAuthLinks);

    /**
     * Cập nhật thông tin người dùng (triển khai phải áp dụng optimistic
     * locking thông qua cột {@code version}).
     *
     * @param user aggregate người dùng cần cập nhật.
     */
    void update(User user);

    /**
     * Chèn một phiên đăng nhập mới.
     *
     * @param session phiên cần chèn.
     */
    void insertSession(Session session);

    /**
     * Tìm phiên đăng nhập theo {@code id}.
     *
     * @param sessionId UUID phiên.
     * @return {@link Optional} chứa {@link Session} nếu tồn tại.
     */
    Optional<Session> findSession(UUID sessionId);

    /**
     * Cập nhật trạng thái phiên (thường dùng để thu hồi).
     *
     * @param session phiên với cờ mới.
     */
    void updateSession(Session session);

    /**
     * Chèn một liên kết OAuth cho người dùng.
     *
     * @param link liên kết OAuth cần chèn.
     */
    void insertOAuthLink(OAuthLink link);

    /**
     * Tìm token xác minh email theo chuỗi token.
     *
     * @param token chuỗi token cần tra cứu.
     * @return {@link Optional} chứa {@link EmailVerificationToken} nếu tồn tại.
     */
    Optional<EmailVerificationToken> findEmailVerificationToken(String token);

    /**
     * Đánh dấu token xác minh email đã được sử dụng.
     *
     * @param token      chuỗi token cần đánh dấu.
     * @param consumedAt thời điểm tiêu thụ.
     */
    void consumeEmailVerificationToken(String token, Instant consumedAt);
}
