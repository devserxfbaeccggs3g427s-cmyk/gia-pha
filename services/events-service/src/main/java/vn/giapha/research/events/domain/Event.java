package vn.giapha.research.events.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import vn.giapha.research.events.support.EventSupport.ValidationException;

/**
 * Event aggregate; participating members and attached media are ordered
 * internal keys persisted through `event_members` / `event_media`
 * (the latter being the sole event↔media source of truth).
 */
public record Event(
        long eventKey,
        long treeKey,
        String externalId,
        EventType type,
        String customType,
        String title,
        LocalDate eventDate,
        String location,
        String description,
        List<Long> memberKeys,
        List<Long> mediaKeys,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public Event {
        if (type == EventType.CUSTOM && (customType == null || customType.isBlank())) {
            throw new ValidationException("customType is required for CUSTOM events");
        }
        memberKeys = List.copyOf(memberKeys);
        mediaKeys = List.copyOf(mediaKeys);
    }
}
