import { describe, expect, it } from 'vitest';
import type { Album } from '@/data/types';
import { BLOB_PATHS } from '@/lib/blob/client';
import { getAlbums, getEvents, getMembers, getMediaMetadata, getRelationships, getTrees } from '@/lib/blob/readers';
import { putAlbums, putEvents, putMembers, putMediaMetadata, putRelationships, putTrees } from '@/lib/blob/writers';
import { exportService } from '@/lib/services/export-service';
import { importService } from '@/lib/services/import-service';
import { buildEvent, buildFamilyTree, buildMember, buildMediaMetadata, buildRelationship } from '../../utils/factories';

describe('ImportService and ExportService', () => {
  it('preserves a complete tree through JSON export and parse round-trip', async () => {
    const tree = buildFamilyTree({ id: 'tree-round-trip', name: 'Gia phả Nguyễn' });
    const parent = buildMember({ id: 'member-parent', treeId: tree.id, fullName: 'Nguyễn Văn A', dateOfBirth: '1950-01-02', generation: 0 });
    const child = buildMember({ id: 'member-child', treeId: tree.id, fullName: 'Nguyễn Thị B', gender: 'FEMALE', generation: 1 });
    const relationship = buildRelationship({ id: 'relationship-1', treeId: tree.id, sourceMemberId: parent.id, targetMemberId: child.id });
    const event = buildEvent({ id: 'event-1', treeId: tree.id, memberIds: [child.id], eventDate: '2024-06-01' });
    const media = buildMediaMetadata({ id: 'media-1', treeId: tree.id, memberIds: [child.id], eventIds: [event.id] });
    const album: Album = { id: 'album-1', treeId: tree.id, title: 'Kỷ niệm', createdAt: tree.createdAt };
    await putTrees([tree]);
    await putMembers(tree.id, [parent, child]);
    await putRelationships(tree.id, [relationship]);
    await putEvents(tree.id, [event]);
    await putMediaMetadata(tree.id, [media]);
    await putAlbums(tree.id, [album]);

    const parsed = await importService.parseJSON(Buffer.from(await exportService.exportJSON(tree.id)));

    expect(parsed.issues.filter((issue) => issue.severity === 'ERROR')).toEqual([]);
    expect(parsed.tree).toEqual(tree);
    expect(parsed.members).toEqual([parent, child]);
    expect(parsed.relationships).toEqual([relationship]);
    expect(parsed.events).toEqual([event]);
    expect(parsed.mediaMetadata).toEqual([media]);
    expect(parsed.albums).toEqual([album]);
  });

  it('parses GEDCOM 5.5 individuals, families, dates and inverse relationships', async () => {
    const gedcom = [
      '0 HEAD', '1 CHAR UTF-8',
      '0 @I1@ INDI', '1 NAME Nguyen /Van A/', '1 SEX M', '1 BIRT', '2 DATE 2 JAN 1950', '2 PLAC Hanoi',
      '0 @I2@ INDI', '1 NAME Nguyen /Thi B/', '1 SEX F',
      '0 @F1@ FAM', '1 HUSB @I1@', '1 WIFE @I2@', '1 MARR', '2 DATE 3 MAR 1970',
      '0 TRLR'
    ].join('\n');

    const parsed = await importService.parseGEDCOM(Buffer.from(gedcom));

    expect(parsed.issues).toEqual([]);
    expect(parsed.members).toHaveLength(2);
    expect(parsed.members[0]).toMatchObject({ fullName: 'Van A Nguyen', dateOfBirth: '1950-01-02', placeOfBirth: 'Hanoi' });
    expect(parsed.relationships).toHaveLength(2);
    expect(parsed.relationships[0]).toMatchObject({ type: 'SPOUSE', marriageDate: '1970-03-03T00:00:00.000Z' });
    expect(parsed.relationships[1].sourceMemberId).toBe(parsed.relationships[0].targetMemberId);
  });

  it('reports malformed JSON and invalid CSV fields with actionable line numbers', async () => {
    const malformed = await importService.parseJSON(Buffer.from('{\n  "members": [\n    { "id": }\n  ]\n}'));
    expect(malformed.issues[0]).toMatchObject({ code: 'SYNTAX_ERROR', line: 3 });

    const csv = await importService.parseCSV(Buffer.from([
      'id,fullName,gender,dateOfBirth',
      'm-1,Nguyen Van A,INVALID,not-a-date',
      'm-1,,MALE,1980-01-01'
    ].join('\n')));
    expect(csv.issues.some((issue) => issue.code === 'INVALID_VALUE' && issue.line === 2)).toBe(true);
    expect(csv.issues.some((issue) => issue.code === 'DUPLICATE_ID' && issue.line === 3)).toBe(true);
    expect((await importService.preview(csv)).valid).toBe(false);
  });

  it('executes a validated import atomically and rejects broken references', async () => {
    const tree = buildFamilyTree({ id: 'tree-import' });
    await putTrees([tree]);
    const member = buildMember({ id: 'import-member', treeId: 'source-tree' });
    const parsed = await importService.parseJSON(Buffer.from(JSON.stringify({ members: [member], relationships: [], events: [], mediaMetadata: [], albums: [] })));

    await expect(importService.execute(tree.id, parsed, { mode: 'REPLACE' })).resolves.toMatchObject({ imported: { members: 1 } });
    await expect(getMembers(tree.id)).resolves.toEqual([expect.objectContaining({ id: member.id, treeId: tree.id })]);
    await expect(getRelationships(tree.id)).resolves.toEqual([]);

    const broken = await importService.parseJSON(Buffer.from(JSON.stringify({
      members: [member], relationships: [{ ...buildRelationship({ id: 'broken', treeId: tree.id, sourceMemberId: 'missing', targetMemberId: member.id }) }], events: [], mediaMetadata: [], albums: []
    })));
    await expect(importService.execute(tree.id, broken)).rejects.toMatchObject({ code: 'INVALID_IMPORT' });
    await expect(getMembers(tree.id)).resolves.toHaveLength(1);
  });

  it('renders PDF, SVG and a 300 DPI PNG export', async () => {
    const tree = buildFamilyTree({ id: 'tree-render' });
    const member = buildMember({ id: 'render-member', treeId: tree.id });
    await putTrees([tree]);
    await putMembers(tree.id, [member]);
    await putRelationships(tree.id, []);
    await putEvents(tree.id, []);
    await putMediaMetadata(tree.id, []);
    await putAlbums(tree.id, []);

    const svg = await exportService.exportSVG(tree.id, { colorScheme: 'EARTH' });
    const pdf = await exportService.exportPDF(tree.id, { paperSize: 'A4', orientation: 'PORTRAIT' });
    const png = await exportService.exportImage(tree.id, { dpi: 300 });
    expect(svg).toContain('<svg');
    expect(pdf.subarray(0, 5).toString()).toBe('%PDF-');
    expect(png.subarray(0, 8).toString('hex')).toBe('89504e470d0a1a0a');
  });
});
