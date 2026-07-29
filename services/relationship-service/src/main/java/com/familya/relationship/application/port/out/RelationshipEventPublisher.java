package com.familya.relationship.application.port.out;

import com.familya.relationship.domain.event.RelationshipEvent;

public interface RelationshipEventPublisher {
    void publish(RelationshipEvent event);
}