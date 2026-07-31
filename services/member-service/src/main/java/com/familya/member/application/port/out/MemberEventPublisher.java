package com.familya.member.application.port.out;

import com.familya.member.domain.event.MemberEvent;

/**
 * Port ra ngoài dùng để phát các sự kiện vòng đời của thành viên (MemberCreated,
 * MemberTombstoned, MemberMerged, ...). Triển khai cụ thể dùng outbox.
 */
public interface MemberEventPublisher {

    /**
     * Phát một sự kiện thành viên. Thường được gọi trong cùng transaction với thay đổi
     * CSDL để đảm bảo nguyên tử (outbox pattern).
     *
     * @param event sự kiện cần phát
     */
    void publish(MemberEvent event);
}