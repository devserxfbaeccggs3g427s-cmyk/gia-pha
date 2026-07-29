package com.familya.media.application.port.in;

import java.util.UUID;

public record CreateAlbumCommand(
        UUID treeId,
        UUID actingUser,
        long expectedTreeRevision,
        String name,
        String description,
        UUID coverMediaId) {
}
