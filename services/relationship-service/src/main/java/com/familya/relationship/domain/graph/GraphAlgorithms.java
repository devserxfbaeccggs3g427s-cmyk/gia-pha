package com.familya.relationship.domain.graph;

import com.familya.relationship.domain.exception.CycleDetectedException;
import com.familya.relationship.domain.model.Relationship;

import java.util.*;

/**
 * Genealogy algorithms. The implementation preserves the legacy
 * family-graph behavior:
 *
 * <ul>
 *   <li><b>Cycle detection</b> (PARENT_CHILD): before adding a
 *       {@code parent → child} edge, walk the ancestry from the
 *       proposed parent. If {@code child} appears in the parent's
 *       ancestors, adding the edge would create a cycle and the
 *       command is rejected.</li>
 *   <li><b>Generation</b>: BFS from a focal ancestor; depth from the
 *       root is the generation number. Root has generation 0.</li>
 *   <li><b>Ancestry</b>: full transitive closure of PARENT_CHILD edges.</li>
 *   <li><b>Spouse</b>: bidirectional SPOUSE edges.</li>
 *   <li><b>Adoption</b>: ADOPTION edges do not change generation; the
 *       adopted child keeps biological generation unless overridden.</li>
 * </ul>
 */
public final class GraphAlgorithms {

    private GraphAlgorithms() { }

    /**
     * Throws {@link CycleDetectedException} if adding
     * {@code parent → child} would create a cycle.
     */
    public static void assertNoCycle(Collection<Relationship> existing, UUID parent, UUID child) {
        if (Objects.equals(parent, child)) {
            throw new CycleDetectedException("Self-loop is not allowed: " + parent);
        }
        // Walk ancestors of parent; if child appears, reject.
        Deque<UUID> stack = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        stack.push(parent);
        while (!stack.isEmpty()) {
            UUID cur = stack.pop();
            if (!visited.add(cur)) continue;
            if (Objects.equals(cur, child)) {
                throw new CycleDetectedException(
                        "Cycle detected: adding " + parent + " -> " + child + " would close an ancestry loop");
            }
            for (Relationship r : existing) {
                if (r.isTombstoned()) continue;
                if (r.kind() != Relationship.Kind.PARENT_CHILD) continue;
                // r.fromMemberId = parent (closer to root), r.toMemberId = child (descendant)
                if (Objects.equals(r.fromMemberId(), cur)) {
                    stack.push(r.toMemberId());
                }
            }
        }
    }

    /**
     * Computes generation numbers by BFS from {@code root}. PARENT_CHILD
     * edges go from ancestor (lower generation) to descendant (higher).
     * SPOUSE edges preserve the partner's generation. ADOPTION edges
     * do not change generation.
     */
    public static Map<UUID, Integer> generations(Collection<Relationship> rels, UUID root) {
        Map<UUID, Integer> gen = new LinkedHashMap<>();
        Deque<UUID> queue = new ArrayDeque<>();
        gen.put(root, 0);
        queue.add(root);
        while (!queue.isEmpty()) {
            UUID cur = queue.poll();
            int depth = gen.get(cur);
            for (Relationship r : rels) {
                if (r.isTombstoned()) continue;
                switch (r.kind()) {
                    case PARENT_CHILD -> {
                        if (Objects.equals(r.fromMemberId(), cur)) {
                            UUID child = r.toMemberId();
                            if (!gen.containsKey(child)) {
                                gen.put(child, depth + 1);
                                queue.add(child);
                            }
                        }
                    }
                    case SPOUSE -> {
                        if (Objects.equals(r.fromMemberId(), cur) || Objects.equals(r.toMemberId(), cur)) {
                            UUID partner = Objects.equals(r.fromMemberId(), cur) ? r.toMemberId() : r.fromMemberId();
                            if (!gen.containsKey(partner)) {
                                gen.put(partner, depth);
                                queue.add(partner);
                            }
                        }
                    }
                    case ADOPTION -> {
                        // no-op for generation
                    }
                }
            }
        }
        return gen;
    }

    /**
     * Returns the transitive ancestors of {@code member}.
     */
    public static Set<UUID> ancestors(Collection<Relationship> rels, UUID member) {
        Set<UUID> result = new LinkedHashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();
        stack.push(member);
        while (!stack.isEmpty()) {
            UUID cur = stack.pop();
            for (Relationship r : rels) {
                if (r.isTombstoned()) continue;
                if (r.kind() == Relationship.Kind.PARENT_CHILD
                        && Objects.equals(r.toMemberId(), cur)) {
                    UUID parent = r.fromMemberId();
                    if (result.add(parent)) {
                        stack.push(parent);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns the spouses of {@code member} (SPOUSE edges are stored
     * once; both directions resolve to the partner).
     */
    public static Set<UUID> spouses(Collection<Relationship> rels, UUID member) {
        Set<UUID> result = new LinkedHashSet<>();
        for (Relationship r : rels) {
            if (r.isTombstoned()) continue;
            if (r.kind() != Relationship.Kind.SPOUSE) continue;
            if (Objects.equals(r.fromMemberId(), member)) result.add(r.toMemberId());
            else if (Objects.equals(r.toMemberId(), member)) result.add(r.fromMemberId());
        }
        return result;
    }

    /**
     * Returns the adoption pairs for {@code member} (as child or adopter).
     */
    public static Set<UUID> adoptions(Collection<Relationship> rels, UUID member) {
        Set<UUID> result = new LinkedHashSet<>();
        for (Relationship r : rels) {
            if (r.isTombstoned()) continue;
            if (r.kind() != Relationship.Kind.ADOPTION) continue;
            if (Objects.equals(r.fromMemberId(), member)) result.add(r.toMemberId());
            else if (Objects.equals(r.toMemberId(), member)) result.add(r.fromMemberId());
        }
        return result;
    }
}