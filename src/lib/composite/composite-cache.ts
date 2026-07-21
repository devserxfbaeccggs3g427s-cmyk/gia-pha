import { createHash } from 'crypto';
import type { ResolvedTreeData, TreeRole } from '@/data/types';
import { BLOB_PATHS, readBlob, writeBlob } from '@/lib/blob/client';

export interface CompositeAudience {
  userId: string;
  permissions: Array<{ sourceTreeId: string; role: TreeRole | null; readable: boolean }>;
  privacy: string;
}

export function compositeCacheKey(treeId: string, audience: CompositeAudience, revision: number, sourceVersions: string[]): string {
  const permissions = [...audience.permissions].sort((a, b) => a.sourceTreeId.localeCompare(b.sourceTreeId));
  return createHash('sha256').update(JSON.stringify({ treeId, userId: audience.userId, permissions, privacy: audience.privacy, revision, sourceVersions: [...sourceVersions].sort() })).digest('base64url').slice(0, 32);
}

export async function readCompositeCache(treeId: string, audienceHash: string): Promise<ResolvedTreeData | null> {
  return readBlob<ResolvedTreeData>(BLOB_PATHS.compositeManifest(treeId, audienceHash));
}

export async function writeCompositeCache(data: ResolvedTreeData, audience: CompositeAudience): Promise<string> {
  const key = compositeCacheKey(data.tree.id, audience, data.configRevision, data.sourceManifest.map((source) => `${source.sourceTreeId}:${source.status}:${source.version}`));
  await writeBlob(BLOB_PATHS.compositeManifest(data.tree.id, key), data);
  return key;
}

export function redactLivingDetails(data: ResolvedTreeData, allowedSourceIds: ReadonlySet<string> = new Set()): ResolvedTreeData {
  return {
    ...data,
    members: data.members.map((member) => member.isAlive && !member.sourceReferences.some((reference) => allowedSourceIds.has(reference.treeId)) ? {
      ...member,
      dateOfBirth: undefined,
      placeOfBirth: undefined,
      currentAddress: undefined,
      phone: undefined,
      email: undefined,
      occupation: undefined,
      education: undefined,
      biography: undefined,
      achievements: undefined,
      notes: undefined,
      avatarMediaId: undefined,
      avatarUrl: undefined
    } : member)
  };
}
