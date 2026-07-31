package com.familya.identity.application.port.out;

import com.familya.identity.domain.event.IdentityEvent;

public interface IdentityEventPublisher {
    void publish(IdentityEvent event);
}
