import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

export type MutationEntity = 'member' | 'relationship' | 'event';
export type MutationOperation = 'create' | 'update' | 'delete';
export type PendingMutationStatus = 'pending' | 'retrying' | 'blocked';

export interface PendingMutation {
  id: string;
  entity: MutationEntity;
  operation: MutationOperation;
  treeId: string;
  entityId?: string;
  payload?: unknown;
  createdAt: string;
  attempts: number;
  status: PendingMutationStatus;
  errorCode?: string;
  errorMessage?: string;
}

export type NewPendingMutation = Omit<PendingMutation, 'id' | 'createdAt' | 'attempts' | 'status'> &
  Partial<Pick<PendingMutation, 'id' | 'createdAt' | 'attempts' | 'status'>>;

interface OfflineState {
  pendingMutations: PendingMutation[];
  isSyncing: boolean;
  addPendingMutation: (mutation: NewPendingMutation) => string;
  removePendingMutation: (id: string) => void;
  markRetrying: (id: string) => void;
  markFailed: (id: string, errorCode: string, errorMessage: string, blocked?: boolean) => void;
  retryMutation: (id: string) => void;
  setSyncing: (isSyncing: boolean) => void;
  clearPendingMutations: () => void;
}

function mutationId(): string {
  return `mutation-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

function sameMutation(left: PendingMutation, right: NewPendingMutation): boolean {
  return left.entity === right.entity &&
    left.operation === right.operation &&
    left.treeId === right.treeId &&
    left.entityId === right.entityId &&
    JSON.stringify(left.payload) === JSON.stringify(right.payload);
}

export const useOfflineStore = create<OfflineState>()(
  persist(
    (set, get) => ({
      pendingMutations: [],
      isSyncing: false,
      addPendingMutation: (input) => {
        const existing = get().pendingMutations.find((item) => sameMutation(item, input));
        if (existing) return existing.id;
        const mutation: PendingMutation = {
          ...input,
          id: input.id ?? mutationId(),
          createdAt: input.createdAt ?? new Date().toISOString(),
          attempts: input.attempts ?? 0,
          status: input.status ?? 'pending'
        };
        set((state) => ({ pendingMutations: [...state.pendingMutations, mutation] }));
        return mutation.id;
      },
      removePendingMutation: (id) => set((state) => ({
        pendingMutations: state.pendingMutations.filter((item) => item.id !== id)
      })),
      markRetrying: (id) => set((state) => ({
        pendingMutations: state.pendingMutations.map((item) => item.id === id
          ? { ...item, status: 'retrying', attempts: item.attempts + 1, errorCode: undefined, errorMessage: undefined }
          : item)
      })),
      markFailed: (id, errorCode, errorMessage, blocked = false) => set((state) => ({
        pendingMutations: state.pendingMutations.map((item) => item.id === id
          ? { ...item, status: blocked ? 'blocked' : 'pending', errorCode, errorMessage }
          : item)
      })),
      retryMutation: (id) => set((state) => ({
        pendingMutations: state.pendingMutations.map((item) => item.id === id
          ? { ...item, status: 'pending', errorCode: undefined, errorMessage: undefined }
          : item)
      })),
      setSyncing: (isSyncing) => set({ isSyncing }),
      clearPendingMutations: () => set({ pendingMutations: [] })
    }),
    {
      name: 'kinship.offline-mutations',
      storage: createJSONStorage(() => localStorage),
      partialize: ({ pendingMutations }) => ({ pendingMutations })
    }
  )
);
