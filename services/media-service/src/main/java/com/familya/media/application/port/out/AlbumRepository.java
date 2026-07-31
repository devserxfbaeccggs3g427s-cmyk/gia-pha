package com.familya.media.application.port.out;

import com.familya.media.domain.model.Album;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port ra (driven port) của kiến trúc hexagonal: kho lưu trữ {@code Album}.
 *
 * <p>Tầng application chỉ phụ thuộc vào interface này; triển khai cụ thể
 * (JDBC, in-memory test double, ...) được cung cấp ở tầng adapter. Hợp
 * đồng:</p>
 *
 * <ul>
 *   <li>Mọi thao tác ghi phải được thực hiện trong transaction bao ngoài
 *       của caller.</li>
 *   <li>Các phương thức ghi có {@code expectedVersion} để áp dụng
 *       optimistic concurrency.</li>
 * </ul>
 */
public interface AlbumRepository {

    /**
     * Chèn mới một album vào kho lưu trữ.
     *
     * @param album bản ghi album cần chèn; phải có {@code id} khác null và
     *              chưa tồn tại trong kho.
     * @throws org.springframework.dao.DuplicateKeyException nếu {@code id}
     *                  đã tồn tại.
     */
    void insert(Album album);

    /**
     * Tra cứu album theo {@code id}.
     *
     * @param id UUID album cần tra.
     * @return {@code Optional} chứa album; rỗng nếu không tìm thấy hoặc
     *         album đã tombstone và bên triển khai lọc ra (tùy chính sách).
     */
    Optional<Album> findById(UUID id);

    /**
     * Liệt kê album thuộc một family-tree.
     *
     * @param treeId UUID family-tree.
     * @param includeTombstoned {@code true} để bao gồm cả album đã
     *                          tombstone; {@code false} chỉ trả về album
     *                          còn hoạt động.
     * @return danh sách album; có thể rỗng nhưng không null.
     */
    List<Album> listByTree(UUID treeId, boolean includeTombstoned);

    /**
     * Cập nhật toàn bộ trường của album.
     *
     * @param album bản ghi album với {@code version} mới; bên triển khai
     *              sẽ so khớp {@code expectedVersion} nội bộ hoặc bằng
     *              trigger tương đương.
     */
    void update(Album album);

    /**
     * Đánh dấu tombstone album.
     *
     * @param id UUID album cần tombstone.
     * @param at mốc thời gian ghi nhận tombstone.
     * @param expectedVersion phiên bản kỳ vọng để chống ghi đè; nếu lệch
     *                       sẽ ném {@code OptimisticConcurrencyException}.
     */
    void tombstone(UUID id, Instant at, long expectedVersion);
}
