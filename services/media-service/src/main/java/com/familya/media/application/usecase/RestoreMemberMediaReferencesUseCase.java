package com.familya.media.application.usecase;

import com.familya.media.application.port.in.RestoreMemberMediaReferencesCommand;
import com.familya.media.application.port.out.MediaReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restores MEMBER-kind media references cleared by
 * {@link DetachMemberMediaReferencesUseCase}. The detach use case persists
 * a compensation snapshot keyed by {@code operationId} so this restore can
 * deterministically re-attach references for binary rollback safety.
 */
@Service
public class RestoreMemberMediaReferencesUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreMemberMediaReferencesUseCase.class);

    private final MediaReferenceRepository refs;

    public RestoreMemberMediaReferencesUseCase(MediaReferenceRepository refs) {
        this.refs = refs;
    }

    @Transactional
    public Result execute(RestoreMemberMediaReferencesCommand cmd) {
        int restored = refs.restoreMemberReferences(cmd.operationId());
        LOG.info("Restored {} media references for member {} operationId={}",
                restored, cmd.memberId(), cmd.operationId());
        return new Result(restored, 0L, 0L);
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}