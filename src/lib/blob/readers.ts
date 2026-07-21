import { compositeTreeConfigSchema, familyTreeSchema } from '@/data/schemas';
import type { Album, ChangeLog, CompositeAuditEntry, CompositeTreeConfig, Event, FamilyTree, MediaMetadata, Member, Relationship, User } from '@/data/types';
import { normalizeRelationships } from '@/lib/algorithms/relationship-normalization';
import { BLOB_PATHS, readBlob } from './client';
import { putRelationships } from './writers';

export async function getUsers(): Promise<User[]> {
  return (await readBlob<User[]>(BLOB_PATHS.users())) ?? [];
}

export async function getTrees(): Promise<FamilyTree[]> {
  const stored = (await readBlob<unknown[]>(BLOB_PATHS.trees())) ?? [];
  return familyTreeSchema.array().parse(stored);
}

export async function getMembers(treeId: string): Promise<Member[]> {
  return (await readBlob<Member[]>(BLOB_PATHS.members(treeId))) ?? [];
}

export async function getRelationships(treeId: string): Promise<Relationship[]> {
  const stored = (await readBlob<Relationship[]>(BLOB_PATHS.relationships(treeId))) ?? [];
  const canonical = normalizeRelationships(stored);
  // Lazy migration keeps legacy reciprocal rows from leaking into any reader.
  // The writer is canonicalizing too, so concurrent reads remain idempotent.
  if (JSON.stringify(stored) !== JSON.stringify(canonical)) {
    await putRelationships(treeId, canonical);
  }
  return canonical;
}

export async function getEvents(treeId: string): Promise<Event[]> {
  return (await readBlob<Event[]>(BLOB_PATHS.events(treeId))) ?? [];
}

export async function getMediaMetadata(treeId: string): Promise<MediaMetadata[]> {
  return (await readBlob<MediaMetadata[]>(BLOB_PATHS.mediaMetadata(treeId))) ?? [];
}

export async function getAlbums(treeId: string): Promise<Album[]> {
  return (await readBlob<Album[]>(BLOB_PATHS.albums(treeId))) ?? [];
}

export async function getChangeLogs(treeId: string): Promise<ChangeLog[]> {
  return (await readBlob<ChangeLog[]>(BLOB_PATHS.changeLogs(treeId))) ?? [];
}

export async function getCompositeConfig(treeId: string): Promise<CompositeTreeConfig | null> {
  const raw = await readBlob<unknown>(BLOB_PATHS.compositeConfig(treeId));
  if (raw === null) return null;
  return compositeTreeConfigSchema.parse(raw);
}

export async function getCompositePublishedConfig(treeId: string): Promise<CompositeTreeConfig | null> {
  const raw = await readBlob<unknown>(BLOB_PATHS.compositePublishedConfig(treeId));
  if (raw === null) return null;
  return compositeTreeConfigSchema.parse(raw);
}

export async function getTreeCollections(treeId: string): Promise<{ members: Member[]; relationships: Relationship[]; events: Event[]; mediaMetadata: MediaMetadata[] }> {
  const [members, relationships, events, mediaMetadata] = await Promise.all([getMembers(treeId), getRelationships(treeId), getEvents(treeId), getMediaMetadata(treeId)]);
  return { members, relationships, events, mediaMetadata };
}

export async function getTreeCollectionsBatch(treeIds: readonly string[], concurrency = 5): Promise<Map<string, Awaited<ReturnType<typeof getTreeCollections>>>> {
  const result = new Map<string, Awaited<ReturnType<typeof getTreeCollections>>>();
  let cursor = 0;
  await Promise.all(Array.from({ length: Math.min(Math.max(1, concurrency), treeIds.length) }, async () => {
    while (cursor < treeIds.length) { const treeId = treeIds[cursor++]; result.set(treeId, await getTreeCollections(treeId)); }
  }));
  return result;
}

export async function getCompositeAuditLog(treeId: string): Promise<CompositeAuditEntry[]> {
  return (await readBlob<CompositeAuditEntry[]>(BLOB_PATHS.compositeChangeLogs(treeId))) ?? [];
}
