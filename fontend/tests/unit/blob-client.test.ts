import { get, put } from '@vercel/blob';
import { describe, expect, it, vi } from 'vitest';
import { BLOB_PATHS, readBlob, writeBlob } from '@/lib/blob/client';
import { getMembers } from '@/lib/blob/readers';
import { putMembers } from '@/lib/blob/writers';
import { buildMember } from '../utils/factories';

describe('blob data layer', () => {
  it('returns null for a missing blob', async () => {
    await expect(readBlob(BLOB_PATHS.trees())).resolves.toBeNull();
  });

  it('round-trips typed JSON blobs', async () => {
    const path = BLOB_PATHS.trees();
    const data = [{ id: 'tree_1', name: 'Tree' }];

    await writeBlob(path, data);

    await expect(readBlob(path)).resolves.toEqual(data);
    expect(vi.mocked(put)).toHaveBeenCalledWith(
      path,
      expect.any(String),
      expect.objectContaining({ access: 'private', allowOverwrite: true })
    );
    expect(vi.mocked(get)).toHaveBeenCalledWith(
      path,
      expect.objectContaining({ access: 'private', useCache: false })
    );
  });

  it('uses typed member helpers with empty fallback', async () => {
    await expect(getMembers('tree_1')).resolves.toEqual([]);

    const members = [buildMember({ treeId: 'tree_1' })];
    await putMembers('tree_1', members);

    await expect(getMembers('tree_1')).resolves.toEqual(members);
  });
});
