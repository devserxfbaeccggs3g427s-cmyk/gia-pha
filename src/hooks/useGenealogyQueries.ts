'use client';

import { useQuery } from '@tanstack/react-query';
import type { Event, MediaMetadata, Member, Relationship } from '@/data/types';
import type { MemberFull } from '@/lib/services/member-service';
import type { UpcomingEvent } from '@/lib/services/event-service';
import { apiRequest } from '@/lib/api/mutations';
import { queryKeys } from '@/lib/query/keys';

export function useMembersQuery(treeId: string) {
  return useQuery({ queryKey: queryKeys.members(treeId), queryFn: () => apiRequest<Member[]>(`/api/trees/${encodeURIComponent(treeId)}/members`) });
}

export function useMemberQuery(treeId: string, memberId: string) {
  return useQuery({ queryKey: queryKeys.member(treeId, memberId), queryFn: () => apiRequest<MemberFull>(`/api/members/${encodeURIComponent(memberId)}?treeId=${encodeURIComponent(treeId)}`) });
}

export function useRelationshipsQuery(treeId: string) {
  return useQuery({ queryKey: queryKeys.relationships(treeId), queryFn: () => apiRequest<Relationship[]>(`/api/trees/${encodeURIComponent(treeId)}/relationships`) });
}

export function useEventsQuery(treeId: string) {
  return useQuery({ queryKey: queryKeys.events(treeId), queryFn: () => apiRequest<Event[]>(`/api/trees/${encodeURIComponent(treeId)}/events`) });
}

export function useUpcomingEventsQuery(treeId: string, days = 7) {
  return useQuery({ queryKey: queryKeys.upcomingEvents(treeId, days), queryFn: () => apiRequest<UpcomingEvent[]>(`/api/trees/${encodeURIComponent(treeId)}/events?upcoming=true&days=${days}`) });
}

export function useMediaQuery(treeId: string) {
  return useQuery({ queryKey: queryKeys.media(treeId), queryFn: () => apiRequest<MediaMetadata[]>(`/api/trees/${encodeURIComponent(treeId)}/media`) });
}
