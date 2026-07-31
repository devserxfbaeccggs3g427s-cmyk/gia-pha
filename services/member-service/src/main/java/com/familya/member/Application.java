package com.familya.member;

import com.familya.member.application.usecase.CreateMemberUseCase;
import com.familya.member.application.usecase.MergeMembersUseCase;
import com.familya.member.application.usecase.TombstoneMemberUseCase;
import com.familya.member.application.usecase.UpdateMemberUseCase;
import com.familya.member.application.port.out.MemberRepository;
import com.familya.member.domain.model.MemberAuthRow;
import com.familya.platform.projection.AuthorizationProjection;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Lớp khởi chạy (entry point) của Member Service — một microservice thuộc hệ thống
 * gia phả thế hệ mới. Lớp này đăng ký các bean cần thiết cho toàn bộ ứng dụng:
 * các {@code Clock} dùng để lấy thời gian trong các use case, {@link java.time.Clock}
 * hệ thống cho các tác vụ nền tảng, và {@link AuthorizationProjection} cho phép
 * kiểm tra quyền truy cập dựa trên projection cục bộ.
 *
 * <p>Việc quét thêm package {@code com.familya.platform} cho phép Member Service
 * tận dụng các thành phần dùng chung của platform (projection SDK, outbox, v.v.).
 */
@SpringBootApplication(scanBasePackages = { "com.familya.member", "com.familya.platform" })
@EnableScheduling
public class Application {

    /**
     * Hàm main chuẩn của Spring Boot — khởi động ApplicationContext, nạp cấu hình
     * và đăng ký các bean.
     *
     * @param args các tham số dòng lệnh được chuyển tới JVM
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /** Cung cấp đồng hồ cho {@link CreateMemberUseCase} — dùng {@link Instant#now()} làm nguồn thời gian. */
    @Bean public CreateMemberUseCase.Clock createClock()      { return Instant::now; }
    /** Cung cấp đồng hồ cho {@link com.familya.member.application.usecase.UpdateMemberUseCase} — dùng {@link Instant#now()}. */
    @Bean public UpdateMemberUseCase.Clock updateClock()      { return Instant::now; }
    /** Cung cấp đồng hồ cho {@link com.familya.member.application.usecase.TombstoneMemberUseCase} — dùng {@link Instant#now()}. */
    @Bean public TombstoneMemberUseCase.Clock tombstoneClock() { return Instant::now; }
    /** Cung cấp đồng hồ cho {@link com.familya.member.application.usecase.MergeMembersUseCase} — dùng {@link Instant#now()}. */
    @Bean public MergeMembersUseCase.Clock mergeClock()       { return Instant::now; }
    /** Cung cấp {@link java.time.Clock} hệ thống ở múi giờ UTC cho các tác vụ nền tảng (retry, deadline scanner, v.v.). */
    @Bean public java.time.Clock systemClock() { return java.time.Clock.systemUTC(); }

    /**
     * Khởi tạo {@link AuthorizationProjection} cho Member Service bằng cách ánh xạ
     * {@link com.familya.member.domain.model.MemberAuthRow} sang reader của SDK.
     *
     * @param repo            kho lưu trữ thành viên — cung cấp projection auth cho mỗi cặp (tree, user)
     * @param freshnessSeconds ngưỡng "tươi" của projection tính bằng giây (mặc định 60s) — sau ngưỡng này
     *                         projection bị coi là cũ và cần được làm mới
     * @param emergencyMs     ngân sách thời gian khẩn cấp (mặc định 250ms) cho phép chờ projection mới
     *                         trong trường hợp vừa mới cập nhật
     * @return projection phân quyền đã được cấu hình cho Member Service
     */
    @Bean
    public AuthorizationProjection authorizationProjection(MemberRepository repo,
                                                          @org.springframework.beans.factory.annotation.Value(
                                                                  "${familya.member.authz.freshness-seconds:60}") long freshnessSeconds,
                                                          @org.springframework.beans.factory.annotation.Value(
                                                                  "${familya.member.authz.emergency-budget-ms:250}") long emergencyMs) {
        // Tạo một ProjectionReader ủy quyền cho MemberRepository: tìm dòng auth theo (aggregateId, userId)
        // rồi ép kiểu sang rowType được yêu cầu. Nếu không khớp kiểu thì trả về rỗng.
        AuthorizationProjection.ProjectionReader reader = new AuthorizationProjection.ProjectionReader() {
            @Override
            public <T extends AuthorizationProjection.ProjectionRow> Optional<T> find(
                    java.util.UUID aggregateId, java.util.UUID userId, Class<T> rowType) {
                // Tra cứu dòng ủy quyền trong kho dữ liệu thành viên
                Optional<MemberAuthRow> row = repo.findAuth(aggregateId, userId);
                // Trả về Optional chứa dòng đã ép kiểu nếu khớp; nếu không thì rỗng
                return row.map(r -> r).flatMap(r -> {
                    if (rowType.isInstance(r)) return Optional.of(rowType.cast(r));
                    return Optional.empty();
                });
            }
        };
        // Gói reader kèm ngưỡng tươi (freshness) và ngân sách khẩn cấp để SDK áp dụng chính sách deny-on-stale
        return new AuthorizationProjection(reader,
                Duration.ofSeconds(freshnessSeconds),
                Duration.ofMillis(emergencyMs));
    }
}