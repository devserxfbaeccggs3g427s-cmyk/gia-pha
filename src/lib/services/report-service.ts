import { PDFDocument, StandardFonts, rgb, type PDFFont, type PDFPage } from 'pdf-lib';
import type { FamilyTree, Gender, Member, Relationship } from '@/data/types';
import { getCanonicalParentChildEdges } from '@/lib/algorithms/ancestry';
import { calculateGenerations } from '@/lib/algorithms/generation';
import { getMembers, getRelationships, getTrees } from '@/lib/blob/readers';
import {
  AGE_BUCKETS,
  type AgeBucket,
  type AgeDistribution,
  type BranchStatistics,
  type GenderDistribution,
  type GrowthTimelinePoint,
  type ReportStatistics
} from '@/types/report';

const UNKNOWN = 'UNKNOWN';
const PAGE_WIDTH = 841.89;
const PAGE_HEIGHT = 595.28;
const PAGE_MARGIN = 42;

type MemberLoader = (treeId: string) => Promise<Member[]>;
type RelationshipLoader = (treeId: string) => Promise<Relationship[]>;
type TreeLoader = () => Promise<FamilyTree[]>;
type Clock = () => Date;

export class ReportServiceError extends Error {
  constructor(
    public readonly code: 'INVALID_INPUT' | 'MEMBER_NOT_FOUND' | 'TREE_NOT_FOUND' | 'RENDER_FAILED',
    message: string
  ) {
    super(message);
    this.name = 'ReportServiceError';
  }
}

export class ReportService {
  constructor(
    private readonly loadMembers: MemberLoader = getMembers,
    private readonly loadRelationships: RelationshipLoader = getRelationships,
    private readonly loadTrees: TreeLoader = getTrees,
    private readonly now: Clock = () => new Date()
  ) {}

  async getStatistics(treeId: string): Promise<ReportStatistics> {
    assertIdentifier(treeId, 'treeId');
    const [members, relationships] = await Promise.all([
      this.loadMembers(treeId),
      this.loadRelationships(treeId)
    ]);
    return calculateStatistics(treeId, members, relationships, this.now());
  }

  async getBranchStatistics(treeId: string, branchRootMemberId: string): Promise<BranchStatistics> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(branchRootMemberId, 'branchRootMemberId');
    const [members, relationships] = await Promise.all([
      this.loadMembers(treeId),
      this.loadRelationships(treeId)
    ]);
    const root = members.find((member) => member.id === branchRootMemberId);
    if (!root) {
      throw new ReportServiceError('MEMBER_NOT_FOUND', 'Branch root member not found');
    }

    const memberIds = collectBranchMemberIds(members, relationships, branchRootMemberId);
    const branchMembers = members.filter((member) => memberIds.has(member.id));
    const branchRelationships = relationships.filter(
      (relationship) => memberIds.has(relationship.sourceMemberId) && memberIds.has(relationship.targetMemberId)
    );

    return {
      ...calculateStatistics(treeId, branchMembers, branchRelationships, this.now()),
      branchRoot: { id: root.id, fullName: root.fullName },
      memberIds: branchMembers.map((member) => member.id)
    };
  }

  async getGrowthTimeline(treeId: string, branchRootMemberId?: string): Promise<GrowthTimelinePoint[]> {
    assertIdentifier(treeId, 'treeId');
    if (!branchRootMemberId) return buildGrowthTimeline(await this.loadMembers(treeId));
    assertIdentifier(branchRootMemberId, 'branchRootMemberId');
    const [members, relationships] = await Promise.all([
      this.loadMembers(treeId),
      this.loadRelationships(treeId)
    ]);
    if (!members.some((member) => member.id === branchRootMemberId)) {
      throw new ReportServiceError('MEMBER_NOT_FOUND', 'Branch root member not found');
    }
    const branchMemberIds = collectBranchMemberIds(members, relationships, branchRootMemberId);
    return buildGrowthTimeline(members.filter((member) => branchMemberIds.has(member.id)));
  }

  async exportPDF(treeId: string, branchRootMemberId?: string): Promise<Buffer> {
    assertIdentifier(treeId, 'treeId');
    const tree = (await this.loadTrees()).find((candidate) => candidate.id === treeId);
    if (!tree) throw new ReportServiceError('TREE_NOT_FOUND', 'Family tree not found');

    const [statistics, timelineMembers] = await Promise.all([
      branchRootMemberId
        ? this.getBranchStatistics(treeId, branchRootMemberId)
        : this.getStatistics(treeId),
      this.loadMembers(treeId)
    ]);
    const scopedMemberIds = branchRootMemberId
      ? new Set((statistics as BranchStatistics).memberIds)
      : undefined;
    const scopedTimelineMembers = scopedMemberIds
      ? timelineMembers.filter((member) => scopedMemberIds.has(member.id))
      : timelineMembers;
    const timeline = buildGrowthTimeline(scopedTimelineMembers);

    try {
      return await renderReportPDF(tree, statistics, timeline);
    } catch (error) {
      if (error instanceof ReportServiceError) throw error;
      const wrapped = new ReportServiceError('RENDER_FAILED', 'Could not render statistics report PDF');
      Object.defineProperty(wrapped, 'cause', { value: error, enumerable: false });
      throw wrapped;
    }
  }
}

export function calculateStatistics(
  treeId: string,
  members: readonly Member[],
  relationships: readonly Relationship[],
  referenceDate: Date
): ReportStatistics {
  const generations = calculateGenerations([...members], [...relationships]);
  const presentGenerations = new Set<number>();
  const genderDistribution: GenderDistribution = { MALE: 0, FEMALE: 0, OTHER: 0 };
  const ageDistribution = Object.fromEntries(AGE_BUCKETS.map((bucket) => [bucket, 0])) as AgeDistribution;
  const ages: number[] = [];
  const geographic = new Map<string, Category>();
  const occupations = new Map<string, Category>();
  const education = new Map<string, Category>();

  for (const member of members) {
    const generation = member.generation ?? generations.get(member.id) ?? 0;
    presentGenerations.add(generation);
    genderDistribution[member.gender] += 1;

    const age = calculateMemberAge(member, referenceDate);
    const ageBucket = getAgeBucket(age);
    ageDistribution[ageBucket] += 1;
    if (age !== undefined) ages.push(age);

    addCategory(geographic, member.currentAddress ?? member.placeOfBirth);
    addCategory(occupations, member.occupation);
    addCategory(education, member.education);
  }

  const livingMembers = members.filter((member) => member.isAlive).length;
  return {
    treeId,
    generatedAt: referenceDate.toISOString(),
    totalMembers: members.length,
    generationsCount: presentGenerations.size,
    livingMembers,
    deceasedMembers: members.length - livingMembers,
    membersWithKnownAge: ages.length,
    averageAge: ages.length === 0
      ? null
      : Math.round((ages.reduce((total, age) => total + age, 0) / ages.length) * 10) / 10,
    genderDistribution,
    ageDistribution,
    geographicDistribution: categoriesToRecord(geographic),
    occupationDistribution: categoriesToRecord(occupations),
    educationDistribution: categoriesToRecord(education)
  };
}

export function buildGrowthTimeline(members: readonly Member[]): GrowthTimelinePoint[] {
  const additions = new Map<string, number>();
  for (const member of members) {
    const period = parseCalendarMonth(member.createdAt);
    if (period) additions.set(period, (additions.get(period) ?? 0) + 1);
  }

  let totalMembers = 0;
  return [...additions.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([period, newMembers]) => {
      totalMembers += newMembers;
      return { period, newMembers, totalMembers };
    });
}

export function collectBranchMemberIds(
  members: readonly Member[],
  relationships: readonly Relationship[],
  branchRootMemberId: string
): Set<string> {
  const validMemberIds = new Set(members.map((member) => member.id));
  if (!validMemberIds.has(branchRootMemberId)) return new Set();

  const childrenByParent = new Map<string, Set<string>>();
  for (const { parentId, childId } of getCanonicalParentChildEdges(members, relationships)) {
    const children = childrenByParent.get(parentId) ?? new Set<string>();
    children.add(childId);
    childrenByParent.set(parentId, children);
  }

  const descendants = new Set<string>();
  const queue = [branchRootMemberId];
  while (queue.length > 0) {
    const memberId = queue.shift()!;
    if (descendants.has(memberId)) continue;
    descendants.add(memberId);
    for (const childId of childrenByParent.get(memberId) ?? []) queue.push(childId);
  }

  // Married-in members belong to the displayed branch, but their unrelated
  // descendants are not traversed, preventing accidental expansion into a
  // different branch in blended families.
  const branch = new Set(descendants);
  for (const relationship of relationships) {
    if (relationship.type !== 'SPOUSE') continue;
    if (descendants.has(relationship.sourceMemberId) && validMemberIds.has(relationship.targetMemberId)) {
      branch.add(relationship.targetMemberId);
    }
    if (descendants.has(relationship.targetMemberId) && validMemberIds.has(relationship.sourceMemberId)) {
      branch.add(relationship.sourceMemberId);
    }
  }
  return branch;
}

function calculateMemberAge(member: Member, referenceDate: Date): number | undefined {
  const birth = parseDateParts(member.dateOfBirth);
  const endpoint = member.dateOfDeath
    ? parseDateParts(member.dateOfDeath)
    : parseDateParts(referenceDate.toISOString());
  if (!birth || !endpoint || compareDateParts(endpoint, birth) < 0) return undefined;

  let age = endpoint.year - birth.year;
  if (endpoint.month < birth.month || (endpoint.month === birth.month && endpoint.day < birth.day)) age -= 1;
  return age >= 0 && age <= 150 ? age : undefined;
}

function getAgeBucket(age: number | undefined): AgeBucket {
  if (age === undefined) return 'UNKNOWN';
  if (age <= 17) return '0-17';
  if (age <= 30) return '18-30';
  if (age <= 45) return '31-45';
  if (age <= 60) return '46-60';
  return '61+';
}

interface DateParts { year: number; month: number; day: number }

function parseDateParts(value?: string): DateParts | undefined {
  if (!value) return undefined;
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!match) return undefined;
  const parts = { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) };
  const check = new Date(Date.UTC(parts.year, parts.month - 1, parts.day));
  if (
    check.getUTCFullYear() !== parts.year ||
    check.getUTCMonth() + 1 !== parts.month ||
    check.getUTCDate() !== parts.day
  ) return undefined;
  return parts;
}

function compareDateParts(left: DateParts, right: DateParts): number {
  return left.year - right.year || left.month - right.month || left.day - right.day;
}

function parseCalendarMonth(value: string): string | undefined {
  const parts = parseDateParts(value);
  return parts ? `${String(parts.year).padStart(4, '0')}-${String(parts.month).padStart(2, '0')}` : undefined;
}

interface Category { label: string; count: number }

function addCategory(target: Map<string, Category>, rawValue?: string): void {
  const label = rawValue?.trim().replace(/\s+/g, ' ') || UNKNOWN;
  const key = normalizeCategory(label);
  const current = target.get(key);
  if (current) current.count += 1;
  else target.set(key, { label, count: 1 });
}

function normalizeCategory(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[đĐ]/g, 'd')
    .toLocaleLowerCase('vi');
}

function categoriesToRecord(categories: ReadonlyMap<string, Category>): Record<string, number> {
  return Object.fromEntries(
    [...categories.values()]
      .sort((left, right) => right.count - left.count || left.label.localeCompare(right.label, 'vi'))
      .map((category) => [category.label, category.count])
  );
}

export async function renderReportPDF(
  tree: FamilyTree,
  statistics: ReportStatistics | BranchStatistics,
  timeline: readonly GrowthTimelinePoint[]
): Promise<Buffer> {
  const pdf = await PDFDocument.create();
  pdf.setTitle(`${tree.name} - Statistics report`);
  pdf.setSubject('Family genealogy statistics with charts and tables');
  pdf.setCreator('Family Genealogy Management');
  pdf.setProducer('Family Genealogy Management ReportService');
  pdf.setCreationDate(new Date(statistics.generatedAt));
  const regular = await pdf.embedFont(StandardFonts.Helvetica);
  const bold = await pdf.embedFont(StandardFonts.HelveticaBold);

  drawOverviewPage(pdf, tree, statistics, timeline, regular, bold);
  drawDistributionPages(pdf, statistics, regular, bold);
  return Buffer.from(await pdf.save());
}

function drawOverviewPage(
  pdf: PDFDocument,
  tree: FamilyTree,
  statistics: ReportStatistics | BranchStatistics,
  timeline: readonly GrowthTimelinePoint[],
  regular: PDFFont,
  bold: PDFFont
): void {
  const page = pdf.addPage([PAGE_WIDTH, PAGE_HEIGHT]);
  page.drawText(pdfSafeText(tree.name), { x: PAGE_MARGIN, y: PAGE_HEIGHT - 48, size: 22, font: bold, color: rgb(0.08, 0.15, 0.28) });
  const scope = 'branchRoot' in statistics ? `Branch: ${statistics.branchRoot.fullName}` : 'Whole family tree';
  page.drawText(pdfSafeText(`Statistics report | ${scope} | ${statistics.generatedAt.slice(0, 10)}`), {
    x: PAGE_MARGIN, y: PAGE_HEIGHT - 68, size: 9, font: regular, color: rgb(0.36, 0.42, 0.52)
  });

  const cards: Array<[string, string]> = [
    ['Members', String(statistics.totalMembers)],
    ['Generations', String(statistics.generationsCount)],
    ['Living', String(statistics.livingMembers)],
    ['Average age', statistics.averageAge === null ? 'N/A' : String(statistics.averageAge)]
  ];
  cards.forEach(([label, value], index) => drawMetricCard(page, 42 + index * 194, 466, 178, 58, label, value, regular, bold));

  drawBarChart(page, 42, 250, 365, 185, 'Gender distribution', statistics.genderDistribution, regular, bold);
  drawBarChart(page, 434, 250, 365, 185, 'Age distribution', statistics.ageDistribution, regular, bold);
  drawTimelineChart(page, 42, 48, 757, 165, timeline, regular, bold);
}

function drawMetricCard(
  page: PDFPage, x: number, y: number, width: number, height: number,
  label: string, value: string, regular: PDFFont, bold: PDFFont
): void {
  page.drawRectangle({ x, y, width, height, color: rgb(0.94, 0.97, 1), borderColor: rgb(0.79, 0.86, 0.95), borderWidth: 0.8 });
  page.drawText(label, { x: x + 12, y: y + height - 19, size: 9, font: regular, color: rgb(0.33, 0.4, 0.5) });
  page.drawText(value, { x: x + 12, y: y + 13, size: 19, font: bold, color: rgb(0.08, 0.27, 0.49) });
}

function drawBarChart(
  page: PDFPage, x: number, y: number, width: number, height: number,
  title: string, values: Readonly<Record<string, number>>, regular: PDFFont, bold: PDFFont
): void {
  page.drawRectangle({ x, y, width, height, color: rgb(0.985, 0.99, 1), borderColor: rgb(0.87, 0.9, 0.94), borderWidth: 0.7 });
  page.drawText(title, { x: x + 12, y: y + height - 21, size: 11, font: bold, color: rgb(0.12, 0.18, 0.28) });
  const entries = Object.entries(values).slice(0, 7);
  const max = Math.max(1, ...entries.map(([, count]) => count));
  const lineHeight = Math.min(21, (height - 40) / Math.max(1, entries.length));
  entries.forEach(([label, count], index) => {
    const lineY = y + height - 42 - index * lineHeight;
    page.drawText(pdfSafeText(truncate(label === UNKNOWN ? 'Unknown' : label, 18)), { x: x + 12, y: lineY, size: 7.5, font: regular });
    const barX = x + 100;
    const barWidth = Math.max(0, (width - 136) * count / max);
    page.drawRectangle({ x: barX, y: lineY - 1, width: barWidth, height: 8, color: rgb(0.2, 0.51, 0.78) });
    page.drawText(String(count), { x: x + width - 27, y: lineY, size: 7.5, font: bold });
  });
}

function drawTimelineChart(
  page: PDFPage, x: number, y: number, width: number, height: number,
  timeline: readonly GrowthTimelinePoint[], regular: PDFFont, bold: PDFFont
): void {
  page.drawRectangle({ x, y, width, height, color: rgb(0.985, 0.99, 1), borderColor: rgb(0.87, 0.9, 0.94), borderWidth: 0.7 });
  page.drawText('Member growth timeline', { x: x + 12, y: y + height - 21, size: 11, font: bold });
  const chart = { x: x + 42, y: y + 31, width: width - 62, height: height - 67 };
  page.drawLine({ start: { x: chart.x, y: chart.y }, end: { x: chart.x, y: chart.y + chart.height }, thickness: 0.6, color: rgb(0.55, 0.6, 0.67) });
  page.drawLine({ start: { x: chart.x, y: chart.y }, end: { x: chart.x + chart.width, y: chart.y }, thickness: 0.6, color: rgb(0.55, 0.6, 0.67) });
  if (timeline.length === 0) {
    page.drawText('No timeline data', { x: chart.x + 10, y: chart.y + chart.height / 2, size: 9, font: regular, color: rgb(0.45, 0.48, 0.53) });
    return;
  }
  const max = Math.max(1, ...timeline.map((point) => point.totalMembers));
  const points = timeline.map((point, index) => ({
    x: chart.x + (timeline.length === 1 ? chart.width / 2 : chart.width * index / (timeline.length - 1)),
    y: chart.y + chart.height * point.totalMembers / max,
    point
  }));
  points.slice(1).forEach((point, index) => page.drawLine({
    start: { x: points[index].x, y: points[index].y }, end: { x: point.x, y: point.y },
    thickness: 1.8, color: rgb(0.13, 0.55, 0.43)
  }));
  points.forEach(({ x: pointX, y: pointY }) => page.drawCircle({ x: pointX, y: pointY, size: 2.3, color: rgb(0.13, 0.55, 0.43) }));
  const labels = [...new Set([0, Math.floor((timeline.length - 1) / 2), timeline.length - 1])];
  labels.forEach((index) => page.drawText(timeline[index].period, { x: points[index].x - 18, y: chart.y - 14, size: 7, font: regular }));
  page.drawText(String(max), { x: chart.x - 25, y: chart.y + chart.height - 2, size: 7, font: regular });
}

function drawDistributionPages(pdf: PDFDocument, statistics: ReportStatistics, regular: PDFFont, bold: PDFFont): void {
  const sections: Array<[string, Record<string, number>]> = [
    ['Geographic distribution', statistics.geographicDistribution],
    ['Occupation distribution', statistics.occupationDistribution],
    ['Education distribution', statistics.educationDistribution]
  ];
  let page = pdf.addPage([PAGE_WIDTH, PAGE_HEIGHT]);
  let y = PAGE_HEIGHT - 45;
  page.drawText('Detailed distribution tables', { x: PAGE_MARGIN, y, size: 19, font: bold });
  y -= 32;

  for (const [title, values] of sections) {
    const entries = Object.entries(values);
    const rows = entries.length || 1;
    const requiredHeight = 32 + rows * 18;
    if (y - requiredHeight < 35) {
      page = pdf.addPage([PAGE_WIDTH, PAGE_HEIGHT]);
      y = PAGE_HEIGHT - 45;
    }
    page.drawText(title, { x: PAGE_MARGIN, y, size: 12, font: bold, color: rgb(0.08, 0.27, 0.49) });
    y -= 20;
    const data = entries.length ? entries : [['No data', 0] as [string, number]];
    for (const [label, count] of data) {
      if (y < 38) {
        page = pdf.addPage([PAGE_WIDTH, PAGE_HEIGHT]);
        y = PAGE_HEIGHT - 45;
        page.drawText(`${title} (continued)`, { x: PAGE_MARGIN, y, size: 12, font: bold });
        y -= 20;
      }
      page.drawText(pdfSafeText(truncate(label === UNKNOWN ? 'Unknown' : label, 80)), { x: PAGE_MARGIN + 8, y, size: 8.5, font: regular });
      page.drawText(String(count), { x: PAGE_WIDTH - PAGE_MARGIN - 40, y, size: 8.5, font: bold });
      page.drawLine({ start: { x: PAGE_MARGIN, y: y - 4 }, end: { x: PAGE_WIDTH - PAGE_MARGIN, y: y - 4 }, thickness: 0.25, color: rgb(0.87, 0.88, 0.9) });
      y -= 18;
    }
    y -= 15;
  }
}

function pdfSafeText(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[đĐ]/g, (character) => character === 'đ' ? 'd' : 'D').replace(/[^\x20-\x7E]/g, '?');
}

function truncate(value: string, maxLength: number): string {
  return value.length <= maxLength ? value : `${value.slice(0, Math.max(1, maxLength - 3))}...`;
}

function assertIdentifier(value: string, field: string): void {
  if (!value?.trim()) throw new ReportServiceError('INVALID_INPUT', `${field} is required`);
}

export const reportService = new ReportService();
export default reportService;
export const getStatistics = reportService.getStatistics.bind(reportService);
export const getBranchStatistics = reportService.getBranchStatistics.bind(reportService);
export const getGrowthTimeline = reportService.getGrowthTimeline.bind(reportService);
export const exportReportPDF = reportService.exportPDF.bind(reportService);
