package com.familya.transfer.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Lớp kiểm thử kiến trúc (architecture test) cho microservice
 * <b>transfer-service</b>.
 *
 * <p>Đây là một dạng <i>static analysis test</i> sử dụng thư viện
 * <b>ArchUnit</b> nhằm tự động hoá việc giám sát và đảm bảo các
 * quy tắc kiến trúc phần mềm của service. Các quy tắc này được thực thi
 * mỗi lần chạy <code>mvn test</code>, giúp phát hiện sớm các vi phạm
 * ngay trong quá trình phát triển thay vì đợi đến khi review thủ công
 * hoặc triển khai production.</p>
 *
 * <h3>Mục tiêu</h3>
 * <ul>
 *   <li>Đảm bảo tính <b>nhất quán kiến trúc</b> giữa các package trong service.</li>
 *   <li>Ngăn chặn <b>sự phụ thuộc không mong muốn</b> giữa domain và framework,
 *       cũng như giữa các microservice khác nhau trong hệ thống Familya.</li>
 *   <li>Phát hiện <b>chu trình phụ thuộc</b> (circular dependencies) giữa
 *       các package con.</li>
 * </ul>
 *
 * <h3>Cấu hình quét</h3>
 * <p>Annotation {@link AnalyzeClasses} chỉ định cho ArchUnit:</p>
 * <ul>
 *   <li><b>packages</b> = <code>"com.familya.transfer"</code> — chỉ quét
 *       các class thuộc service hiện tại, không lấy nhầm sang service khác.</li>
 *   <li><b>importOptions</b> = {@link ImportOption.DoNotIncludeTests#class}
 *       — <i>loại trừ</i> các class trong thư mục test khi phân tích, giúp
 *       kiểm tra phản ánh đúng mã nguồn production.</li>
 * </ul>
 *
 * @author  Đội phát triển Familya
 * @since   1.0.0
 */
@AnalyzeClasses(packages = "com.familya.transfer", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    /**
     * Quy tắc: <b>Lớp domain không được phép phụ thuộc vào Spring Framework</b>.
     *
     * <p>Mục đích của quy tắc này là thực thi nguyên tắc
     * <i>Clean Architecture / Hexagonal Architecture</i>: tầng
     * <b>domain</b> chứa logic nghiệp vụ cốt lõi phải hoàn toàn độc lập
     * với các framework bên ngoài (như Spring), để:</p>
     *
     * <ul>
     *   <li>Có thể thay đổi hoặc thay thế framework (Spring → Micronaut,
     *       Jakarta EE…) mà không ảnh hưởng tới logic nghiệp vụ.</li>
     *   <li>Dễ dàng viết unit test cho domain mà không cần context Spring.</li>
     *   <li>Đảm bảo domain là "trái tim" của ứng dụng, không bị ô nhiễm
     *       bởi các annotation kỹ thuật (<code>@Entity</code>,
     *       <code>@Autowired</code>…).</li>
     * </ul>
     *
     * <p>Cú pháp ArchUnit được sử dụng:</p>
     * <pre>
     *   noClasses()                       // không áp dụng cho class nào...
     *       .that().resideInAPackage("..domain..")   // ... mà nằm trong package domain
     *       .should().dependOnClassesThat()          // ... nếu chúng phụ thuộc vào class
     *       .resideInAPackage("org.springframework.."); // thuộc package Spring
     * </pre>
     *
     * <p>Nếu vi phạm, ArchUnit sẽ liệt kê tất cả các class có vi phạm,
     * giúp lập trình viên sửa chữa ngay lập tức.</p>
     */
    @ArchTest
    static final ArchRule domain_does_not_depend_on_spring =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    /**
     * Quy tắc: <b>Cấm import chéo giữa các microservice</b> trong hệ sinh thái
     * Familya.
     *
     * <p>Mục đích: đảm bảo <b>tính cô lập (isolation) giữa các service</b>,
     * một nguyên tắc cốt lõi của kiến trúc microservice. Theo đó:</p>
     *
     * <ul>
     *   <li>Mỗi service chỉ nên giao tiếp với service khác thông qua
     *       <i>API công khai</i> (REST/gRPC) hoặc <i>hàng đợi sự kiện</i>
     *       (Kafka/RabbitMQ), chứ KHÔNG import trực tiếp class của nhau.</li>
     *   <li>Tránh tạo ra sự <i>ràng buộc chặt</i> (tight coupling) về thời
     *       gian biên dịch (compile-time coupling).</li>
     *   <li>Cho phép mỗi service phát triển, triển khai và mở rộng quy mô
     *       một cách độc lập.</li>
     * </ul>
     *
     * <p>Các service bị cấm kết nối trực tiếp vào <code>transfer</code>:</p>
     * <ul>
     *   <li><code>com.familya.identity</code> — quản lý định danh người dùng.</li>
     *   <li><code>com.familya.member</code> — quản lý thành viên gia đình.</li>
     *   <li><code>com.familya.relationship</code> — quản lý quan hệ huyết thống.</li>
     *   <li><code>com.familya.treeaccess</code> — quản lý quyền truy cập cây gia phả.</li>
     *   <li><code>com.familya.event</code> — module sự kiện miền (domain event).</li>
     *   <li><code>com.familya.media</code> — quản lý tài nguyên đa phương tiện.</li>
     *   <li><code>com.familya.sharing</code> — chia sẻ dữ liệu gia phả.</li>
     *   <li><code>com.familya.search</code> — tìm kiếm và lập chỉ mục.</li>
     *   <li><code>com.familya.auditops</code> — ghi log kiểm toán.</li>
     *   <li><code>com.familya.migration</code> — di chuyển dữ liệu.</li>
     * </ul>
     *
     * <p>Khi cần trao đổi dữ liệu, transfer-service sẽ gọi qua
     * {@code RestClient/FeignClient} hoặc publish/consume event qua
     * message broker thay vì import class.</p>
     */
    @ArchTest
    static final ArchRule no_cross_service_imports =
            noClasses().that().resideInAPackage("com.familya.transfer..")
                    .should().dependOnClassesThat().resideInAPackage("com.familya.identity..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.member..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.relationship..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.treeaccess..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.event..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.media..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.sharing..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.search..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.auditops..")
                    .orShould().dependOnClassesThat().resideInAPackage("com.familya.migration..");

    /**
     * Quy tắc: <b>Các package con của transfer-service không được tạo ra
     * chu trình phụ thuộc</b> (circular dependencies).
     *
     * <p>ArchUnit sử dụng khái niệm <i>slice</i> — mỗi slice là một nhóm
     * các class được xác định bởi một mẫu package. Pattern
     * <code>"com.familya.transfer.(*).."</code> có nghĩa là: lấy tất cả
     * các package con cấp một của <code>com.familya.transfer</code>
     * (ví dụ: <code>com.familya.transfer.domain</code>,
     * <code>com.familya.transfer.application</code>,
     * <code>com.familya.transfer.adapter</code>…).</p>
     *
     * <p>Quy tắc {@link com.tngtech.archunit.library.dependencies.SliceRule#beFreeOfCycles()}
     * yêu cầu các slice này <b>phải tạo thành đồ thị phi chu trình</b>
     * (DAG - Directed Acyclic Graph).</p>
     *
     * <h4>Tại sao chu trình phụ thuộc lại nguy hiểm?</h4>
     * <ul>
     *   <li>Gây khó khăn cho việc hiểu và bảo trì hệ thống — không thể xác
     *       định "lớp nào là lớp trong cùng".</li>
     *   <li>Phá vỡ nguyên tắc phân lớp — application không thể gọi domain
     *       nếu domain cũng gọi lại application.</li>
     *   <li>Spring có thể gặp lỗi khởi tạo bean do vòng lặp vòng đời
     *       (<i>circular bean dependency</i>).</li>
     * </ul>
     *
     * <p>Cách khắc phục khi vi phạm: tách phần phụ thuộc chung ra một module
     * riêng, hoặc sử dụng <i>Dependency Inversion</i> (giao tiếp qua
     * interface trừu tượng).</p>
     */
    @ArchTest
    static final ArchRule no_cycles = slices().matching("com.familya.transfer.(*)..").should().beFreeOfCycles();
}
