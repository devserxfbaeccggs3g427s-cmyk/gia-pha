// @vitest-environment jsdom

import React, { type ReactNode } from 'react';
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CreateMemberInput } from '@/data/schemas';
import type { Member } from '@/data/types';
import { apiRequest, MutationApiError } from '@/lib/api/mutations';
import { queryKeys } from '@/lib/query/keys';
import { useMemberMutation } from '@/hooks/useMemberMutation';
import { useOfflineStore } from '@/store/offline-store';
import { useTreeUiStore } from '@/store/tree-ui-store';
import { useUiStore } from '@/store/ui-store';

const toast = vi.fn();
vi.mock('next-intl', () => ({ useLocale: () => 'vi' }));
vi.mock('@/components/ui/toast', () => ({ useToast: () => ({ toast }) }));

const input: CreateMemberInput = {
  firstName: 'An',
  lastName: 'Nguyễn',
  fullName: 'An Nguyễn',
  gender: 'OTHER',
  isAlive: true
};

const existing: Member = {
  ...input,
  id: 'member-existing',
  treeId: 'tree-1',
  createdAt: '2026-01-01T00:00:00.000Z',
  updatedAt: '2026-01-01T00:00:00.000Z'
};

function testClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
}

function wrapper(client: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  };
}

beforeEach(() => {
  vi.mocked(fetch).mockReset();
  localStorage.clear();
  toast.mockReset();
  useOfflineStore.setState({ pendingMutations: [], isSyncing: false });
  useUiStore.setState({ theme: 'system', sidebarCollapsed: false, mobileSidebarOpen: false, locale: 'vi' });
  useTreeUiStore.getState().reset();
});

describe('optimistic member mutation', () => {
  it('publishes a temporary member before the API confirms, then reconciles the server id', async () => {
    const client = testClient();
    client.setQueryData(queryKeys.members('tree-1'), [existing]);
    let resolveResponse!: (response: Response) => void;
    vi.mocked(fetch).mockImplementationOnce(() => new Promise<Response>((resolve) => { resolveResponse = resolve; }));
    const { result } = renderHook(() => useMemberMutation('tree-1'), { wrapper: wrapper(client) });

    act(() => result.current.mutate({ operation: 'create', data: input }));
    await waitFor(() => expect(client.getQueryData<Member[]>(queryKeys.members('tree-1'))?.[0].id).toMatch(/^temp-member-/));

    const saved: Member = { ...input, id: 'member-server', treeId: 'tree-1', createdAt: '2026-07-18T00:00:00.000Z', updatedAt: '2026-07-18T00:00:00.000Z' };
    resolveResponse(new Response(JSON.stringify(saved), { status: 201, headers: { 'Content-Type': 'application/json' } }));
    await waitFor(() => expect(client.getQueryData<Member[]>(queryKeys.members('tree-1'))?.map((member) => member.id)).toEqual(['member-server', 'member-existing']));
    expect(useOfflineStore.getState().pendingMutations).toHaveLength(0);
  });

  it('rolls back the cache and queues a network failure for retry', async () => {
    const client = testClient();
    client.setQueryData(queryKeys.members('tree-1'), [existing]);
    vi.mocked(fetch).mockRejectedValueOnce(new TypeError('Failed to fetch'));
    const { result } = renderHook(() => useMemberMutation('tree-1'), { wrapper: wrapper(client) });

    act(() => result.current.mutate({ operation: 'create', data: input }));
    await waitFor(() => expect(useOfflineStore.getState().pendingMutations).toHaveLength(1));

    expect(client.getQueryData(queryKeys.members('tree-1'))).toEqual([existing]);
    expect(useOfflineStore.getState().pendingMutations[0]).toMatchObject({
      entity: 'member', operation: 'create', treeId: 'tree-1', payload: input, status: 'pending', errorCode: 'NETWORK'
    });
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ tone: 'destructive', description: expect.stringContaining('Mất kết nối') }));
  });
});

describe('offline and UI stores', () => {
  it('deduplicates queued mutations and allows blocked mutations to be retried manually', () => {
    const queue = useOfflineStore.getState();
    const first = queue.addPendingMutation({ entity: 'event', operation: 'update', treeId: 'tree-1', entityId: 'event-1', payload: { title: 'Họp mặt' } });
    const duplicate = useOfflineStore.getState().addPendingMutation({ entity: 'event', operation: 'update', treeId: 'tree-1', entityId: 'event-1', payload: { title: 'Họp mặt' } });
    expect(duplicate).toBe(first);
    expect(useOfflineStore.getState().pendingMutations).toHaveLength(1);

    useOfflineStore.getState().markFailed(first, 'STORAGE_FULL', 'Storage full', true);
    expect(useOfflineStore.getState().pendingMutations[0].status).toBe('blocked');
    useOfflineStore.getState().retryMutation(first);
    expect(useOfflineStore.getState().pendingMutations[0].status).toBe('pending');
  });

  it('keeps global UI preferences separate from per-tree viewport state', () => {
    useUiStore.getState().setTheme('dark');
    useUiStore.getState().setSidebarCollapsed(true);
    useTreeUiStore.getState().setActiveTree('tree-1');
    useTreeUiStore.getState().setViewport({ x: 20, y: -10, zoom: 1.4 });
    useTreeUiStore.getState().selectNode('member-1');
    expect(useUiStore.getState()).toMatchObject({ theme: 'dark', sidebarCollapsed: true });
    expect(useTreeUiStore.getState()).toMatchObject({ activeTreeId: 'tree-1', viewport: { x: 20, y: -10, zoom: 1.4 }, selectedNodeId: 'member-1' });

    useTreeUiStore.getState().setActiveTree('tree-2');
    expect(useTreeUiStore.getState()).toMatchObject({ activeTreeId: 'tree-2', viewport: { x: 0, y: 0, zoom: 1 }, selectedNodeId: undefined });
  });
});

describe('typed API errors', () => {
  it('preserves Blob error codes returned by API routes', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(new Response(JSON.stringify({ ok: false, error: { code: 'STORAGE_FULL', message: 'Quota exceeded' } }), { status: 503 }));
    await expect(apiRequest('/api/test')).rejects.toEqual(expect.objectContaining<Partial<MutationApiError>>({ code: 'STORAGE_FULL', status: 503, message: 'Quota exceeded' }));
  });
});
