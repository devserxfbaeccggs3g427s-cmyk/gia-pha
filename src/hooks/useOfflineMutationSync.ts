'use client';

import { useCallback, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { executePendingMutation, MutationApiError } from '@/lib/api/mutations';
import { queryKeys } from '@/lib/query/keys';
import { useOfflineStore, type PendingMutation } from '@/store/offline-store';

export function useOfflineMutationSync(): { retryPendingMutations: () => Promise<void> } {
  const queryClient = useQueryClient();

  const retryPendingMutations = useCallback(async () => {
    const state = useOfflineStore.getState();
    if (state.isSyncing || (typeof navigator !== 'undefined' && !navigator.onLine)) return;
    state.setSyncing(true);
    try {
      const queue = [...useOfflineStore.getState().pendingMutations]
        .filter((item) => item.status !== 'blocked')
        .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
      for (const mutation of queue) {
        useOfflineStore.getState().markRetrying(mutation.id);
        try {
          await executePendingMutation(mutation);
          useOfflineStore.getState().removePendingMutation(mutation.id);
          invalidateMutation(queryClient, mutation);
        } catch (error) {
          const code = error instanceof MutationApiError ? error.code : 'UNKNOWN';
          const message = error instanceof Error ? error.message : String(error);
          const blocked = code === 'STORAGE_FULL' || code === 'VALIDATION_ERROR' || code === 'FORBIDDEN';
          useOfflineStore.getState().markFailed(mutation.id, code, message, blocked);
          if (code === 'NETWORK' || code === 'RATE_LIMIT' || code === 'STORAGE_FULL') break;
        }
      }
    } finally {
      useOfflineStore.getState().setSyncing(false);
    }
  }, [queryClient]);

  useEffect(() => {
    const handleOnline = () => void retryPendingMutations();
    window.addEventListener('online', handleOnline);
    if (navigator.onLine && useOfflineStore.getState().pendingMutations.length > 0) handleOnline();
    return () => window.removeEventListener('online', handleOnline);
  }, [retryPendingMutations]);

  return { retryPendingMutations };
}

function invalidateMutation(queryClient: ReturnType<typeof useQueryClient>, mutation: PendingMutation): void {
  if (mutation.entity === 'member') {
    void queryClient.invalidateQueries({ queryKey: queryKeys.members(mutation.treeId) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.tree(mutation.treeId) });
  } else if (mutation.entity === 'relationship') {
    void queryClient.invalidateQueries({ queryKey: queryKeys.relationships(mutation.treeId) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.tree(mutation.treeId) });
  } else {
    void queryClient.invalidateQueries({ queryKey: queryKeys.events(mutation.treeId) });
    void queryClient.invalidateQueries({ queryKey: ['events', mutation.treeId, 'upcoming'] });
  }
}
