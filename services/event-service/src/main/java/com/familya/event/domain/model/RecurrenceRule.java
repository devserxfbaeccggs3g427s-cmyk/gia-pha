package com.familya.event.domain.model;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Quy tắc lặp lại của một {@link DomainEvent sự kiện gia phả}, mô hình
 * hóa theo <b>tập con RFC 5545 (iCalendar) phiên bản 1</b>.
 *
 * <p>Cấu trúc gồm ba thành phần:
 * <ul>
 *   <li>{@link Frequency tần suất}: {@code DAILY | WEEKLY | MONTHLY | YEARLY}.</li>
 *   <li>{@code interval}: bội số khoảng cách giữa hai lần lặp kế tiếp.
 *       Ví dụ {@code MONTHLY/2} = cách 2 tháng.</li>
 *   <li>{@link Termination điều kiện dừng}: đếm số lần ({@link Count})
 *       hoặc tới ngày ({@link Until}).</li>
 * </ul>
 *
 * <h2>Quy tắc đặc biệt: sinh nhật ngày 29 tháng 2</h2>
 * <p>Theo hành vi kế thừa (legacy) của hệ thống, nếu anchor (ngày neo)
 * là <b>29 tháng 2</b> thì ở những năm không nhuận, sự kiện sẽ được neo
 * về <b>28 tháng 2</b> cùng năm. Xem thêm {@link #applyLeapDay(LocalDate, LocalDate)}.
 *
 * <p>Lớp này là {@code record} bất biến — mọi thay đổi cần tạo instance
 * mới, giúp cho việc chia sẻ giữa các thread trở nên an toàn.
 *
 * @param frequency  tần suất lặp ({@code DAILY}, {@code WEEKLY}, {@code MONTHLY}, {@code YEARLY}).
 * @param interval   bội số khoảng cách lặp (>= 1).
 * @param termination điều kiện dừng {@link Count} hoặc {@link Until}.
 *
 * @author gia-pha platform
 * @version 1.0.0
 */
public record RecurrenceRule(Frequency frequency, int interval, Termination termination) {

    /**
     * Tần suất lặp lại theo RFC 5545.
     */
    public enum Frequency {
        /** Lặp hằng ngày (mỗi {@code interval} ngày). */
        DAILY,
        /** Lặp hằng tuần. */
        WEEKLY,
        /** Lặp hằng tháng theo ngày cố định. */
        MONTHLY,
        /** Lặp hằng năm theo ngày cố định. */
        YEARLY
    }

    /**
     * Điều kiện dừng của quy tắc lặp. Đây là một {@code sealed interface}
     * cho phép duy nhất hai biến thể {@link Count} và {@link Until}.
     */
    public sealed interface Termination permits RecurrenceRule.Count, RecurrenceRule.Until { }

    /**
     * Điều kiện dừng theo số lần lặp.
     *
     * @param count số lần lặp tối đa, phải {@code >= 1}.
     */
    public record Count(int count) implements RecurrenceRule.Termination {
        /**
         * Compact constructor — đảm bảo {@code count} hợp lệ ngay từ khi
         * khởi tạo, giúp tránh lỗi runtime khi sử dụng.
         *
         * @throws IllegalArgumentException nếu {@code count < 1}.
         */
        public Count {
            if (count < 1) throw new IllegalArgumentException("count must be >= 1");
        }
    }

    /**
     * Điều kiện dừng theo ngày kết thúc (inclusive).
     *
     * @param until ngày dừng, không được {@code null}.
     */
    public record Until(LocalDate until) implements RecurrenceRule.Termination {
        /**
         * Compact constructor — bảo vệ tính bắt buộc của {@code until}.
         *
         * @throws NullPointerException nếu {@code until} là {@code null}.
         */
        public Until {
            Objects.requireNonNull(until, "until is required");
        }
    }

    /**
     * Tính ngày diễn ra tiếp theo của sự kiện sau {@code from}, dựa trên
     * {@link #frequency()}, {@link #interval()}. Ngày kết quả sau đó được
     * điều chỉnh theo quy tắc ngày nhuận của {@link #applyLeapDay(LocalDate, LocalDate)}.
     *
     * <p>Quy trình xử lý:
     * <ol>
     *   <li>Chọn phép cộng thời gian phù hợp với {@code frequency}:
     *       <ul>
     *           <li>{@code DAILY}: cộng {@code interval} ngày.</li>
     *           <li>{@code WEEKLY}: cộng {@code interval} tuần.</li>
     *           <li>{@code MONTHLY}: cộng {@code interval} tháng.</li>
     *           <li>{@code YEARLY}: cộng {@code interval} năm.</li>
     *       </ul>
     *   </li>
     *   <li>Áp dụng {@link #applyLeapDay(LocalDate, LocalDate)} để xử lý
     *       trường hợp anchor ngày 29 tháng 2.</li>
     * </ol>
     *
     * @param from   ngày bắt đầu tính (thường là {@code startDate} hoặc
     *               ngày diễn ra gần nhất). Không được {@code null}.
     * @param anchor ngày neo ban đầu của sự kiện (thường là
     *               {@code startDate}). Không được {@code null}.
     * @return ngày diễn ra tiếp theo (LocalDate).
     * @throws NullPointerException nếu {@code from} hoặc {@code anchor} là {@code null}.
     */
    public LocalDate nextAfter(LocalDate from, LocalDate anchor) {
        // Bước 1: xác thực tham số đầu vào.
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(anchor, "anchor is required");

        // Bước 2: chọn phép cộng thời gian theo tần suất — Java time API xử lý
        // cộng âm (lùi thời gian) hoàn toàn an toàn nếu cần dùng trong tương lai.
        LocalDate candidate = switch (frequency) {
            case DAILY   -> from.plusDays(interval);
            case WEEKLY  -> from.plusWeeks(interval);
            case MONTHLY -> from.plusMonths(interval);
            case YEARLY  -> from.plusYears(interval);
        };

        // Bước 3: áp quy tắc neo ngày 29/2 — đảm bảo sinh nhật ngày nhuận
        // vẫn diễn ra vào 28/2 ở những năm không nhuận.
        candidate = applyLeapDay(anchor, candidate);
        return candidate;
    }

    /**
     * Cho biết liệu sự kiện đã <i>kết thúc</i> theo quy tắc dừng tại
     * ngày {@code current} hay chưa.
     *
     * <p>Lưu ý: với {@link Count} biến thể, phương thức luôn trả về
     * {@code false} vì việc đếm số lần được thực hiện ở phía gọi (caller).
     *
     * @param current ngày hiện tại cần kiểm tra.
     * @return {@code true} nếu sự kiện đã kết thúc (chỉ áp dụng cho
     *         {@link Until}, và khi {@code current >= until}).
     */
    public boolean isTerminated(LocalDate current) {
        return switch (termination) {
            // Count-based: caller phải tự đếm số lần đã lặp, ta không đếm ở đây.
            case RecurrenceRule.Count c -> false;
            // Until-based: dừng khi current đã qua ngày until (inclusive).
            case RecurrenceRule.Until u -> !current.isBefore(u.until());
        };
    }

    /**
     * Áp dụng quy tắc xử lý ngày nhuận (29/2) cho {@code candidate}.
     *
     * <p>Nguyên tắc:
     * <ul>
     *   <li>Nếu {@code anchor} là 29 tháng 2:
     *     <ul>
     *       <li>Năm nhuận: giữ nguyên ngày 29/2.</li>
     *       <li>Năm thường: lùi về 28/2.</li>
     *     </ul>
     *   </li>
     *   <li>Các trường hợp khác: trả về nguyên {@code candidate}.</li>
     * </ul>
     *
     * <p>Đây là hành vi <b>kế thừa (legacy)</b> của hệ thống; các dịch vụ
     * mới nên tuân theo để đảm bảo tương thích ngược.
     *
     * @param anchor    ngày neo ban đầu của sự kiện (tham chiếu phát hiện nhuận).
     * @param candidate ngày đang xét (kết quả của phép cộng).
     * @return ngày đã điều chỉnh theo quy tắc nhuận hoặc chính {@code candidate}.
     */
    public static LocalDate applyLeapDay(LocalDate anchor, LocalDate candidate) {
        // Chỉ áp dụng khi anchor nằm đúng vào 29 tháng 2.
        if (anchor.getMonthValue() == 2 && anchor.getDayOfMonth() == 29) {
            // Năm nhuận giữ nguyên 29/2.
            if (candidate.isLeapYear()) {
                return LocalDate.of(candidate.getYear(), 2, 29);
            }
            // Năm không nhuận rơi về 28/2 (legacy).
            return LocalDate.of(candidate.getYear(), 2, 28);
        }
        return candidate;
    }
}
