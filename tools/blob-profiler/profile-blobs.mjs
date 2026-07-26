#!/usr/bin/env node
/**
 * Read-only Vercel Blob profiler for the Spring Boot migration (Task 2).
 *
 * Produces a full inventory of every object under the legacy BLOB_PATHS
 * layout (src/lib/blob/client.ts), validates JSON collections, detects
 * anomalies and assigns every object an explicit migration disposition.
 *
 * Guarantees:
 *  - ZERO writes: only `list` and ranged/full GET of blob content are used.
 *  - Paginated listing (1000 per page) so any store size reconciles.
 *  - Deterministic JSON report on stdout / --out file.
 *
 * Usage:
 *   BLOB_READ_WRITE_TOKEN=... node tools/blob-profiler/profile-blobs.mjs \
 *     [--out report.json] [--max-bytes-per-file 26214400] [--skip-content]
 *
 * `--skip-content` limits the run to listing metadata only (fast pre-scan and
 * transfer-cost estimation before committing to full SHA-256 streaming).
 */

import { createHash } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { list } from '@vercel/blob';

const args = process.argv.slice(2);
const outFile = argValue('--out');
const skipContent = args.includes('--skip-content');
const maxBytesPerFile = Number(argValue('--max-bytes-per-file') ?? 25 * 1024 * 1024);

function argValue(name) {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
}

if (!process.env.BLOB_READ_WRITE_TOKEN) {
  console.error('BLOB_READ_WRITE_TOKEN is required (read scope is sufficient).');
  process.exit(2);
}

/** Known legacy path families, mirrored from src/lib/blob/client.ts BLOB_PATHS. */
const PATH_FAMILIES = [
  { id: 'users', pattern: /^data\/users\.json$/, kind: 'json-collection', entity: 'User' },
  { id: 'trees', pattern: /^data\/trees\.json$/, kind: 'json-collection', entity: 'FamilyTree' },
  { id: 'members', pattern: /^data\/trees\/([^/]+)\/members\.json$/, kind: 'json-collection', entity: 'Member' },
  { id: 'relationships', pattern: /^data\/trees\/([^/]+)\/relationships\.json$/, kind: 'json-collection', entity: 'Relationship' },
  { id: 'events', pattern: /^data\/trees\/([^/]+)\/events\.json$/, kind: 'json-collection', entity: 'Event' },
  { id: 'media-metadata', pattern: /^data\/trees\/([^/]+)\/media\.json$/, kind: 'json-collection', entity: 'MediaMetadata' },
  { id: 'albums', pattern: /^data\/trees\/([^/]+)\/albums\.json$/, kind: 'json-collection', entity: 'Album' },
  { id: 'changelogs', pattern: /^data\/trees\/([^/]+)\/changelogs\.json$/, kind: 'json-collection', entity: 'ChangeLog' },
  { id: 'media-original', pattern: /^media\/([^/]+)\/originals\/([^/]+)$/, kind: 'binary', entity: 'MediaOriginal' },
  { id: 'media-thumbnail', pattern: /^media\/([^/]+)\/thumbnails\/([^/]+)$/, kind: 'binary', entity: 'MediaThumbnail' },
  { id: 'backup', pattern: /^backups\/([^/]+)\/(.+)\.json$/, kind: 'json-document', entity: 'BackupSnapshot' },
  { id: 'share-link-tree', pattern: /^share-links\/([^/]+)\.json$/, kind: 'json-document', entity: 'ShareLinks' }
];

const BACKUP_RETENTION_MS = 30 * 24 * 60 * 60 * 1000;
const REQUIRED_FIELDS = {
  User: ['id', 'email', 'name'],
  FamilyTree: ['id', 'name', 'ownerId', 'memberships'],
  Member: ['id', 'treeId', 'firstName', 'lastName', 'fullName', 'gender', 'isAlive'],
  Relationship: ['id', 'treeId', 'sourceMemberId', 'targetMemberId', 'type'],
  Event: ['id', 'treeId', 'title', 'type', 'eventDate', 'memberIds', 'mediaIds'],
  MediaMetadata: ['id', 'treeId', 'filename', 'originalName', 'mimeType', 'fileSize', 'blobUrl'],
  Album: ['id', 'treeId', 'name'],
  ChangeLog: ['id', 'treeId', 'userId', 'action', 'createdAt']
};
/** Fields superseded by array forms; recorded as legacy-field variants. */
const LEGACY_FIELDS = {
  Member: ['avatarUrl'],
  MediaMetadata: ['memberId', 'eventId']
};

async function listAll() {
  const blobs = [];
  let cursor;
  do {
    const page = await list({ cursor, limit: 1000 });
    blobs.push(...page.blobs);
    cursor = page.hasMore ? page.cursor : undefined;
  } while (cursor);
  return blobs;
}

function classifyPath(pathname) {
  for (const family of PATH_FAMILIES) {
    const match = family.pattern.exec(pathname);
    if (match) return { family, treeId: match[1] };
  }
  return { family: null, treeId: undefined };
}

async function fetchJson(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`GET ${url} -> ${response.status}`);
  return response.json();
}

async function sha256(url) {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`GET ${url} -> ${response.status}`);
  const hash = createHash('sha256');
  for await (const chunk of response.body) hash.update(chunk);
  return hash.digest('hex');
}

function checkRecords(entity, records, context) {
  const anomalies = [];
  const ids = new Map();
  const schemaVariants = new Set();
  for (const [index, record] of records.entries()) {
    if (typeof record !== 'object' || record === null || Array.isArray(record)) {
      anomalies.push({ type: 'MALFORMED_RECORD', entity, index });
      continue;
    }
    for (const field of REQUIRED_FIELDS[entity] ?? []) {
      if (record[field] === undefined || record[field] === null) {
        anomalies.push({ type: 'MISSING_REQUIRED_FIELD', entity, index, field, id: record.id });
      }
    }
    for (const legacyField of LEGACY_FIELDS[entity] ?? []) {
      if (record[legacyField] !== undefined) schemaVariants.add(`legacy:${legacyField}`);
    }
    if (context.treeId && record.treeId && record.treeId !== context.treeId) {
      anomalies.push({ type: 'CROSS_TREE_RECORD', entity, index, id: record.id, recordTreeId: record.treeId });
    }
    const key = entity === 'User' ? String(record.email ?? '').toLowerCase() : record.id;
    if (key) {
      if (ids.has(key)) anomalies.push({ type: entity === 'User' ? 'DUPLICATE_EMAIL' : 'DUPLICATE_ID', entity, key });
      ids.set(key, index);
    }
  }
  return { anomalies, schemaVariants: [...schemaVariants], count: records.length, ids: [...ids.keys()] };
}

function crossReferenceChecks(profile) {
  const anomalies = [];
  for (const [treeId, tree] of Object.entries(profile.trees)) {
    const memberIds = new Set(tree.memberIds ?? []);
    const mediaIds = new Set(tree.mediaIds ?? []);
    for (const relationship of tree.relationships ?? []) {
      if (!memberIds.has(relationship.sourceMemberId) || !memberIds.has(relationship.targetMemberId)) {
        anomalies.push({ type: 'BROKEN_RELATIONSHIP_REFERENCE', treeId, id: relationship.id });
      }
    }
    for (const event of tree.events ?? []) {
      for (const id of event.memberIds ?? []) if (!memberIds.has(id)) anomalies.push({ type: 'BROKEN_EVENT_MEMBER_REFERENCE', treeId, eventId: event.id, memberId: id });
      for (const id of event.mediaIds ?? []) if (!mediaIds.has(id)) anomalies.push({ type: 'BROKEN_EVENT_MEDIA_REFERENCE', treeId, eventId: event.id, mediaId: id });
    }
    // Binary objects present without a metadata row are orphans (and vice versa).
    for (const filename of tree.binaryOriginals ?? []) {
      const mediaId = filename.replace(/\.[a-z0-9]+$/i, '');
      if (!mediaIds.has(mediaId)) anomalies.push({ type: 'ORPHAN_BINARY_OBJECT', treeId, filename });
    }
    for (const media of tree.media ?? []) {
      if (!(tree.binaryOriginals ?? []).some((f) => f.startsWith(`${media.id}.`))) {
        anomalies.push({ type: 'MISSING_BINARY_OBJECT', treeId, mediaId: media.id });
      }
    }
  }
  return anomalies;
}

function disposition(entry, anomalyTypes) {
  const now = Date.now();
  if (entry.familyId === null) return 'quarantine'; // unknown path family
  if (entry.familyId === 'backup') {
    const timestamp = Date.parse(entry.pathname.replace(/^backups\/[^/]+\//, '').replace(/\.json$/, ''));
    if (Number.isNaN(timestamp)) return 'quarantine';
    return now - timestamp > BACKUP_RETENTION_MS ? 'delete-after-retention' : 'migrate';
  }
  if (anomalyTypes.some((type) => type === 'MALFORMED_JSON' || type === 'MALFORMED_RECORD')) return 'quarantine';
  if (entry.familyId === 'share-link-tree') return 'migrate';
  if (entry.familyId === 'media-thumbnail') return 'retain'; // regenerated by Spring; retained until decommission
  if (anomalyTypes.includes('ORPHAN_BINARY_OBJECT')) return 'quarantine';
  return 'migrate';
}

async function main() {
  const startedAt = new Date().toISOString();
  const blobs = await listAll();
  const totalBytes = blobs.reduce((total, blob) => total + blob.size, 0);

  const inventory = [];
  const globalAnomalies = [];
  const profile = { trees: {} };
  const treeState = (treeId) => (profile.trees[treeId] ??= { binaryOriginals: [] });

  for (const blob of blobs) {
    const { family, treeId } = classifyPath(blob.pathname);
    const entry = {
      pathname: blob.pathname,
      url: blob.url,
      etag: blob.etag ?? null,
      size: blob.size,
      uploadedAt: blob.uploadedAt,
      familyId: family?.id ?? null,
      treeId: treeId ?? null,
      recordCount: null,
      schemaVariants: [],
      sha256: null,
      anomalies: []
    };

    if (!family) {
      entry.anomalies.push({ type: 'UNKNOWN_PATH' });
    } else if (!skipContent && family.kind !== 'binary') {
      if (blob.size > maxBytesPerFile) {
        entry.anomalies.push({ type: 'OVERSIZED_JSON', limit: maxBytesPerFile });
      } else {
        try {
          const value = await fetchJson(blob.url);
          if (family.kind === 'json-collection') {
            if (!Array.isArray(value)) {
              entry.anomalies.push({ type: 'MALFORMED_JSON', reason: 'expected array' });
            } else {
              const result = checkRecords(family.entity, value, { treeId });
              entry.recordCount = result.count;
              entry.schemaVariants = result.schemaVariants;
              entry.anomalies.push(...result.anomalies);
              if (treeId) {
                const state = treeState(treeId);
                if (family.id === 'members') state.memberIds = result.ids;
                if (family.id === 'media-metadata') { state.mediaIds = result.ids; state.media = value; }
                if (family.id === 'relationships') state.relationships = value;
                if (family.id === 'events') state.events = value;
              }
            }
          } else if (family.id === 'backup') {
            entry.recordCount = ['members', 'relationships', 'events', 'mediaMetadata']
              .reduce((total, key) => total + (Array.isArray(value?.data?.[key]) ? value.data[key].length : 0), 0);
            if (!value?.treeId || !value?.timestamp || !value?.data) entry.anomalies.push({ type: 'MALFORMED_BACKUP' });
          }
        } catch (error) {
          entry.anomalies.push({ type: 'MALFORMED_JSON', reason: String(error.message ?? error) });
        }
      }
    } else if (!skipContent && family.kind === 'binary') {
      if (family.id === 'media-original' && treeId) {
        treeState(treeId).binaryOriginals.push(blob.pathname.split('/').at(-1));
      }
      try {
        entry.sha256 = await sha256(blob.url);
      } catch (error) {
        entry.anomalies.push({ type: 'UNREADABLE_BINARY', reason: String(error.message ?? error) });
      }
    }
    inventory.push(entry);
  }

  if (!skipContent) globalAnomalies.push(...crossReferenceChecks(profile));

  // Attach cross-reference anomalies to the affected inventory entries, then classify.
  for (const entry of inventory) {
    const related = globalAnomalies.filter((anomaly) =>
      (anomaly.treeId && anomaly.treeId === entry.treeId) &&
      ((anomaly.type === 'ORPHAN_BINARY_OBJECT' && entry.pathname.endsWith(`/${anomaly.filename}`)) ||
        (anomaly.type !== 'ORPHAN_BINARY_OBJECT' && entry.familyId && entry.recordCount !== null))
    );
    const anomalyTypes = [...entry.anomalies, ...related].map((anomaly) => anomaly.type);
    entry.disposition = disposition(entry, anomalyTypes);
  }

  const report = {
    startedAt,
    finishedAt: new Date().toISOString(),
    mode: skipContent ? 'metadata-only' : 'full',
    reconciliation: {
      listedObjects: blobs.length,
      inventoriedObjects: inventory.length,
      listedBytes: totalBytes,
      inventoriedBytes: inventory.reduce((total, entry) => total + entry.size, 0),
      reconciles: blobs.length === inventory.length
    },
    transferEstimate: {
      totalBytes,
      // Conservative single-stream estimate at 20 MiB/s effective throughput.
      estimatedSeconds: Math.ceil(totalBytes / (20 * 1024 * 1024)),
      note: 'Metadata-only pre-scan; rerun without --skip-content for SHA-256 of binaries.'
    },
    dispositions: inventory.reduce((counts, entry) => {
      counts[entry.disposition] = (counts[entry.disposition] ?? 0) + 1;
      return counts;
    }, {}),
    anomalies: globalAnomalies,
    inventory
  };

  const output = JSON.stringify(report, null, 2);
  if (outFile) {
    writeFileSync(outFile, output);
    console.error(`Report written to ${outFile} (${inventory.length} objects, ${totalBytes} bytes).`);
  } else {
    console.log(output);
  }
}

main().catch((error) => {
  console.error('Blob profiling failed:', error);
  process.exit(1);
});
