import { describe, expect, it } from 'vitest';
import { getEvents, getMembers, getMediaMetadata, getRelationships } from '@/lib/blob/readers';
import { putEvents, putMembers, putMediaMetadata, putRelationships, putTrees } from '@/lib/blob/writers';
import { BackupService } from '@/lib/services/backup-service';
import { buildEvent, buildFamilyTree, buildMember, buildMediaMetadata, buildRelationship } from '../../utils/factories';

const NOW = new Date('2026-07-18T10:30:00.000Z');

describe('BackupService', () => {
  it('creates, lists and restores a complete timestamped snapshot', async () => {
    const tree = buildFamilyTree({ id: 'tree-backup' });
    const member = buildMember({ id: 'member-original', treeId: tree.id });
    const child = buildMember({ id: 'member-child', treeId: tree.id });
    const relationship = buildRelationship({ treeId: tree.id, sourceMemberId: member.id, targetMemberId: child.id });
    const event = buildEvent({ treeId: tree.id, memberIds: [member.id] });
    const media = buildMediaMetadata({ treeId: tree.id, memberIds: [member.id], eventIds: [event.id] });
    await putTrees([tree]);
    await putMembers(tree.id, [member, child]);
    await putRelationships(tree.id, [relationship]);
    await putEvents(tree.id, [event]);
    await putMediaMetadata(tree.id, [media]);
    const service = new BackupService(() => new Date(NOW));

    const backup = await service.createBackup(tree.id);
    expect(backup).toEqual({
      treeId: tree.id,
      timestamp: NOW.toISOString(),
      data: { members: [member, child], relationships: [relationship], events: [event], mediaMetadata: [media] }
    });
    await expect(service.listBackups(tree.id)).resolves.toEqual([
      expect.objectContaining({ treeId: tree.id, timestamp: NOW.toISOString(), pathname: `backups/${tree.id}/${NOW.toISOString()}.json` })
    ]);

    await putMembers(tree.id, [buildMember({ id: 'replacement', treeId: tree.id })]);
    await putRelationships(tree.id, []);
    await putEvents(tree.id, []);
    await putMediaMetadata(tree.id, []);

    const restored = await service.restoreFromBackup(tree.id, backup.timestamp);
    expect(restored).toMatchObject({ restoredFrom: backup.timestamp, safetyBackupTimestamp: '2026-07-18T10:30:00.001Z' });
    await expect(getMembers(tree.id)).resolves.toEqual([member, child]);
    await expect(getRelationships(tree.id)).resolves.toEqual([relationship]);
    await expect(getEvents(tree.id)).resolves.toEqual([event]);
    await expect(getMediaMetadata(tree.id)).resolves.toEqual([media]);
  });

  it('deduplicates automatic daily backups and rejects snapshots older than 30 days', async () => {
    const tree = buildFamilyTree({ id: 'tree-retention' });
    await putTrees([tree]);
    const service = new BackupService(() => new Date(NOW));
    const first = await service.ensureDailyBackup(tree.id);
    const second = await service.ensureDailyBackup(tree.id);
    expect(first.created).toBe(true);
    expect(second.created).toBe(false);
    await expect(service.listBackups(tree.id)).resolves.toHaveLength(1);

    const expiredTimestamp = new Date(NOW.getTime() - 31 * 24 * 60 * 60 * 1000).toISOString();
    await service.createBackup(tree.id, expiredTimestamp);
    await expect(service.restoreFromBackup(tree.id, expiredTimestamp)).rejects.toMatchObject({ code: 'BACKUP_EXPIRED' });
    await expect(service.deleteExpiredBackups(tree.id)).resolves.toBe(1);
  });

  it('rejects path-like identifiers before accessing storage', async () => {
    const service = new BackupService(() => new Date(NOW));
    await expect(service.createBackup('../another-tree')).rejects.toMatchObject({ code: 'INVALID_INPUT' });
  });
});
