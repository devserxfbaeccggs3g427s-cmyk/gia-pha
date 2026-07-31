package com.familya.event.application.usecase;

import com.familya.event.application.port.in.RestoreMemberEventReferencesCommand;
import com.familya.event.application.port.out.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restores primary/additional member references on events that were cleared
 * by {@link DetachMemberEventReferencesUseCase}. The detach use case persists
 * a compensation snapshot keyed by {@code operationId} so this restore can
 * deterministically put the member back.
 */
@Service
public class RestoreMemberEventReferencesUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreMemberEventReferencesUseCase.class);

    private final EventRepository repo;

    public RestoreMemberEventReferencesUseCase(EventRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Result execute(RestoreMemberEventReferencesCommand cmd) {
        String snapshot = repo.loadCompensationSnapshot(cmd.operationId());
        if (snapshot == null) {
            LOG.warn("No compensation snapshot for operationId={} (likely already restored)",
                    cmd.operationId());
            return new Result(0, 0L, 0L);
        }
        int restored = repo.restoreMemberReferences(cmd.operationId(), cmd.memberId());
        LOG.info("Restored member references on {} events for operationId={}",
                restored, cmd.operationId());
        return new Result(restored, restored == 0 ? 0L : 1L, 0L);
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}