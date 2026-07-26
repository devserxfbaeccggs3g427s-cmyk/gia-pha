import fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import { getEvents, getMembers, getMediaMetadata, getRelationships } from '@/lib/blob/readers';
import { putEvents, putMembers, putMediaMetadata, putRelationships, putTrees } from '@/lib/blob/writers';
import { BackupService } from '@/lib/services/backup-service';
import { mockBlobStorage } from '../../utils/mock-blob-storage';
import { buildEvent, buildFamilyTree, buildMediaMetadata, buildMember, buildRelationship } from '../../utils/factories';

describe('Property 14: Backup/Restore Round-Trip', () => {
  it('restores every data collection exactly for arbitrary valid tree snapshots', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.array(fc.record({ name: fc.string({ minLength: 1, maxLength: 40 }), alive: fc.boolean() }), { maxLength: 12 }),
        fc.array(fc.string({ minLength: 1, maxLength: 50 }), { maxLength: 8 }),
        async (memberInputs, eventTitles) => {
          mockBlobStorage.clear();
          const tree = buildFamilyTree({ id: 'tree-property-backup' });
          const members = memberInputs.map((input, index) => buildMember({
            id: `member-${index}`,
            treeId: tree.id,
            fullName: input.name,
            isAlive: input.alive,
            ...(input.alive ? {} : { dateOfDeath: '2020-01-01' })
          }));
          const relationships = members.slice(1).map((member, index) => buildRelationship({
            id: `relationship-${index}`,
            treeId: tree.id,
            sourceMemberId: members[index].id,
            targetMemberId: member.id
          }));
          const events = eventTitles.map((title, index) => buildEvent({ id: `event-${index}`, treeId: tree.id, title }));
          const mediaMetadata = events.map((event, index) => buildMediaMetadata({
            id: `media-${index}`,
            treeId: tree.id,
            eventIds: [event.id],
            memberIds: members[index] ? [members[index].id] : []
          }));
          await putTrees([tree]);
          await putMembers(tree.id, members);
          await putRelationships(tree.id, relationships);
          await putEvents(tree.id, events);
          await putMediaMetadata(tree.id, mediaMetadata);

          const service = new BackupService(() => new Date('2026-07-18T10:30:00.000Z'));
          const snapshot = await service.createBackup(tree.id);
          await putMembers(tree.id, []);
          await putRelationships(tree.id, []);
          await putEvents(tree.id, []);
          await putMediaMetadata(tree.id, []);
          await service.restoreFromBackup(tree.id, snapshot.timestamp);

          expect(await getMembers(tree.id)).toEqual(members);
          expect(await getRelationships(tree.id)).toEqual(relationships);
          expect(await getEvents(tree.id)).toEqual(events);
          expect(await getMediaMetadata(tree.id)).toEqual(mediaMetadata);
        }
      ),
      { numRuns: 40 }
    );
  });
});
