package com.familya.relationship.domain.graph;

import com.familya.relationship.domain.exception.CycleDetectedException;
import com.familya.relationship.domain.model.Relationship;

import java.util.*;

/**
 * Tập hợp các thuật toán trên đồ thị gia phả, được thiết kế để giữ nguyên hành
 * vi của hệ thống family-graph cũ.
 *
 * <h2>Các thuật toán được hỗ trợ</h2>
 * <ul>
 *   <li><b>Phát hiện chu trình (Cycle detection)</b> cho cạnh {@code PARENT_CHILD}:
 *       trước khi thêm một cạnh {@code parent -> child}, thuật toán duyệt
 *       ngược tổ tiên của {@code parent}. Nếu {@code child} xuất hiện trong tập
 *       tổ tiên đó thì cạnh mới sẽ tạo thành chu trình và lệnh bị từ chối.</li>
 *   <li><b>Tính thế hệ (Generation):</b> BFS bắt đầu từ một tổ tiên gốc; độ sâu
 *       của một thành viên tính từ gốc chính là số thế hệ của thành viên đó.
 *       Thành viên gốc có thế hệ 0. Quan hệ vợ chồng giữ nguyên thế hệ của cả
 *       hai vợ chồng; quan hệ nhận nuôi không làm thay đổi thế hệ.</li>
 *   <li><b>Tổ tiên (Ancestry):</b> bao đóng bắc cầu (transitive closure) của
 *       các cạnh {@code PARENT_CHILD}.</li>
 *   <li><b>Vợ chồng (Spouse):</b> các cạnh {@code SPOUSE} là hai chiều - mỗi
 *       cạnh được lưu một lần nhưng có thể truy vấn theo cả hai phía.</li>
 *   <li><b>Nhận nuôi (Adoption):</b> các cạnh {@code ADOPTION} không ảnh hưởng
 *       đến thế hệ sinh học của đứa trẻ.</li>
 * </ul>
 *
 * <h2>Bất biến của miền</h2>
 * <p>
 * Tất cả các thuật toán đều <b>bỏ qua</b> các quan hệ đã bị tombstone
 * ({@link Relationship#isTombstoned()} trả về {@code true}). Điều này đảm bảo
 * rằng các quan hệ đã xóa mềm không ảnh hưởng đến các tính toán về thế hệ,
 * tổ tiên, vợ chồng hay nhận nuôi.
 * </p>
 *
 * <p>
 * Lớp này là {@code final} và có constructor riêng để ngăn chặn việc tạo thể
 * hiện - tất cả các thuật toán đều được cung cấp dưới dạng phương thức tĩnh.
 * </p>
 */
public final class GraphAlgorithms {

    /** Ngăn chặn việc tạo thể hiện - lớp tiện ích chỉ chứa phương thức tĩnh. */
    private GraphAlgorithms() { }

    /**
     * Kiểm tra rằng việc thêm cạnh {@code parent -> child} (PARENT_CHILD) không
     * tạo thành chu trình trong đồ thị.
     * <p>
     * Quy trình kiểm tra:
     * </p>
     * <ol>
     *   <li>Nếu {@code parent} và {@code child} trùng nhau (self-loop) thì lập
     *       tức ném {@link CycleDetectedException} - một cá nhân không thể là
     *       cha/mẹ của chính mình.</li>
     *   <li>Thực hiện DFS đệ quy (dùng stack) bắt đầu từ {@code parent},
     *       đi ngược lên các tổ tiên bằng cách xét các cạnh
     *       {@code PARENT_CHILD} chưa tombstone.</li>
     *   <li>Nếu trong quá trình duyệt gặp {@code child} thì tức là
     *       {@code child} nằm trong tập tổ tiên của {@code parent}, nghĩa là
     *       việc thêm cạnh mới sẽ tạo vòng và lệnh bị từ chối.</li>
     * </ol>
     *
     * @param existing tập các quan hệ hiện có trong cây (chưa tombstone và đã
     *                 tombstone đều có thể truyền vào; hàm sẽ tự lọc)
     * @param parent   định danh thành viên sẽ là cha/mẹ trong cạnh mới
     * @param child    định danh thành viên sẽ là con trong cạnh mới
     * @throws CycleDetectedException nếu việc thêm cạnh sẽ tạo thành chu trình
     */
    public static void assertNoCycle(Collection<Relationship> existing, UUID parent, UUID child) {
        // Bước 1: loại bỏ trường hợp đặc biệt - một người không thể là cha/mẹ của chính mình.
        if (Objects.equals(parent, child)) {
            throw new CycleDetectedException("Self-loop is not allowed: " + parent);
        }
        // Bước 2: DFS đi ngược tổ tiên của parent. Dùng stack để duyệt
        // không đệ quy nhằm tránh StackOverflow với cây gia phả rất sâu.
        Deque<UUID> stack = new ArrayDeque<>();
        // Tập visited dùng để chống lặp vô tận nếu đồ thị đã có chu trình
        // trước khi gọi hàm (mặc dù hệ thống không cho phép, nhưng phòng hờ).
        Set<UUID> visited = new HashSet<>();
        stack.push(parent);
        while (!stack.isEmpty()) {
            UUID cur = stack.pop();
            // Nếu nút đã được thăm thì bỏ qua để tránh xử lý lại.
            if (!visited.add(cur)) continue;
            // Nếu gặp child trong tập tổ tiên của parent -> chu trình!
            if (Objects.equals(cur, child)) {
                throw new CycleDetectedException(
                        "Cycle detected: adding " + parent + " -> " + child + " would close an ancestry loop");
            }
            // Duyệt các cạnh PARENT_CHILD chưa tombstone để đi tiếp lên trên.
            for (Relationship r : existing) {
                if (r.isTombstoned()) continue;            // Bỏ qua quan hệ đã xóa mềm.
                if (r.kind() != Relationship.Kind.PARENT_CHILD) continue;  // Chỉ quan tâm cha-con.
                // r.fromMemberId = cha (gần gốc hơn), r.toMemberId = con (hậu duệ).
                // Nếu cur đang là cha trong một quan hệ, đẩy đứa con lên stack
                // để lần sau duyệt ngược tổ tiên của nó.
                if (Objects.equals(r.fromMemberId(), cur)) {
                    stack.push(r.toMemberId());
                }
            }
        }
        // Kết thúc duyệt mà không tìm thấy child nghĩa là không có chu trình.
    }

    /**
     * Tính số thế hệ cho mọi thành viên trong cây bằng BFS bắt đầu từ {@code root}.
     * <p>
     * Quy tắc về thế hệ:
     * </p>
     * <ul>
     *   <li>Cạnh {@code PARENT_CHILD}: con có thế hệ bằng thế hệ của cha + 1.</li>
     *   <li>Cạnh {@code SPOUSE}: vợ/chồng có cùng thế hệ với nhau (vì
     *       thuộc cùng một thế hệ sinh học).</li>
     *   <li>Cạnh {@code ADOPTION}: không làm thay đổi thế hệ (giữ nguyên
     *       thế hệ sinh học của đứa trẻ).</li>
     * </ul>
     *
     * @param rels tập các quan hệ trong cây (hàm sẽ tự lọc các quan hệ đã tombstone)
     * @param root định danh thành viên gốc (sẽ có thế hệ 0)
     * @return {@code Map} từ định danh thành viên sang số thế hệ; thứ tự chèn
     *         được bảo toàn (LinkedHashMap) để tiện cho việc hiển thị
     */
    public static Map<UUID, Integer> generations(Collection<Relationship> rels, UUID root) {
        // LinkedHashMap để giữ thứ tự BFS -> tiện cho hiển thị và kiểm thử.
        Map<UUID, Integer> gen = new LinkedHashMap<>();
        Deque<UUID> queue = new ArrayDeque<>();
        // Thành viên gốc luôn có thế hệ 0.
        gen.put(root, 0);
        queue.add(root);
        while (!queue.isEmpty()) {
            UUID cur = queue.poll();
            int depth = gen.get(cur);
            // Duyệt tất cả các quan hệ, xử lý theo từng loại.
            for (Relationship r : rels) {
                if (r.isTombstoned()) continue;   // Bỏ qua quan hệ đã xóa mềm.
                switch (r.kind()) {
                    case PARENT_CHILD -> {
                        // Nếu cur là cha trong cạnh cha-con, đứa con có thế hệ depth + 1.
                        if (Objects.equals(r.fromMemberId(), cur)) {
                            UUID child = r.toMemberId();
                            // Chỉ gán thế hệ nếu chưa được gán (giữ thế hệ thấp nhất).
                            if (!gen.containsKey(child)) {
                                gen.put(child, depth + 1);
                                queue.add(child);
                            }
                        }
                    }
                    case SPOUSE -> {
                        // Nếu cur là một đầu của cạnh SPOUSE, người còn lại giữ nguyên thế hệ.
                        if (Objects.equals(r.fromMemberId(), cur) || Objects.equals(r.toMemberId(), cur)) {
                            UUID partner = Objects.equals(r.fromMemberId(), cur) ? r.toMemberId() : r.fromMemberId();
                            if (!gen.containsKey(partner)) {
                                gen.put(partner, depth);
                                queue.add(partner);
                            }
                        }
                    }
                    case ADOPTION -> {
                        // Nhận nuôi không ảnh hưởng đến thế hệ -> no-op.
                    }
                }
            }
        }
        return gen;
    }

    /**
     * Trả về tập tổ tiên (transitive ancestors) của {@code member}, dựa trên
     * bao đóng bắc cầu của các cạnh {@code PARENT_CHILD}.
     * <p>
     * Thuật toán: DFS đi ngược từ {@code member} bằng cách xét các cạnh mà
     * {@code toMemberId == cur}, đẩy {@code fromMemberId} (cha/mẹ) vào stack
     * để tiếp tục duyệt. Quan hệ đã tombstone được bỏ qua.
     * </p>
     *
     * @param rels   tập các quan hệ (có thể bao gồm cả đã tombstone)
     * @param member thành viên cần tính tổ tiên
     * @return {@code LinkedHashSet} các tổ tiên theo thứ tự phát hiện
     */
    public static Set<UUID> ancestors(Collection<Relationship> rels, UUID member) {
        Set<UUID> result = new LinkedHashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();
        stack.push(member);
        while (!stack.isEmpty()) {
            UUID cur = stack.pop();
            // Tìm mọi quan hệ cha-con trong đó cur là đứa con -> cha của nó là tổ tiên.
            for (Relationship r : rels) {
                if (r.isTombstoned()) continue;
                if (r.kind() == Relationship.Kind.PARENT_CHILD
                        && Objects.equals(r.toMemberId(), cur)) {
                    UUID parent = r.fromMemberId();
                    // result.add trả về true nếu parent chưa có trong tập;
                    // chỉ đẩy lên stack khi thực sự thêm mới để tránh xử lý lặp.
                    if (result.add(parent)) {
                        stack.push(parent);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Trả về tập vợ/chồng của {@code member}.
     * <p>
     * Do mỗi cạnh {@code SPOUSE} được lưu một lần duy nhất (cạnh đối xứng do
     * use case tạo), hàm này xét cả hai chiều: nếu {@code member} nằm ở
     * {@code fromMemberId} thì trả về {@code toMemberId}, ngược lại trả về
     * {@code fromMemberId}.
     * </p>
     *
     * @param rels   tập các quan hệ trong cây
     * @param member thành viên cần truy vấn vợ/chồng
     * @return tập các định danh vợ/chồng của {@code member}
     */
    public static Set<UUID> spouses(Collection<Relationship> rels, UUID member) {
        Set<UUID> result = new LinkedHashSet<>();
        for (Relationship r : rels) {
            if (r.isTombstoned()) continue;                       // Bỏ qua quan hệ đã xóa.
            if (r.kind() != Relationship.Kind.SPOUSE) continue;   // Chỉ xét quan hệ vợ chồng.
            // Xét cả hai chiều của cạnh SPOUSE (vì lưu một lần).
            if (Objects.equals(r.fromMemberId(), member)) result.add(r.toMemberId());
            else if (Objects.equals(r.toMemberId(), member)) result.add(r.fromMemberId());
        }
        return result;
    }

    /**
     * Trả về tập các cặp nhận nuôi liên quan tới {@code member} (với tư cách
     * người nhận nuôi hoặc người được nhận nuôi).
     *
     * @param rels   tập các quan hệ trong cây
     * @param member thành viên cần truy vấn
     * @return tập các định danh liên quan trong quan hệ nhận nuôi
     */
    public static Set<UUID> adoptions(Collection<Relationship> rels, UUID member) {
        Set<UUID> result = new LinkedHashSet<>();
        for (Relationship r : rels) {
            if (r.isTombstoned()) continue;                         // Bỏ qua quan hệ đã xóa.
            if (r.kind() != Relationship.Kind.ADOPTION) continue;   // Chỉ xét quan hệ nhận nuôi.
            // Xét cả hai chiều (người nhận nuôi ↔ người được nhận nuôi).
            if (Objects.equals(r.fromMemberId(), member)) result.add(r.toMemberId());
            else if (Objects.equals(r.toMemberId(), member)) result.add(r.fromMemberId());
        }
        return result;
    }
}