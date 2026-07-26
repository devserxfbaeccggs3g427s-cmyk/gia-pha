package vn.giapha.research.tree.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import vn.giapha.research.tree.infrastructure.kernel.principal.TreeRole;

/**
 * In-memory tree authorization cache (Task 21.4). Entries expire after
 * {@link #TTL}; mutation operations
 * ({@link vn.giapha.research.tree.application.service.TreeMembershipService},
 * tree deletion) call {@link #invalidateTree(long)} / {@link #invalidateUser(long)}
 * so a stale role never survives a write. Distributed deployments use a
 * shared cache in front of this component (Task 16 / 41).
 */
@Component
public class TreeAuthorizationCache {

    private static final Duration TTL = Duration.ofMinutes(2);

    private final Map<CacheKey, CacheEntry> entries = new ConcurrentHashMap<>();

    public TreeRole get(long treeKey, long userKey) {
        CacheEntry entry = entries.get(new CacheKey(treeKey, userKey));
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt.isBefore(Instant.now())) {
            entries.remove(new CacheKey(treeKey, userKey));
            return null;
        }
        return entry.role;
    }

    public void put(long treeKey, long userKey, TreeRole role) {
        entries.put(new CacheKey(treeKey, userKey),
                new CacheEntry(role, Instant.now().plus(TTL)));
    }

    public void invalidateTree(long treeKey) {
        Iterator<CacheKey> it = entries.keySet().iterator();
        while (it.hasNext()) {
            CacheKey key = it.next();
            if (key.treeKey == treeKey) {
                it.remove();
            }
        }
    }

    public void invalidateUser(long userKey) {
        Iterator<CacheKey> it = entries.keySet().iterator();
        while (it.hasNext()) {
            CacheKey key = it.next();
            if (key.userKey == userKey) {
                it.remove();
            }
        }
    }

    public int size() {
        return entries.size();
    }

    private record CacheKey(long treeKey, long userKey) {}

    private record CacheEntry(TreeRole role, Instant expiresAt) {}
}
