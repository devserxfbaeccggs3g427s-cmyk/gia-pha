package com.familya.member.adapter.in.rest;

import com.familya.member.application.port.in.LoadMemberCommand;
import com.familya.member.application.usecase.LoadMemberUseCase;
import com.familya.platform.api.AsyncOperation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api/v2/internal/member", produces = MediaType.APPLICATION_JSON_VALUE)
public class MigrationController {

    private final LoadMemberUseCase loader;

    public MigrationController(LoadMemberUseCase loader) {
        this.loader = loader;
    }

    @PostMapping("/migration/members")
    public ResponseEntity<AsyncOperation> load(@RequestHeader("X-Correlation-Id") String correlationId,
                                               @Valid @RequestBody LoadMemberRequest req) {
        LoadMemberUseCase.LoadResult r = loader.execute(new LoadMemberCommand(
                req.memberId(), req.treeId(), req.userId(),
                req.displayName(), req.givenName(), req.surname(),
                req.birthDate(), req.deathDate(),
                Boolean.TRUE.equals(req.birthYearKnown()), Boolean.TRUE.equals(req.deathYearKnown()),
                req.gender(), req.status() == null ? "LIVING" : req.status(),
                req.generation(), req.legacyAvatarUrl(), req.notes(),
                req.createdAt() == null ? Instant.now() : req.createdAt(),
                req.updatedAt() == null ? Instant.now() : req.updatedAt(),
                Boolean.TRUE.equals(req.tombstoned()),
                req.replaySafe()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(AsyncOperation.accepted(r.memberId(), "/api/v2/operations/" + r.memberId()));
    }

    public record LoadMemberRequest(
            @NotNull UUID memberId,
            @NotNull UUID treeId,
            UUID userId,
            @NotBlank String displayName,
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
            String notes,
            Instant createdAt,
            Instant updatedAt,
            Boolean tombstoned,
            boolean replaySafe) { }
}