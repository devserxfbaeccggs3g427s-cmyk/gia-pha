package com.familya.treeaccess.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Membership event base. Topic is {@code tree.memberships.v1} so
 * downstream services can subscribe to a single ordered stream of
 * membership changes per tree. Partition key is treeId.
 */
public abstract class MembershipEvent {
    /**
     * @return mã cây
     */
    public abstract UUID treeId();
    /**
     * @return UUID người dùng liên quan
     */
    public abstract UUID userId();
    /**
     * @return tên loại sự kiện (ví dụ {@code MembershipGranted})
     */
    public abstract String eventType();
    /**
     * @return phiên bản schema của sự kiện
     */
    public abstract int eventVersion();
    /**
     * @return thời điểm phát sinh sự kiện
     */
    public abstract Instant occurredAt();

    /**
     * @return tên topic Kafka mặc định cho sự kiện thành viên
     */
    public String topic() { return "tree.memberships.v1"; }
    /**
     * @return khoá phân vùng — luôn là {@code treeId} để bảo toàn thứ tự
     */
    public String partitionKey() { return treeId().toString(); }
}