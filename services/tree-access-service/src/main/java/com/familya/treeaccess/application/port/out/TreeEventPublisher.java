package com.familya.treeaccess.application.port.out;

import com.familya.treeaccess.domain.event.MembershipEvent;
import com.familya.treeaccess.domain.event.TreeEvent;

public interface TreeEventPublisher {
    void publishTreeEvent(TreeEvent event);
    void publishMembershipEvent(MembershipEvent event);
}