package com.familya.member.application.port.in;

import com.familya.member.domain.model.Member;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateMemberCommand(
        UUID memberId,
        UUID actingUser,
        long expectedVersion,
        long expectedTreeRevision,
        String displayName,
        String givenName,
        String surname,
        LocalDate birthDate,
        LocalDate deathDate,
        Member.Gender gender,
        Integer generation,
        String notes
) { }