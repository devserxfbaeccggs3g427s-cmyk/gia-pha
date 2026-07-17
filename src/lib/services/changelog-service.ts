import { nanoid } from 'nanoid';
import type { ChangeAction, ChangeLog } from '@/data/types';
import { getChangeLogs as readChangeLogs } from '@/lib/blob/readers';
import { putChangeLogs } from '@/lib/blob/writers';

export interface RecordChangeInput {
  treeId: string;
  userId?: string;
  action: ChangeAction;
  memberId?: string;
  previousData?: Record<string, unknown>;
  newData?: Record<string, unknown>;
  /** A comma-separated list is used for multi-field updates. */
  fieldChanged?: string;
  createdAt?: string;
}

/**
 * Audit trail for member mutations.
 *
 * Logs are append-only from the service's point of view.  A new array is
 * written for every append, which keeps the blob format deterministic and
 * makes the operation easy to test with an in-memory Blob implementation.
 */
export class ChangeLogService {
  async recordChange(input: RecordChangeInput): Promise<ChangeLog> {
    if (!input.treeId) throw new ChangeLogError('INVALID_INPUT', 'treeId is required');
    if (!input.action) throw new ChangeLogError('INVALID_INPUT', 'action is required');

    const changeLog: ChangeLog = {
      id: nanoid(),
      treeId: input.treeId,
      userId: input.userId?.trim() || 'system',
      action: input.action,
      entityType: 'MEMBER',
      ...(input.memberId ? { memberId: input.memberId } : {}),
      ...(input.previousData ? { previousData: clone(input.previousData) } : {}),
      ...(input.newData ? { newData: clone(input.newData) } : {}),
      ...(input.fieldChanged ? { fieldChanged: input.fieldChanged } : {}),
      createdAt: input.createdAt ?? new Date().toISOString()
    };

    const logs = await readChangeLogs(input.treeId);
    await putChangeLogs(input.treeId, [...logs, changeLog]);
    return changeLog;
  }

  // Alias kept intentionally: it reads naturally at call sites and is useful
  // to consumers that treat the service as a generic audit-log writer.
  record(input: RecordChangeInput): Promise<ChangeLog> {
    return this.recordChange(input);
  }

  logChange(input: RecordChangeInput): Promise<ChangeLog> {
    return this.recordChange(input);
  }

  async getChangeLogs(treeId: string): Promise<ChangeLog[]> {
    return readChangeLogs(treeId);
  }

  getLogs(treeId: string): Promise<ChangeLog[]> {
    return this.getChangeLogs(treeId);
  }

  async getMemberChangeLogs(treeId: string, memberId: string): Promise<ChangeLog[]> {
    const logs = await readChangeLogs(treeId);
    return logs.filter((log) => log.memberId === memberId);
  }

  getMemberLogs(treeId: string, memberId: string): Promise<ChangeLog[]> {
    return this.getMemberChangeLogs(treeId, memberId);
  }
}

export class ChangeLogError extends Error {
  constructor(public readonly code: 'INVALID_INPUT', message: string) {
    super(message);
    this.name = 'ChangeLogError';
  }
}

function clone<T>(value: T): T {
  // Data stored in a ChangeLog is JSON-compatible by contract.  Cloning here
  // prevents callers from mutating the audit snapshot after recording it.
  return JSON.parse(JSON.stringify(value)) as T;
}

export const changeLogService = new ChangeLogService();
export const changelogService = changeLogService;
export default changeLogService;

export const recordChange = changeLogService.recordChange.bind(changeLogService);
export const getChangeLogs = changeLogService.getChangeLogs.bind(changeLogService);
export const getMemberChangeLogs = changeLogService.getMemberChangeLogs.bind(changeLogService);
