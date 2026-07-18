import type { BackupSnapshot } from '@/data/types';
import { BLOB_PATHS, deleteBlobs, listBlobs, readBlob, writeBlob } from '@/lib/blob/client';
import { getEvents, getMembers, getMediaMetadata, getRelationships, getTrees } from '@/lib/blob/readers';
import { putEvents, putMembers, putMediaMetadata, putRelationships } from '@/lib/blob/writers';

export const BACKUP_RETENTION_DAYS = 30;
const BACKUP_RETENTION_MS = BACKUP_RETENTION_DAYS * 24 * 60 * 60 * 1000;

export interface BackupInfo {
  treeId: string;
  timestamp: string;
  pathname: string;
  size: number;
  uploadedAt: string;
}

export interface RestoreResult {
  restoredFrom: string;
  safetyBackupTimestamp: string;
  counts: {
    members: number;
    relationships: number;
    events: number;
    mediaMetadata: number;
  };
}

export class BackupServiceError extends Error {
  constructor(
    public readonly code:
      | 'INVALID_INPUT'
      | 'TREE_NOT_FOUND'
      | 'BACKUP_NOT_FOUND'
      | 'BACKUP_EXPIRED'
      | 'INVALID_BACKUP'
      | 'RESTORE_FAILED',
    message: string,
    public readonly cause?: unknown
  ) {
    super(message);
    this.name = 'BackupServiceError';
  }
}

export class BackupService {
  constructor(private readonly clock: () => Date = () => new Date()) {}

  async createBackup(treeId: string, requestedTimestamp?: string): Promise<BackupSnapshot> {
    assertIdentifier(treeId, 'treeId');
    await this.assertTreeExists(treeId);
    const baseTimestamp = requestedTimestamp ?? this.clock().toISOString();
    assertTimestamp(baseTimestamp);
    const timestamp = await this.nextAvailableTimestamp(treeId, baseTimestamp);
    const snapshot = await this.captureSnapshot(treeId, timestamp);
    await writeBlob(BLOB_PATHS.backup(treeId, timestamp), snapshot);
    return snapshot;
  }

  /** Creates at most one snapshot per UTC day to conserve Blob write quota. */
  async ensureDailyBackup(treeId: string): Promise<{ snapshot: BackupSnapshot; created: boolean }> {
    const today = this.clock().toISOString().slice(0, 10);
    const existing = (await this.listBackups(treeId)).find((backup) => backup.timestamp.slice(0, 10) === today);
    if (existing) {
      const snapshot = await this.readBackup(treeId, existing.timestamp);
      return { snapshot, created: false };
    }
    return { snapshot: await this.createBackup(treeId), created: true };
  }

  async listBackups(treeId: string): Promise<BackupInfo[]> {
    assertIdentifier(treeId, 'treeId');
    await this.assertTreeExists(treeId);
    const now = this.clock().getTime();
    const blobs = await listBlobs(BLOB_PATHS.backupPrefix(treeId));

    return blobs
      .map((blob) => {
        const filename = blob.pathname.slice(BLOB_PATHS.backupPrefix(treeId).length);
        const timestamp = filename.endsWith('.json') ? filename.slice(0, -5) : '';
        const time = Date.parse(timestamp);
        if (!timestamp || Number.isNaN(time) || time > now + 5 * 60 * 1000 || now - time > BACKUP_RETENTION_MS) {
          return null;
        }
        return {
          treeId,
          timestamp,
          pathname: blob.pathname,
          size: blob.size,
          uploadedAt: blob.uploadedAt.toISOString()
        } satisfies BackupInfo;
      })
      .filter((backup): backup is BackupInfo => backup !== null)
      .sort((left, right) => right.timestamp.localeCompare(left.timestamp));
  }

  async restoreFromBackup(treeId: string, timestamp: string): Promise<RestoreResult> {
    assertIdentifier(treeId, 'treeId');
    assertTimestamp(timestamp);
    await this.assertTreeExists(treeId);
    this.assertWithinRetention(timestamp);
    const snapshot = await this.readBackup(treeId, timestamp);

    const safetyTimestamp = await this.nextAvailableTimestamp(treeId, this.clock().toISOString());
    const current = await this.captureSnapshot(treeId, safetyTimestamp);
    await writeBlob(BLOB_PATHS.backup(treeId, safetyTimestamp), current);

    try {
      await this.writeSnapshotData(treeId, snapshot);
    } catch (error) {
      const rollback = await Promise.allSettled([
        putMembers(treeId, current.data.members),
        putRelationships(treeId, current.data.relationships),
        putEvents(treeId, current.data.events),
        putMediaMetadata(treeId, current.data.mediaMetadata)
      ]);
      const rollbackFailed = rollback.some((result) => result.status === 'rejected');
      throw new BackupServiceError(
        'RESTORE_FAILED',
        rollbackFailed
          ? 'Restore failed and the automatic rollback was incomplete; use the safety backup to recover'
          : 'Restore failed; the previous data was restored automatically',
        error
      );
    }

    return {
      restoredFrom: timestamp,
      safetyBackupTimestamp: safetyTimestamp,
      counts: {
        members: snapshot.data.members.length,
        relationships: snapshot.data.relationships.length,
        events: snapshot.data.events.length,
        mediaMetadata: snapshot.data.mediaMetadata.length
      }
    };
  }

  async deleteExpiredBackups(treeId: string): Promise<number> {
    assertIdentifier(treeId, 'treeId');
    const now = this.clock().getTime();
    const blobs = await listBlobs(BLOB_PATHS.backupPrefix(treeId));
    const expired = blobs.filter((blob) => {
      const filename = blob.pathname.slice(BLOB_PATHS.backupPrefix(treeId).length).replace(/\.json$/, '');
      const time = Date.parse(filename);
      return Number.isNaN(time) || now - time > BACKUP_RETENTION_MS;
    });
    await deleteBlobs(expired.map((blob) => blob.pathname));
    return expired.length;
  }

  private async captureSnapshot(treeId: string, timestamp: string): Promise<BackupSnapshot> {
    const [members, relationships, events, mediaMetadata] = await Promise.all([
      getMembers(treeId),
      getRelationships(treeId),
      getEvents(treeId),
      getMediaMetadata(treeId)
    ]);
    return { treeId, timestamp, data: { members, relationships, events, mediaMetadata } };
  }

  private async readBackup(treeId: string, timestamp: string): Promise<BackupSnapshot> {
    const value = await readBlob<unknown>(BLOB_PATHS.backup(treeId, timestamp));
    if (value === null) throw new BackupServiceError('BACKUP_NOT_FOUND', 'Backup snapshot not found');
    return parseBackupSnapshot(value, treeId, timestamp);
  }

  private async writeSnapshotData(treeId: string, snapshot: BackupSnapshot): Promise<void> {
    // Keep the ordering deterministic. If any write fails, restoreFromBackup rolls all four files back.
    await putMembers(treeId, snapshot.data.members);
    await putRelationships(treeId, snapshot.data.relationships);
    await putEvents(treeId, snapshot.data.events);
    await putMediaMetadata(treeId, snapshot.data.mediaMetadata);
  }

  private async nextAvailableTimestamp(treeId: string, requested: string): Promise<string> {
    let date = new Date(requested);
    for (let attempt = 0; attempt < 1000; attempt += 1) {
      const timestamp = date.toISOString();
      if ((await readBlob<unknown>(BLOB_PATHS.backup(treeId, timestamp))) === null) return timestamp;
      date = new Date(date.getTime() + 1);
    }
    throw new BackupServiceError('RESTORE_FAILED', 'Could not allocate a unique backup timestamp');
  }

  private assertWithinRetention(timestamp: string): void {
    const age = this.clock().getTime() - Date.parse(timestamp);
    if (age < -5 * 60 * 1000) throw new BackupServiceError('INVALID_INPUT', 'Backup timestamp cannot be in the future');
    if (age > BACKUP_RETENTION_MS) {
      throw new BackupServiceError('BACKUP_EXPIRED', `Only backups from the last ${BACKUP_RETENTION_DAYS} days can be restored`);
    }
  }

  private async assertTreeExists(treeId: string): Promise<void> {
    if (!(await getTrees()).some((tree) => tree.id === treeId)) {
      throw new BackupServiceError('TREE_NOT_FOUND', 'Family tree not found');
    }
  }
}

function parseBackupSnapshot(value: unknown, treeId: string, timestamp: string): BackupSnapshot {
  if (!isRecord(value) || value.treeId !== treeId || value.timestamp !== timestamp || !isRecord(value.data)) {
    throw new BackupServiceError('INVALID_BACKUP', 'Backup metadata does not match the requested tree and timestamp');
  }
  const { data } = value;
  if (!Array.isArray(data.members) || !Array.isArray(data.relationships) || !Array.isArray(data.events) || !Array.isArray(data.mediaMetadata)) {
    throw new BackupServiceError('INVALID_BACKUP', 'Backup snapshot is missing one or more data collections');
  }
  const allBelongToTree = [...data.members, ...data.relationships, ...data.events, ...data.mediaMetadata]
    .every((item) => isRecord(item) && item.treeId === treeId);
  if (!allBelongToTree) throw new BackupServiceError('INVALID_BACKUP', 'Backup contains data from another family tree');
  const validItems = data.members.every(isStoredMember)
    && data.relationships.every(isStoredRelationship)
    && data.events.every(isStoredEvent)
    && data.mediaMetadata.every(isStoredMedia);
  if (!validItems) throw new BackupServiceError('INVALID_BACKUP', 'Backup contains malformed records');
  return value as unknown as BackupSnapshot;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function hasIdentity(value: unknown): value is Record<string, unknown> {
  return isRecord(value) && typeof value.id === 'string' && value.id.length > 0 && typeof value.treeId === 'string';
}

function isStoredMember(value: unknown): boolean {
  return hasIdentity(value)
    && typeof value.firstName === 'string'
    && typeof value.lastName === 'string'
    && typeof value.fullName === 'string'
    && ['MALE', 'FEMALE', 'OTHER'].includes(String(value.gender))
    && typeof value.isAlive === 'boolean';
}

function isStoredRelationship(value: unknown): boolean {
  return hasIdentity(value)
    && typeof value.sourceMemberId === 'string'
    && typeof value.targetMemberId === 'string'
    && ['PARENT_CHILD', 'SPOUSE', 'SIBLING', 'ADOPTED', 'CUSTOM'].includes(String(value.type));
}

function isStoredEvent(value: unknown): boolean {
  return hasIdentity(value)
    && typeof value.title === 'string'
    && typeof value.eventDate === 'string'
    && Array.isArray(value.memberIds)
    && Array.isArray(value.mediaIds);
}

function isStoredMedia(value: unknown): boolean {
  return hasIdentity(value)
    && typeof value.filename === 'string'
    && typeof value.originalName === 'string'
    && typeof value.mimeType === 'string'
    && typeof value.fileSize === 'number'
    && typeof value.blobUrl === 'string';
}

function assertIdentifier(value: string, field: string): void {
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(value)) {
    throw new BackupServiceError('INVALID_INPUT', `${field} is invalid`);
  }
}

function assertTimestamp(value: string): void {
  if (Number.isNaN(Date.parse(value)) || new Date(value).toISOString() !== value) {
    throw new BackupServiceError('INVALID_INPUT', 'timestamp must be a canonical ISO date-time');
  }
}

export const backupService = new BackupService();
export default backupService;

export const createBackup = backupService.createBackup.bind(backupService);
export const restoreFromBackup = backupService.restoreFromBackup.bind(backupService);
export const listBackups = backupService.listBackups.bind(backupService);
