package vn.giapha.research.tree.application.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import vn.giapha.research.tree.application.port.out.FamilyTreeRepository;
import vn.giapha.research.tree.application.port.out.TreeMembershipRepository;
import vn.giapha.research.tree.domain.FamilyTree;
import vn.giapha.research.tree.infrastructure.identity.IdentityPort;
import vn.giapha.research.tree.infrastructure.identity.IdentityPort.IdentityUser;
import vn.giapha.research.tree.infrastructure.kernel.error.ForbiddenException;
import vn.giapha.research.tree.infrastructure.kernel.error.NotFoundException;
import vn.giapha.research.tree.infrastructure.kernel.principal.Principal;
import vn.giapha.research.tree.infrastructure.kernel.principal.TreeRole;

@Service
public class TreeAuthorizationService {
    public enum Action {
        READ, WRITE, DELETE, SHARE, MANAGE_MEMBERS
    }

    private final IdentityPort identity;
    private final FamilyTreeRepository trees;
    private final TreeMembershipRepository memberships;
    private final TreeAuthorizationCache cache;

    public TreeAuthorizationService(IdentityPort identity, FamilyTreeRepository trees,
            TreeMembershipRepository memberships, TreeAuthorizationCache cache) {
        this.identity = identity;
        this.trees = trees;
        this.memberships = memberships;
        this.cache = cache;
    }

    public Optional<TreeRole> authorize(long treeKey, Principal principal, Action action,
            boolean failClosed) {
        if (principal == null || principal.isSystem()) {
            return systemAuthorize(action, failClosed);
        }
        Optional<TreeRole> role = resolveRole(treeKey, principal);
        if (role.isEmpty() || !isAllowed(role.get(), action)) {
            if (failClosed) {
                throw new ForbiddenException("Principal cannot perform " + action
                        + " on tree " + treeKey);
            }
            return Optional.empty();
        }
        return role;
    }

    public Optional<TreeRole> resolveRole(long treeKey, Principal principal) {
        IdentityUser user = identity.findUser(principal.userId()).orElse(null);
        if (user == null) {
            return Optional.empty();
        }
        TreeRole cached = cache.get(treeKey, user.userKey());
        if (cached != null) {
            return Optional.of(cached);
        }
        FamilyTree tree = trees.findByKey(treeKey).orElse(null);
        if (tree == null) {
            return Optional.empty();
        }
        TreeRole role = tree.ownerUserKey() == user.userKey()
                ? TreeRole.ADMIN
                : memberships.findRole(treeKey, user.userKey()).orElse(null);
        if (role != null) {
            cache.put(treeKey, user.userKey(), role);
        }
        return Optional.ofNullable(role);
    }

    public long authorizeAndResolve(String treeExternalId, Principal principal) {
        FamilyTree tree = trees.findByExternalId(treeExternalId)
                .orElseThrow(() -> new NotFoundException("TREE_NOT_FOUND", "Tree does not exist"));
        authorize(tree.treeKey(), principal, Action.READ, true);
        return tree.treeKey();
    }

    public void invalidate(long treeKey) {
        cache.invalidateTree(treeKey);
    }

    public void invalidateUser(long userKey) {
        cache.invalidateUser(userKey);
    }

    public boolean isOwner(long treeKey, Principal principal) {
        if (principal == null || principal.isSystem()) {
            return false;
        }
        return identity.findUser(principal.userId())
                .flatMap(user -> trees.findByKey(treeKey)
                        .map(tree -> tree.ownerUserKey() == user.userKey()))
                .orElse(false);
    }

    private static boolean isAllowed(TreeRole role, Action action) {
        return switch (action) {
            case READ -> role.atLeast(TreeRole.VIEWER);
            case WRITE -> role.atLeast(TreeRole.EDITOR);
            case MANAGE_MEMBERS, SHARE -> role.atLeast(TreeRole.ADMIN);
            case DELETE -> role == TreeRole.ADMIN;
        };
    }

    private static Optional<TreeRole> systemAuthorize(Action action, boolean failClosed) {
        if (action == Action.READ) {
            return Optional.of(TreeRole.VIEWER);
        }
        if (failClosed) {
            throw new ForbiddenException("System principal cannot perform " + action);
        }
        return Optional.empty();
    }
}
