package com.familya.media.application.port.out;

import java.util.UUID;

/**
 * Member projection availability. Used by
 * {@code AssociateMediaUseCase} for {@code AVATAR} targets: an avatar
 * must point at a member that is alive in the projection. Returns
 * false when missing.
 */
public interface ReferenceAvailability {

    boolean isMemberAvailable(UUID treeId, UUID memberId);

    boolean isEventAvailable(UUID treeId, UUID eventId);

    boolean isAlbumAvailable(UUID treeId, UUID albumId);
}
