package com.familya.treeaccess.application.port.out;

import com.familya.treeaccess.domain.event.MembershipEvent;
import com.familya.treeaccess.domain.event.TreeEvent;

/**
 * Port publish sự kiện cây và thành viên ra ngoài (outbox + Kafka).
 * Adapter cụ thể chịu trách nhiệm ghi vào outbox trong cùng transaction với
 * thay đổi trạng thái.
 */
public interface TreeEventPublisher {
    /**
     * Publish một sự kiện cây.
     *
     * @param event sự kiện cây cần publish
     */
    void publishTreeEvent(TreeEvent event);

    /**
     * Publish một sự kiện thành viên.
     *
     * @param event sự kiện thành viên cần publish
     */
    void publishMembershipEvent(MembershipEvent event);
}