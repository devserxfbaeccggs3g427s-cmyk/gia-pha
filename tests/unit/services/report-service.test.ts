import { PDFDocument } from 'pdf-lib';
import { describe, expect, it } from 'vitest';
import type { Member, Relationship } from '@/data/types';
import {
  ReportService,
  ReportServiceError,
  buildGrowthTimeline,
  calculateStatistics,
  collectBranchMemberIds
} from '@/lib/services/report-service';
import { buildFamilyTree, buildMember, buildRelationship } from '../../utils/factories';

describe('ReportService', () => {
  const referenceDate = new Date('2026-07-18T12:00:00.000Z');
  const members: Member[] = [
    buildMember({
      id: 'root', treeId: 'tree-report', fullName: 'Nguyễn Văn Gốc', gender: 'MALE',
      dateOfBirth: '1950-07-19', currentAddress: 'Hà Nội', occupation: 'Giáo viên',
      education: 'Đại học', createdAt: '2024-01-02T00:00:00.000Z'
    }),
    buildMember({
      id: 'spouse', treeId: 'tree-report', fullName: 'Trần Thị Gốc', gender: 'FEMALE',
      dateOfBirth: '1955-01-10', placeOfBirth: 'ha noi', occupation: 'Giáo viên',
      education: 'Đại học', createdAt: '2024-01-20T00:00:00.000Z'
    }),
    buildMember({
      id: 'child', treeId: 'tree-report', fullName: 'Nguyễn Thị Con', gender: 'FEMALE',
      dateOfBirth: '1980-10-10', dateOfDeath: '2020-10-09', isAlive: false,
      currentAddress: 'Đà Nẵng', occupation: 'Bác sĩ', education: 'Sau đại học',
      createdAt: '2024-02-01T00:00:00.000Z'
    }),
    buildMember({
      id: 'grandchild', treeId: 'tree-report', fullName: 'Nguyễn Minh Cháu', gender: 'OTHER',
      dateOfBirth: '2010-03-01', currentAddress: 'Đà Nẵng',
      createdAt: '2024-02-14T00:00:00.000Z'
    }),
    buildMember({
      id: 'unrelated', treeId: 'tree-report', fullName: 'Lê Văn Ngoài', gender: 'MALE',
      dateOfBirth: '1990-01-01', currentAddress: 'Huế', occupation: 'Kỹ sư',
      education: 'Cao đẳng', createdAt: '2024-04-01T00:00:00.000Z'
    })
  ];
  const relationships: Relationship[] = [
    buildRelationship({ id: 'root-child', treeId: 'tree-report', sourceMemberId: 'root', targetMemberId: 'child' }),
    buildRelationship({ id: 'child-grandchild', treeId: 'tree-report', sourceMemberId: 'child', targetMemberId: 'grandchild' }),
    buildRelationship({ id: 'root-spouse', treeId: 'tree-report', sourceMemberId: 'root', targetMemberId: 'spouse', type: 'SPOUSE' })
  ];

  it('calculates complete, deterministic chart distributions and exact ages', () => {
    const statistics = calculateStatistics('tree-report', members, relationships, referenceDate);

    expect(statistics).toMatchObject({
      totalMembers: 5,
      generationsCount: 3,
      livingMembers: 4,
      deceasedMembers: 1,
      membersWithKnownAge: 5,
      averageAge: 47.4,
      genderDistribution: { MALE: 2, FEMALE: 2, OTHER: 1 },
      ageDistribution: { '0-17': 1, '18-30': 0, '31-45': 2, '46-60': 0, '61+': 2, UNKNOWN: 0 },
      geographicDistribution: { 'Hà Nội': 2, 'Đà Nẵng': 2, 'Huế': 1 },
      occupationDistribution: { 'Giáo viên': 2, 'Bác sĩ': 1, 'Kỹ sư': 1, UNKNOWN: 1 },
      educationDistribution: { 'Đại học': 2, 'Cao đẳng': 1, 'Sau đại học': 1, UNKNOWN: 1 }
    });
  });

  it('builds branch statistics from descendants and direct spouses without leaking unrelated members', async () => {
    const service = new ReportService(
      async () => members,
      async () => relationships,
      async () => [],
      () => referenceDate
    );

    const branch = await service.getBranchStatistics('tree-report', 'root');
    expect(branch.branchRoot).toEqual({ id: 'root', fullName: 'Nguyễn Văn Gốc' });
    expect(branch.memberIds).toEqual(['root', 'spouse', 'child', 'grandchild']);
    expect(branch.totalMembers).toBe(4);
    expect(branch.generationsCount).toBe(3);
    expect(branch.geographicDistribution).not.toHaveProperty('Huế');
    expect(collectBranchMemberIds(members, relationships, 'unrelated')).toEqual(new Set(['unrelated']));
    await expect(service.getGrowthTimeline('tree-report', 'root')).resolves.toEqual([
      { period: '2024-01', newMembers: 2, totalMembers: 2 },
      { period: '2024-02', newMembers: 2, totalMembers: 4 }
    ]);
  });

  it('groups additions by month and returns a cumulative growth timeline', () => {
    expect(buildGrowthTimeline(members)).toEqual([
      { period: '2024-01', newMembers: 2, totalMembers: 2 },
      { period: '2024-02', newMembers: 2, totalMembers: 4 },
      { period: '2024-04', newMembers: 1, totalMembers: 5 }
    ]);
  });

  it('rejects missing roots and invalid identifiers with domain errors', async () => {
    const service = new ReportService(async () => members, async () => relationships);
    await expect(service.getStatistics('')).rejects.toMatchObject({ code: 'INVALID_INPUT' } satisfies Partial<ReportServiceError>);
    await expect(service.getBranchStatistics('tree-report', 'missing'))
      .rejects.toMatchObject({ code: 'MEMBER_NOT_FOUND' } satisfies Partial<ReportServiceError>);
  });

  it('exports a multi-page PDF containing charts and distribution tables', async () => {
    const tree = buildFamilyTree({ id: 'tree-report', name: 'Gia phả Nguyễn' });
    const service = new ReportService(
      async () => members,
      async () => relationships,
      async () => [tree],
      () => referenceDate
    );

    const output = await service.exportPDF(tree.id, 'root');
    const pdf = await PDFDocument.load(output);
    expect(output.subarray(0, 5).toString()).toBe('%PDF-');
    expect(pdf.getPageCount()).toBeGreaterThanOrEqual(2);
    expect(pdf.getTitle()).toBe(`${tree.name} - Statistics report`);
  });

  it('calculates a 1,000-member report well within the three-second requirement', async () => {
    const dataset = Array.from({ length: 1000 }, (_, index) => buildMember({
      id: `member-${index}`,
      treeId: 'tree-performance',
      gender: index % 2 ? 'MALE' : 'FEMALE',
      dateOfBirth: `${1950 + index % 70}-01-01`,
      occupation: `Occupation ${index % 20}`,
      education: `Education ${index % 8}`,
      currentAddress: `Location ${index % 30}`
    }));
    const service = new ReportService(async () => dataset, async () => []);
    const startedAt = performance.now();

    const statistics = await service.getStatistics('tree-performance');

    expect(statistics.totalMembers).toBe(1000);
    expect(performance.now() - startedAt).toBeLessThan(3000);
  });
});
