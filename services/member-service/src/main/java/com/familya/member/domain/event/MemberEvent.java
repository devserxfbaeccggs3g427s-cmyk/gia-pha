package com.familya.member.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Member event base. Topic is {@code member.events.v1}, partitioned by
 * treeId so consumers can subscribe to a single ordered stream of
 * member changes per tree.
 */
public abstract class MemberEvent {
    /** Mã cây liên quan đến sự kiện. */
    public abstract UUID treeId();
    /** Mã thành viên liên quan đến sự kiện. */
    public abstract UUID memberId();
    /** Loại sự kiện (ví dụ: MemberCreated, MemberTombstoned, MemberMerged). */
    public abstract String eventType();
    /** Phiên bản schema của sự kiện. */
    public abstract int eventVersion();
    /** Thời điểm sự kiện xảy ra. */
    public abstract Instant occurredAt();

    /** Topic Kafka mặc định cho mọi sự kiện thành viên. */
    public String topic() { return "member.events.v1"; }
    /** Khóa phân vùng — đảm bảo thứ tự theo cây. */
    public String partitionKey() { return treeId().toString(); }
}