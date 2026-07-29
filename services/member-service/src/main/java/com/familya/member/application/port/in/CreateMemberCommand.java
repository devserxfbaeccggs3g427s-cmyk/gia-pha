package com.familya.member.application.port.in;

import com.familya.member.domain.model.Member;

import java.time.LocalDate;
import java.util.UUID;

public record CreateMemberCommand(
        UUID treeId,
        UUID actingUser,
        UUID userId,
        String displayName,
        String givenName,
        String surname,
        LocalDate birthDate,
        LocalDate deathDate,
        boolean birthYearKnown,
        boolean deathYearKnown,
        Member.Gender gender,
        Member.Status status,
        Integer generation,
        String legacyAvatarUrl,
        String notes,
        long expectedTreeRevision
) { }