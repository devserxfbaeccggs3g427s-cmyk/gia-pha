import { describe, expect, it } from 'vitest';
import * as fc from 'fast-check';
import type { FamilyTree, Member } from '@/data/types';
import { putAlbums, putEvents, putMembers, putMediaMetadata, putRelationships, putTrees } from '@/lib/blob/writers';
import { exportService } from '@/lib/services/export-service';
import { importService } from '@/lib/services/import-service';

describe('Feature: family-genealogy-management, import/export properties', () => {
  it('Property 13: invalid import error reporting never silently accepts malformed input', async () => {
    await fc.assert(fc.asyncProperty(fc.string({ minLength: 0, maxLength: 120 }), async (value) => {
      const parsed = await importService.parseJSON(Buffer.from(`{ "members": [${value}`));
      expect(parsed.issues.some((issue) => issue.severity === 'ERROR')).toBe(true);
      expect(parsed.issues.every((issue) => issue.line >= 1)).toBe(true);
    }), { numRuns: 100 });
  });

  it('Property 12: JSON export/import preserves every generated tree data field', async () => {
    await fc.assert(fc.asyncProperty(fc.integer({ min: 0, max: 8 }), async (count) => {
      const tree: FamilyTree = {
        id: 'property-tree', kind: 'STANDALONE', name: 'Property Tree', ownerId: 'owner', memberships: [{ userId: 'owner', role: 'ADMIN', createdAt: '2024-01-01T00:00:00.000Z' }],
        createdAt: '2024-01-01T00:00:00.000Z', updatedAt: '2024-01-01T00:00:00.000Z'
      };
      const members: Member[] = Array.from({ length: count }, (_, index) => ({
        id: `member-${index}`, treeId: tree.id, firstName: `First${index}`, lastName: 'Family', fullName: `Family First${index}`,
        gender: index % 2 ? 'FEMALE' : 'MALE', isAlive: true, createdAt: tree.createdAt, updatedAt: tree.updatedAt
      }));
      await putTrees([tree]);
      await putMembers(tree.id, members);
      await putRelationships(tree.id, []);
      await putEvents(tree.id, []);
      await putMediaMetadata(tree.id, []);
      await putAlbums(tree.id, []);
      const parsed = await importService.parseJSON(Buffer.from(await exportService.exportJSON(tree.id)));
      expect(parsed.issues.filter((issue) => issue.severity === 'ERROR')).toEqual([]);
      expect(parsed.tree).toEqual(tree);
      expect(parsed.members).toEqual(members);
      expect(parsed.relationships).toEqual([]);
      expect(parsed.events).toEqual([]);
      expect(parsed.mediaMetadata).toEqual([]);
    }), { numRuns: 100 });
  });
});
