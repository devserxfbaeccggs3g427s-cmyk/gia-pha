'use client';

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useLocale } from 'next-intl';
import type { CreateEventInput, UpdateEventInput } from '@/data/schemas';
import type { Event } from '@/data/types';
import { useToast } from '@/components/ui/toast';
import { mutationApi } from '@/lib/api/mutations';
import { queryKeys } from '@/lib/query/keys';
import { cancelMutationQueries, mutationErrorDescription, queueFailedMutation, temporaryId } from './mutation-utils';

export type EventMutation =
  | { operation: 'create'; data: CreateEventInput }
  | { operation: 'update'; eventId: string; data: UpdateEventInput }
  | { operation: 'delete'; eventId: string };

interface EventMutationContext { previous?: Event[]; hadCache?: boolean; temporaryId?: string }

const sortEvents = (events: Event[]) => [...events].sort((a, b) => a.eventDate.localeCompare(b.eventDate) || a.title.localeCompare(b.title));

export function useEventMutation(treeId: string) {
  const queryClient = useQueryClient();
  const locale = useLocale();
  const { toast } = useToast();

  return useMutation<Event | void, Error, EventMutation, EventMutationContext>({
    mutationKey: ['event-mutation', treeId],
    mutationFn: (variables) => {
      if (variables.operation === 'create') return mutationApi.createEvent(treeId, variables.data);
      if (variables.operation === 'update') return mutationApi.updateEvent(treeId, variables.eventId, variables.data);
      return mutationApi.deleteEvent(treeId, variables.eventId);
    },
    onMutate: async (variables) => {
      await cancelMutationQueries(queryClient, [queryKeys.events(treeId), queryKeys.upcomingEvents(treeId)]);
      const cached = queryClient.getQueryData<Event[]>(queryKeys.events(treeId));
      const previous = cached ?? [];
      const now = new Date().toISOString();
      if (variables.operation === 'create') {
        const id = temporaryId('event');
        const optimistic: Event = { ...variables.data, id, treeId, createdAt: now, updatedAt: now };
        queryClient.setQueryData<Event[]>(queryKeys.events(treeId), (current = []) => sortEvents([...current, optimistic]));
        return { previous, hadCache: cached !== undefined, temporaryId: id };
      }
      if (variables.operation === 'update') {
        queryClient.setQueryData<Event[]>(queryKeys.events(treeId), (current = []) => sortEvents(current.map((event) =>
          event.id === variables.eventId ? { ...event, ...variables.data, updatedAt: now } : event
        )));
      } else {
        queryClient.setQueryData<Event[]>(queryKeys.events(treeId), (current = []) => current.filter((event) => event.id !== variables.eventId));
      }
      return { previous, hadCache: cached !== undefined };
    },
    onSuccess: (result, variables, context) => {
      if (variables.operation === 'delete' || !result) return;
      queryClient.setQueryData<Event[]>(queryKeys.events(treeId), (current = []) => sortEvents(current.map((event) =>
        event.id === (context?.temporaryId ?? result.id) ? result : event
      )));
    },
    onError: (error, variables, context) => {
      if (context?.previous) {
        if (context.hadCache === false) queryClient.removeQueries({ queryKey: queryKeys.events(treeId), exact: true });
        else queryClient.setQueryData(queryKeys.events(treeId), context.previous);
      }
      const entityId = variables.operation === 'create' ? undefined : variables.eventId;
      queueFailedMutation(error, {
        entity: 'event', operation: variables.operation, treeId, entityId,
        payload: variables.operation === 'delete' ? undefined : variables.data
      });
      toast({
        title: locale === 'vi' ? 'Chưa thể lưu sự kiện' : 'Event change was not saved',
        description: mutationErrorDescription(error, locale), tone: 'destructive', duration: 6500
      });
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.events(treeId) });
      void queryClient.invalidateQueries({ queryKey: ['events', treeId, 'upcoming'] });
      void queryClient.invalidateQueries({ queryKey: queryKeys.media(treeId) });
    }
  });
}
