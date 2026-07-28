package com.familya.member.adapter.in.rest;

import com.familya.member.application.port.in.CreateMemberCommand;
import com.familya.member.application.port.in.MergeMembersCommand;
import com.familya.member.application.port.in.TombstoneMemberCommand;
import com.familya.member.application.port.in.UpdateMemberCommand;
import com.familya.member.application.usecase.CreateMemberUseCase;
import com.familya.member.application.usecase.MergeMembersUseCase;
import com.familya.member.application.usecase.TombstoneMemberUseCase;
import com.familya.member.application.usecase.UpdateMemberUseCase;
import com.familya.member.domain.model.Member;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/trees/{treeId}/members", produces = MediaType.APPLICATION_JSON_VALUE)
public class MemberController {

    private final CreateMemberUseCase createMember;
    private final UpdateMemberUseCase updateMember;
    private final TombstoneMemberUseCase tombstoneMember;
    private final MergeMembersUseCase mergeMembers;

    public MemberController(CreateMemberUseCase createMember, UpdateMemberUseCase updateMember,
                            TombstoneMemberUseCase tombstoneMember, MergeMembersUseCase mergeMembers) {
        this.createMember = createMember;
        this.updateMember = updateMember;
        this.tombstoneMember = tombstoneMember;
        this.mergeMembers = mergeMembers;
    }

    @PostMapping
    public ResponseEntity<AsyncOperation> create(@RequestHeader("X-Acting-User") UUID actingUser,
                                                 @PathVariable UUID treeId,
                                                 @RequestHeader(value = "If-Match", required = false) Long expectedTreeRevision,
                                                 @Valid @RequestBody CreateMemberRequest req) {
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        UUID id = createMember.execute(new CreateMemberCommand(
                treeId, actingUser, req.userId(),
                req.displayName(), req.givenName(), req.surname(),
                req.birthDate(), req.deathDate(),
                Boolean.TRUE.equals(req.birthYearKnown()), Boolean.TRUE.equals(req.deathYearKnown()),
                req.gender() == null ? null : Member.Gender.valueOf(req.gender()),
                req.status() == null ? Member.Status.LIVING : Member.Status.valueOf(req.status()),
                req.generation(), req.legacyAvatarUrl(), req.notes(), er));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v2/trees/" + treeId + "/members/" + id)
                .body(AsyncOperation.accepted(id, "/api/v2/operations/" + id));
    }

    @PutMapping("/{memberId}")
    public ResponseEntity<AsyncOperation> update(@RequestHeader("X-Acting-User") UUID actingUser,
                                                  @PathVariable UUID treeId,
                                                  @PathVariable UUID memberId,
                                                  @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                  @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                  @Valid @RequestBody UpdateMemberRequest req) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        updateMember.execute(new UpdateMemberCommand(memberId, actingUser, ev, er,
                req.displayName(), req.givenName(), req.surname(),
                req.birthDate(), req.deathDate(),
                req.gender() == null ? null : Member.Gender.valueOf(req.gender()),
                req.generation(), req.notes()));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(memberId, "/api/v2/operations/" + memberId));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<AsyncOperation> tombstone(@RequestHeader("X-Acting-User") UUID actingUser,
                                                     @PathVariable UUID treeId,
                                                     @PathVariable UUID memberId,
                                                     @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                     @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        tombstoneMember.execute(new TombstoneMemberCommand(memberId, actingUser, ev, er));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(memberId, "/api/v2/operations/" + memberId));
    }

    @PostMapping("/{memberId}/merge")
    public ResponseEntity<AsyncOperation> merge(@RequestHeader("X-Acting-User") UUID actingUser,
                                                 @PathVariable UUID treeId,
                                                 @PathVariable UUID memberId,
                                                 @RequestHeader(value = "If-Match", required = false) Long expectedVersion,
                                                 @RequestHeader(value = "X-Tree-Revision", required = false) Long expectedTreeRevision,
                                                 @RequestBody MergeRequest req) {
        long ev = expectedVersion == null ? 0L : expectedVersion;
        long er = expectedTreeRevision == null ? 0L : expectedTreeRevision;
        mergeMembers.execute(new MergeMembersCommand(memberId, req.sourceMemberId(), actingUser, ev, er));
        return ResponseEntity.accepted().body(AsyncOperation.accepted(memberId, "/api/v2/operations/" + memberId));
    }

    public record CreateMemberRequest(
            @NotBlank String displayName,
            UUID userId,
            String givenName,
            String surname,
            LocalDate birthDate,
            LocalDate deathDate,
            Boolean birthYearKnown,
            Boolean deathYearKnown,
            String gender,
            String status,
            Integer generation,
            String legacyAvatarUrl,
            String notes) { }

    public record UpdateMemberRequest(
            String displayName,
            String givenName,
            String surname,
            LocalDate birthDate,
            LocalDate deathDate,
            String gender,
            Integer generation,
            String notes) { }

    public record MergeRequest(@NotNull UUID sourceMemberId) { }
}