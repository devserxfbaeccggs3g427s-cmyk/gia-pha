package com.familya.member.application.port.in;

import java.util.UUID;

/**
 * Initiating input to the delete-member Saga. The Member service owns this
 * Saga (ADR-003); authorization is captured here and carried in the envelope
 * so participants do not impersonate the user.
 */
public record InitiateDeleteMemberCommand(
        UUID treeId,
        UUID memberId,
        UUID actingUser,
        long expectedMemberVersion,
        long expectedTreeRevision,
        long expectedTreeEpoch) {
}