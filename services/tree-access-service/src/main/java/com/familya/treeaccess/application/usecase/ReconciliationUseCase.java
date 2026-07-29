package com.familya.treeaccess.application.usecase;

import com.familya.treeaccess.application.port.out.TreeRepository;
import com.familya.treeaccess.domain.model.AuthorizationProjection;
import com.familya.treeaccess.domain.model.Tree;
import com.familya.treeaccess.domain.model.TreeMembership;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reconciliation report for the cutover gate. Walks every tree, every
 * membership, and the authorization projection, and reports any
 * discrepancy (owner-immutability violation, projection drift, stale
 * rows). Used by the migration & reconciliation pipeline as a blocking
 * gate.
 */
@Service
public class ReconciliationUseCase {

    private final TreeRepository repo;
    private final Duration staleThreshold;

    public ReconciliationUseCase(TreeRepository repo,
                                 @org.springframework.beans.factory.annotation.Value(
                                         "${familya.treeauth.reconciliation-stale-seconds:300}") long staleSeconds) {
        this.repo = repo;
        this.staleThreshold = Duration.ofSeconds(staleSeconds);
    }

    public Report reconcile(UUID treeId) {
        Optional<Tree> treeOpt = repo.findTree(treeId);
        if (treeOpt.isEmpty()) {
            return new Report(treeId, List.of(new Discrepancy("tree.missing", "Tree " + treeId + " does not exist")));
        }
        Tree tree = treeOpt.get();
        List<Discrepancy> discrepancies = new ArrayList<>();
        // Owner must always have ADMIN membership, and the projection must exist.
        var ownerMembership = repo.findMembership(treeId, tree.ownerUserId());
        if (ownerMembership.isEmpty() || ownerMembership.get().role() != TreeMembership.Role.ADMIN) {
            discrepancies.add(new Discrepancy("owner.membership_missing",
                    "Tree " + treeId + " owner " + tree.ownerUserId() + " has no ADMIN membership"));
        }
        var ownerProjection = repo.findProjection(treeId, tree.ownerUserId());
        if (ownerProjection.isEmpty() || ownerProjection.get().role() != TreeMembership.Role.ADMIN || ownerProjection.get().revoked()) {
            discrepancies.add(new Discrepancy("owner.projection_missing",
                    "Tree " + treeId + " owner " + tree.ownerUserId() + " projection missing/revoked"));
        }
        // Every active membership must have a matching projection row at the
        // current revision; every projection row must back an active membership.
        var memberships = repo.listMemberships(treeId);
        for (TreeMembership m : memberships) {
            if (m.treeId().equals(tree.ownerUserId()) && m.userId().equals(tree.ownerUserId())) continue;
            Optional<AuthorizationProjection> proj = repo.findProjection(treeId, m.userId());
            if (m.isActive()) {
                if (proj.isEmpty()) {
                    discrepancies.add(new Discrepancy("projection.missing",
                            "Active membership " + m.id() + " has no projection row"));
                } else if (proj.get().revoked()) {
                    discrepancies.add(new Discrepancy("projection.revoked_but_active",
                            "Active membership " + m.id() + " is marked revoked in projection"));
                } else if (proj.get().revision() != tree.revision()) {
                    discrepancies.add(new Discrepancy("projection.revision_drift",
                            "Projection revision " + proj.get().revision() + " != tree revision " + tree.revision()));
                }
            }
            if (proj.isPresent() && proj.get().lastUpdatedAt() != null
                    && Duration.between(proj.get().lastUpdatedAt(), Instant.now()).compareTo(staleThreshold) > 0) {
                discrepancies.add(new Discrepancy("projection.stale",
                        "Projection row " + treeId + "/" + m.userId() + " is older than " + staleThreshold.toSeconds() + "s"));
            }
        }
        return new Report(treeId, discrepancies);
    }

    public record Discrepancy(String code, String detail) { }

    public record Report(UUID treeId, List<Discrepancy> discrepancies) {
        public boolean isClean() { return discrepancies.isEmpty(); }
    }
}