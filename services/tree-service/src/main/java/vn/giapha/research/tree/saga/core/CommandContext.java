package vn.giapha.research.tree.saga.core;

import java.util.UUID;

public record CommandContext(UUID idempotencyKey, String actorUserKey, Long treeKey,
                             String correlationId) {}
