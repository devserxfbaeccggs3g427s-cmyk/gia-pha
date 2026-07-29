package com.familya.event.application.port.out;

import com.familya.event.domain.event.DomainEventChange;

public interface EventChangePublisher {
    void publish(DomainEventChange change);
}