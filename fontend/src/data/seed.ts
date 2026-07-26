import type { Event, FamilyTree, MediaMetadata, Member, Relationship } from './types';

export const emptyTreeData = {
  members: [] satisfies Member[],
  relationships: [] satisfies Relationship[],
  events: [] satisfies Event[],
  mediaMetadata: [] satisfies MediaMetadata[]
};

export function createInitialTree(params: {
  id: string;
  ownerId: string;
  name: string;
  description?: string;
  now: string;
}): FamilyTree {
  return {
    id: params.id,
    name: params.name,
    description: params.description,
    ownerId: params.ownerId,
    memberships: [
      {
        userId: params.ownerId,
        role: 'ADMIN',
        createdAt: params.now
      }
    ],
    createdAt: params.now,
    updatedAt: params.now
  };
}
