package com.familya.sharing.application.usecase;

import com.familya.sharing.application.port.in.RevokeSharingTreeCommand;
import com.familya.sharing.application.port.out.ShareLinkRepository;
import com.familya.sharing.domain.model.ShareLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class RevokeSharingTreeUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RevokeSharingTreeUseCase.class);

    private final ShareLinkRepository repo;

    public RevokeSharingTreeUseCase(ShareLinkRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Result execute(RevokeSharingTreeCommand cmd) {
        List<ShareLink> links = repo.listByTree(cmd.treeId());
        Instant now = Instant.now();
        long maxVersion = 0L;
        for (ShareLink l : links) {
            if (l.revokedAt() != null) continue;
            ShareLink revoked = new ShareLink(l.id(), l.treeId(), l.scope(), l.targetId(),
                    l.role(), l.tokenHash(), l.createdByUserId(), l.createdAt(), l.expiresAt(),
                    now, "delete-tree-saga:" + cmd.operationId(), l.revision(), l.version() + 1);
            repo.update(revoked);
            maxVersion = Math.max(maxVersion, revoked.version());
        }
        long appliedVersion = Math.max(maxVersion, cmd.targetAggregateVersion());
        LOG.info("Revoked {} share links on tree {} operationId={}",
                links.size(), cmd.treeId(), cmd.operationId());
        return new Result(links.size(), appliedVersion, cmd.targetEpoch());
    }

    public record Result(int affectedCount, long appliedAggregateVersion, long appliedEpoch) { }
}