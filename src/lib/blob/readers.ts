import { compositeTreeConfigSchema } from '@/data/schemas';
import { familyTreeSchema } from '@/data/schemas';
import type {
  Album,
  ChangeLog,
  CompositeAuditEntry,
  CompositeTreeConfig,
  Event,
  FamilyTree,
  MediaMetadata,
  Member,
  Relationship,
  User,
} from '@/data/types';
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

/**
 * Read and Zod-validate the CompositeTreeConfig blob.
 * Returns null when the blob does not exist (config has not been initialized).
 * Throws a Zod error when the stored JSON fails schema validation.
 */
export async function getCompositeConfig(treeId: string): Promise<CompositeTreeConfig | null> {
  const raw = await readBlob<unknown>(BLOB_PATHS.compositeConfig(treeId));
  if (raw === null) return null;
  return compositeTreeConfigSchema.parse(raw);
}

/**
 * Read the composite audit log for a tree.
 * Returns an empty array when the blob does not exist.
 */
export async function getCompositeAuditLog(treeId: string): Promise<CompositeAuditEntry[]> {
  return (await readBlob<CompositeAuditEntry[]>(BLOB_PATHS.compositeChangeLogs(treeId))) ?? [];
}
