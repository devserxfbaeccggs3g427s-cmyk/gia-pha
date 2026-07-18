import { nanoid } from 'nanoid';
import { z } from 'zod';
import {
  eventTypeSchema,
  genderSchema,
  marriageStatusSchema,
  relationTypeSchema,
  treeRoleSchema
} from '@/data/schemas';
import type { Album, Event, FamilyTree, MediaMetadata, Member, Relationship } from '@/data/types';
import { parseGEDCOMContent } from '@/lib/algorithms/gedcom-parser';
import { getAlbums, getEvents, getMediaMetadata, getMembers, getRelationships, getTrees } from '@/lib/blob/readers';
import { putAlbums, putEvents, putMediaMetadata, putMembers, putRelationships } from '@/lib/blob/writers';
import type {
  FamilyTreeExportDocument,
  ImportIssue,
  ImportOptions,
  ImportPreview,
  ImportResult,
  ParsedCSV,
  ParsedGEDCOM,
  ParsedImportData,
  ParsedJSON
} from '@/types/import-export';

const MAX_IMPORT_BYTES = 25 * 1024 * 1024;
const isoDate = z.string().refine((value) => !Number.isNaN(Date.parse(value)), 'Must be a valid ISO date');
const id = z.string().trim().min(1).max(300);

const memberSchema = z.object({
  id,
  treeId: id,
  firstName: z.string().min(1).max(100),
  lastName: z.string().min(1).max(100),
  fullName: z.string().min(1).max(200),
  nickname: z.string().max(100).optional(),
  gender: genderSchema,
  dateOfBirth: isoDate.optional(),
  dateOfDeath: isoDate.optional(),
  placeOfBirth: z.string().max(200).optional(),
  currentAddress: z.string().max(500).optional(),
  phone: z.string().max(20).optional(),
  email: z.string().email().optional(),
  occupation: z.string().max(200).optional(),
  education: z.string().max(200).optional(),
  biography: z.string().max(5000).optional(),
  achievements: z.string().max(2000).optional(),
  notes: z.string().max(2000).optional(),
  avatarMediaId: id.optional(),
  avatarUrl: z.string().url().optional(),
  generation: z.number().int().min(0).optional(),
  isAlive: z.boolean(),
  createdAt: isoDate,
  updatedAt: isoDate
}).strict();

const relationshipSchema = z.object({
  id,
  treeId: id,
  sourceMemberId: id,
  targetMemberId: id,
  type: relationTypeSchema,
  customType: z.string().max(100).optional(),
  marriageDate: isoDate.optional(),
  divorceDate: isoDate.optional(),
  marriageStatus: marriageStatusSchema.optional(),
  createdAt: isoDate
}).strict();

const eventSchema = z.object({
  id,
  treeId: id,
  type: eventTypeSchema,
  customType: z.string().max(100).optional(),
  title: z.string().min(1).max(200),
  eventDate: isoDate,
  location: z.string().max(300).optional(),
  description: z.string().max(2000).optional(),
  memberIds: z.array(id),
  mediaIds: z.array(id),
  createdAt: isoDate,
  updatedAt: isoDate
}).strict();

const mediaSchema = z.object({
  id,
  treeId: id,
  memberId: id.optional(),
  eventId: id.optional(),
  memberIds: z.array(id).optional(),
  eventIds: z.array(id).optional(),
  albumId: id.optional(),
  filename: z.string().min(1).max(255),
  originalName: z.string().min(1).max(255),
  mimeType: z.string().min(1).max(100),
  fileSize: z.number().int().nonnegative(),
  blobUrl: z.string().url(),
  thumbnailUrl: z.string().url().optional(),
  contentUrl: z.string().optional(),
  thumbnailContentUrl: z.string().optional(),
  caption: z.string().max(500).optional(),
  takenAt: isoDate.optional(),
  uploadedAt: isoDate
}).strict();

const albumSchema = z.object({
  id,
  treeId: id,
  title: z.string().min(1).max(200),
  description: z.string().max(2000).optional(),
  createdAt: isoDate,
  updatedAt: isoDate.optional()
}).strict();

const treeSchema = z.object({
  id,
  name: z.string().min(1).max(200),
  description: z.string().max(2000).optional(),
  ownerId: id,
  memberships: z.array(z.object({ userId: id, role: treeRoleSchema, createdAt: isoDate }).strict()),
  createdAt: isoDate,
  updatedAt: isoDate
}).strict();

export class ImportServiceError extends Error {
  constructor(
    public readonly code: 'INVALID_INPUT' | 'INVALID_IMPORT' | 'TREE_NOT_FOUND' | 'WRITE_FAILED',
    message: string,
    public readonly issues: ImportIssue[] = []
  ) {
    super(message);
    this.name = 'ImportServiceError';
  }
}

export class ImportService {
  async parseGEDCOM(file: Buffer | Uint8Array | string): Promise<ParsedGEDCOM> {
    return validateParsed(parseGEDCOMContent(decodeFile(file)), undefined) as ParsedGEDCOM;
  }

  async parseJSON(file: Buffer | Uint8Array | string): Promise<ParsedJSON> {
    const content = decodeFile(file);
    let value: unknown;
    try {
      value = JSON.parse(stripBom(content));
    } catch (error) {
      const location = jsonErrorLocation(error, content);
      return emptyParsed('JSON', [{
        severity: 'ERROR',
        code: 'SYNTAX_ERROR',
        message: error instanceof Error ? error.message : 'Malformed JSON',
        line: location.line,
        column: location.column
      }]);
    }

    if (!isRecord(value)) {
      return emptyParsed('JSON', [errorIssue('JSON root must be an object', 1, '$')]);
    }
    const root = isRecord(value.data) ? value.data : value;
    const parsed: ParsedJSON = {
      format: 'JSON',
      version: '1.0',
      ...(isRecord(value.tree) ? { tree: value.tree as unknown as FamilyTree } : {}),
      members: arrayOrIssue(root, 'members') as Member[],
      relationships: arrayOrIssue(root, 'relationships') as Relationship[],
      events: arrayOrIssue(root, 'events') as Event[],
      mediaMetadata: (Array.isArray(root.mediaMetadata)
        ? root.mediaMetadata
        : Array.isArray(root.media) ? root.media : []) as MediaMetadata[],
      albums: (Array.isArray(root.albums) ? root.albums : []) as Album[],
      issues: []
    };
    for (const key of ['members', 'relationships', 'events'] as const) {
      if (!Array.isArray(root[key])) parsed.issues.push(errorIssue(`"${key}" must be an array`, lineOfKey(content, key), key));
    }
    if (root.mediaMetadata !== undefined && !Array.isArray(root.mediaMetadata)) {
      parsed.issues.push(errorIssue('"mediaMetadata" must be an array', lineOfKey(content, 'mediaMetadata'), 'mediaMetadata'));
    }
    if (root.albums !== undefined && !Array.isArray(root.albums)) {
      parsed.issues.push(errorIssue('"albums" must be an array', lineOfKey(content, 'albums'), 'albums'));
    }
    return validateParsed(parsed, content) as ParsedJSON;
  }

  async parseCSV(file: Buffer | Uint8Array | string): Promise<ParsedCSV> {
    const content = decodeFile(file);
    const issues: ImportIssue[] = [];
    const rows = parseCsvRows(stripBom(content), issues);
    if (rows.length === 0) return emptyParsed('CSV', [errorIssue('CSV file is empty', 1)]);
    const header = rows[0].values.map(normalizeHeader);
    const now = new Date().toISOString();
    const members: Member[] = [];
    const relationships: Relationship[] = [];
    const seenIds = new Set<string>();

    rows.slice(1).forEach((row) => {
      if (row.values.every((value) => !value.trim())) return;
      if (row.values.length > header.length) {
        issues.push(errorIssue('CSV row has more fields than the header', row.line));
        return;
      }
      const values = Object.fromEntries(header.map((key, index) => [key, row.values[index]?.trim() ?? '']));
      // A relationship CSV can be supplied as a second, compact CSV file
      // using sourceMemberId,targetMemberId,type columns. Supporting it here
      // keeps CSV useful for bulk relationship maintenance without requiring
      // a bespoke upload endpoint.
      const sourceMemberId = read(values, 'sourcememberid', 'parentid');
      const targetMemberId = read(values, 'targetmemberid', 'childid');
      if (sourceMemberId || targetMemberId) {
        if (!sourceMemberId || !targetMemberId) {
          issues.push(errorIssue('CSV relationship requires sourceMemberId and targetMemberId', row.line, 'relationship'));
          return;
        }
        const relationType = read(values, 'relationshiptype', 'relationtype', 'type').toUpperCase() || 'PARENT_CHILD';
        const relationship = compact({
          id: read(values, 'id', 'relationshipid') || `csv-relationship-${row.line}`,
          treeId: read(values, 'treeid') || 'imported',
          sourceMemberId,
          targetMemberId,
          type: relationType,
          customType: read(values, 'customtype'),
          createdAt: read(values, 'createdat') || now
        }) as unknown as Relationship;
        relationships.push(relationship);
        return;
      }
      const memberId = read(values, 'id', 'memberid') || `csv-member-${row.line}`;
      if (seenIds.has(memberId)) {
        issues.push({ severity: 'ERROR', code: 'DUPLICATE_ID', message: `Duplicate member id "${memberId}"`, line: row.line, path: 'id' });
        return;
      }
      seenIds.add(memberId);
      const fullName = read(values, 'fullname', 'name');
      const firstName = read(values, 'firstname', 'givenname') || inferFirstName(fullName);
      const lastName = read(values, 'lastname', 'surname', 'familyname') || inferLastName(fullName);
      if (!fullName && !(firstName && lastName)) {
        issues.push(errorIssue('CSV member requires fullName or both firstName and lastName', row.line, 'fullName'));
        return;
      }
      const gender = csvGender(read(values, 'gender', 'sex'), row.line, issues);
      const dateOfBirth = read(values, 'dateofbirth', 'birthdate', 'dob');
      const dateOfDeath = read(values, 'dateofdeath', 'deathdate', 'dod');
      const aliveValue = read(values, 'isalive', 'alive').toLowerCase();
      const isAlive = aliveValue ? ['true', '1', 'yes', 'y', 'có', 'co'].includes(aliveValue) : !dateOfDeath;
      members.push(compact({
        id: memberId,
        treeId: read(values, 'treeid') || 'imported',
        firstName: firstName || fullName,
        lastName: lastName || '-',
        fullName: fullName || `${lastName} ${firstName}`.trim(),
        nickname: read(values, 'nickname'),
        gender,
        dateOfBirth,
        dateOfDeath,
        placeOfBirth: read(values, 'placeofbirth', 'birthplace'),
        currentAddress: read(values, 'currentaddress', 'address'),
        phone: read(values, 'phone'),
        email: read(values, 'email'),
        occupation: read(values, 'occupation'),
        education: read(values, 'education'),
        biography: read(values, 'biography'),
        achievements: read(values, 'achievements'),
        notes: read(values, 'notes'),
        avatarMediaId: read(values, 'avatarmediaid'),
        avatarUrl: read(values, 'avatarurl'),
        generation: parseOptionalInteger(read(values, 'generation')),
        isAlive,
        createdAt: read(values, 'createdat') || now,
        updatedAt: read(values, 'updatedat') || now
      }) as Member);
    });

    return validateParsed({
      format: 'CSV', version: '1.0', members, relationships, events: [], mediaMetadata: [], albums: [], issues
    }, content, new Map(rows.slice(1).map((row, index) => [`members.${index}`, row.line]))) as ParsedCSV;
  }

  async preview(parsed: ParsedImportData): Promise<ImportPreview> {
    const normalized = validateParsed(parsed, undefined);
    const errors = normalized.issues.filter((issue) => issue.severity === 'ERROR');
    const warnings = normalized.issues.filter((issue) => issue.severity === 'WARNING');
    return {
      format: normalized.format,
      valid: errors.length === 0,
      counts: {
        members: normalized.members.length,
        relationships: normalized.relationships.length,
        events: normalized.events.length,
        media: normalized.mediaMetadata.length,
        albums: normalized.albums.length,
        errors: errors.length,
        warnings: warnings.length
      },
      sampleMembers: normalized.members.slice(0, 10),
      issues: normalized.issues
    };
  }

  async execute(treeId: string, parsed: ParsedImportData, options: ImportOptions = {}): Promise<ImportResult> {
    if (!treeId?.trim()) throw new ImportServiceError('INVALID_INPUT', 'treeId is required');
    const normalized = validateParsed(parsed, undefined);
    const errors = normalized.issues.filter((issue) => issue.severity === 'ERROR');
    if (errors.length) throw new ImportServiceError('INVALID_IMPORT', 'Import contains invalid data', errors);
    if (!(await getTrees()).some((tree) => tree.id === treeId)) {
      throw new ImportServiceError('TREE_NOT_FOUND', 'Family tree not found');
    }
    const mode = options.mode ?? 'APPEND';
    const strategy = options.conflictStrategy ?? 'REGENERATE';
    const previous = await readTreeCollections(treeId);
    const incoming = retargetAndRemap(normalized, treeId, mode === 'APPEND' ? previous : undefined, strategy);
    const next = mode === 'REPLACE' ? incoming.data : mergeCollections(previous, incoming.data, strategy);
    const combinedIssues = validateReferences(next);
    if (combinedIssues.length) throw new ImportServiceError('INVALID_IMPORT', 'Import would create broken references', combinedIssues);

    try {
      await writeTreeCollections(treeId, next);
    } catch (error) {
      await rollbackTreeCollections(treeId, previous);
      const wrapped = new ImportServiceError('WRITE_FAILED', 'Import could not be committed; previous data was restored');
      Object.defineProperty(wrapped, 'cause', { value: error, enumerable: false });
      throw wrapped;
    }
    return {
      treeId,
      mode,
      imported: {
        members: incoming.data.members.length,
        relationships: incoming.data.relationships.length,
        events: incoming.data.events.length,
        media: incoming.data.mediaMetadata.length,
        albums: incoming.data.albums.length
      },
      skipped: incoming.skipped,
      warnings: normalized.issues.filter((issue) => issue.severity === 'WARNING')
    };
  }
}

interface TreeCollections {
  members: Member[];
  relationships: Relationship[];
  events: Event[];
  mediaMetadata: MediaMetadata[];
  albums: Album[];
}

function validateParsed(
  parsed: ParsedImportData,
  content?: string,
  lineOverrides = new Map<string, number>()
): ParsedImportData {
  const issues = [...parsed.issues];
  validateArray(parsed.members, memberSchema, 'members', issues, content, lineOverrides);
  validateArray(parsed.relationships, relationshipSchema, 'relationships', issues, content, lineOverrides);
  validateArray(parsed.events, eventSchema, 'events', issues, content, lineOverrides);
  validateArray(parsed.mediaMetadata, mediaSchema, 'mediaMetadata', issues, content, lineOverrides);
  validateArray(parsed.albums, albumSchema, 'albums', issues, content, lineOverrides);
  if (parsed.tree) validateValue(parsed.tree, treeSchema, 'tree', issues, content, lineOverrides);
  issues.push(...validateReferences(parsed));
  return { ...parsed, issues: uniqueIssues(issues) };
}

function validateArray(
  values: unknown[],
  schema: z.ZodTypeAny,
  name: string,
  issues: ImportIssue[],
  content?: string,
  overrides = new Map<string, number>()
): void {
  values.forEach((value, index) => validateValue(value, schema, `${name}.${index}`, issues, content, overrides));
  const ids = new Map<string, number>();
  values.forEach((value, index) => {
    const valueId = isRecord(value) && typeof value.id === 'string' ? value.id : undefined;
    if (!valueId) return;
    if (ids.has(valueId)) issues.push({
      severity: 'ERROR', code: 'DUPLICATE_ID', message: `Duplicate ${name} id "${valueId}"`,
      line: entityLine(content, name, valueId, index), path: `${name}.${index}.id`
    });
    ids.set(valueId, index);
  });
}

function validateValue(
  value: unknown,
  schema: z.ZodTypeAny,
  path: string,
  issues: ImportIssue[],
  content?: string,
  overrides = new Map<string, number>()
): void {
  const result = schema.safeParse(value);
  if (result.success) return;
  for (const zodIssue of result.error.issues) {
    const issuePath = `${path}${zodIssue.path.length ? `.${zodIssue.path.join('.')}` : ''}`;
    const idValue = isRecord(value) && typeof value.id === 'string' ? value.id : undefined;
    issues.push({
      severity: 'ERROR',
      code: zodIssue.code === 'invalid_type' && 'received' in zodIssue && zodIssue.received === 'undefined'
        ? 'MISSING_FIELD' : 'INVALID_VALUE',
      message: zodIssue.message,
      line: overrides.get(path) ?? entityLine(content, path.split('.')[0], idValue, Number(path.split('.')[1] ?? 0)),
      path: issuePath
    });
  }
}

function validateReferences(data: TreeCollections | ParsedImportData): ImportIssue[] {
  const issues: ImportIssue[] = [];
  const memberIds = new Set(data.members.map((item) => item.id));
  const eventIds = new Set(data.events.map((item) => item.id));
  const mediaIds = new Set(data.mediaMetadata.map((item) => item.id));
  const albumIds = new Set(data.albums.map((item) => item.id));
  data.members.forEach((member, index) => {
    if (member.avatarMediaId && !mediaIds.has(member.avatarMediaId)) {
      issues.push(referenceIssue(`members.${index}.avatarMediaId`, member.avatarMediaId));
    }
  });
  data.relationships.forEach((relationship, index) => {
    for (const [field, referencedId] of [['sourceMemberId', relationship.sourceMemberId], ['targetMemberId', relationship.targetMemberId]] as const) {
      if (!memberIds.has(referencedId)) issues.push(referenceIssue(`relationships.${index}.${field}`, referencedId));
    }
    if (relationship.sourceMemberId === relationship.targetMemberId) {
      issues.push(errorIssue('Relationship cannot reference the same member on both sides', index + 2, `relationships.${index}`));
    }
  });
  data.events.forEach((event, index) => {
    event.memberIds.forEach((value) => { if (!memberIds.has(value)) issues.push(referenceIssue(`events.${index}.memberIds`, value)); });
    event.mediaIds.forEach((value) => { if (!mediaIds.has(value)) issues.push(referenceIssue(`events.${index}.mediaIds`, value)); });
  });
  data.mediaMetadata.forEach((media, index) => {
    [...(media.memberIds ?? []), ...(media.memberId ? [media.memberId] : [])].forEach((value) => {
      if (!memberIds.has(value)) issues.push(referenceIssue(`mediaMetadata.${index}.memberIds`, value));
    });
    [...(media.eventIds ?? []), ...(media.eventId ? [media.eventId] : [])].forEach((value) => {
      if (!eventIds.has(value)) issues.push(referenceIssue(`mediaMetadata.${index}.eventIds`, value));
    });
    if (media.albumId && !albumIds.has(media.albumId)) issues.push(referenceIssue(`mediaMetadata.${index}.albumId`, media.albumId));
  });
  return issues;
}

function retargetAndRemap(
  parsed: ParsedImportData,
  treeId: string,
  existing: TreeCollections | undefined,
  strategy: NonNullable<ImportOptions['conflictStrategy']>
): { data: TreeCollections; skipped: number } {
  const incoming: TreeCollections = {
    members: parsed.members,
    relationships: parsed.relationships,
    events: parsed.events,
    mediaMetadata: parsed.mediaMetadata,
    albums: parsed.albums
  };
  const maps = {
    members: idMap(incoming.members, existing?.members, strategy),
    relationships: idMap(incoming.relationships, existing?.relationships, strategy),
    events: idMap(incoming.events, existing?.events, strategy),
    mediaMetadata: idMap(incoming.mediaMetadata, existing?.mediaMetadata, strategy),
    albums: idMap(incoming.albums, existing?.albums, strategy)
  };
  const skippedIds = {
    members: conflictingIds(incoming.members, existing?.members, strategy),
    relationships: conflictingIds(incoming.relationships, existing?.relationships, strategy),
    events: conflictingIds(incoming.events, existing?.events, strategy),
    mediaMetadata: conflictingIds(incoming.mediaMetadata, existing?.mediaMetadata, strategy),
    albums: conflictingIds(incoming.albums, existing?.albums, strategy)
  };
  const member = (value: string) => maps.members.get(value) ?? value;
  const event = (value: string) => maps.events.get(value) ?? value;
  const media = (value: string) => maps.mediaMetadata.get(value) ?? value;
  const album = (value: string) => maps.albums.get(value) ?? value;
  const skipped = Object.values(skippedIds).reduce((count, ids) => count + ids.size, 0);
  return {
    skipped,
    data: {
      members: incoming.members.filter((item) => !skippedIds.members.has(item.id)).map((item) => ({ ...item, id: member(item.id), treeId })),
      relationships: incoming.relationships.filter((item) => !skippedIds.relationships.has(item.id)).map((item) => ({
        ...item, id: maps.relationships.get(item.id) ?? item.id, treeId,
        sourceMemberId: member(item.sourceMemberId), targetMemberId: member(item.targetMemberId)
      })).filter((item) => item.sourceMemberId && item.targetMemberId),
      events: incoming.events.filter((item) => !skippedIds.events.has(item.id)).map((item) => ({
        ...item, id: event(item.id), treeId,
        memberIds: item.memberIds.map(member).filter(Boolean), mediaIds: item.mediaIds.map(media).filter(Boolean)
      })),
      mediaMetadata: incoming.mediaMetadata.filter((item) => !skippedIds.mediaMetadata.has(item.id)).map((item) => compact({
        ...item, id: media(item.id), treeId,
        memberId: item.memberId ? member(item.memberId) : undefined,
        eventId: item.eventId ? event(item.eventId) : undefined,
        memberIds: item.memberIds?.map(member).filter(Boolean),
        eventIds: item.eventIds?.map(event).filter(Boolean),
        albumId: item.albumId ? album(item.albumId) : undefined
      }) as MediaMetadata),
      albums: incoming.albums.filter((item) => !skippedIds.albums.has(item.id)).map((item) => ({ ...item, id: album(item.id), treeId }))
    }
  };
}

function idMap<T extends { id: string }>(
  incoming: T[], existing: T[] | undefined, strategy: NonNullable<ImportOptions['conflictStrategy']>
): Map<string, string> {
  const existingIds = new Set((existing ?? []).map((item) => item.id));
  return new Map(incoming.map((item) => {
    if (!existingIds.has(item.id)) return [item.id, item.id];
    if (strategy === 'SKIP') return [item.id, item.id];
    if (strategy === 'REGENERATE') return [item.id, nanoid()];
    return [item.id, item.id];
  }));
}

function conflictingIds<T extends { id: string }>(
  incoming: T[], existing: T[] | undefined, strategy: NonNullable<ImportOptions['conflictStrategy']>
): Set<string> {
  if (strategy !== 'SKIP') return new Set();
  const existingIds = new Set((existing ?? []).map((item) => item.id));
  return new Set(incoming.filter((item) => existingIds.has(item.id)).map((item) => item.id));
}

function mergeCollections(
  existing: TreeCollections,
  incoming: TreeCollections,
  strategy: NonNullable<ImportOptions['conflictStrategy']>
): TreeCollections {
  return {
    members: mergeById(existing.members, incoming.members, strategy),
    relationships: mergeById(existing.relationships, incoming.relationships, strategy),
    events: mergeById(existing.events, incoming.events, strategy),
    mediaMetadata: mergeById(existing.mediaMetadata, incoming.mediaMetadata, strategy),
    albums: mergeById(existing.albums, incoming.albums, strategy)
  };
}

function mergeById<T extends { id: string }>(existing: T[], incoming: T[], strategy: string): T[] {
  if (strategy !== 'OVERWRITE') return [...existing, ...incoming.filter((item) => !existing.some((old) => old.id === item.id))];
  const replacements = new Map(incoming.map((item) => [item.id, item]));
  return [...existing.map((item) => replacements.get(item.id) ?? item), ...incoming.filter((item) => !existing.some((old) => old.id === item.id))];
}

async function readTreeCollections(treeId: string): Promise<TreeCollections> {
  const [members, relationships, events, mediaMetadata, albums] = await Promise.all([
    getMembers(treeId), getRelationships(treeId), getEvents(treeId), getMediaMetadata(treeId), getAlbums(treeId)
  ]);
  return { members, relationships, events, mediaMetadata, albums };
}

async function writeTreeCollections(treeId: string, data: TreeCollections): Promise<void> {
  await putMembers(treeId, data.members);
  await putRelationships(treeId, data.relationships);
  await putEvents(treeId, data.events);
  await putMediaMetadata(treeId, data.mediaMetadata);
  await putAlbums(treeId, data.albums);
}

async function rollbackTreeCollections(treeId: string, data: TreeCollections): Promise<void> {
  await Promise.allSettled([
    putMembers(treeId, data.members), putRelationships(treeId, data.relationships), putEvents(treeId, data.events),
    putMediaMetadata(treeId, data.mediaMetadata), putAlbums(treeId, data.albums)
  ]);
}

function parseCsvRows(content: string, issues: ImportIssue[]): Array<{ line: number; values: string[] }> {
  const rows: Array<{ line: number; values: string[] }> = [];
  let values: string[] = [];
  let value = '';
  let quoted = false;
  let line = 1;
  let rowLine = 1;
  for (let index = 0; index < content.length; index += 1) {
    const char = content[index];
    if (char === '"') {
      if (quoted && content[index + 1] === '"') { value += '"'; index += 1; }
      else quoted = !quoted;
    } else if (char === ',' && !quoted) {
      values.push(value); value = '';
    } else if ((char === '\n' || char === '\r') && !quoted) {
      if (char === '\r' && content[index + 1] === '\n') index += 1;
      values.push(value); rows.push({ line: rowLine, values });
      values = []; value = ''; line += 1; rowLine = line;
    } else {
      value += char;
      if (char === '\n') line += 1;
    }
  }
  if (quoted) issues.push(errorIssue('CSV contains an unterminated quoted field', rowLine));
  if (value.length || values.length) { values.push(value); rows.push({ line: rowLine, values }); }
  return rows;
}

function csvGender(value: string, line: number, issues: ImportIssue[]): Member['gender'] {
  const normalized = value.trim().toUpperCase();
  if (!normalized || ['OTHER', 'O', 'U', 'KHÁC', 'KHAC'].includes(normalized)) return 'OTHER';
  if (['MALE', 'M', 'NAM'].includes(normalized)) return 'MALE';
  if (['FEMALE', 'F', 'NỮ', 'NU'].includes(normalized)) return 'FEMALE';
  issues.push({ severity: 'ERROR', code: 'INVALID_VALUE', message: `Invalid gender "${value}"`, line, path: 'gender' });
  return 'OTHER';
}

function decodeFile(file: Buffer | Uint8Array | string): string {
  if (typeof file === 'string') {
    if (Buffer.byteLength(file, 'utf8') > MAX_IMPORT_BYTES) throw new ImportServiceError('INVALID_INPUT', 'Import file exceeds 25MB');
    return file;
  }
  if (file.byteLength > MAX_IMPORT_BYTES) throw new ImportServiceError('INVALID_INPUT', 'Import file exceeds 25MB');
  return Buffer.from(file).toString('utf8');
}

function emptyParsed(format: 'JSON', issues: ImportIssue[]): ParsedJSON;
function emptyParsed(format: 'CSV', issues: ImportIssue[]): ParsedCSV;
function emptyParsed(format: 'JSON' | 'CSV', issues: ImportIssue[]): ParsedJSON | ParsedCSV {
  const base = { version: '1.0' as const, members: [], relationships: [], events: [], mediaMetadata: [], albums: [], issues };
  return format === 'JSON' ? { ...base, format: 'JSON' } : { ...base, format: 'CSV' };
}

function arrayOrIssue(root: Record<string, unknown>, key: string): unknown[] {
  return Array.isArray(root[key]) ? root[key] : [];
}

function jsonErrorLocation(error: unknown, content: string): { line: number; column: number } {
  const message = error instanceof Error ? error.message : '';
  const match = /position\s+(\d+)/i.exec(message);
  // Node 22+ omits the numeric offset from some JSON.parse errors.  The
  // common missing-value form is still unambiguous and lets us report the
  // offending line instead of falling back to line one.
  const fallback = /:\s*[,}\]]/.exec(content);
  const offset = match ? Number(match[1]) : fallback ? fallback.index + fallback[0].length - 1 : 0;
  const before = content.slice(0, offset);
  const lines = before.split('\n');
  return { line: lines.length, column: (lines.at(-1)?.length ?? 0) + 1 };
}

function lineOfKey(content: string, key: string): number {
  const offset = content.search(new RegExp(`"${escapeRegExp(key)}"\\s*:`));
  return offset < 0 ? 1 : content.slice(0, offset).split('\n').length;
}

function entityLine(content: string | undefined, collection: string, entityId: string | undefined, index: number): number {
  if (!content) return index + 2;
  if (entityId) {
    const match = new RegExp(`"id"\\s*:\\s*"${escapeRegExp(entityId)}"`).exec(content);
    if (match) return content.slice(0, match.index).split('\n').length;
  }
  return lineOfKey(content, collection);
}

function referenceIssue(path: string, value: string): ImportIssue {
  return { severity: 'ERROR', code: 'BROKEN_REFERENCE', message: `Referenced id "${value}" does not exist`, line: Number(path.split('.')[1] ?? 0) + 2, path };
}

function errorIssue(message: string, line: number, path?: string): ImportIssue {
  return { severity: 'ERROR', code: 'INVALID_VALUE', message, line, ...(path ? { path } : {}) };
}

function uniqueIssues(issues: ImportIssue[]): ImportIssue[] {
  const seen = new Set<string>();
  return issues.filter((issue) => {
    const key = `${issue.severity}|${issue.code}|${issue.line}|${issue.path ?? ''}|${issue.message}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  }).sort((a, b) => a.line - b.line || a.message.localeCompare(b.message));
}

function normalizeHeader(value: string): string {
  return value.trim().toLowerCase().replace(/[\s_-]+/g, '');
}

function read(values: Record<string, string>, ...keys: string[]): string {
  for (const key of keys) if (values[key]) return values[key];
  return '';
}

function inferFirstName(fullName: string): string {
  const parts = fullName.trim().split(/\s+/);
  return parts.at(-1) ?? '';
}

function inferLastName(fullName: string): string {
  return fullName.trim().split(/\s+/)[0] ?? '';
}

function parseOptionalInteger(value: string): number | undefined {
  if (!value) return undefined;
  const parsed = Number(value);
  return Number.isInteger(parsed) ? parsed : Number.NaN;
}

function compact<T extends Record<string, unknown>>(value: T): Partial<T> {
  return Object.fromEntries(Object.entries(value).filter(([, item]) => item !== undefined && item !== '')) as Partial<T>;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function stripBom(value: string): string {
  return value.charCodeAt(0) === 0xfeff ? value.slice(1) : value;
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export const importService = new ImportService();
export default importService;
export const parseGEDCOM = importService.parseGEDCOM.bind(importService);
export const parseJSON = importService.parseJSON.bind(importService);
export const parseCSV = importService.parseCSV.bind(importService);
export const preview = importService.preview.bind(importService);
export const execute = importService.execute.bind(importService);

// Re-exporting the document contract keeps integrations from depending on a
// service implementation detail.
export type { FamilyTreeExportDocument };
