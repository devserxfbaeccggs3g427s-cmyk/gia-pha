package com.familya.treeaccess.application.port.out;

import com.familya.treeaccess.domain.model.Tree;
import com.familya.treeaccess.domain.model.TreeMembership;
import com.familya.treeaccess.domain.model.AuthorizationProjection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port kho lưu trữ chính cho cây, thành viên và projection phân quyền.
 * Cài đặt cụ thể (JDBC) chịu trách nhiệm tương tác với cơ sở dữ liệu.
 */
public interface TreeRepository {

    /**
     * Chèn một cây mới kèm membership owner.
     *
     * @param tree            thực thể cây
     * @param ownerMembership dòng membership ADMIN của owner
     */
    void insertTree(Tree tree, TreeMembership ownerMembership);

    /**
     * Tìm cây theo mã.
     *
     * @param treeId mã cây
     * @return {@link Optional} chứa cây nếu tồn tại
     */
    Optional<Tree> findTree(UUID treeId);

    /**
     * Lấy danh sách cây của một owner.
     *
     * @param ownerUserId UUID owner
     * @return danh sách cây
     */
    List<Tree> findTreesByOwner(UUID ownerUserId);

    /**
     * Cập nhật cây.
     *
     * @param tree thực thể cây cần cập nhật
     */
    void updateTree(Tree tree);

    /**
     * Chèn một dòng membership.
     *
     * @param membership thực thể membership
     */
    void insertMembership(TreeMembership membership);

    /**
     * Tìm membership mới nhất của một người dùng trên cây.
     *
     * @param treeId mã cây
     * @param userId mã người dùng
     * @return {@link Optional} chứa membership nếu có
     */
    Optional<TreeMembership> findMembership(UUID treeId, UUID userId);

    /**
     * Lấy tất cả membership của một cây.
     *
     * @param treeId mã cây
     * @return danh sách membership
     */
    List<TreeMembership> listMemberships(UUID treeId);

    /**
     * Cập nhật dòng membership.
     *
     * @param membership thực thể membership đã mutate
     */
    void updateMembership(TreeMembership membership);

    /**
     * Upsert một hàng projection phân quyền.
     *
     * @param projection projection cần lưu
     */
    void upsertProjection(AuthorizationProjection projection);

    /**
     * Tìm projection theo cây và người dùng.
     *
     * @param treeId mã cây
     * @param userId mã người dùng
     * @return {@link Optional} chứa projection nếu có
     */
    Optional<AuthorizationProjection> findProjection(UUID treeId, UUID userId);

    /**
     * Revision hiện tại của cây, {@code 0} nếu cây không tồn tại.
     *
     * @param treeId mã cây
     * @return revision hiện tại
     */
    long currentRevision(UUID treeId);
}