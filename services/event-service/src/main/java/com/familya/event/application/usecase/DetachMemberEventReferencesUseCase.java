package com.familya.event.application.usecase;

import com.familya.event.application.port.in.DetachMemberEventReferencesCommand;
import com.familya.event.application.port.out.EventRepository;
import com.familya.event.domain.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Participant step for delete-member Saga. Removes the member from the
 * primary/additional reference lists of every event that references it.
 * The event itself is preserved; only member references are cleared so the
 * event no longer shows the deleted person.
 */
@Service
public class DetachMemberEventReferencesUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(DetachMemberEventReferencesUseCase.class);

    private final EventRepository repo;

    public DetachMemberEventReferencesUseCase(EventRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Result execute(DetachMemberEventReferencesCommand cmd) {
        List<DomainEvent> referencing = repo.listReferencingMember(cmd.treeId(), cmd.memberId());
        Instant now = Instant.now();
        long maxVersion = 0L;
        for (DomainEvent ev : referencing) {
            List<UUID> additional = ev.additionalMemberIds().stream()
                    .filter(id -> !id.equals(cmd.memberId()))
                    .toList();
            ev.update(ev.title(), ev.description(), ev.kind(),
                    ev.startDate(), ev.endDate(), ev.recurrence(),
                    ev.primaryMemberId() != null && ev.primaryMemberId().equals(cmd.memberId())
                            ? null : ev.primaryMemberId(),
                    additional, ev.mediaRefs(), ev.location(),
                    ev.version(), now);
            repo.update(ev);
            maxVersion = Math.max(maxVersion, ev.version());
        }

        long appliedVersion = Math.max(maxVersion, cmd.targetAggregateVersion());
        long appliedEpoch = cmd.targetEpoch();
        LOG.info("Detached member {} from {} events on tree {} operationId={}",
                cmd.memberId(), referencing.size(), cmd.treeId(), cmd.operationId());
        return new Result(referencing.size(), appliedVersion, appliedEpoch);
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}