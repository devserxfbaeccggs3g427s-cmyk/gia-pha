package com.familya.sharing.application.port.out;

import java.util.*;

public interface ProjectionSource {
    Map<String, Object> rebuild(UUID treeId, com.familya.sharing.domain.model.ShareLink.Scope scope, UUID targetId, long watermark);
}
