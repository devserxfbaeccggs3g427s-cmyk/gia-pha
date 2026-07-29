package com.familya.sharing.application.usecase;

import com.familya.sharing.application.port.in.RestoreSharingTreeCommand;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.domain.model.ShareLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestoreSharingTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RestoreSharingTreeUseCase.class);

    private final ShareLinkRepository repo;

    public RestoreSharingTreeUseCase(ShareLinkRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Result execute(RestoreSharingTreeCommand cmd) {
        long maxVersion = 0L;
        int restored = 0;
        for (ShareLink l : repo.listByTree(cmd.treeId())) {
            if (l.revokedAt() == null) continue;
            if (l.revocationReason() == null
                    || !l.revocationReason().startsWith("delete-tree-saga:")) continue;
            ShareLink alive = new ShareLink(l.id(), l.treeId(), l.scope(), l.targetId(),
                    l.role(), l.tokenHash(), l.createdByUserId(),
                    l.createdAt(), l.expiresAt(), null, null,
                    l.revision(), l.version() + 1);
            repo.update(alive);
            maxVersion = Math.max(maxVersion, alive.version());
            restored++;
        }
        LOG.info("Restored {} share links on tree {} operationId={}",
                restored, cmd.treeId(), cmd.operationId());
        return new Result(restored, maxVersion, 0L);
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}