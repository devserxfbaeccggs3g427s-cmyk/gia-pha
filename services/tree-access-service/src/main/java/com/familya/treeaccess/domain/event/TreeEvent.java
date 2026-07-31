package com.familya.treeaccess.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Tree event base. The topic is derived from the bounded context
 * (tree.events.v1) so all tree events partition by treeId. The
 * partition key is required for downstream ordering.
 */
public abstract class TreeEvent {
    /**
     * @return mã cây
     */
    public abstract UUID treeId();
    /**
     * @return revision tại thời điểm phát sinh sự kiện
     */
    public abstract long revision();
    /**
     * @return epoch tại thời điểm phát sinh sự kiện
     */
    public abstract long epoch();
    /**
     * @return tên loại sự kiện
     */
    public abstract String eventType();
    /**
     * @return phiên bản schema
     */
    public abstract int eventVersion();
    /**
     * @return thời điểm phát sinh
     */
    public abstract Instant occurredAt();

    /**
     * @return topic Kafka mặc định cho sự kiện cây
     */
    public String topic() { return "tree.events.v1"; }
    /**
     * @return khoá phân vùng — luôn là {@code treeId}
     */
    public String partitionKey() { return treeId().toString(); }
}