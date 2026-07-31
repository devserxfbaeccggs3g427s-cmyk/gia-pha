package com.familya.platform.inbox;

/**
 * Inbox dedupe store. Consumers call {@link #exists} before processing
 * an event and {@link #markProcessed} after the local transaction
 * commits. The inbox is the at-least-once safe boundary; downstream
 * handlers are idempotent by aggregate version.
 */
public interface InboxStore {
    boolean exists(String eventId, String consumer);

    void markProcessed(InboxRecord record);
}
