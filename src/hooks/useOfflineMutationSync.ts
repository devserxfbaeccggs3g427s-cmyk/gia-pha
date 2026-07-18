'use client';

import { useCallback, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { executePendingMutation, MutationApiError } from '@/lib/api/mutations';
import { queryKeys } from '@/lib/query/keys';
import { useOfflineStore, type PendingMutation } from '@/store/offline-store';

export function useOfflineMutationSync(): { retryPendingMutations: () => Promise<void> } {
  const queryClient = useQueryClient();
  const pendingCount = useOfflineStore((state) => state.pendingMutations.length);

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
    const handleServiceWorkerMessage = (event: MessageEvent<{ type?: string }>) => {
      if (event.data?.type === 'KINSHIP_SYNC_REQUEST') void retryPendingMutations();
    };
    window.addEventListener('online', handleOnline);
    navigator.serviceWorker?.addEventListener('message', handleServiceWorkerMessage);
    if (navigator.onLine && useOfflineStore.getState().pendingMutations.length > 0) handleOnline();
    if (!navigator.onLine && useOfflineStore.getState().pendingMutations.length > 0) requestBackgroundSync();
    return () => {
      window.removeEventListener('online', handleOnline);
      navigator.serviceWorker?.removeEventListener('message', handleServiceWorkerMessage);
    };
  }, [retryPendingMutations]);

  useEffect(() => {
    if (pendingCount > 0 && typeof navigator !== 'undefined' && !navigator.onLine) requestBackgroundSync();
  }, [pendingCount]);

  return { retryPendingMutations };
}

/** Ask supporting browsers to wake the worker as soon as connectivity returns.
 * The local online event remains the fallback for browsers without Background
 * Sync (including Safari). */
function requestBackgroundSync(): void {
  if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) return;
  void navigator.serviceWorker.ready.then((registration) => {
    const syncManager = (registration as ServiceWorkerRegistration & {
      sync?: { register: (tag: string) => Promise<void> };
    }).sync;
    return syncManager?.register('kinship-mutation-sync');
  }).catch(() => undefined);
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
