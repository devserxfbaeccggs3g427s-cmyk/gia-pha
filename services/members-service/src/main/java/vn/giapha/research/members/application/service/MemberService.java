package vn.giapha.research.members.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.giapha.research.members.application.port.out.FileCleanupPort;
import vn.giapha.research.members.application.port.out.OutboxPort;
import vn.giapha.research.members.support.MemberSupport.ConflictException;
import vn.giapha.research.members.support.MemberSupport.NotFoundException;
import vn.giapha.research.members.support.MemberSupport.Principal;
import vn.giapha.research.members.support.MemberSupport.ValidationException;
import vn.giapha.research.members.application.port.out.FamilyTreeRepository;
import vn.giapha.research.members.application.port.out.MemberRepository;
import vn.giapha.research.members.application.service.TreeAuthorizationService.Action;
import vn.giapha.research.members.domain.FamilyTree;
import vn.giapha.research.members.domain.Member;

/**
 * Member lifecycle service (Task 23, Req 4). Mirrors the legacy
 * {@code data/members.json} semantics:
 *
 * <ul>
 *   <li>{@code dateOfDeath} cannot precede {@code dateOfBirth}; setting
 *       {@code dateOfDeath} forces {@code alive = false};</li>
 *   <li>avatar must reference an image media of the same tree (enforced by
 *       FK at the database layer); the legacy {@code avatarUrl} is kept as a
 *       read-only fallback when no {@code avatarMediaId} is set;</li>
 *   <li>member deletion cascades through memberships, event links and media
 *       links; binary deletion is enqueued after the relational commit.</li>
 * </ul>
 */
@Service
public class MemberService {

    private final FamilyTreeRepository trees;
    private final MemberRepository members;
    private final TreeAuthorizationService authorization;
    private final FileCleanupPort cleanupEnqueuer;
    private final OutboxPort outbox;

    public MemberService(FamilyTreeRepository trees, MemberRepository members,
            TreeAuthorizationService authorization, FileCleanupPort cleanupEnqueuer,
            OutboxPort outbox) {
        this.trees = trees;
        this.members = members;
        this.authorization = authorization;
        this.cleanupEnqueuer = cleanupEnqueuer;
        this.outbox = outbox;
    }

    @Transactional
    public Member create(Principal principal, String treeExternalId,
            NewMemberInput input, Instant now) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.WRITE, true);
        if (input.firstName() == null || input.firstName().isBlank()) {
            throw new ValidationException("firstName is required");
        }
        validateDates(input.dateOfBirth(), input.dateOfDeath(), input.alive());
        Member member = new Member(0, tree.treeKey(),
                vn.giapha.research.members.support.MemberSupport.newId(),
                input.firstName().trim(),
                input.lastName() == null ? null : input.lastName().trim(),
                joinFullName(input.firstName(), input.lastName()),
                input.nickname(),
                input.gender(),
                input.dateOfBirth(), input.dateOfDeath(),
                input.placeOfBirth(), input.currentAddress(),
                input.phone(), input.email(), input.occupation(), input.education(),
                input.biography(), input.achievements(), input.notes(),
                input.legacyAvatarUrl(),
                input.generation(),
                computeAlive(input.dateOfDeath(), input.alive()),
                vn.giapha.research.members.support.MemberSupport.normalize(
                        joinFullName(input.firstName(), input.lastName())),
                vn.giapha.research.members.support.MemberSupport.normalize(input.nickname()),
                1L, now, now);
        Member inserted = members.insert(member);
        enqueueTreeMutation(tree, "MEMBER_CREATED", inserted.externalId(), principal, now);
        return inserted;
    }

    @Transactional
    public Member update(Principal principal, String treeExternalId, String memberExternalId,
            long expectedVersion, NewMemberInput input, Instant now) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.WRITE, true);
        Member existing = members.findByExternalId(tree.treeKey(), memberExternalId)
                .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND",
                        "Member does not exist"));
        if (existing.version() != expectedVersion) {
            throw new ConflictException("VERSION_CONFLICT",
                    "Member version is stale; refresh and retry");
        }
        validateDates(input.dateOfBirth(), input.dateOfDeath(), input.alive());
        Member updated = new Member(existing.memberKey(), existing.treeKey(),
                existing.externalId(),
                input.firstName().trim(),
                input.lastName() == null ? null : input.lastName().trim(),
                joinFullName(input.firstName(), input.lastName()),
                input.nickname(),
                input.gender(),
                input.dateOfBirth(), input.dateOfDeath(),
                input.placeOfBirth(), input.currentAddress(),
                input.phone(), input.email(), input.occupation(), input.education(),
                input.biography(), input.achievements(), input.notes(),
                input.legacyAvatarUrl(),
                input.generation(),
                computeAlive(input.dateOfDeath(), input.alive()),
                vn.giapha.research.members.support.MemberSupport.normalize(
                        joinFullName(input.firstName(), input.lastName())),
                vn.giapha.research.members.support.MemberSupport.normalize(input.nickname()),
                existing.version() + 1, existing.createdAt(), now);
        Member saved = members.update(updated);
        enqueueTreeMutation(tree, "MEMBER_UPDATED", saved.externalId(), principal, now);
        return saved;
    }

    @Transactional
    public void delete(Principal principal, String treeExternalId, String memberExternalId,
            Instant now) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.DELETE, true);
        Member member = members.findByExternalId(tree.treeKey(), memberExternalId)
                .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND",
                        "Member does not exist"));
        // Binary cleanup is enqueued first so the relational cascade and the
        // eventual blob delete commit before any worker drains the row.
        cleanupEnqueuer.enqueueForMedia(mediaKeyFor(member));
        members.delete(tree.treeKey(), member.memberKey());
        enqueueTreeMutation(tree, "MEMBER_DELETED", member.externalId(), principal, now);
    }

    @Transactional(readOnly = true)
    public Member detail(Principal principal, String treeExternalId,
            String memberExternalId) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.READ, true);
        return members.findByExternalId(tree.treeKey(), memberExternalId)
                .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND",
                        "Member does not exist"));
    }

    @Transactional(readOnly = true)
    public Optional<Member> preview(Principal principal, String treeExternalId,
            String memberExternalId) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.READ, true);
        return members.findByExternalId(tree.treeKey(), memberExternalId);
    }

    @Transactional(readOnly = true)
    public List<Member> list(Principal principal, String treeExternalId) {
        FamilyTree tree = requireTree(treeExternalId);
        authorization.authorize(tree.treeKey(), principal, Action.READ, true);
        return members.listAll(tree.treeKey());
    }

    private FamilyTree requireTree(String externalId) {
        return trees.findByExternalId(externalId)
                .orElseThrow(() -> new NotFoundException("TREE_NOT_FOUND",
                        "Tree does not exist"));
    }

    private void enqueueTreeMutation(FamilyTree tree, String eventType, String externalId,
            Principal actor, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("treeExternalId", tree.externalId());
        payload.put("memberExternalId", externalId);
        payload.put("actor", actor == null ? "system" : actor.userId());
        outbox.append("MEMBER", externalId, tree.treeKey(), eventType);
    }

    /** Placeholder avatar resolution — wired by the media service in Task 26. */
    private long mediaKeyFor(Member member) {
        return 0L;
    }

    private static void validateDates(java.time.LocalDate dob, java.time.LocalDate dod,
            boolean alive) {
        if (dob != null && dod != null && dod.isBefore(dob)) {
            throw new ValidationException("dateOfDeath must not be before dateOfBirth");
        }
        if (alive && dod != null) {
            throw new ValidationException("a living member cannot have a dateOfDeath");
        }
    }

    private static boolean computeAlive(java.time.LocalDate dod, boolean requested) {
        return dod == null && requested;
    }

    private static String joinFullName(String first, String last) {
        if (first == null && last == null) {
            return null;
        }
        if (last == null || last.isBlank()) {
            return first.trim();
        }
        if (first == null || first.isBlank()) {
            return last.trim();
        }
        return (first + " " + last).trim();
    }

    public record NewMemberInput(
            String firstName,
            String lastName,
            String nickname,
            vn.giapha.research.members.domain.Gender gender,
            java.time.LocalDate dateOfBirth,
            java.time.LocalDate dateOfDeath,
            String placeOfBirth,
            String currentAddress,
            String phone,
            String email,
            String occupation,
            String education,
            String biography,
            String achievements,
            String notes,
            String legacyAvatarUrl,
            Integer generation,
            boolean alive) {}
}
