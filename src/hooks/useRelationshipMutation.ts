'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useLocale } from 'next-intl';
import type { CreateRelationshipInput } from '@/data/schemas';
import type { Relationship } from '@/data/types';
import { useToast } from '@/components/ui/toast';
import { mutationApi } from '@/lib/api/mutations';
import { queryKeys } from '@/lib/query/keys';
import { cancelMutationQueries, mutationErrorDescription, queueFailedMutation, temporaryId } from './mutation-utils';

export type RelationshipMutation =
  | { operation: 'create'; data: CreateRelationshipInput }
  | { operation: 'delete'; relationshipId: string };

interface RelationshipMutationContext {
  previous?: Relationship[];
  hadCache?: boolean;
  temporaryIds?: string[];
}

export function useRelationshipMutation(treeId: string) {
  const queryClient = useQueryClient();
  const locale = useLocale();
  const { toast } = useToast();

  return useMutation<Relationship | void, Error, RelationshipMutation, RelationshipMutationContext>({
    mutationKey: ['relationship-mutation', treeId],
    mutationFn: (variables) => variables.operation === 'create'
      ? mutationApi.createRelationship(treeId, variables.data)
      : mutationApi.deleteRelationship(treeId, variables.relationshipId),
    onMutate: async (variables) => {
      await cancelMutationQueries(queryClient, [queryKeys.relationships(treeId), queryKeys.tree(treeId)]);
      const cached = queryClient.getQueryData<Relationship[]>(queryKeys.relationships(treeId));
      const previous = cached ?? [];
      if (variables.operation === 'create') {
        const createdAt = new Date().toISOString();
        const directId = temporaryId('relationship');
        const inverseId = temporaryId('relationship');
        const direct: Relationship = { ...variables.data, id: directId, treeId, createdAt };
        const inverse: Relationship = {
          ...direct, id: inverseId,
          sourceMemberId: direct.targetMemberId,
          targetMemberId: direct.sourceMemberId
        };
        queryClient.setQueryData<Relationship[]>(queryKeys.relationships(treeId), (current = []) => [...current, direct, inverse]);
        return { previous, hadCache: cached !== undefined, temporaryIds: [directId, inverseId] };
      }
      queryClient.setQueryData<Relationship[]>(queryKeys.relationships(treeId), (current = []) => {
        const target = current.find((item) => item.id === variables.relationshipId);
        if (!target) return current;
        return current.filter((item) => item.id !== target.id && !(
          item.sourceMemberId === target.targetMemberId &&
          item.targetMemberId === target.sourceMemberId &&
          item.type === target.type &&
          (item.customType ?? '') === (target.customType ?? '')
        ));
      });
      return { previous, hadCache: cached !== undefined };
    },
    onSuccess: (result, variables, context) => {
      if (variables.operation !== 'create' || !result) return;
      queryClient.setQueryData<Relationship[]>(queryKeys.relationships(treeId), (current = []) => current.map((item) =>
        item.id === context?.temporaryIds?.[0] ? result : item
      ));
    },
    onError: (error, variables, context) => {
      if (context?.previous) {
        if (context.hadCache === false) queryClient.removeQueries({ queryKey: queryKeys.relationships(treeId), exact: true });
        else queryClient.setQueryData(queryKeys.relationships(treeId), context.previous);
      }
      queueFailedMutation(error, {
        entity: 'relationship', operation: variables.operation, treeId,
        entityId: variables.operation === 'delete' ? variables.relationshipId : undefined,
        payload: variables.operation === 'create' ? variables.data : undefined
      });
      toast({
        title: locale === 'vi' ? 'Chưa thể lưu mối quan hệ' : 'Relationship change was not saved',
        description: mutationErrorDescription(error, locale), tone: 'destructive', duration: 6500
      });
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.relationships(treeId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.tree(treeId) });
    }
  });
}
