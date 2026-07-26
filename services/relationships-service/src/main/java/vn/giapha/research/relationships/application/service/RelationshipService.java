package vn.giapha.research.relationships.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.relationships.application.port.out.OutboxPort;
import vn.giapha.research.relationships.support.RelationshipSupport;
import vn.giapha.research.relationships.support.RelationshipSupport.ConflictException;
import vn.giapha.research.relationships.support.RelationshipSupport.NotFoundException;
import vn.giapha.research.relationships.support.RelationshipSupport.Principal;
import vn.giapha.research.relationships.support.RelationshipSupport.ValidationException;
import vn.giapha.research.relationships.application.port.out.FamilyTreeRepository;
import vn.giapha.research.relationships.application.port.out.MemberRepository;
import vn.giapha.research.relationships.application.port.out.RelationshipRepository;
import vn.giapha.research.relationships.application.service.TreeAuthorizationService.Action;
import vn.giapha.research.relationships.domain.FamilyTree;
import vn.giapha.research.relationships.domain.MarriageStatus;
import vn.giapha.research.relationships.domain.Member;
import vn.giapha.research.relationships.domain.RelationType;
import vn.giapha.research.relationships.domain.Relationship;

/**
 * Relationship and genealogy algorithms (Task 24, Req 5).
 *
 * <p>Every graph mutation acquires the tree row lock via
 * {@link FamilyTreeRepository#lockForUpdate(long)} before reading edges so
 * concurrent inserts cannot jointly form a cycle. Symmetric types
 * ({@code SPOUSE}, {@code SIBLING}) use canonical endpoint ordering so the
 * database unique constraint blocks logical reverse duplicates.
 *
 * <p>Read operations are pure — no lazy migration writes (Task 24.5).
 */
@Service
public class RelationshipService {

    private final FamilyTreeRepository trees;
    private final MemberRepository members;
    private final RelationshipRepository relationships;
    private final TreeAuthorizationService authorization;
    private final OutboxPort outbox;

    public RelationshipService(FamilyTreeRepository trees, MemberRepository members,
            RelationshipRepository relationships, TreeAuthorizationService authorization,
            OutboxPort outbox) {
        this.trees = trees;
        this.members = members;
        this.relationships = relationships;
        this.authorization = authorization;
        this.outbox = outbox;
    }

    @Transactional
    public Relationship create(Principal principal, String treeExternalId,
            String sourceExternalId, String targetExternalId, RelationType type,
            String customType, LocalDate marriageDate, LocalDate divorceDate,
            MarriageStatus status, Instant now) {
        if (type == null) {
            throw new ValidationException("type is required");
        }
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.WRITE, true);
        trees.lockForUpdate(tree.treeKey());
        Member source = requireMember(tree.treeKey(), sourceExternalId);
        Member target = requireMember(tree.treeKey(), targetExternalId);
        validateDates(marriageDate, divorceDate, status);
        long[] ordered = canonicalize(type, source.memberKey(), target.memberKey());
        ensureNoCycle(tree.treeKey(), ordered[0], ordered[1], type, -1);
        Relationship relationship = new Relationship(0, tree.treeKey(), RelationshipSupport.newId(),
                ordered[0], ordered[1], type,
                type == RelationType.CUSTOM ? customType : null,
                marriageDate, divorceDate, status, 1L, now, now);
        Relationship saved = relationships.insert(relationship);
        enqueueTreeMutation(tree, "RELATIONSHIP_CREATED", saved.externalId(), principal, now);
        return saved;
    }

    @Transactional
    public void delete(Principal principal, String treeExternalId, String externalId,
            Instant now) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.WRITE, true);
        trees.lockForUpdate(tree.treeKey());
        Relationship existing = relationships.findByExternalId(tree.treeKey(), externalId)
                .orElseThrow(() -> new NotFoundException("RELATIONSHIP_NOT_FOUND",
                        "Relationship does not exist"));
        relationships.delete(tree.treeKey(), existing.relationshipKey());
        enqueueTreeMutation(tree, "RELATIONSHIP_DELETED", externalId, principal, now);
    }

    @Transactional(readOnly = true)
    public List<Relationship> list(Principal principal, String treeExternalId) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.READ, true);
        return relationships.listAll(tree.treeKey());
    }

    @Transactional(readOnly = true)
    public GenealogyView perspective(Principal principal, String treeExternalId) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.READ, true);
        List<Relationship> edges = relationships.listAll(tree.treeKey());
        Map<Long, Integer> generation = generationNumbers(edges);
        return new GenealogyView(edges, generation);
    }

    private FamilyTree requireTree(String externalId) {
        return trees.findByExternalId(externalId)
                .orElseThrow(() -> new NotFoundException("TREE_NOT_FOUND",
                        "Tree does not exist"));
    }

    private Member requireMember(long treeKey, String externalId) {
        return members.findByExternalId(treeKey, externalId)
                .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND",
                        "Member does not exist"));
    }

    private static void validateDates(LocalDate marriageDate, LocalDate divorceDate,
            MarriageStatus status) {
        if (marriageDate != null && divorceDate != null
                && divorceDate.isBefore(marriageDate)) {
            throw new ValidationException("Divorce date must not precede marriage date");
        }
        if (status == MarriageStatus.DIVORCED && divorceDate == null) {
            throw new ValidationException("Divorce status requires a divorce date");
        }
    }

    /**
     * Canonical pair ordering for symmetric relationship types. For
     * {@code SPOUSE} and {@code SIBLING} the smaller memberKey comes first;
     * for {@code PARENT_CHILD} the parent must come first.
     */
    static long[] canonicalize(RelationType type, long source, long target) {
        if (source == target) {
            throw new ValidationException("Relationship endpoints must differ");
        }
        return switch (type) {
            case PARENT_CHILD -> new long[] { source, target };
            case SPOUSE, SIBLING, ADOPTED, CUSTOM -> source < target
                    ? new long[] { source, target }
                    : new long[] { target, source };
        };
    }

    /**
     * Run a BFS from {@code newParent} to {@code newChild}; if {@code newChild}
     * is already an ancestor of {@code newParent} the proposed edge would
     * close a cycle. {@code ignoreEdgeKey} lets deletes and updates skip
     * themselves during the check.
     */
    void ensureNoCycle(long treeKey, long source, long target, RelationType type,
            long ignoreEdgeKey) {
        if (type != RelationType.PARENT_CHILD) {
            // Spouse/sibling/custom edges cannot form PARENT_CHILD cycles.
            return;
        }
        if (ancestorReachable(treeKey, target, source, ignoreEdgeKey)) {
            throw new ConflictException("CYCLE_DETECTED",
                    "The proposed edge would create a parent-child cycle");
        }
    }

    private boolean ancestorReachable(long treeKey, long from, long toFind, long ignoreEdgeKey) {
        Deque<Long> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        queue.add(from);
        while (!queue.isEmpty()) {
            long current = queue.poll();
            if (current == toFind) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            for (Relationship edge : relationships.listAll(treeKey)) {
                if (edge.relationshipKey() == ignoreEdgeKey) {
                    continue;
                }
                if (edge.type() != RelationType.PARENT_CHILD) {
                    continue;
                }
                // current -> parent means current's parent is edge.sourceMemberKey;
                // parent -> current means current's parent is edge.targetMemberKey.
                if (edge.sourceMemberKey() == current && !visited.contains(edge.targetMemberKey())) {
                    queue.add(edge.targetMemberKey());
                } else if (edge.targetMemberKey() == current
                        && !visited.contains(edge.sourceMemberKey())) {
                    queue.add(edge.sourceMemberKey());
                }
            }
        }
        return false;
    }

    /** Generation numbers from a stable root (BFS from the smallest memberKey). */
    static Map<Long, Integer> generationNumbers(List<Relationship> edges) {
        Map<Long, Set<Long>> parents = new HashMap<>();
        Map<Long, Set<Long>> children = new HashMap<>();
        Set<Long> members = new LinkedHashSet<>();
        for (Relationship edge : edges) {
            members.add(edge.sourceMemberKey());
            members.add(edge.targetMemberKey());
            if (edge.type() == RelationType.PARENT_CHILD) {
                children.computeIfAbsent(edge.sourceMemberKey(), key -> new LinkedHashSet<>())
                        .add(edge.targetMemberKey());
                parents.computeIfAbsent(edge.targetMemberKey(), key -> new LinkedHashSet<>())
                        .add(edge.sourceMemberKey());
            }
        }
        if (members.isEmpty()) {
            return Map.of();
        }
        long root = members.stream().min(Comparator.naturalOrder()).orElseThrow();
        Map<Long, Integer> generation = new HashMap<>();
        Deque<long[]> queue = new ArrayDeque<>();
        queue.add(new long[] { root, 0 });
        generation.put(root, 0);
        while (!queue.isEmpty()) {
            long[] pair = queue.poll();
            long current = pair[0];
            int depth = (int) pair[1];
            for (long child : children.getOrDefault(current, Set.of())) {
                if (!generation.containsKey(child)) {
                    generation.put(child, depth + 1);
                    queue.add(new long[] { child, depth + 1 });
                }
            }
            for (long parent : parents.getOrDefault(current, Set.of())) {
                if (!generation.containsKey(parent)) {
                    generation.put(parent, depth - 1);
                    queue.add(new long[] { parent, depth - 1 });
                }
            }
        }
        return generation;
    }

    private void enqueueTreeMutation(FamilyTree tree, String eventType, String externalId,
            Principal actor, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeExternalId", tree.externalId());
        payload.put("relationshipExternalId", externalId);
        payload.put("actor", actor == null ? "system" : actor.userId());
        outbox.append("RELATIONSHIP", externalId, tree.treeKey(), eventType);
    }

    public record GenealogyView(List<Relationship> edges, Map<Long, Integer> generations) {}
}
