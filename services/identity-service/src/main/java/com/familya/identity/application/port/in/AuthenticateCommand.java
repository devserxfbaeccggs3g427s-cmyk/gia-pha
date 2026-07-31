package com.familya.identity.application.port.in;

import java.util.UUID;

/**
 * Command (đối tượng dữ liệu đầu vào) cho use case {@code AuthenticateUseCase}.
 *
 * <p>Trong kiến trúc hexagonal, các command thuộc về "port in" – là hợp
 * đồng giữa tầng adapter (controller REST, gRPC,…) và tầng application.
 * Chúng là các đối tượng bất biến (immutable record) giúp:
 * <ul>
 *     <li>Đảm bảo dữ liệu đầu vào không bị thay đổi giữa các tầng.</li>
 *     <li>Dễ dàng tuần tự hóa cho mục đích log / audit.</li>
 *     <li>Giữ cho use case không phụ thuộc vào framework (Spring, JAX-RS,…).</li>
 * </ul>
 *
 * @param email      địa chỉ email người dùng (đã được validate ở lớp controller).
 * @param password   mật khẩu thô do client gửi lên (sẽ được băm/so sánh ở use case).
 * @param ipAddress  địa chỉ IP client (phục vụ audit, rate limit).
 * @param userAgent  chuỗi User-Agent (phục vụ audit, bảo mật).
 */
public record AuthenticateCommand(
        String email,
        String password,
        String ipAddress,
        String userAgent
) { }
