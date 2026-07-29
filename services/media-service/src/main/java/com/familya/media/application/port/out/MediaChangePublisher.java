package com.familya.media.application.port.out;

import com.familya.media.domain.event.MediaChange;

public interface MediaChangePublisher {
    void publish(MediaChange change);
}
