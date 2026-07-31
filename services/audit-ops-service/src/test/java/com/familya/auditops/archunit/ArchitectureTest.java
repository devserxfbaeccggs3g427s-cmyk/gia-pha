/**
 * Các quy tắc kiến trúc hexagonal cho dịch vụ audit-ops.
 *
 * <p>Phản chiếu bộ quy tắc nền tảng (Task 4.5 / ADR-002):</p>
 * <ul>
 *   <li>Tầng domain độc lập với framework.</li>
 *   <li>Dịch vụ không import mã nguồn của dịch vụ khác.</li>
 *   <li>Không có phụ thuộc vòng tròn giữa các bounded layer.</li>
 * </ul>
 *
 * <p>Đây là một cổng kiến trúc (architecture gate) - nếu một dịch vụ
 * nào đó vô tình phụ thuộc vào package của dịch vụ khác hoặc kéo một
 * kiểu Spring vào tầng domain, bộ test sẽ làm build thất bại.</p>
 */
package com.familya.auditops.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Lớp test chứa các {@link ArchTest} cho ArchUnit.
 *
 * <p>Các rule:</p>
 * <ol>
 *   <li>{@link #domain_does_not_depend_on_spring}: tầng domain không
 *       phụ thuộc vào Spring.</li>
 *   <li>{@link #no_cross_service_imports}: không import code từ các
 *       bounded context khác.</li>
 *   <li>{@link #adapters_depend_on_application_and_domain}: adapter
 *       không tự phụ thuộc vào adapter khác trong cùng service.</li>
 *   <li>{@link #no_cycles}: không có chu trình phụ thuộc.</li>
 * </ol>
 */
@AnalyzeClasses(packages = "com.familya.auditops", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    /**
     * Tầng domain không được phụ thuộc vào Spring.
     *
     * <p>Đảm bảo tính thuần Java của domain, cho phép thay thế
     * framework mà không ảnh hưởng tới logic nghiệp vụ.</p>
     */
    @ArchTest
    static final ArchRule domain_does_not_depend_on_spring =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Không import code từ các bounded context khác.
     *
     * <p>Danh sách package được liệt kê tường minh để tránh phải
     * phụ thuộc vào các bounded context chưa tồn tại trong CI của
     * audit-ops.</p>
     */
    @ArchTest
    static final ArchRule no_cross_service_imports =
            noClasses().that().resideInAPackage("com.familya.auditops..")
                    .should().dependOnClassesThat().resideInAPackage("com.familya.identity..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.treeaccess..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.member..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.relationship..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.event..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.media..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.sharing..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.search..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.transfer..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.auditops..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.migration..");

    /**
     * Adapter không được phụ thuộc lẫn nhau trong cùng bounded context.
     *
     * <p>Điều này buộc adapter phải giao tiếp qua application hoặc
     * domain thay vì gọi trực tiếp nhau, giữ đúng hướng phụ thuộc
     * của kiến trúc hexagonal.</p>
     */
    @ArchTest
    static final ArchRule adapters_depend_on_application_and_domain =
            noClasses().that().resideInAPackage("..adapter..")
                    .should().dependOnClassesThat().resideInAPackage("com.familya.auditops.adapter..");

    /**
     * Không có chu trình phụ thuộc giữa các slice (top-level package con).
     */
    @ArchTest
    static final ArchRule no_cycles =
            slices().matching("com.familya.auditops.(*)..").should().beFreeOfCycles();
}