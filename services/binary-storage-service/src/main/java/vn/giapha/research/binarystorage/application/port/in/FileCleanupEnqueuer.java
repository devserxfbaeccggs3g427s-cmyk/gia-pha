package vn.giapha.research.binarystorage.application.port.in;

import java.util.List;

public interface FileCleanupEnqueuer {

    void enqueueForTreeDeletion(long treeKey);

    void enqueueForMedia(long mediaKey);

    List<EnqueuedCleanup> recent(int limit);

    record EnqueuedCleanup(long fileCleanupJobKey, String objectPath, String reason) {
    }
}
