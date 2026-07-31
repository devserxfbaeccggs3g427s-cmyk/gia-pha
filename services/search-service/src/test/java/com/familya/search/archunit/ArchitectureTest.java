package com.familya.search.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Bộ kiểm thử kiến trúc (ArchUnit) cho search-service.
 *
 * <p>Ba quy tắc được kiểm tra:</p>
 * <ol>
 *   <li>{@code domain_does_not_depend_on_spring} - tầng {@code domain}
 *       không được phụ thuộc vào Spring, đảm bảo tầng này là Java thuần.</li>
 *   <li>{@code no_cross_service_imports} - không có lớp nào trong
 *       {@code com.familya.search} được phép import trực tiếp các service
 *       khác, đảm bảo giao tiếp chỉ thông qua sự kiện/Kafka.</li>
 *   <li>{@code no_cycles} - không có chu trình phụ thuộc giữa các slice
 *       con của {@code com.familya.search}.</li>
 * </ol>
 *
 * <p>Annotation {@code @AnalyzeClasses} chỉ định quét cả package
 * {@code com.familya.search} và bỏ qua các lớp test (chính lớp này).</p>
 */
@AnalyzeClasses(packages = "com.familya.search", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {
    /**
     * Tầng domain phải độc lập với framework Spring - giúp domain có thể
     * được kiểm thử thuần và tái sử dụng ở các ngữ cảnh khác.
     */
    @ArchTest static final ArchRule domain_does_not_depend_on_spring =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Không được import trực tiếp các service khác - mọi giao tiếp phải qua
     * sự kiện/Kafka hoặc qua các cổng (port) nền tảng. Quy tắc này áp dụng
     * cho mọi lớp trong {@code com.familya.search..} và liệt kê các service
     * mà search không được phép liên kết trực tiếp.
     */
    @ArchTest static final ArchRule no_cross_service_imports =
            noClasses().that().resideInAPackage("com.familya.search..")
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
     * Các slice con (sub-package) của {@code com.familya.search} phải tạo
     * thành đồ thị không có chu trình (DAG).
     */
    @ArchTest static final ArchRule no_cycles = slices().matching("com.familya.search.(*)..").should().beFreeOfCycles();
}
