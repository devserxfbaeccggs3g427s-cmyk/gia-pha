package vn.giapha.research.members.adapter.in.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import vn.giapha.research.members.support.MemberSupport.ApiSuccess;
import vn.giapha.research.members.support.MemberSupport.Principal;
import vn.giapha.research.members.support.MemberSupport.UnauthorizedException;
import vn.giapha.research.members.application.service.MemberService;
import vn.giapha.research.members.application.service.MemberService.NewMemberInput;
import vn.giapha.research.members.domain.Gender;
import vn.giapha.research.members.domain.Member;

/**
 * Member CRUD controller (Task 23). Mirrors the legacy
 * {@code /api/trees/{treeId}/members} contract; partial updates accept
 * {@code If-Match} for optimistic concurrency.
 */
@RestController
@RequestMapping(path = "/api/trees/{treeExternalId}/members",
        produces = "application/json")
public class MemberController {

    private final MemberService members;

    public MemberController(MemberService members) {
        this.members = members;
    }

    @PostMapping(consumes = "application/json")
    ResponseEntity<ApiSuccess<Map<String, Object>>> create(
            @PathVariable String treeExternalId,
            @Valid @RequestBody MemberRequest body, Authentication auth) {
        Member created = members.create(principal(auth), treeExternalId,
                toInput(body), Instant.now());
        return ResponseEntity.status(201).body(ApiSuccess.ok(toMap(created)));
    }

    @GetMapping
    ApiSuccess<List<Map<String, Object>>> list(@PathVariable String treeExternalId,
            Authentication auth) {
        List<Map<String, Object>> rows = members.list(principal(auth), treeExternalId).stream()
                .map(this::toMap).toList();
        return ApiSuccess.ok(rows);
    }

    @GetMapping("/{memberExternalId}")
    ApiSuccess<Map<String, Object>> get(@PathVariable String treeExternalId,
            @PathVariable String memberExternalId, Authentication auth) {
        return ApiSuccess.ok(toMap(members.detail(principal(auth), treeExternalId,
                memberExternalId)));
    }

    @PatchMapping(path = "/{memberExternalId}", consumes = "application/json")
    ApiSuccess<Map<String, Object>> update(@PathVariable String treeExternalId,
            @PathVariable String memberExternalId,
            @RequestHeader("If-Match") long ifMatch,
            @Valid @RequestBody MemberRequest body, Authentication auth) {
        Member updated = members.update(principal(auth), treeExternalId, memberExternalId,
                ifMatch, toInput(body), Instant.now());
        return ApiSuccess.ok(toMap(updated));
    }

    @DeleteMapping("/{memberExternalId}")
    ResponseEntity<ApiSuccess<Void>> delete(@PathVariable String treeExternalId,
            @PathVariable String memberExternalId, Authentication auth) {
        members.delete(principal(auth), treeExternalId, memberExternalId, Instant.now());
        return ResponseEntity.noContent().build();
    }

    private static NewMemberInput toInput(MemberRequest body) {
        return new NewMemberInput(body.firstName(), body.lastName(), body.nickname(),
                body.gender(), body.dateOfBirth(), body.dateOfDeath(),
                body.placeOfBirth(), body.currentAddress(), body.phone(), body.email(),
                body.occupation(), body.education(), body.biography(), body.achievements(),
                body.notes(), body.legacyAvatarUrl(), body.generation(), body.alive());
    }

    private Map<String, Object> toMap(Member member) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", member.externalId());
        map.put("firstName", member.firstName());
        map.put("lastName", member.lastName());
        map.put("fullName", member.fullName());
        map.put("nickname", member.nickname());
        map.put("gender", member.gender() == null ? null : member.gender().name());
        map.put("dateOfBirth", member.dateOfBirth() == null ? null
                : member.dateOfBirth().toString());
        map.put("dateOfDeath", member.dateOfDeath() == null ? null
                : member.dateOfDeath().toString());
        map.put("placeOfBirth", member.placeOfBirth());
        map.put("currentAddress", member.currentAddress());
        map.put("phone", member.phone());
        map.put("email", member.email());
        map.put("occupation", member.occupation());
        map.put("education", member.education());
        map.put("biography", member.biography());
        map.put("achievements", member.achievements());
        map.put("notes", member.notes());
        map.put("legacyAvatarUrl", member.legacyAvatarUrl());
        map.put("generation", member.generation());
        map.put("alive", member.alive());
        map.put("version", member.version());
        return map;
    }

    private static Principal principal(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authentication required");
        }
        return new Principal(auth.getName(),
                auth.getName() + "@giapha.local", "Authenticated user");
    }

    public record MemberRequest(
            @NotBlank String firstName,
            String lastName,
            String nickname,
            Gender gender,
            LocalDate dateOfBirth,
            LocalDate dateOfDeath,
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
