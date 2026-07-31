package com.familya.event.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Bộ kiểm thử kiến trúc (architecture test) cho event-service, sử dụng
 * thư viện <b>ArchUnit</b>.
 *
 * <h2>Ba quy tắc kiến trúc chính</h2>
 * <ol>
 *   <li><b>domain_does_not_depend_on_spring</b>: tầng domain (gói
 *       {@code com.familya.event.domain..}) <b>không được</b> phụ thuộc
 *       vào Spring Framework. Đảm bảo domain giữ tính thuần (pure) và
 *       có thể test cô lập.</li>
 *   <li><b>no_cross_service_imports</b>: tầng/cấu trúc nào của
 *       {@code com.familya.event..} cũng <b>không được</b> trực tiếp
 *       import mã của dịch vụ khác (identity, member, relationship,
 *       treeaccess, media, sharing, search, transfer, auditops,
 *       migration, hoặc chính nó). Trao đổi giữa các dịch vụ phải qua
 *       contract (event, port) chứ không qua coupling code-level.</li>
 *   <li><b>no_cycles</b>: cấu trúc gói {@code com.familya.event.(*)..}
 *       phải <b>phi chu trình</b> — nghĩa là không được có vòng phụ
 *       thuộc giữa các gói con.</li>
 * </ol>
 *
 * <p>Các quy tắc này giúp duy trì <i>architectural fitness</i> theo
 * thời gian và tự động phát hiện các vi phạm khi thay đổi mã.
 *
 * @author gia-pha platform
 */
@AnalyzeClasses(packages = "com.familya.event", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    /**
     * Tầng domain không được phụ thuộc Spring — giữ domain thuần.
     */
    @ArchTest static final ArchRule domain_does_not_depend_on_spring =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Cấm import trực tiếp mã nguồn các dịch vụ khác trong cùng platform.
     */
    @ArchTest static final ArchRule no_cross_service_imports =
            noClasses().that().resideInAPackage("com.familya.event..")
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
     * Cấm chu trình giữa các gói con của {@code com.familya.event}.
     */
    @ArchTest static final ArchRule no_cycles = slices().matching("com.familya.event.(*)..").should().beFreeOfCycles();
}
