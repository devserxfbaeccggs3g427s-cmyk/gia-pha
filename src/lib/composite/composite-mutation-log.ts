import { nanoid } from 'nanoid';
import { compositeTreeConfigSchema } from '@/data/schemas';
import type { CompositeAuditAction, CompositeTreeConfig, SourceReference } from '@/data/types';
import { BLOB_PATHS, listBlobs, readBlob, writeImmutableBlob } from '@/lib/blob/client';

export interface CompositeMutationRecord {
  id: string;
  treeId: string;
  expectedRevision: number;
  actorId: string;
  action: CompositeAuditAction;
  sourceReferences: SourceReference[];
  previousConfig: CompositeTreeConfig;
  nextConfig: CompositeTreeConfig;
  createdAt: string;
}

export interface CompositeMutationFold {
  config: CompositeTreeConfig;
  accepted: CompositeMutationRecord[];
  conflicts: CompositeMutationRecord[];
}

export async function appendCompositeMutation(input: Omit<CompositeMutationRecord, 'id'>): Promise<CompositeMutationRecord> {
  const record = { ...input, id: nanoid() };
  await writeImmutableBlob(BLOB_PATHS.compositeMutation(record.treeId, record.id), record);
  return record;
}

export async function foldCompositeMutations(treeId: string, base: CompositeTreeConfig): Promise<CompositeMutationFold> {
  const blobs = await listBlobs(BLOB_PATHS.compositeMutationPrefix(treeId));
  const records = (await Promise.all(blobs.map((blob) => readBlob<CompositeMutationRecord>(blob.pathname))))
    .filter((record): record is CompositeMutationRecord => record !== null && record.treeId === treeId)
    .sort(compareMutations);
  let config = compositeTreeConfigSchema.parse(base);
  const accepted: CompositeMutationRecord[] = [];
  const conflicts: CompositeMutationRecord[] = [];
  for (const record of records) {
    if (record.expectedRevision !== config.revision || record.nextConfig.revision !== record.expectedRevision + 1 || !sameConfig(record.previousConfig, config)) {
      conflicts.push(record);
      continue;
    }
    const parsed = compositeTreeConfigSchema.safeParse(record.nextConfig);
    if (!parsed.success) {
      conflicts.push(record);
      continue;
    }
    config = parsed.data;
    accepted.push(record);
  }
  return { config, accepted, conflicts };
}

export async function readFoldedCompositeConfig(treeId: string): Promise<CompositeMutationFold | null> {
  const base = await readBlob<unknown>(BLOB_PATHS.compositeConfig(treeId));
  if (base === null) return null;
  return foldCompositeMutations(treeId, compositeTreeConfigSchema.parse(base));
}

/**
 * Compaction protocol: while mutations are externally quiesced, fold the full
 * prefix, write the folded config as the new base, then delete exactly the
 * mutation paths observed by that fold. Never compact concurrently with API
 * mutations because Blob provides neither transactions nor prefix snapshots;
 * normal request handling therefore only appends and folds immutable records.
 */
export async function planCompositeMutationCompaction(treeId: string): Promise<{ base: CompositeTreeConfig; mutationPaths: string[] } | null> {
  const folded = await readFoldedCompositeConfig(treeId);
  if (!folded) return null;
  const blobs = await listBlobs(BLOB_PATHS.compositeMutationPrefix(treeId));
  return { base: folded.config, mutationPaths: blobs.map((blob) => blob.pathname).sort() };
}

function compareMutations(left: CompositeMutationRecord, right: CompositeMutationRecord): number {
  return left.expectedRevision - right.expectedRevision || left.createdAt.localeCompare(right.createdAt) || left.id.localeCompare(right.id);
}

function sameConfig(left: CompositeTreeConfig, right: CompositeTreeConfig): boolean {
  return JSON.stringify(left) === JSON.stringify(right);
}
