package com.familya.member.application.port.out;

import com.familya.member.domain.model.CanonicalKey;
import com.familya.member.domain.model.Member;
import com.familya.member.domain.model.MemberAuthRow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hợp đồng (port) cho kho lưu trữ thành viên. Triển khai cụ thể thuộc tầng adapter-out
 * (xem {@code JdbcMemberRepository}). Cung cấp các thao tác CRUD cho thành viên,
 * khóa canonical (dùng cho phát hiện trùng lặp) và projection ủy quyền.
 */
public interface MemberRepository {

    /**
     * Chèn một thành viên mới.
     * @param member thành viên cần chèn
     */
    void insert(Member member);

    /**
     * Tra cứu thành viên theo mã id.
     * @param memberId mã thành viên
     * @return thành viên hoặc rỗng
     */
    Optional<Member> findById(UUID memberId);

    /**
     * Liệt kê thành viên của một cây.
     * @param treeId            mã cây
     * @param includeTombstoned {@code true} nếu bao gồm cả thành viên đã tombstone
     * @return danh sách thành viên
     */
    List<Member> listByTree(UUID treeId, boolean includeTombstoned);

    /**
     * Cập nhật thông tin thành viên.
     * @param member thành viên cần cập nhật
     */
    void update(Member member);

    /**
     * Tìm mã thành viên theo khóa canonical.
     * @param key khóa canonical
     * @return mã thành viên hoặc rỗng
     */
    Optional<UUID> findByCanonicalKey(CanonicalKey key);

    /**
     * Chèn một khóa canonical cho thành viên.
     * @param key      khóa canonical
     * @param memberId mã thành viên
     */
    void insertCanonicalKey(CanonicalKey key, UUID memberId);

    /**
     * Xóa khóa canonical của một thành viên.
     * @param memberId mã thành viên
     * @param key      khóa canonical
     */
    void removeCanonicalKey(UUID memberId, CanonicalKey key);

    /**
     * Tra cứu projection ủy quyền cho cặp (cây, người dùng).
     * @param treeId mã cây
     * @param userId mã người dùng
     * @return dòng ủy quyền hoặc rỗng
     */
    Optional<MemberAuthRow> findAuth(UUID treeId, UUID userId);

    /**
     * Upsert projection ủy quyền.
     * @param row dòng ủy quyền
     */
    void upsertAuth(MemberAuthRow row);

    /**
     * Cập nhật vai trò/trạng thái thu hồi của projection ủy quyền — dùng bởi consumer projection.
     */
    void updateAuthRole(UUID treeId, UUID userId, String role, boolean revoked,
                        long revision, long epoch, java.time.Instant grantedAt,
                        String sourceEventId, java.time.Instant now);

    /**
     * Tombstone hàng loạt mọi thành viên chưa tombstone trong cây. Triển khai mặc định
     * lặp qua từng thành viên để giữ interface tương thích nhị phân; triển khai JDBC
     * ghi đè bằng câu UPDATE hàng loạt tối ưu cho môi trường sản xuất.
     *
     * @param treeId mã cây cần tombstone
     * @param at     thời điểm tombstone
     * @return số thành viên đã được tombstone
     */
    default int bulkTombstoneByTree(UUID treeId, Instant at) {
        int n = 0;
        for (Member m : listByTree(treeId, false)) {
            m.tombstone(m.version(), at);
            update(m);
            n++;
        }
        return n;
    }
}