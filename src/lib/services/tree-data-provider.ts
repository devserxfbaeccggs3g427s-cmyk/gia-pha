import type { FamilyTree, ResolvedTreeData } from '@/data/types';
import { calculateGenerations } from '@/lib/algorithms/generation';
import { getEvents, getMediaMetadata, getMembers, getRelationships } from '@/lib/blob/readers';
import { treeService } from './tree-service';
import { compositeResolver } from './composite-resolver';

export interface TreeDataProvider { resolveForUser(treeId: string, userId: string): Promise<ResolvedTreeData>; }
export class StandaloneTreeDataProvider implements TreeDataProvider {
  async resolveForUser(treeId: string, _userId: string): Promise<ResolvedTreeData> {
    const [tree, members, relationships, events, mediaMetadata] = await Promise.all([treeService.getTree(treeId), getMembers(treeId), getRelationships(treeId), getEvents(treeId), getMediaMetadata(treeId)]);
    const generations = calculateGenerations(members, relationships);
    return { tree, members: members.map((member) => ({ ...member, generation: generations.get(member.id) ?? member.generation, sourceReferences: [{ treeId, memberId: member.id }], preferredReference: { treeId, memberId: member.id }, provenance: [{ treeId, entityId: member.id, entityType: 'MEMBER', sourceUpdatedAt: member.updatedAt }], fieldProvenance: {}, conflictingFields: [], hasConflictingFields: false })), relationships: relationships.map((relationship) => ({ ...relationship, provenance: [{ treeId, entityId: relationship.id, entityType: 'RELATIONSHIP' }], isCrossTree: false })), events: events.map((event) => ({ ...event, provenance: [{ treeId, entityId: event.id, entityType: 'EVENT', sourceUpdatedAt: event.updatedAt }] })), mediaMetadata: mediaMetadata.map((media) => ({ ...media, memberIds: [...new Set([...(media.memberIds ?? []), ...(media.memberId ? [media.memberId] : [])])], eventIds: [...new Set([...(media.eventIds ?? []), ...(media.eventId ? [media.eventId] : [])])], provenance: [{ treeId, entityId: media.id, entityType: 'MEDIA' }] })), sourceManifest: [{ sourceTreeId: treeId, status: 'ACTIVE', version: tree.updatedAt, resolvedMemberCount: members.length }], warnings: [], resolvedAt: new Date().toISOString(), configRevision: 0, stale: false };
  }
}
export class CompositeTreeDataProvider implements TreeDataProvider { resolveForUser(treeId: string, userId: string) { return compositeResolver.resolveForUser(treeId, userId); } }
export async function getTreeDataProvider(treeId: string): Promise<TreeDataProvider> { const tree: FamilyTree = await treeService.getTree(treeId); return (tree.kind ?? 'STANDALONE') === 'COMPOSITE' ? new CompositeTreeDataProvider() : new StandaloneTreeDataProvider(); }
export async function resolveTreeForUser(treeId: string, userId: string) { return (await getTreeDataProvider(treeId)).resolveForUser(treeId, userId); }
