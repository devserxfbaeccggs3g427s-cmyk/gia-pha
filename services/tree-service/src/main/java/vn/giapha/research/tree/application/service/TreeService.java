package vn.giapha.research.tree.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.giapha.research.tree.application.port.out.FamilyTreeRepository;
import vn.giapha.research.tree.application.port.out.TreeMembershipRepository;
import vn.giapha.research.tree.domain.FamilyTree;
import vn.giapha.research.tree.domain.TreeMembership;
import vn.giapha.research.tree.infrastructure.config.TreeProperties;
import vn.giapha.research.tree.infrastructure.identity.IdentityPort;
import vn.giapha.research.tree.infrastructure.identity.IdentityPort.IdentityUser;
import vn.giapha.research.tree.infrastructure.kernel.error.ConflictException;
import vn.giapha.research.tree.infrastructure.kernel.error.ForbiddenException;
import vn.giapha.research.tree.infrastructure.kernel.error.NotFoundException;
import vn.giapha.research.tree.infrastructure.kernel.error.ValidationException;
import vn.giapha.research.tree.infrastructure.kernel.id.Ids;
import vn.giapha.research.tree.infrastructure.kernel.principal.Principal;
import vn.giapha.research.tree.infrastructure.kernel.principal.TreeRole;
import vn.giapha.research.tree.infrastructure.outbox.FileCleanupEnqueuer;
import vn.giapha.research.tree.infrastructure.outbox.OutboxRelay;

@Service
public class TreeService {
    private final FamilyTreeRepository trees;
    private final TreeMembershipRepository memberships;
    private final IdentityPort identity;
    private final TreeAuthorizationService authorization;
    private final TreeAuthorizationCache authorizationCache;
    private final FileCleanupEnqueuer cleanupEnqueuer;
    private final OutboxRelay outbox;
    private final boolean allowOwnerOnlyFinalDeletion;

    public TreeService(FamilyTreeRepository trees, TreeMembershipRepository memberships,
            IdentityPort identity, TreeAuthorizationService authorization,
            TreeAuthorizationCache authorizationCache, FileCleanupEnqueuer cleanupEnqueuer,
            OutboxRelay outbox, TreeProperties properties) {
        this.trees = trees;
        this.memberships = memberships;
        this.identity = identity;
        this.authorization = authorization;
        this.authorizationCache = authorizationCache;
        this.cleanupEnqueuer = cleanupEnqueuer;
        this.outbox = outbox;
        this.allowOwnerOnlyFinalDeletion = properties.routing().ownerOnlyFinalDeletion();
    }

    @Transactional
    public FamilyTree create(Principal principal, String name, String description, Instant now) {
        if (principal == null || principal.isSystem()) {
            throw new ForbiddenException("Only authenticated users can create trees");
        }
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new ValidationException("Tree name must be 1-100 characters");
        }
        IdentityUser owner = requiredUser(principal.userId(), "Authenticated user does not exist");
        FamilyTree inserted = trees.insert(new FamilyTree(0, Ids.newId(), owner.userKey(),
                name.trim(), description == null ? null : description.trim(),
                1L, 1L, now, now));
        memberships.upsert(inserted.treeKey(), owner.userKey(), TreeRole.ADMIN);
        authorizationCache.put(inserted.treeKey(), owner.userKey(), TreeRole.ADMIN);
        enqueueTreeEvent(inserted, "TREE_CREATED", owner.externalId());
        return inserted;
    }

    @Transactional(readOnly = true)
    public List<FamilyTree> listForUser(Principal principal) {
        if (principal == null || principal.isSystem()) {
            return List.of();
        }
        return trees.listForUser(requiredUser(principal.userId(),
                "Authenticated user does not exist").userKey());
    }

    @Transactional(readOnly = true)
    public FamilyTree get(Principal principal, String externalId) {
        FamilyTree tree = requiredTree(externalId);
        authorization.authorize(tree.treeKey(), principal, TreeAuthorizationService.Action.READ, true);
        return tree;
    }

    @Transactional
    public FamilyTree update(Principal principal, String externalId, String name,
            String description, long expectedVersion, Instant now) {
        FamilyTree tree = requiredTree(externalId);
        authorization.authorize(tree.treeKey(), principal, TreeAuthorizationService.Action.WRITE, true);
        if (expectedVersion != tree.version()) {
            throw new ConflictException("VERSION_CONFLICT",
                    "Tree version is stale; refresh and retry");
        }
        FamilyTree updated = trees.update(new FamilyTree(tree.treeKey(), tree.externalId(),
                tree.ownerUserKey(), name == null ? tree.name() : name.trim(),
                description == null ? tree.description() : description.trim(), tree.revision(),
                tree.version(), tree.createdAt(), now));
        enqueueTreeEvent(updated, "TREE_UPDATED", principal.userId());
        return updated;
    }

    @Transactional
    public void delete(Principal principal, String externalId, Instant now) {
        FamilyTree tree = requiredTree(externalId);
        authorization.authorize(tree.treeKey(), principal, TreeAuthorizationService.Action.DELETE, true);
        if (allowOwnerOnlyFinalDeletion && !authorization.isOwner(tree.treeKey(), principal)) {
            throw new ForbiddenException("Final tree deletion is owner-only");
        }
        cleanupEnqueuer.enqueueForTreeDeletion(tree.treeKey());
        trees.delete(tree.treeKey());
        authorizationCache.invalidateTree(tree.treeKey());
        enqueueTreeEvent(tree, "TREE_DELETED", principal.userId());
    }

    @Transactional
    public TreeMembership assignRole(Principal principal, String externalId, String userExternalId,
            TreeRole role, Instant now) {
        FamilyTree tree = requiredTree(externalId);
        authorization.authorize(tree.treeKey(), principal,
                TreeAuthorizationService.Action.MANAGE_MEMBERS, true);
        if (role == null) {
            throw new ValidationException("role is required");
        }
        IdentityUser target = requiredUser(userExternalId, "User does not exist");
        if (target.userKey() == tree.ownerUserKey() && role != TreeRole.ADMIN) {
            throw new ConflictException("OWNER_CANNOT_BE_DEMOTED",
                    "The owner always has effective ADMIN and cannot be demoted");
        }
        if (target.userKey() != tree.ownerUserKey()) {
            memberships.upsert(tree.treeKey(), target.userKey(), role);
            authorizationCache.invalidateTree(tree.treeKey());
        }
        enqueueTreeEvent(tree, "TREE_ROLE_ASSIGNED", target.externalId());
        return new TreeMembership(tree.treeKey(), target.userKey(), target.externalId(),
                target.userKey() == tree.ownerUserKey() ? TreeRole.ADMIN : role, 1L, now, now);
    }

    @Transactional(readOnly = true)
    public List<TreeMembership> listMemberships(Principal principal, String externalId) {
        FamilyTree tree = requiredTree(externalId);
        authorization.authorize(tree.treeKey(), principal, TreeAuthorizationService.Action.READ, true);
        return memberships.listByTree(tree.treeKey());
    }

    private FamilyTree requiredTree(String externalId) {
        return trees.findByExternalId(externalId)
                .orElseThrow(() -> new NotFoundException("TREE_NOT_FOUND", "Tree does not exist"));
    }

    private IdentityUser requiredUser(String externalId, String message) {
        return identity.findUser(externalId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", message));
    }

    private void enqueueTreeEvent(FamilyTree tree, String eventType, String actorExternalId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeExternalId", tree.externalId());
        payload.put("revision", tree.revision());
        payload.put("actor", actorExternalId);
        outbox.append(eventType, tree.externalId(), tree.treeKey(), payload);
    }
}
