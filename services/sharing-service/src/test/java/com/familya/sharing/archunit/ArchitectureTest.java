package com.familya.sharing.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Bộ kiểm thử kiến trúc (architecture test) cho sharing-service sử dụng
 * thư viện <b>ArchUnit</b>.
 * <p>
 * Các quy tắc được áp dụng:
 * <ol>
 *     <li>{@code domain_does_not_depend_on_spring} &mdash; tầng domain không
 *         được phép phụ thuộc vào Spring Framework; đảm bảo domain là
 *         framework-agnostic.</li>
 *     <li>{@code no_cross_service_imports} &mdash; cấm import trực tiếp từ
 *         các microservice khác (identity, member, relationship, treeaccess,
 *         event, media, sharing, search, transfer, auditops, migration); giao
 *         tiếp chỉ qua Kafka/contract.</li>
 *     <li>{@code no_cycles} &mdash; cấm chu trình phụ thuộc giữa các package
 *         con của {@code com.familya.sharing}.</li>
 * </ol>
 * Việc kiểm tra này giúp bảo vệ kiến trúc phân lớp theo thời gian và phát
 * hiện sớm các vi phạm khi thêm mới mã nguồn.
 */
@AnalyzeClasses(packages = "com.familya.sharing", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {
    /**
     * Quy tắc: tầng domain không được phụ thuộc Spring.
     */
    @ArchTest static final ArchRule domain_does_not_depend_on_spring =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Quy tắc: không cho phép import trực tiếp giữa các microservice.
     */
    @ArchTest static final ArchRule no_cross_service_imports =
            noClasses().that().resideInAPackage("com.familya.sharing..")
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
     * Quy tắc: cấm chu trình phụ thuộc giữa các package con của sharing-service.
     */
    @ArchTest static final ArchRule no_cycles = slices().matching("com.familya.sharing.(*)..").should().beFreeOfCycles();
}