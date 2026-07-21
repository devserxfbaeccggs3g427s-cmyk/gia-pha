import { createHash } from 'crypto';
import type { CompositeIdentityGroup, CompositeWarning, FamilyTree, Member, ResolvedEvent, ResolvedMediaMetadata, ResolvedSourceManifest, ResolvedTreeData, SourceReference, VirtualMember, VirtualRelationship } from '@/data/types';
import { sourceReferenceKey } from '@/data/schemas';
import { calculateGenerations } from '@/lib/algorithms/generation';
import { resolveSourceScope } from '@/lib/algorithms/source-scope';
import { getCompositeConfig, getTreeCollectionsBatch, getTrees } from '@/lib/blob/readers';
import { canAccessTree, getUserTreeRole } from '@/lib/auth/rbac';
import { compositeCacheKey, readCompositeCache, writeCompositeCache, type CompositeAudience } from '@/lib/composite/composite-cache';
import { emitCompositeMetric, requireCompositeFeature } from '@/lib/composite/feature-flags';
import { CompositeConfigError } from './composite-config-service';

const CONCURRENCY = Math.max(1, Number(process.env.COMPOSITE_RESOLVER_CONCURRENCY) || 5);
const hashId = (prefix: string, value: string) => `${prefix}_${createHash('sha256').update(value).digest('base64url').slice(0, 12)}`;
const refKey = (treeId: string, memberId: string) => sourceReferenceKey({ treeId, memberId });

export class CompositeResolver {
  async resolveForUser(treeId: string, userId: string, options: { offline?: boolean } = {}): Promise<ResolvedTreeData> {
    requireCompositeFeature('trees');
    const started = Date.now();
    const [trees, config] = await Promise.all([getTrees(), getCompositeConfig(treeId)]);
    const tree = trees.find((item) => item.id === treeId);
    if (!tree || (tree.kind ?? 'STANDALONE') !== 'COMPOSITE') throw new CompositeConfigError('NOT_COMPOSITE_TREE', 'Composite tree not found');
    if (!config) throw new CompositeConfigError('INVALID_COMPOSITE_CONFIG', 'Composite config not found');
    const treeIndex = new Map(trees.map((item) => [item.id, item]));
    const audience: CompositeAudience = { userId, privacy: config.sources.map((source) => `${source.sourceTreeId}:${source.shareLivingDetails}`).sort().join('|'), permissions: config.sources.map((source) => { const sourceTree = treeIndex.get(source.sourceTreeId); const role = sourceTree ? getUserTreeRole(sourceTree, userId) : null; return { sourceTreeId: source.sourceTreeId, role, readable: Boolean(sourceTree && canAccessTree(sourceTree, userId, 'READ')) }; }) };
    const sourceVersions = config.sources.map((source) => `${source.sourceTreeId}:${treeIndex.get(source.sourceTreeId)?.updatedAt ?? 'unavailable'}`);
    const cacheKey = compositeCacheKey(treeId, audience, config.revision, sourceVersions);
    if (options.offline) {
      const cached = await readCompositeCache(treeId, cacheKey);
      if (cached) return { ...cached, stale: true, warnings: [...cached.warnings, { code: 'STALE_SOURCE', message: 'Offline snapshot requires permission reauthorization on reconnect' }] };
    }
    const readableSourceIds = config.sources.filter((source) => { const sourceTree = treeIndex.get(source.sourceTreeId); return Boolean(sourceTree && (sourceTree.kind ?? 'STANDALONE') === 'STANDALONE' && canAccessTree(sourceTree, userId, 'READ')); }).map((source) => source.sourceTreeId);
    const collections = await getTreeCollectionsBatch(readableSourceIds, CONCURRENCY).catch(() => new Map());
    const loaded = config.sources.map((source) => {
      const sourceTree = treeIndex.get(source.sourceTreeId);
      const data = collections.get(source.sourceTreeId);
      if (!sourceTree || !data) return { source, sourceTree, available: false as const };
      return { source, sourceTree, available: true as const, scoped: resolveSourceScope(data.members, data.relationships, data.events, data.mediaMetadata, source) };
    });
    const warnings: CompositeWarning[] = [];
    const manifest: ResolvedSourceManifest[] = [];
    const records = new Map<string, Member>();
    for (const item of loaded) {
      if (!item.available) {
        manifest.push({ sourceTreeId: item.source.sourceTreeId, status: 'UNAVAILABLE', version: '', resolvedMemberCount: 0, warningCode: item.sourceTree ? 'SOURCE_FORBIDDEN' : 'SOURCE_UNAVAILABLE' });
        warnings.push({ code: item.sourceTree ? 'SOURCE_FORBIDDEN' : 'SOURCE_UNAVAILABLE', message: 'Source is unavailable', sourceTreeId: item.source.sourceTreeId });
        continue;
      }
      for (const member of item.scoped.members) records.set(refKey(item.source.sourceTreeId, member.id), member);
      const stale = Boolean(item.source.sourceVersion && item.source.sourceVersion !== item.sourceTree.updatedAt);
      manifest.push({ sourceTreeId: item.source.sourceTreeId, status: 'ACTIVE', version: item.sourceTree.updatedAt, resolvedMemberCount: item.scoped.members.length, ...(stale ? { warningCode: 'STALE_SOURCE' } : {}) });
      if (stale) warnings.push({ code: 'STALE_SOURCE', message: 'Source version changed since it was configured', sourceTreeId: item.source.sourceTreeId });
    }
    const confirmed = config.identityGroups.filter((group) => group.status === 'CONFIRMED');
    const groupByRef = new Map<string, CompositeIdentityGroup>();
    for (const group of confirmed) for (const ref of group.references) if (records.has(sourceReferenceKey(ref))) groupByRef.set(sourceReferenceKey(ref), group);
    const buckets = new Map<string, SourceReference[]>();
    for (const key of [...records.keys()].sort()) {
      const [sourceTreeId, memberId] = key.split('\0');
      const group = groupByRef.get(key);
      const virtualId = group ? hashId('vm', treeId + group.id) : hashId('vm', treeId + sourceTreeId + memberId);
      buckets.set(virtualId, [...(buckets.get(virtualId) ?? []), { treeId: sourceTreeId, memberId }]);
    }
    const virtualByRef = new Map<string, string>();
    const members: VirtualMember[] = [...buckets].sort(([a], [b]) => a.localeCompare(b)).map(([id, references]) => {
      references.forEach((ref) => virtualByRef.set(sourceReferenceKey(ref), id));
      const group = groupByRef.get(sourceReferenceKey(references[0]));
      const preferred = group?.preferredReference && records.has(sourceReferenceKey(group.preferredReference)) ? group.preferredReference : [...references].sort((a, b) => sourceReferenceKey(a).localeCompare(sourceReferenceKey(b)))[0];
      const source = records.get(sourceReferenceKey(preferred))!;
      const provenance = references.map((ref) => ({ treeId: ref.treeId, entityId: ref.memberId, entityType: 'MEMBER' as const, sourceUpdatedAt: records.get(sourceReferenceKey(ref))?.updatedAt }));
      const conflictingFields = detectConflictingFields(references.map((ref) => records.get(sourceReferenceKey(ref))!));
      const fieldProvenance = Object.fromEntries(MEMBER_PROVENANCE_FIELDS.map((field) => [field, references.filter((ref) => records.get(sourceReferenceKey(ref))?.[field] !== undefined).map((ref) => ({ treeId: ref.treeId, entityId: ref.memberId, entityType: 'MEMBER' as const, sourceUpdatedAt: records.get(sourceReferenceKey(ref))?.updatedAt }))]));
      return { ...source, id, treeId, sourceReferences: references, preferredReference: preferred, provenance, fieldProvenance, conflictingFields, hasConflictingFields: conflictingFields.length > 0 };
    });
    const relationshipMap = new Map<string, VirtualRelationship>();
    const events: ResolvedEvent[] = [];
    const mediaMetadata: ResolvedMediaMetadata[] = [];
    for (const item of loaded) if (item.available) {
      for (const rel of item.scoped.relationships) addRelationship(relationshipMap, treeId, virtualByRef, { source: { treeId: item.source.sourceTreeId, memberId: rel.sourceMemberId }, target: { treeId: item.source.sourceTreeId, memberId: rel.targetMemberId }, rel, provenanceId: rel.id, cross: false });
      const eventIds = new Map<string, string>();
      if (item.source.includeEvents) for (const event of item.scoped.events) {
        const id = hashId('ve', treeId + item.source.sourceTreeId + event.id); eventIds.set(event.id, id);
        events.push({ ...event, id, treeId, memberIds: [...new Set(event.memberIds.map((memberId) => virtualByRef.get(refKey(item.source.sourceTreeId, memberId))).filter((value): value is string => Boolean(value)))], mediaIds: event.mediaIds.map((mediaId) => hashId('vx', treeId + item.source.sourceTreeId + mediaId)), provenance: [{ treeId: item.source.sourceTreeId, entityId: event.id, entityType: 'EVENT', sourceUpdatedAt: event.updatedAt }] });
      }
      if (item.source.includeMedia) for (const media of item.scoped.mediaMetadata) mediaMetadata.push({ ...media, id: hashId('vx', treeId + item.source.sourceTreeId + media.id), treeId, memberIds: [...new Set([...(media.memberIds ?? []), ...(media.memberId ? [media.memberId] : [])].map((id) => virtualByRef.get(refKey(item.source.sourceTreeId, id))).filter((value): value is string => Boolean(value)))], eventIds: [...new Set([...(media.eventIds ?? []), ...(media.eventId ? [media.eventId] : [])].map((id) => eventIds.get(id)).filter((value): value is string => Boolean(value)))], provenance: [{ treeId: item.source.sourceTreeId, entityId: media.id, entityType: 'MEDIA' }] });
    }
    for (const rel of config.crossTreeRelationships) {
      for (const ref of [rel.source, rel.target]) if (!virtualByRef.has(sourceReferenceKey(ref))) {
        const unavailable = loaded.find((item) => item.source.sourceTreeId === ref.treeId && !item.available);
        if (unavailable) {
           const id = hashId('vm', treeId + ref.treeId + ref.memberId);
          virtualByRef.set(sourceReferenceKey(ref), id);
          if (!members.some((member) => member.id === id)) members.push({ id, treeId, firstName: 'Unavailable', lastName: 'person', fullName: 'Unavailable person', gender: 'OTHER', isAlive: true, createdAt: '', updatedAt: '', sourceReferences: [{ treeId: ref.treeId, memberId: '' }], preferredReference: { treeId: ref.treeId, memberId: '' }, provenance: [{ treeId: ref.treeId, entityId: '', entityType: 'MEMBER' }], fieldProvenance: {}, conflictingFields: [], hasConflictingFields: false, isPlaceholder: true });
        }
      }
      addRelationship(relationshipMap, treeId, virtualByRef, { source: rel.source, target: rel.target, rel, provenanceId: rel.id, cross: true });
    }
    const relationships = [...relationshipMap.values()].sort((a, b) => a.id.localeCompare(b.id));
    assertAcyclic(relationships);
    const generations = calculateGenerations(members, relationships);
    members.forEach((member) => { member.generation = generations.get(member.id) ?? 0; });
    const result = { tree, members, relationships, events, mediaMetadata, sourceManifest: manifest, warnings, resolvedAt: new Date().toISOString(), configRevision: config.revision, stale: false } satisfies ResolvedTreeData;
    await writeCompositeCache(result, audience);
    emitCompositeMetric('resolve', { treeId, durationMs: Date.now() - started, sourceReadCount: loaded.filter((item) => item.available).length * 4, sourceFailures: loaded.filter((item) => !item.available).length, permissionDenials: loaded.filter((item) => !item.available && Boolean(item.sourceTree)).length, partialResolve: warnings.length > 0, cacheHit: false, invalidConfiguration: false, members: members.length });
    return result;
  }

  async validate(treeId: string, userId: string): Promise<{ valid: boolean; warnings: CompositeWarning[] }> {
    try { const result = await this.resolveForUser(treeId, userId); return { valid: true, warnings: result.warnings }; }
    catch (error) { if (error instanceof CompositeConfigError) return { valid: false, warnings: [{ code: 'INVALID_REFERENCE', message: error.message }] }; throw error; }
  }
}

function addRelationship(map: Map<string, VirtualRelationship>, treeId: string, refs: Map<string, string>, input: { source: SourceReference; target: SourceReference; rel: any; provenanceId: string; cross: boolean }) {
  const sourceMemberId = refs.get(sourceReferenceKey(input.source)); const targetMemberId = refs.get(sourceReferenceKey(input.target));
  if (!sourceMemberId || !targetMemberId || sourceMemberId === targetMemberId) return;
  const directed = input.rel.type === 'PARENT_CHILD' || input.rel.type === 'ADOPTED';
  const endpoints = directed ? [sourceMemberId, targetMemberId] : [sourceMemberId, targetMemberId].sort();
  const key = [input.rel.type, input.rel.customType ?? '', ...endpoints].join('\0');
  const provenance = { treeId: input.cross ? treeId : input.source.treeId, entityId: input.provenanceId, entityType: 'RELATIONSHIP' as const };
  const current = map.get(key);
  if (current) { current.provenance.push(provenance); return; }
  map.set(key, { id: hashId('vr', treeId + key), treeId, sourceMemberId: endpoints[0], targetMemberId: endpoints[1], type: input.rel.type, ...(input.rel.customType ? { customType: input.rel.customType } : {}), ...(input.rel.marriageDate ? { marriageDate: input.rel.marriageDate } : {}), ...(input.rel.divorceDate ? { divorceDate: input.rel.divorceDate } : {}), ...(input.rel.marriageStatus ? { marriageStatus: input.rel.marriageStatus } : {}), createdAt: input.rel.createdAt, provenance: [provenance], isCrossTree: input.cross });
}
function assertAcyclic(relationships: VirtualRelationship[]) { const edges = new Map<string, string[]>(); for (const r of relationships) if (r.type === 'PARENT_CHILD') edges.set(r.sourceMemberId, [...(edges.get(r.sourceMemberId) ?? []), r.targetMemberId]); const visiting = new Set<string>(), done = new Set<string>(), path: string[] = []; const visit = (id: string): boolean => { if (visiting.has(id)) { path.push(id); return true; } if (done.has(id)) return false; visiting.add(id); for (const child of edges.get(id) ?? []) if (visit(child)) { path.push(id); return true; } visiting.delete(id); done.add(id); return false; }; for (const id of edges.keys()) if (visit(id)) { const cycleNodes = path.reverse(); throw new CompositeConfigError('RELATIONSHIP_CYCLE', `Parent-child cycle: ${cycleNodes.join(' -> ')}`, { cycleNodes }); } }
const MEMBER_PROVENANCE_FIELDS: Array<keyof Member> = ['fullName', 'dateOfBirth', 'placeOfBirth', 'gender', 'dateOfDeath'];
function detectConflictingFields(members: Member[]): string[] { return MEMBER_PROVENANCE_FIELDS.filter((field) => new Set(members.map((member) => member[field] ?? null)).size > 1); }
async function mapLimit<T, R>(items: T[], limit: number, fn: (item: T) => Promise<R>): Promise<R[]> { const result = new Array<R>(items.length); let cursor = 0; await Promise.all(Array.from({ length: Math.min(limit, items.length) }, async () => { while (cursor < items.length) { const index = cursor++; result[index] = await fn(items[index]); } })); return result; }
export const compositeResolver = new CompositeResolver();
