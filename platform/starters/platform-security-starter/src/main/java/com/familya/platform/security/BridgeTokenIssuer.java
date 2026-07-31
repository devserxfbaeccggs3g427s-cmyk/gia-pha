package com.familya.platform.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Bộ phát hành và xác minh Bridge token cho cầu nối NextAuth.
 *
 * <p>Bridge token là JWT bất đối xứng (thuật toán RS256) được ký bằng khóa RSA
 * riêng của dịch vụ phát hành. Token có các đặc điểm:</p>
 * <ul>
 *   <li>Audience được giới hạn theo từng dịch vụ nhận — tránh việc token của
 *       dịch vụ A bị lợi dụng để gọi dịch vụ B.</li>
 *   <li>Thời gian sống tối đa 5 phút (đã được giới hạn cứng trong
 *       {@link #BridgeTokenIssuer(String, long)}), đáp ứng chính sách
 *       giảm thiểu rủi ro của ADR-005.</li>
 *   <li>Khóa công khai được công bố qua endpoint JWKS của nền tảng để các
 *       dịch vụ khác tự xác minh.</li>
 * </ul>
 *
 * <p>Lớp này không được Spring quản lý tự động; mỗi dịch vụ phát hành cần tạo
 * một instance theo cấu hình riêng và lưu trữ vòng đời của nó.</p>
 *
 * @author Family Tree Platform Team
 */
public class BridgeTokenIssuer {

    /**
     * Khóa RSA dùng để ký và xác minh token. Được sinh ngẫu nhiên với độ dài
     * 2048 bit khi khởi tạo, đảm bảo tuân thủ yêu cầu bảo mật hiện đại.
     */
    private final RSAKey signingKey;

    /** Audience mặc định của token do issuer này phát hành. */
    private final String audience;

    /** Thời gian sống tối đa của token, tính bằng giây (đã được giới hạn ≤ 300). */
    private final long maxLifetimeSeconds;

    /**
     * Khởi tạo issuer với một cặp khóa RSA mới và thông số audience/lifetime.
     *
     * @param audience          audience mặc định của token
     * @param maxLifetimeSeconds thời gian sống tối đa (giây) yêu cầu từ phía dịch vụ;
     *                          giá trị thực tế sẽ được giới hạn ở mức tối đa 300 giây
     *                          để đảm bảo tuân thủ ADR-005.
     * @throws Exception nếu quá trình sinh khóa RSA thất bại
     */
    public BridgeTokenIssuer(String audience, long maxLifetimeSeconds) throws Exception {
        // Bước 1: Sinh cặp khóa RSA 2048-bit kèm keyID ngẫu nhiên. KeyID sẽ
        // được nhúng vào JWSHeader để bên xác minh chọn đúng khóa công khai.
        this.signingKey = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();

        // Bước 2: Lưu audience để gắn vào claim khi phát hành và kiểm tra khi xác minh.
        this.audience = audience;

        // Bước 3: Giới hạn cứng thời gian sống ở mức tối đa 300 giây (5 phút)
        // theo chính sách ADR-005, bất kể giá trị yêu cầu từ dịch vụ là bao nhiêu.
        this.maxLifetimeSeconds = Math.min(maxLifetimeSeconds, 300L);
    }

    /**
     * Phát hành một Bridge token mới cho người dùng.
     *
     * @param subject định danh người dùng (user id), sẽ được ghi vào claim {@code sub}
     * @param email   email của người dùng, được lưu trong custom claim {@code email}
     * @return chuỗi JWT đã được ký, sẵn sàng gửi về cho client
     * @throws Exception nếu quá trình ký JWT thất bại
     */
    public String issue(String subject, String email) throws Exception {
        // Bước 1: Xác định thời điểm hiện tại và xây dựng bộ claim cho token.
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)                                      // sub: định danh người dùng
                .audience(audience)                                    // aud: giới hạn audience theo dịch vụ
                .issuer("familya-nextauth-bridge")                     // iss: định danh issuer
                .issueTime(Date.from(now))                             // iat: thời điểm phát hành
                .expirationTime(Date.from(now.plusSeconds(maxLifetimeSeconds))) // exp: thời điểm hết hạn
                .claim("email", email)                                 // custom claim email
                .jwtID(UUID.randomUUID().toString())                  // jti: định danh duy nhất của token
                .build();

        // Bước 2: Tạo JWSHeader với thuật toán RS256 và keyID của khóa ký.
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signingKey.getKeyID())
                .build();

        // Bước 3: Ký JWT bằng khóa RSA riêng và tuần tự hóa thành chuỗi compact.
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    /** Độ lệch đồng hồ cho phép khi kiểm tra thời gian, tính bằng giây. */
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 30;

    /** Giá trị issuer kỳ vọng — phải khớp với giá trị dùng trong {@link #issue(String, String)}. */
    private static final String EXPECTED_ISSUER = "familya-nextauth-bridge";

    /**
     * Xác minh tính hợp lệ của một Bridge token.
     *
     * <p>Quy trình kiểm tra bao gồm: chữ ký, issuer, audience, thời hạn
     * (cho phép sai lệch đồng hồ {@value #ALLOWED_CLOCK_SKEW_SECONDS} giây)
     * và sự tồn tại của subject.</p>
     *
     * @param token chuỗi JWT cần xác minh
     * @return {@code true} nếu token hợp lệ, {@code false} nếu bất kỳ kiểm
     *         tra nào thất bại
     * @throws Exception nếu xảy ra lỗi phân tích cú pháp JWT
     */
    public boolean verify(String token) throws Exception {
        // Bước 1: Phân tích cú pháp JWT.
        SignedJWT jwt = SignedJWT.parse(token);

        // Bước 2: Xác minh chữ ký bằng khóa công khai RSA. Nếu chữ ký không
        // hợp lệ thì phủ nhận token ngay lập tức.
        if (!jwt.verify(new RSASSAVerifier(signingKey.toRSAPublicKey()))) {
            return false;
        }

        // Bước 3: Lấy tập claim và đảm bảo chúng tồn tại.
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        if (claims == null) {
            return false;
        }

        // Bước 4: Kiểm tra issuer để chống token từ nguồn khác.
        if (!EXPECTED_ISSUER.equals(claims.getIssuer())) {
            return false;
        }

        // Bước 5: Kiểm tra audience — token chỉ hợp lệ khi audience của nó
        // bao gồm audience mà issuer này phục vụ.
        if (claims.getAudience() == null || !claims.getAudience().contains(audience)) {
            return false;
        }

        // Bước 6: Kiểm tra thời hạn (exp) với độ lệch đồng hồ cho phép.
        Instant now = Instant.now();
        Date expiration = claims.getExpirationTime();
        if (expiration == null || expiration.toInstant().isBefore(now.minusSeconds(ALLOWED_CLOCK_SKEW_SECONDS))) {
            return false;
        }

        // Bước 7: Kiểm tra nbf (notBefore) nếu có — token không hợp lệ trước
        // thời điểm này (cộng thêm độ lệch đồng hồ).
        Date notBefore = claims.getNotBeforeTime();
        if (notBefore != null && notBefore.toInstant().isAfter(now.plusSeconds(ALLOWED_CLOCK_SKEW_SECONDS))) {
            return false;
        }

        // Bước 8: Subject phải tồn tại và không rỗng.
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            return false;
        }

        // Tất cả kiểm tra đều thỏa mãn — token hợp lệ.
        return true;
    }

    /**
     * Lấy khóa công khai dưới dạng {@link RSAKey} (chỉ chứa thành phần công khai)
     * để công bố qua endpoint JWKS của nền tảng.
     *
     * @return {@link RSAKey} chỉ chứa thành phần công khai (n, e, kid)
     */
    public RSAKey publicJwk() {
        // Chuyển đổi sang PublicJWK để đảm bảo thành phần riêng tư (private key)
        // không bao giờ bị lộ ra ngoài.
        return signingKey.toPublicJWK();
    }

    /**
     * Trả về keyID của cặp khóa ký hiện tại, dùng để tra cứu khóa trong JWKS.
     *
     * @return chuỗi keyID duy nhất
     */
    public String keyId() {
        return signingKey.getKeyID();
    }
}
