package com.familya.media.application.port.out;

import com.familya.platform.outbox.OutboxRecord;

import java.util.List;
import java.util.UUID;

public interface MediaOutbox {
    void stage(OutboxRecord record);

    List<OutboxRecord> listPending(int limit);
}
