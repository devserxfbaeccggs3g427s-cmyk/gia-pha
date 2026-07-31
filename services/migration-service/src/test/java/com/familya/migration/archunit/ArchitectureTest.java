package com.familya.migration.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Bộ kiểm thử kiến trúc (Architecture Tests) cho microservice
 * {@code migration-service}, sử dụng thư viện ArchUnit.
 *
 * <p>Mục tiêu của lớp này là đảm bảo service luôn tuân thủ các quy tắc kiến trúc
 * đã được thống nhất trong các ADR (Architecture Decision Record) của dự án,
 * đặc biệt là nguyên tắc <b>phân lớp</b> (layered architecture) và
 * <b>cô lập dịch vụ</b> (service isolation). Nếu một thay đổi mã nguồn vi
 * phạm các quy tắc này, bộ test sẽ thất bại trong quá trình CI/CD,
 * giúp phát hiện sớm các "khoản vay kỹ thuật" (technical debt) về kiến trúc.</p>
 *
 * <h2>Các quy tắc được kiểm tra</h2>
 * <ul>
 *   <li>{@link #domain_does_not_depend_on_spring}: tầng {@code domain} không
 *       được phép phụ thuộc vào Spring Framework — đảm bảo logic nghiệp vụ cốt lõi
 *       (business core) không bị ràng buộc vào bất kỳ framework nào, giúp dễ
 *       dàng kiểm thử đơn vị và tái sử dụng.</li>
 *   <li>{@link #no_cross_service_imports}: service không được phép import trực
 *       tiếp các class từ các microservice khác trong cùng hệ sinh thái, đảm bảo
 *       tính độc lập và khả năng triển khai riêng lẻ của từng service.</li>
 *   <li>{@link #no_cycles}: cấu trúc package nội bộ phải là đồ thị phi chu trình
 *       (DAG) — tránh hiện tượng phụ thuộc vòng tròn giữa các module.</li>
 * </ul>
 *
 * <h2>Cách hoạt động</h2>
 * <p>ArchUnit sẽ quét toàn bộ bytecode của project (theo package gốc
 * {@code com.familya.migration}) và đánh giá các {@link ArchRule} được khai báo
 * bằng annotation {@link ArchTest}. Khi chạy {@code mvn test}, các quy tắc
 * này sẽ được tự động thực thi bởi runner của JUnit 5.</p>
 *
 * @author Family Tree Platform Team
 * @since 1.0.0
 */
@AnalyzeClasses(
        packages = "com.familya.migration",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class ArchitectureTest {

    /**
     * Quy tắc 1: Tầng {@code domain} không được phụ thuộc vào Spring Framework.
     *
     * <p><b>Mục đích:</b> Bảo vệ tầng {@code domain} — nơi chứa các
     * {@code entity}, {@code value object}, {@code aggregate} và các quy tắc
     * nghiệp vụ cốt lõi — khỏi việc bị "ô nhiễm" bởi các annotation hoặc
     * class của Spring (ví dụ: {@code @Service}, {@code @Component},
     * {@code ApplicationContext}...).</p>
     *
     * <p><b>Lý do:</b></p>
     * <ul>
     *   <li>Giữ cho logic nghiệp vụ thuần túy (pure Java), dễ kiểm thử đơn vị
     *       mà không cần khởi động Spring Context.</li>
     *   <li>Cho phép thay thế hoặc nâng cấp framework mà không ảnh hưởng đến
     *       logic nghiệp vụ cốt lõi.</li>
     *   <li>Đảm bảo nguyên tắc <i>Dependency Inversion</i> của kiến trúc
     *       sạch (Clean Architecture): tầng trong không biết về tầng ngoài.</li>
     * </ul>
     *
     * <p><b>Cách áp dụng:</b> Bất kỳ class nào nằm trong package
     * {@code com.familya.migration.domain.*} mà có dependency (import, kế thừa,
     * sử dụng field/method...) đến class thuộc package {@code org.springframework.*}
     * đều sẽ bị đánh vi phạm.</p>
     */
    @ArchTest
    static final ArchRule domain_does_not_depend_on_spring =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Quy tắc 2: Không cho phép import chéo giữa các microservice.
     *
     * <p><b>Mục đích:</b> Ngăn chặn việc {@code migration-service} truy cập trực
     * tiếp vào mã nguồn của các microservice khác trong cùng hệ sinh thái
     * (identity, member, relationship, treeaccess, event, media, sharing,
     * search, transfer, auditops, migration).</p>
     *
     * <p><b>Lý do:</b></p>
     * <ul>
     *   <li>Mỗi microservice phải là một đơn vị triển khai (deployment unit)
     *       độc lập; việc chia sẻ mã nguồn sẽ tạo ra sự ghép nối (coupling)
     *       chặt và phá vỡ nguyên tắc bounded context.</li>
     *   <li>Giao tiếp giữa các service phải thông qua các hợp đồng (contract)
     *       đã được định nghĩa: REST API, gRPC, message broker (Kafka), hoặc
     *       các sự kiện domain — không phải qua import Java trực tiếp.</li>
     *   <li>Đảm bảo khả năng phát triển, thử nghiệm và phát hành độc lập
     *       giữa các team khác nhau.</li>
     * </ul>
     *
     * <p><b>Cách áp dụng:</b> Bất kỳ class nào thuộc package
     * {@code com.familya.migration.**} mà có dependency đến bất kỳ class nào
     * thuộc các package {@code com.familya.identity.**},
     * {@code com.familya.member.**}, {@code com.familya.relationship.**},
     * {@code com.familya.treeaccess.**}, {@code com.familya.event.**},
     * {@code com.familya.media.**}, {@code com.familya.sharing.**},
     * {@code com.familya.search.**}, {@code com.familya.transfer.**},
     * {@code com.familya.auditops.**}, hoặc {@code com.familya.migration.**}
     * đều bị đánh vi phạm.</p>
     *
     * <p><b>Lưu ý:</b> Quy tắc này cũng cấm service tự import chính nó qua
     * các package khác nhau — giúp tránh hiện tượng truy cập vòng tròn nội bộ
     * không kiểm soát.</p>
     */
    @ArchTest
    static final ArchRule no_cross_service_imports =
            noClasses()
                    .that().resideInAPackage("com.familya.migration..")
                    .should().dependOnClassesThat().resideInAPackage("com.familya.identity..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.member..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.relationship..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.treeaccess..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.event..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.media..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.sharing..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.search..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.transfer..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.auditops..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.migration..");

    /**
     * Quy tắc 3: Đồ thị phụ thuộc giữa các package nội bộ phải phi chu trình (acyclic).
     *
     * <p><b>Mục đích:</b> Phát hiện và ngăn chặn <i>circular dependencies</i>
     * (phụ thuộc vòng tròn) giữa các package cấp 1 của service
     * ({@code com.familya.migration.<sub-module>.<...>}).</p>
     *
     * <p><b>Lý do:</b></p>
     * <ul>
     *   <li>Phụ thuộc vòng tròn khiến việc biên dịch, đóng gói và khởi động
     *       trở nên không ổn định — Spring có thể ném ra lỗi
     *       {@code BeanCurrentlyInCreationException} khi khởi tạo bean.</li>
     *   <li>Làm giảm khả năng bảo trì: khi một package thay đổi sẽ kéo theo
     *       thay đổi ở chính package đang phụ thuộc nó, tạo hiệu ứng domino.</li>
     *   <li>Vi phạm nguyên tắc <i>Acyclic Dependencies Principle (ADP)</i>
     *       trong các nguyên lý thiết kế hướng đối tượng.</li>
     * </ul>
     *
     * <p><b>Cách áp dụng:</b> ArchUnit sẽ coi mỗi package con cấp 1 của
     * {@code com.familya.migration} (ví dụ: {@code domain}, {@code application},
     * {@code infrastructure}, {@code api}...) như một "slice" (lát cắt) trong đồ thị.
     * Nếu tồn tại chu trình {@code A -> B -> C -> A} giữa các slice thì quy tắc
     * bị vi phạm.</p>
     */
    @ArchTest
    static final ArchRule no_cycles =
            slices().matching("com.familya.migration.(*)..")
                    .should().beFreeOfCycles();
}
