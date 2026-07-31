package com.familya.treeaccess;

import com.familya.treeaccess.application.usecase.CreateTreeUseCase;
import com.familya.treeaccess.application.usecase.GrantMembershipUseCase;
import com.familya.treeaccess.application.usecase.RevokeMembershipUseCase;
import com.familya.treeaccess.application.usecase.TreeLifecycleUseCases;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;

/**
 * Điểm khởi đầu (entry point) của microservice tree-access-service.
 *
 * <p>Service này chịu trách nhiệm quản lý vòng đời cây gia phả (tạo, đóng băng,
 * xóa), quản lý thành viên (cấp quyền, thu hồi quyền) và điều phối Saga
 * xóa cây phối hợp nhiều bounded-context. Lớp {@code Application} chỉ làm
 * nhiệm vụ bootstrap Spring Boot và đăng ký các bean đồng hồ (Clock)
 * dùng cho việc sinh mốc thời gian trong các use-case.</p>
 */
@SpringBootApplication(scanBasePackages = { "com.familya.treeaccess", "com.familya.platform" })
@EnableScheduling
public class Application {
    /**
     * Hàm main chuẩn của Spring Boot: khởi chạy ApplicationContext, nạp
     * cấu hình từ {@code application.yml} và kích hoạt các bean đã khai báo.
     *
     * @param args tham số dòng lệnh truyền cho JVM (thường không sử dụng)
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * Bean cung cấp đồng hồ cho {@link CreateTreeUseCase}, dùng để lấy
     * {@link Instant#now()} tại thời điểm tạo cây và phát sự kiện {@code TreeCreated}.
     * Việc tách thành bean giúp dễ mock trong kiểm thử đơn vị.
     *
     * @return implementation của {@link CreateTreeUseCase.Clock} dựa trên {@link Instant#now()}
     */
    @Bean public CreateTreeUseCase.Clock createTreeClock()        { return Instant::now; }

    /**
     * Bean đồng hồ cho {@link GrantMembershipUseCase}, ghi nhận thời điểm
     * cấp quyền thành viên mới.
     *
     * @return implementation của {@link GrantMembershipUseCase.Clock}
     */
    @Bean public GrantMembershipUseCase.Clock grantMembershipClock() { return Instant::now; }

    /**
     * Bean đồng hồ cho {@link RevokeMembershipUseCase}, ghi nhận thời điểm
     * thu hồi quyền thành viên.
     *
     * @return implementation của {@link RevokeMembershipUseCase.Clock}
     */
    @Bean public RevokeMembershipUseCase.Clock revokeMembershipClock() { return Instant::now; }

    /**
     * Bean đồng hồ cho {@link TreeLifecycleUseCases}, dùng cho các thao tác
     * đóng băng, bỏ đóng băng, đánh dấu tombstone và tăng revision.
     *
     * @return implementation của {@link TreeLifecycleUseCases.Clock}
     */
    @Bean public TreeLifecycleUseCases.Clock lifecycleClock()     { return Instant::now; }

    /**
     * Bean đồng hồ hệ thống ở múi giờ UTC, dùng cho các tác vụ nền như
     * deadline scanner của Saga.
     *
     * @return {@link java.time.Clock} hệ thống theo UTC
     */
    @Bean public java.time.Clock systemClock() { return java.time.Clock.systemUTC(); }
}