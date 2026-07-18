'use client';

import type { QueryClient, QueryKey } from '@tanstack/react-query';
import { getMutationErrorMessage, shouldQueueMutation, type MutationApiError } from '@/lib/api/mutations';
import { useOfflineStore, type MutationEntity, type MutationOperation } from '@/store/offline-store';

export interface QueueableMutation {
  entity: MutationEntity;
  operation: MutationOperation;
  treeId: string;
  entityId?: string;
  payload?: unknown;
}

export function queueFailedMutation(error: unknown, mutation: QueueableMutation): boolean {
  if (!shouldQueueMutation(error)) return false;
  useOfflineStore.getState().addPendingMutation({
    ...mutation,
    errorCode: error.code,
    errorMessage: error.message,
    status: error.code === 'STORAGE_FULL' ? 'blocked' : 'pending'
  });
  return true;
}

export function mutationErrorDescription(error: unknown, locale: string): string {
  return getMutationErrorMessage(error, locale);
}

export async function cancelMutationQueries(queryClient: QueryClient, keys: QueryKey[]): Promise<void> {
  await Promise.all(keys.map((queryKey) => queryClient.cancelQueries({ queryKey })));
}

export function temporaryId(entity: MutationEntity): string {
  return `temp-${entity}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

export function isQueuedError(error: unknown): error is MutationApiError {
  return shouldQueueMutation(error);
}

