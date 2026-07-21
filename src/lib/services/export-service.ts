import sharp from 'sharp';
import { PDFDocument, StandardFonts, rgb, type PDFFont, type PDFPage } from 'pdf-lib';
import type { Event, FamilyTree, MediaMetadata, Member, Relationship } from '@/data/types';
import { resolveTreeForUser } from './tree-data-provider';
import { getCompositeAuditLog, getCompositeConfig } from '@/lib/blob/readers';
import { getCanonicalParentChildEdges } from '@/lib/algorithms/ancestry';
import { calculateGenerations } from '@/lib/algorithms/generation';
import { getAlbums, getEvents, getMediaMetadata, getMembers, getRelationships, getTrees } from '@/lib/blob/readers';
import type {
  FamilyTreeExportDocument,
  ImageOptions,
  PDFOptions,
  PaperSize,
  PrintFont,
  PrintOptions,
  PrintPreview,
  SVGOptions
} from '@/types/import-export';

const PAPER_MM: Record<PaperSize, readonly [number, number]> = {
  A4: [210, 297],
  A3: [297, 420],
  A2: [420, 594],
  A1: [594, 841]
};
const NODE_WIDTH = 190;
const NODE_HEIGHT = 76;
const COLUMN_GAP = 42;
const ROW_GAP = 92;
const CANVAS_PADDING = 32;
const PRINT_MARGIN_PT = 28;
const MM_TO_PT = 72 / 25.4;

interface TreeData {
  tree: FamilyTree;
  members: Member[];
  relationships: Relationship[];
  events: Event[];
  mediaMetadata: MediaMetadata[];
  albums: Awaited<ReturnType<typeof getAlbums>>;
}

interface LayoutNode {
  member: Member;
  x: number;
  y: number;
}

interface TreeLayout {
  nodes: LayoutNode[];
  nodeById: Map<string, LayoutNode>;
  edges: Array<{ source: LayoutNode; target: LayoutNode; type: 'PARENT_CHILD' | 'SPOUSE' }>;
  width: number;
  height: number;
  generations: number;
}

interface NormalizedPrintOptions {
  paperSize: PaperSize;
  orientation: 'PORTRAIT' | 'LANDSCAPE';
  font: PrintFont;
  colorScheme: 'CLASSIC' | 'MONOCHROME' | 'EARTH';
  display: {
    showDates: boolean;
    showGender: boolean;
    showLocations: boolean;
    showMemberIds: boolean;
  };
  dpi: number;
}

export class ExportServiceError extends Error {
  constructor(
    public readonly code: 'INVALID_INPUT' | 'NOT_FOUND' | 'EXPORT_TOO_LARGE' | 'RENDER_FAILED',
    message: string
  ) {
    super(message);
    this.name = 'ExportServiceError';
  }
}

export class ExportService {
  async exportGEDCOM(treeId: string, userId?: string): Promise<Buffer> {
    const data = await loadTreeData(treeId, userId);
    const lines = [
      '0 HEAD',
      '1 SOUR FAMILY-GENEALOGY-MANAGEMENT',
      '2 VERS 1.0',
      '1 GEDC',
      '2 VERS 5.5',
      '2 FORM LINEAGE-LINKED',
      '1 CHAR UTF-8',
      `1 DATE ${isoToGedcomDate(new Date().toISOString())}`,
      `1 NOTE ${gedcomText(data.tree.name)}`,
      `1 NOTE Exported ${new Date().toISOString()} with source attribution in member notes`
    ];
    const xrefByMember = new Map(data.members.map((member, index) => [member.id, `@I${index + 1}@`]));
    for (const member of data.members) {
      lines.push(`0 ${xrefByMember.get(member.id)} INDI`);
      lines.push(`1 NAME ${gedcomName(member)}`);
      lines.push(`1 SEX ${member.gender === 'MALE' ? 'M' : member.gender === 'FEMALE' ? 'F' : 'U'}`);
      addGedcomEvent(lines, 'BIRT', member.dateOfBirth, member.placeOfBirth);
      if (member.dateOfDeath || !member.isAlive) addGedcomEvent(lines, 'DEAT', member.dateOfDeath);
      if (member.occupation) lines.push(`1 OCCU ${gedcomText(member.occupation)}`);
      if (member.notes) addGedcomMultiline(lines, 'NOTE', member.notes);
      const provenance = 'provenance' in member ? (member as Member & { provenance?: Array<{ treeId: string }> }).provenance : undefined;
      if (provenance?.length) lines.push(`1 NOTE Sources: ${[...new Set(provenance.map((item) => item.treeId))].join(', ')}`);
    }
    const families = buildGedcomFamilies(data.members, data.relationships);
    families.forEach((family, index) => {
      lines.push(`0 @F${index + 1}@ FAM`);
      if (family.husband) lines.push(`1 HUSB ${xrefByMember.get(family.husband)}`);
      if (family.wife) lines.push(`1 WIFE ${xrefByMember.get(family.wife)}`);
      if (!family.husband && family.parents[0]) lines.push(`1 HUSB ${xrefByMember.get(family.parents[0])}`);
      if (!family.wife && family.parents[1]) lines.push(`1 WIFE ${xrefByMember.get(family.parents[1])}`);
      for (const child of family.children) lines.push(`1 CHIL ${xrefByMember.get(child)}`);
      if (family.marriageDate) addGedcomEvent(lines, 'MARR', family.marriageDate);
      if (family.divorceDate) addGedcomEvent(lines, 'DIV', family.divorceDate);
    });
    lines.push('0 TRLR');
    return Buffer.from(`${lines.join('\r\n')}\r\n`, 'utf8');
  }

  async exportCompositeJSON(treeId: string, userId: string): Promise<string> {
    const [resolved, config, auditLog] = await Promise.all([resolveTreeForUser(treeId, userId), getCompositeConfig(treeId), getCompositeAuditLog(treeId)]);
    if (!config) throw new ExportServiceError('INVALID_INPUT', 'Tree is not composite');
    const authorizedSources = new Set(resolved.sourceManifest.filter((source) => source.status === 'ACTIVE').map((source) => source.sourceTreeId));
    const safeConfig = { ...config, sources: config.sources.map((source) => authorizedSources.has(source.sourceTreeId) ? source : { ...source, anchorMemberIds: [], selectedMemberIds: [] }), identityGroups: config.identityGroups.map((group) => ({ ...group, references: group.references.filter((reference) => authorizedSources.has(reference.treeId)) })).filter((group) => group.references.length > 1), crossTreeRelationships: config.crossTreeRelationships.filter((relationship) => authorizedSources.has(relationship.source.treeId) && authorizedSources.has(relationship.target.treeId)) };
    return JSON.stringify({ schema: 'family-genealogy-management/composite', version: 1, exportedAt: new Date().toISOString(), tree: { ...resolved.tree, ownerId: '', memberships: [] }, config: safeConfig, sourceManifest: resolved.sourceManifest, provenance: { members: resolved.members.map((item) => ({ id: item.id, provenance: item.provenance.filter((entry) => authorizedSources.has(entry.treeId)) })), relationships: resolved.relationships.map((item) => ({ id: item.id, provenance: item.provenance.filter((entry) => authorizedSources.has(entry.treeId)) })) }, auditLog: auditLog.map(({ actorId: _actorId, ...entry }) => entry) }, null, 2);
  }

  async exportJSON(treeId: string, userId?: string): Promise<string> {
    const data = await loadTreeData(treeId, userId);
    const document: FamilyTreeExportDocument = {
      schema: 'family-genealogy-management/export',
      version: '1.0',
      exportedAt: new Date().toISOString(),
      ...data
    };
    return JSON.stringify(document, null, 2);
  }

  async exportSVG(treeId: string, options: SVGOptions = {}, userId?: string): Promise<string> {
    const data = await loadTreeData(treeId, userId);
    return renderSvg(data.tree, createTreeLayout(data.members, data.relationships), normalizeOptions(options));
  }

  async exportImage(treeId: string, options: ImageOptions = {}, userId?: string): Promise<Buffer> {
    const normalized = normalizeOptions(options);
    if (normalized.dpi < 300) throw new ExportServiceError('INVALID_INPUT', 'PNG export requires at least 300 DPI');
    const data = await loadTreeData(treeId, userId);
    const layout = createTreeLayout(data.members, data.relationships);
    const scale = normalized.dpi / 96;
    if (layout.width * scale * layout.height * scale > 160_000_000) {
      throw new ExportServiceError(
        'EXPORT_TOO_LARGE',
        'The requested 300 DPI PNG exceeds the safe 160 megapixel render limit; use SVG or PDF for this tree'
      );
    }
    const svg = renderSvg(data.tree, layout, normalized);
    try {
      return await sharp(Buffer.from(svg), { density: normalized.dpi, limitInputPixels: 160_000_000 })
        .png({ compressionLevel: 9, adaptiveFiltering: true })
        .withMetadata({ density: normalized.dpi })
        .toBuffer();
    } catch (error) {
      throw renderError(error, 'PNG');
    }
  }

  async exportPDF(treeId: string, options: PDFOptions = {}, userId?: string): Promise<Buffer> {
    const normalized = normalizeOptions(options);
    const data = await loadTreeData(treeId, userId);
    const layout = createTreeLayout(data.members, data.relationships);
    const pdf = await PDFDocument.create();
    pdf.setTitle(data.tree.name);
    pdf.setSubject('Family genealogy tree export');
    pdf.setCreator('Family Genealogy Management');
    pdf.setProducer('Family Genealogy Management ExportService');
    pdf.setCreationDate(new Date());
    const regular = await pdf.embedFont(pdfFontName(normalized.font, false));
    const bold = await pdf.embedFont(pdfFontName(normalized.font, true));
    const [pageWidth, pageHeight] = paperPoints(normalized.paperSize, normalized.orientation);
    const printableWidth = pageWidth - PRINT_MARGIN_PT * 2;
    const printableHeight = pageHeight - PRINT_MARGIN_PT * 2 - 24;
    const treeScale = 0.72;
    const tileWidth = printableWidth / treeScale;
    const tileHeight = printableHeight / treeScale;
    const columns = Math.max(1, Math.ceil(layout.width / tileWidth));
    const rows = Math.max(1, Math.ceil(layout.height / tileHeight));
    const colors = palette(normalized.colorScheme);

    for (let row = 0; row < rows; row += 1) {
      for (let column = 0; column < columns; column += 1) {
        const page = pdf.addPage([pageWidth, pageHeight]);
        drawTreeTile(page, layout, {
          row, column, rows, columns, tileWidth, tileHeight, scale: treeScale,
          pageWidth, pageHeight, regular, bold, normalized, colors
        });
      }
    }
    drawStatisticsPage(pdf, data, layout, normalized, regular, bold, pageWidth, pageHeight);
    drawMemberPages(pdf, data.members, normalized, regular, bold, pageWidth, pageHeight);
    return Buffer.from(await pdf.save());
  }

  async createPrintPreview(treeId: string, options: PrintOptions = {}, userId?: string): Promise<PrintPreview> {
    const normalized = normalizeOptions(options);
    const data = await loadTreeData(treeId, userId);
    const layout = createTreeLayout(data.members, data.relationships);
    let [widthMm, heightMm] = PAPER_MM[normalized.paperSize];
    if (normalized.orientation === 'LANDSCAPE') [widthMm, heightMm] = [heightMm, widthMm];
    const printableWidthPx = Math.max(1, (widthMm - 20) * 96 / 25.4);
    const printableHeightPx = Math.max(1, (heightMm - 26) * 96 / 25.4);
    const columns = Math.max(1, Math.ceil(layout.width / printableWidthPx));
    const rows = Math.max(1, Math.ceil(layout.height / printableHeightPx));
    return {
      paperSize: normalized.paperSize,
      orientation: normalized.orientation,
      widthMm,
      heightMm,
      treeWidth: layout.width,
      treeHeight: layout.height,
      pageCount: columns * rows,
      columns,
      rows,
      svg: renderSvg(data.tree, layout, normalized)
    };
  }
}

function createTreeLayout(members: Member[], relationships: Relationship[]): TreeLayout {
  const generations = calculateGenerations(members, relationships);
  const byGeneration = new Map<number, Member[]>();
  for (const member of members) {
    const generation = member.generation ?? generations.get(member.id) ?? 0;
    const group = byGeneration.get(generation) ?? [];
    group.push(member);
    byGeneration.set(generation, group);
  }
  const orderedGenerations = [...byGeneration.keys()].sort((a, b) => a - b);
  const maxPerRow = Math.max(1, ...[...byGeneration.values()].map((group) => group.length));
  const width = Math.max(420, CANVAS_PADDING * 2 + maxPerRow * NODE_WIDTH + (maxPerRow - 1) * COLUMN_GAP);
  const nodes: LayoutNode[] = [];
  orderedGenerations.forEach((generation, row) => {
    const group = byGeneration.get(generation)!.sort((a, b) => a.fullName.localeCompare(b.fullName, 'vi'));
    const rowWidth = group.length * NODE_WIDTH + Math.max(0, group.length - 1) * COLUMN_GAP;
    const startX = (width - rowWidth) / 2;
    group.forEach((member, column) => nodes.push({
      member,
      x: startX + column * (NODE_WIDTH + COLUMN_GAP),
      y: CANVAS_PADDING + row * (NODE_HEIGHT + ROW_GAP)
    }));
  });
  const nodeById = new Map(nodes.map((node) => [node.member.id, node]));
  const edges: TreeLayout['edges'] = [];
  for (const edge of getCanonicalParentChildEdges(members, relationships)) {
    const source = nodeById.get(edge.parentId);
    const target = nodeById.get(edge.childId);
    if (source && target) edges.push({ source, target, type: 'PARENT_CHILD' });
  }
  const spousePairs = new Set<string>();
  for (const relationship of relationships) {
    if (relationship.type !== 'SPOUSE') continue;
    const ids = [relationship.sourceMemberId, relationship.targetMemberId].sort();
    const key = ids.join('\0');
    if (spousePairs.has(key)) continue;
    spousePairs.add(key);
    const source = nodeById.get(ids[0]);
    const target = nodeById.get(ids[1]);
    if (source && target) edges.push({ source, target, type: 'SPOUSE' });
  }
  return {
    nodes,
    nodeById,
    edges,
    width,
    height: Math.max(240, CANVAS_PADDING * 2 + orderedGenerations.length * NODE_HEIGHT + Math.max(0, orderedGenerations.length - 1) * ROW_GAP),
    generations: orderedGenerations.length
  };
}

function renderSvg(tree: FamilyTree, layout: TreeLayout, options: NormalizedPrintOptions): string {
  const colors = palette(options.colorScheme);
  const lines = [
    `<?xml version="1.0" encoding="UTF-8"?>`,
    `<svg xmlns="http://www.w3.org/2000/svg" width="${layout.width}" height="${layout.height + 46}" viewBox="0 0 ${layout.width} ${layout.height + 46}" role="img" aria-labelledby="tree-title">`,
    `<title id="tree-title">${xml(tree.name)}</title>`,
    `<metadata>Exported ${xml(new Date().toISOString())}; attribution preserved in resolved source provenance</metadata>`,
    `<rect width="100%" height="100%" fill="${colors.background}"/>`,
    `<text x="${layout.width / 2}" y="28" text-anchor="middle" font-family="${svgFont(options.font)}" font-size="20" font-weight="700" fill="${colors.text}">${xml(tree.name)}</text>`,
    `<g transform="translate(0 40)">`
  ];
  for (const edge of layout.edges) {
    if (edge.type === 'PARENT_CHILD') {
      const x1 = edge.source.x + NODE_WIDTH / 2;
      const y1 = edge.source.y + NODE_HEIGHT;
      const x2 = edge.target.x + NODE_WIDTH / 2;
      const y2 = edge.target.y;
      const mid = (y1 + y2) / 2;
      lines.push(`<path d="M ${x1} ${y1} V ${mid} H ${x2} V ${y2}" fill="none" stroke="${colors.line}" stroke-width="2"/>`);
    } else {
      lines.push(`<line x1="${edge.source.x + NODE_WIDTH / 2}" y1="${edge.source.y + NODE_HEIGHT / 2}" x2="${edge.target.x + NODE_WIDTH / 2}" y2="${edge.target.y + NODE_HEIGHT / 2}" stroke="${colors.spouse}" stroke-width="2" stroke-dasharray="6 4"/>`);
    }
  }
  for (const node of layout.nodes) {
    const fill = node.member.gender === 'FEMALE' ? colors.female : node.member.gender === 'MALE' ? colors.male : colors.other;
    lines.push(`<g transform="translate(${node.x} ${node.y})">`);
    lines.push(`<rect width="${NODE_WIDTH}" height="${NODE_HEIGHT}" rx="10" fill="${fill}" stroke="${colors.border}" stroke-width="1.5"/>`);
    lines.push(`<text x="12" y="23" font-family="${svgFont(options.font)}" font-size="13" font-weight="700" fill="${colors.text}">${xml(truncate(node.member.fullName, 25))}</text>`);
    const details = memberDetails(node.member, options);
    details.slice(0, 2).forEach((detail, index) => lines.push(
      `<text x="12" y="${43 + index * 17}" font-family="${svgFont(options.font)}" font-size="10" fill="${colors.muted}">${xml(truncate(detail, 32))}</text>`
    ));
    lines.push('</g>');
  }
  lines.push('</g>', '</svg>');
  return lines.join('');
}

interface TileContext {
  row: number; column: number; rows: number; columns: number;
  tileWidth: number; tileHeight: number; scale: number;
  pageWidth: number; pageHeight: number; regular: PDFFont; bold: PDFFont;
  normalized: NormalizedPrintOptions;
  colors: ReturnType<typeof palette>;
}

function drawTreeTile(page: PDFPage, layout: TreeLayout, context: TileContext): void {
  const { row, column, rows, columns, tileWidth, tileHeight, scale, pageWidth, pageHeight, regular, bold, normalized, colors } = context;
  const offsetX = column * tileWidth;
  const offsetY = row * tileHeight;
  page.drawText(pdfSafeText(`Family tree - page ${row * columns + column + 1}/${rows * columns}`), {
    x: PRINT_MARGIN_PT, y: pageHeight - 21, size: 9, font: bold, color: hexRgb(colors.text)
  });
  const toPage = (x: number, y: number) => ({
    x: PRINT_MARGIN_PT + (x - offsetX) * scale,
    y: pageHeight - PRINT_MARGIN_PT - 24 - (y - offsetY) * scale
  });
  for (const edge of layout.edges) {
    const start = toPage(edge.source.x + NODE_WIDTH / 2, edge.source.y + NODE_HEIGHT / 2);
    const end = toPage(edge.target.x + NODE_WIDTH / 2, edge.target.y + NODE_HEIGHT / 2);
    if (!lineTouchesPage(start, end, pageWidth, pageHeight)) continue;
    page.drawLine({ start, end, thickness: edge.type === 'SPOUSE' ? 0.8 : 1.1, color: hexRgb(edge.type === 'SPOUSE' ? colors.spouse : colors.line), dashArray: edge.type === 'SPOUSE' ? [4, 3] : undefined });
  }
  for (const node of layout.nodes) {
    if (node.x + NODE_WIDTH < offsetX || node.x > offsetX + tileWidth || node.y + NODE_HEIGHT < offsetY || node.y > offsetY + tileHeight) continue;
    const topLeft = toPage(node.x, node.y);
    const width = NODE_WIDTH * scale;
    const height = NODE_HEIGHT * scale;
    const fill = node.member.gender === 'FEMALE' ? colors.female : node.member.gender === 'MALE' ? colors.male : colors.other;
    page.drawRectangle({ x: topLeft.x, y: topLeft.y - height, width, height, color: hexRgb(fill), borderColor: hexRgb(colors.border), borderWidth: 0.8 });
    page.drawText(pdfSafeText(truncate(node.member.fullName, 27)), { x: topLeft.x + 6, y: topLeft.y - 15, size: 8.5, font: bold, color: hexRgb(colors.text) });
    memberDetails(node.member, normalized).slice(0, 2).forEach((detail, index) => page.drawText(pdfSafeText(truncate(detail, 36)), {
      x: topLeft.x + 6, y: topLeft.y - 29 - index * 11, size: 6.7, font: regular, color: hexRgb(colors.muted)
    }));
  }
  drawJoinGuides(page, row, column, rows, columns, pageWidth, pageHeight, regular);
}

function drawJoinGuides(page: PDFPage, row: number, column: number, rows: number, columns: number, width: number, height: number, font: PDFFont): void {
  const color = rgb(0.45, 0.45, 0.45);
  if (column > 0) page.drawText(`<- ${column}`, { x: 5, y: height / 2, size: 7, font, color });
  if (column < columns - 1) page.drawText(`${column + 2} ->`, { x: width - 25, y: height / 2, size: 7, font, color });
  if (row > 0) page.drawText(`^ ${row}`, { x: width / 2, y: height - 10, size: 7, font, color });
  if (row < rows - 1) page.drawText(`v ${row + 2}`, { x: width / 2, y: 5, size: 7, font, color });
}

function drawStatisticsPage(
  pdf: PDFDocument, data: TreeData, layout: TreeLayout, options: NormalizedPrintOptions,
  regular: PDFFont, bold: PDFFont, width: number, height: number
): void {
  const page = pdf.addPage([width, height]);
  const living = data.members.filter((member) => member.isAlive).length;
  const stats = [
    ['Members', data.members.length],
    ['Generations', layout.generations],
    ['Living', living],
    ['Deceased', data.members.length - living],
    ['Male', data.members.filter((member) => member.gender === 'MALE').length],
    ['Female', data.members.filter((member) => member.gender === 'FEMALE').length],
    ['Events', data.events.length],
    ['Media records', data.mediaMetadata.length]
  ] as const;
  page.drawText(pdfSafeText(`${data.tree.name} - Statistics`), { x: 40, y: height - 58, size: 20, font: bold });
  stats.forEach(([label, value], index) => {
    const column = index % 2;
    const row = Math.floor(index / 2);
    const x = 42 + column * ((width - 84) / 2);
    const y = height - 110 - row * 58;
    page.drawRectangle({ x, y: y - 24, width: (width - 100) / 2, height: 42, color: rgb(0.95, 0.97, 0.98) });
    page.drawText(label, { x: x + 10, y: y + 2, size: 9, font: regular, color: rgb(0.35, 0.38, 0.42) });
    page.drawText(String(value), { x: x + 10, y: y - 17, size: 16, font: bold });
  });
  page.drawText(`Paper: ${options.paperSize} / ${options.orientation}`, { x: 42, y: 35, size: 8, font: regular, color: rgb(0.45, 0.45, 0.45) });
}

function drawMemberPages(
  pdf: PDFDocument, members: Member[], options: NormalizedPrintOptions,
  regular: PDFFont, bold: PDFFont, width: number, height: number
): void {
  const lineHeight = 20;
  const perPage = Math.max(1, Math.floor((height - 105) / lineHeight));
  const sorted = [...members].sort((a, b) => a.fullName.localeCompare(b.fullName, 'vi'));
  for (let start = 0; start < Math.max(1, sorted.length); start += perPage) {
    const page = pdf.addPage([width, height]);
    page.drawText('Member list', { x: 40, y: height - 48, size: 18, font: bold });
    const chunk = sorted.slice(start, start + perPage);
    chunk.forEach((member, index) => {
      const y = height - 82 - index * lineHeight;
      page.drawText(pdfSafeText(`${start + index + 1}. ${member.fullName}`), { x: 42, y, size: 9, font: bold });
      const details = memberDetails(member, options).join(' | ');
      if (details) page.drawText(pdfSafeText(truncate(details, Math.max(40, Math.floor(width / 5)))), { x: width * 0.48, y, size: 8, font: regular, color: rgb(0.35, 0.35, 0.35) });
      page.drawLine({ start: { x: 40, y: y - 5 }, end: { x: width - 40, y: y - 5 }, thickness: 0.3, color: rgb(0.85, 0.85, 0.85) });
    });
  }
}

function buildGedcomFamilies(members: Member[], relationships: Relationship[]): Array<{
  husband?: string; wife?: string; parents: string[]; children: string[]; marriageDate?: string; divorceDate?: string;
}> {
  const memberById = new Map(members.map((member) => [member.id, member]));
  const spouseById = new Map<string, string>();
  const spouseMetadata = new Map<string, Relationship>();
  for (const relationship of relationships) {
    if (relationship.type !== 'SPOUSE') continue;
    if (!memberById.has(relationship.sourceMemberId) || !memberById.has(relationship.targetMemberId)) continue;
    spouseById.set(relationship.sourceMemberId, relationship.targetMemberId);
    spouseMetadata.set([relationship.sourceMemberId, relationship.targetMemberId].sort().join('\0'), relationship);
  }
  const families = new Map<string, { parents: string[]; children: Set<string> }>();
  for (const edge of getCanonicalParentChildEdges(members, relationships)) {
    const spouse = spouseById.get(edge.parentId);
    const parents = spouse ? [edge.parentId, spouse].sort() : [edge.parentId];
    const key = parents.join('\0');
    const family = families.get(key) ?? { parents, children: new Set<string>() };
    family.children.add(edge.childId);
    families.set(key, family);
  }
  for (const relationship of relationships) {
    if (relationship.type !== 'SPOUSE') continue;
    if (!memberById.has(relationship.sourceMemberId) || !memberById.has(relationship.targetMemberId)) continue;
    const parents = [relationship.sourceMemberId, relationship.targetMemberId].sort();
    const key = parents.join('\0');
    if (!families.has(key)) families.set(key, { parents, children: new Set() });
  }
  return [...families.entries()].map(([key, family]) => {
    const first = memberById.get(family.parents[0]);
    const second = memberById.get(family.parents[1]);
    const husband = [first, second].find((member) => member?.gender === 'MALE')?.id;
    const wife = [first, second].find((member) => member?.gender === 'FEMALE')?.id;
    const metadata = spouseMetadata.get(key);
    return {
      ...(husband ? { husband } : {}),
      ...(wife ? { wife } : {}),
      parents: family.parents,
      children: [...family.children],
      ...(metadata?.marriageDate ? { marriageDate: metadata.marriageDate } : {}),
      ...(metadata?.divorceDate ? { divorceDate: metadata.divorceDate } : {})
    };
  });
}

async function loadTreeData(treeId: string, userId?: string): Promise<TreeData> {
  if (!treeId?.trim()) throw new ExportServiceError('INVALID_INPUT', 'treeId is required');
  const tree = (await getTrees()).find((item) => item.id === treeId);
  if (!tree) throw new ExportServiceError('NOT_FOUND', 'Family tree not found');
  if ((tree.kind ?? 'STANDALONE') === 'COMPOSITE') {
    if (!userId) throw new ExportServiceError('INVALID_INPUT', 'userId is required for composite export');
    const resolved = await resolveTreeForUser(treeId, userId);
    return { tree: resolved.tree, members: resolved.members, relationships: resolved.relationships, events: resolved.events, mediaMetadata: resolved.mediaMetadata, albums: [] };
  }
  const [members, relationships, events, mediaMetadata, albums] = await Promise.all([getMembers(treeId), getRelationships(treeId), getEvents(treeId), getMediaMetadata(treeId), getAlbums(treeId)]);
  return { tree, members, relationships, events, mediaMetadata, albums };
}

function normalizeOptions(options: PrintOptions): NormalizedPrintOptions {
  const paperSize = options.paperSize ?? 'A4';
  const orientation = options.orientation ?? 'LANDSCAPE';
  const font = options.font ?? 'HELVETICA';
  const colorScheme = options.colorScheme ?? 'CLASSIC';
  const dpi = options.dpi ?? 300;
  if (!PAPER_MM[paperSize]) throw new ExportServiceError('INVALID_INPUT', 'paperSize must be A4, A3, A2, or A1');
  if (!['PORTRAIT', 'LANDSCAPE'].includes(orientation)) throw new ExportServiceError('INVALID_INPUT', 'Invalid orientation');
  if (!['HELVETICA', 'TIMES_ROMAN', 'COURIER'].includes(font)) throw new ExportServiceError('INVALID_INPUT', 'Invalid font');
  if (!['CLASSIC', 'MONOCHROME', 'EARTH'].includes(colorScheme)) throw new ExportServiceError('INVALID_INPUT', 'Invalid color scheme');
  if (!Number.isFinite(dpi) || dpi < 1 || dpi > 600) throw new ExportServiceError('INVALID_INPUT', 'dpi must be between 1 and 600');
  return {
    paperSize, orientation, font, colorScheme, dpi,
    display: {
      showDates: options.display?.showDates ?? true,
      showGender: options.display?.showGender ?? true,
      showLocations: options.display?.showLocations ?? false,
      showMemberIds: options.display?.showMemberIds ?? false
    }
  };
}

function memberDetails(member: Member, options: NormalizedPrintOptions): string[] {
  const details: string[] = [];
  if (options.display.showDates) {
    const lifespan = [member.dateOfBirth?.slice(0, 10), member.dateOfDeath?.slice(0, 10)].filter(Boolean).join(' - ');
    if (lifespan) details.push(lifespan);
  }
  if (options.display.showGender) details.push(member.gender);
  if (options.display.showLocations && (member.currentAddress || member.placeOfBirth)) details.push(member.currentAddress ?? member.placeOfBirth!);
  if (options.display.showMemberIds) details.push(`ID: ${member.id}`);
  return details;
}

function paperPoints(size: PaperSize, orientation: 'PORTRAIT' | 'LANDSCAPE'): [number, number] {
  let [width, height] = PAPER_MM[size].map((value) => value * MM_TO_PT) as [number, number];
  if (orientation === 'LANDSCAPE') [width, height] = [height, width];
  return [width, height];
}

function palette(scheme: NormalizedPrintOptions['colorScheme']) {
  if (scheme === 'MONOCHROME') return { background: '#ffffff', text: '#111111', muted: '#444444', line: '#333333', spouse: '#666666', border: '#222222', male: '#f2f2f2', female: '#e3e3e3', other: '#fafafa' };
  if (scheme === 'EARTH') return { background: '#fffdf7', text: '#332d24', muted: '#675f52', line: '#8a7653', spouse: '#a45f4f', border: '#816f50', male: '#e8dfc8', female: '#efcfbd', other: '#e3e1c6' };
  return { background: '#f8fafc', text: '#172033', muted: '#536177', line: '#64748b', spouse: '#9c4dcc', border: '#7b8ba5', male: '#dceeff', female: '#ffe1ed', other: '#e8e4ff' };
}

function pdfFontName(font: PrintFont, bold: boolean): StandardFonts {
  if (font === 'COURIER') return bold ? StandardFonts.CourierBold : StandardFonts.Courier;
  if (font === 'TIMES_ROMAN') return bold ? StandardFonts.TimesRomanBold : StandardFonts.TimesRoman;
  return bold ? StandardFonts.HelveticaBold : StandardFonts.Helvetica;
}

function svgFont(font: PrintFont): string {
  if (font === 'COURIER') return 'Courier New, monospace';
  if (font === 'TIMES_ROMAN') return 'Times New Roman, serif';
  return 'Inter, Arial, sans-serif';
}

function hexRgb(hex: string) {
  const value = Number.parseInt(hex.slice(1), 16);
  return rgb(((value >> 16) & 255) / 255, ((value >> 8) & 255) / 255, (value & 255) / 255);
}

function lineTouchesPage(start: { x: number; y: number }, end: { x: number; y: number }, width: number, height: number): boolean {
  return !((start.x < 0 && end.x < 0) || (start.x > width && end.x > width) || (start.y < 0 && end.y < 0) || (start.y > height && end.y > height));
}

function addGedcomEvent(lines: string[], tag: string, date?: string, place?: string): void {
  if (!date && !place) return;
  lines.push(`1 ${tag}`);
  if (date) lines.push(`2 DATE ${isoToGedcomDate(date)}`);
  if (place) lines.push(`2 PLAC ${gedcomText(place)}`);
}

function addGedcomMultiline(lines: string[], tag: string, value: string): void {
  const [first, ...rest] = value.replace(/\r/g, '').split('\n');
  lines.push(`1 ${tag} ${gedcomText(first)}`);
  rest.forEach((line) => lines.push(`2 CONT ${gedcomText(line)}`));
}

function isoToGedcomDate(value: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!match) return value;
  const months = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];
  return `${Number(match[3])} ${months[Number(match[2]) - 1]} ${match[1]}`;
}

function gedcomName(member: Member): string {
  return `${gedcomText(member.firstName)} /${gedcomText(member.lastName)}/`;
}

function gedcomText(value: string): string {
  return value.replace(/[\r\n]+/g, ' ').trim();
}

function pdfSafeText(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[đĐ]/g, (char) => char === 'đ' ? 'd' : 'D').replace(/[^\x20-\x7E]/g, '?');
}

function xml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;');
}

function truncate(value: string, length: number): string {
  return value.length <= length ? value : `${value.slice(0, Math.max(1, length - 1))}…`;
}

function renderError(error: unknown, format: string): ExportServiceError {
  const wrapped = new ExportServiceError('RENDER_FAILED', `Could not render ${format} export`);
  Object.defineProperty(wrapped, 'cause', { value: error, enumerable: false });
  return wrapped;
}

export const exportService = new ExportService();
export default exportService;
export const exportGEDCOM = exportService.exportGEDCOM.bind(exportService);
export const exportJSON = exportService.exportJSON.bind(exportService);
export const exportPDF = exportService.exportPDF.bind(exportService);
export const exportImage = exportService.exportImage.bind(exportService);
export const exportSVG = exportService.exportSVG.bind(exportService);
export const createPrintPreview = exportService.createPrintPreview.bind(exportService);
