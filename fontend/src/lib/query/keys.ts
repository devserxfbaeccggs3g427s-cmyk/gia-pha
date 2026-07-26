export const queryKeys = {
  tree: (treeId: string) => ['tree', treeId] as const,
  members: (treeId: string) => ['members', treeId] as const,
  member: (treeId: string, memberId: string) => ['members', treeId, memberId] as const,
  relationships: (treeId: string) => ['relationships', treeId] as const,
  events: (treeId: string) => ['events', treeId] as const,
  upcomingEvents: (treeId: string, days = 7) => ['events', treeId, 'upcoming', days] as const,
  media: (treeId: string) => ['media', treeId] as const
};

