'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useLocale } from 'next-intl';
import type { CreateMemberInput, UpdateMemberInput } from '@/data/schemas';
import type { Event, Member, Relationship } from '@/data/types';
import { useToast } from '@/components/ui/toast';
import { mutationApi } from '@/lib/api/mutations';
import { queryKeys } from '@/lib/query/keys';
import { cancelMutationQueries, mutationErrorDescription, queueFailedMutation, temporaryId } from './mutation-utils';

export type MemberMutation =
  | { operation: 'create'; data: CreateMemberInput }
  | { operation: 'update'; memberId: string; data: UpdateMemberInput }
  | { operation: 'delete'; memberId: string };

type MemberMutationResult = Member | void | unknown;

interface MemberMutationContext {
  previousMembers?: Member[];
  hadMembersCache?: boolean;
  previousDetail?: unknown;
  previousRelationships?: Relationship[];
  previousEvents?: Event[];
  hadRelationshipsCache?: boolean;
  hadEventsCache?: boolean;
  temporaryId?: string;
}

export function useMemberMutation(treeId: string) {
  const queryClient = useQueryClient();
  const locale = useLocale();
  const { toast } = useToast();

  return useMutation<MemberMutationResult, Error, MemberMutation, MemberMutationContext>({
    mutationKey: ['member-mutation', treeId],
    mutationFn: (variables) => {
      if (variables.operation === 'create') return mutationApi.createMember(treeId, variables.data);
      if (variables.operation === 'update') return mutationApi.updateMember(treeId, variables.memberId, variables.data);
      return mutationApi.deleteMember(treeId, variables.memberId);
    },
    onMutate: async (variables) => {
      await cancelMutationQueries(queryClient, [
        queryKeys.members(treeId), queryKeys.tree(treeId), queryKeys.relationships(treeId), queryKeys.events(treeId)
      ]);
      const cachedMembers = queryClient.getQueryData<Member[]>(queryKeys.members(treeId));
      const previousMembers = cachedMembers ?? [];
      const entityId = variables.operation === 'create' ? undefined : variables.memberId;
      const previousDetail = entityId ? queryClient.getQueryData(queryKeys.member(treeId, entityId)) : undefined;
      const now = new Date().toISOString();
      if (variables.operation === 'create') {
        const id = temporaryId('member');
        const optimistic: Member = { ...variables.data, id, treeId, createdAt: now, updatedAt: now };
        queryClient.setQueryData<Member[]>(queryKeys.members(treeId), (current = []) => [optimistic, ...current]);
        return { previousMembers, hadMembersCache: cachedMembers !== undefined, temporaryId: id };
      }
      if (variables.operation === 'update') {
        queryClient.setQueryData<Member[]>(queryKeys.members(treeId), (current = []) => current.map((member) =>
          member.id === variables.memberId
            ? { ...member, ...variables.data, isAlive: variables.data.dateOfDeath ? false : variables.data.isAlive ?? member.isAlive, updatedAt: now }
            : member
        ));
        queryClient.setQueryData(queryKeys.member(treeId, variables.memberId), (current: Record<string, unknown> | undefined) => current
          ? { ...current, ...variables.data, member: { ...(current.member as object | undefined), ...variables.data, updatedAt: now }, updatedAt: now }
          : current);
      } else {
        queryClient.setQueryData<Member[]>(queryKeys.members(treeId), (current = []) => current.filter((member) => member.id !== variables.memberId));
        queryClient.removeQueries({ queryKey: queryKeys.member(treeId, variables.memberId), exact: true });
        const cachedRelationships = queryClient.getQueryData<Relationship[]>(queryKeys.relationships(treeId));
        const cachedEvents = queryClient.getQueryData<Event[]>(queryKeys.events(treeId));
        const previousRelationships = cachedRelationships ?? [];
        const previousEvents = cachedEvents ?? [];
        queryClient.setQueryData<Relationship[]>(queryKeys.relationships(treeId), (current = []) => current.filter((item) =>
          item.sourceMemberId !== variables.memberId && item.targetMemberId !== variables.memberId
        ));
        queryClient.setQueryData<Event[]>(queryKeys.events(treeId), (current = []) => current.map((event) => event.memberIds.includes(variables.memberId)
          ? { ...event, memberIds: event.memberIds.filter((id) => id !== variables.memberId), updatedAt: now }
          : event));
        return { previousMembers, hadMembersCache: cachedMembers !== undefined, previousDetail, previousRelationships, previousEvents, hadRelationshipsCache: cachedRelationships !== undefined, hadEventsCache: cachedEvents !== undefined };
      }
      return { previousMembers, hadMembersCache: cachedMembers !== undefined, previousDetail };
    },
    onSuccess: (result, variables, context) => {
      if ((variables.operation === 'create' || variables.operation === 'update') && result && typeof result === 'object' && 'id' in result) {
        const saved = result as Member;
        queryClient.setQueryData<Member[]>(queryKeys.members(treeId), (current = []) => current.map((member) =>
          member.id === (context?.temporaryId ?? saved.id) ? saved : member
        ));
        queryClient.setQueryData(queryKeys.member(treeId, saved.id), (current: Record<string, unknown> | undefined) => current
          ? { ...current, ...saved, member: saved }
          : saved);
      }
    },
    onError: (error, variables, context) => {
      if (context?.previousMembers) {
        const previousMember = variables.operation === 'create'
          ? undefined
          : context.previousMembers.find((member) => member.id === variables.memberId);
        const previousIndex = previousMember ? context.previousMembers.indexOf(previousMember) : -1;
        const rollbackMembers = (current: Member[] = []) => {
          if (variables.operation === 'create') return current.filter((member) => member.id !== context.temporaryId);
          if (!previousMember) return current;
          const withoutTarget = current.filter((member) => member.id !== previousMember.id);
          withoutTarget.splice(Math.min(Math.max(previousIndex, 0), withoutTarget.length), 0, previousMember);
          return withoutTarget;
        };
        if (context.hadMembersCache === false) queryClient.removeQueries({ queryKey: queryKeys.members(treeId), exact: true });
        else queryClient.setQueryData<Member[]>(queryKeys.members(treeId), rollbackMembers);
      }
      if (variables.operation !== 'create' && context?.previousDetail !== undefined) {
        queryClient.setQueryData(queryKeys.member(treeId, variables.memberId), context.previousDetail);
      }
      if (context?.previousRelationships) {
        if (context.hadRelationshipsCache === false) queryClient.removeQueries({ queryKey: queryKeys.relationships(treeId), exact: true });
        else queryClient.setQueryData(queryKeys.relationships(treeId), context.previousRelationships);
      }
      if (context?.previousEvents) {
        if (context.hadEventsCache === false) queryClient.removeQueries({ queryKey: queryKeys.events(treeId), exact: true });
        else queryClient.setQueryData(queryKeys.events(treeId), context.previousEvents);
      }
      const entityId = variables.operation === 'create' ? undefined : variables.memberId;
      queueFailedMutation(error, {
        entity: 'member', operation: variables.operation, treeId, entityId,
        payload: variables.operation === 'delete' ? undefined : variables.data
      });
      toast({
        title: locale === 'vi' ? 'Chưa thể lưu thay đổi thành viên' : 'Member change was not saved',
        description: mutationErrorDescription(error, locale),
        tone: 'destructive',
        duration: 6500
      });
    },
    onSettled: (_result, _error, variables) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.members(treeId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.tree(treeId) });
      if (variables.operation !== 'create') void queryClient.invalidateQueries({ queryKey: queryKeys.member(treeId, variables.memberId) });
      if (variables.operation === 'delete') {
        void queryClient.invalidateQueries({ queryKey: queryKeys.relationships(treeId) });
        void queryClient.invalidateQueries({ queryKey: queryKeys.events(treeId) });
        void queryClient.invalidateQueries({ queryKey: queryKeys.media(treeId) });
      }
    }
  });
}
