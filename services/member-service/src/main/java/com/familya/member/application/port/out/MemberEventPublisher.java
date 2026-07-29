package com.familya.member.application.port.out;

import com.familya.member.domain.event.MemberEvent;

public interface MemberEventPublisher {
    void publish(MemberEvent event);
}