import type { CreateEventInput, CreateMemberInput, CreateRelationshipInput, UpdateEventInput, UpdateMemberInput } from '@/data/schemas';
import type { Event, Member, Relationship } from '@/data/types';
import type { PendingMutation } from '@/store/offline-store';

export type MutationErrorCode =
  | 'NETWORK'
  | 'RATE_LIMIT'
  | 'STORAGE_FULL'
  | 'CONFIGURATION'
  | 'UNAUTHENTICATED'
  | 'FORBIDDEN'
  | 'VALIDATION_ERROR'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'INTERNAL_ERROR'
  | 'UNKNOWN';

export class MutationApiError extends Error {
  constructor(
    public readonly code: MutationErrorCode,
    message: string,
    public readonly status?: number,
    public readonly details?: unknown
  ) {
    super(message);
    this.name = 'MutationApiError';
  }
}

export async function apiRequest<T>(url: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
        ...init?.headers
      }
    });
  } catch (error) {
    throw new MutationApiError('NETWORK', 'Không thể kết nối máy chủ. Thay đổi đã được lưu để thử lại.', undefined, error);
  }

  const body = response.status === 204
    ? undefined
    : await response.json().catch(() => undefined) as unknown;
  if (!response.ok) {
    const failure = body as { error?: { code?: string; message?: string; details?: unknown }; message?: string } | undefined;
    throw new MutationApiError(
      normalizeErrorCode(failure?.error?.code, response.status),
      failure?.error?.message ?? failure?.message ?? `Request failed (${response.status})`,
      response.status,
      failure?.error?.details
    );
  }
  return body as T;
}

export const mutationApi = {
  createMember: (treeId: string, data: CreateMemberInput) => apiRequest<Member>(`/api/trees/${encodeURIComponent(treeId)}/members`, { method: 'POST', body: JSON.stringify(data) }),
  updateMember: (treeId: string, memberId: string, data: UpdateMemberInput) => apiRequest<Member>(`/api/members/${encodeURIComponent(memberId)}?treeId=${encodeURIComponent(treeId)}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteMember: (treeId: string, memberId: string) => apiRequest<unknown>(`/api/members/${encodeURIComponent(memberId)}?treeId=${encodeURIComponent(treeId)}`, { method: 'DELETE' }),
  createRelationship: (treeId: string, data: CreateRelationshipInput) => apiRequest<Relationship>(`/api/trees/${encodeURIComponent(treeId)}/relationships`, { method: 'POST', body: JSON.stringify(data) }),
  deleteRelationship: (treeId: string, relationshipId: string) => apiRequest<void>(`/api/relationships/${encodeURIComponent(relationshipId)}?treeId=${encodeURIComponent(treeId)}`, { method: 'DELETE' }),
  createEvent: (treeId: string, data: CreateEventInput) => apiRequest<Event>(`/api/trees/${encodeURIComponent(treeId)}/events`, { method: 'POST', body: JSON.stringify(data) }),
  updateEvent: (treeId: string, eventId: string, data: UpdateEventInput) => apiRequest<Event>(`/api/events/${encodeURIComponent(eventId)}?treeId=${encodeURIComponent(treeId)}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteEvent: (treeId: string, eventId: string) => apiRequest<void>(`/api/events/${encodeURIComponent(eventId)}?treeId=${encodeURIComponent(treeId)}`, { method: 'DELETE' })
};

export async function executePendingMutation(mutation: PendingMutation): Promise<unknown> {
  if (mutation.entity === 'member') {
    if (mutation.operation === 'create') return mutationApi.createMember(mutation.treeId, mutation.payload as CreateMemberInput);
    if (mutation.operation === 'update' && mutation.entityId) return mutationApi.updateMember(mutation.treeId, mutation.entityId, mutation.payload as UpdateMemberInput);
    if (mutation.operation === 'delete' && mutation.entityId) return mutationApi.deleteMember(mutation.treeId, mutation.entityId);
  }
  if (mutation.entity === 'relationship') {
    if (mutation.operation === 'create') return mutationApi.createRelationship(mutation.treeId, mutation.payload as CreateRelationshipInput);
    if (mutation.operation === 'delete' && mutation.entityId) return mutationApi.deleteRelationship(mutation.treeId, mutation.entityId);
  }
  if (mutation.entity === 'event') {
    if (mutation.operation === 'create') return mutationApi.createEvent(mutation.treeId, mutation.payload as CreateEventInput);
    if (mutation.operation === 'update' && mutation.entityId) return mutationApi.updateEvent(mutation.treeId, mutation.entityId, mutation.payload as UpdateEventInput);
    if (mutation.operation === 'delete' && mutation.entityId) return mutationApi.deleteEvent(mutation.treeId, mutation.entityId);
  }
  throw new MutationApiError('VALIDATION_ERROR', 'Pending mutation is incomplete and cannot be retried.');
}

export function shouldQueueMutation(error: unknown): error is MutationApiError {
  return error instanceof MutationApiError && ['NETWORK', 'RATE_LIMIT', 'STORAGE_FULL'].includes(error.code);
}

export function isRetryableMutationError(error: unknown): boolean {
  return error instanceof MutationApiError && ['NETWORK', 'RATE_LIMIT'].includes(error.code);
}

export function getMutationErrorMessage(error: unknown, locale: string): string {
  const vi = locale === 'vi';
  if (!(error instanceof MutationApiError)) return vi ? 'Không thể lưu thay đổi.' : 'Could not save the change.';
  if (error.code === 'NETWORK') return vi ? 'Mất kết nối mạng. Thay đổi đã được giữ lại để thử lại.' : 'Network unavailable. The change was kept for retry.';
  if (error.code === 'RATE_LIMIT') return vi ? 'Đã đạt giới hạn ghi dữ liệu. Thay đổi sẽ được thử lại sau.' : 'The storage write limit was reached. The change will be retried later.';
  if (error.code === 'STORAGE_FULL') return vi ? 'Kho lưu trữ đã đầy. Thay đổi được giữ lại; hãy dọn dung lượng trước khi thử lại.' : 'Storage is full. The change was kept; free some space before retrying.';
  return error.message;
}

function normalizeErrorCode(code: string | undefined, status: number): MutationErrorCode {
  const known: MutationErrorCode[] = ['NETWORK', 'RATE_LIMIT', 'STORAGE_FULL', 'CONFIGURATION', 'UNAUTHENTICATED', 'FORBIDDEN', 'VALIDATION_ERROR', 'NOT_FOUND', 'CONFLICT', 'INTERNAL_ERROR', 'UNKNOWN'];
  if (code && known.includes(code as MutationErrorCode)) return code as MutationErrorCode;
  if (status === 429) return 'RATE_LIMIT';
  if (status === 507) return 'STORAGE_FULL';
  if (status === 401) return 'UNAUTHENTICATED';
  if (status === 403) return 'FORBIDDEN';
  if (status >= 500) return 'NETWORK';
  return 'UNKNOWN';
}
