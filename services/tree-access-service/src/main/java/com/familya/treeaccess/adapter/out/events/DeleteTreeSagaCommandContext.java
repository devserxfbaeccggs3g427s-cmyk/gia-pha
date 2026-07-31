package com.familya.treeaccess.adapter.out.events;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Per-thread context carrying the most recent event id and traceparent emitted
 * by a Saga initiator. Used by {@link OutboxDeleteTreeSagaGateway} to thread
 * a correct {@code causationId} (the immediately preceding message id) and
 * {@code traceparent} (W3C trace context) into the next envelope.
 */
@Component
public class DeleteTreeSagaCommandContext {

    private static final int MAX_DEPTH = 64;

    private final ThreadLocal<Entry> current = new ThreadLocal<>();

    public Optional<String> lastEventId() {
        Entry e = current.get();
        if (e == null) return Optional.empty();
        return e.lastEventId == null ? Optional.empty() : Optional.of(e.lastEventId);
    }

    public Optional<String> traceparent() {
        Entry e = current.get();
        if (e == null) return Optional.empty();
        return e.traceparent == null || e.traceparent.isBlank() ? Optional.empty() : Optional.of(e.traceparent);
    }

    public void startSaga(String traceparent) {
        current.set(new Entry(traceparent));
    }

    public void clear() {
        current.remove();
    }

    public void recordEvent(String eventId) {
        Entry e = current.get();
        if (e == null) return;
        if (e.history.size() >= MAX_DEPTH) {
            e.history.pollFirst();
        }
        e.history.addLast(eventId);
        e.lastEventId = eventId;
    }

    private static final class Entry {
        final String traceparent;
        final Deque<String> history = new ArrayDeque<>();
        String lastEventId;

        Entry(String traceparent) {
            this.traceparent = traceparent;
        }
    }
}
