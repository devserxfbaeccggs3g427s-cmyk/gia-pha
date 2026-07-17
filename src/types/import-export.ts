import type { Album, Event, FamilyTree, MediaMetadata, Member, Relationship } from '@/data/types';

export type ImportFormat = 'GEDCOM' | 'JSON' | 'CSV';
export type ImportIssueSeverity = 'ERROR' | 'WARNING';

export interface ImportIssue {
  severity: ImportIssueSeverity;
  code:
    | 'SYNTAX_ERROR'
    | 'INVALID_VALUE'
    | 'MISSING_FIELD'
    | 'DUPLICATE_ID'
    | 'BROKEN_REFERENCE'
    | 'UNSUPPORTED_VALUE';
  message: string;
  line: number;
  column?: number;
  path?: string;
}

/** The normalized, validated representation shared by every importer. */
export interface ParsedImportData {
  format: ImportFormat;
  version: '1.0';
  tree?: FamilyTree;
  members: Member[];
  relationships: Relationship[];
  events: Event[];
  mediaMetadata: MediaMetadata[];
  albums: Album[];
  issues: ImportIssue[];
}

export type ParsedGEDCOM = ParsedImportData & { format: 'GEDCOM' };
export type ParsedJSON = ParsedImportData & { format: 'JSON' };
export type ParsedCSV = ParsedImportData & { format: 'CSV' };

export interface ImportPreview {
  format: ImportFormat;
  valid: boolean;
  counts: {
    members: number;
    relationships: number;
    events: number;
    media: number;
    albums: number;
    errors: number;
    warnings: number;
  };
  sampleMembers: Member[];
  issues: ImportIssue[];
}

export interface ImportOptions {
  mode?: 'APPEND' | 'REPLACE';
  conflictStrategy?: 'SKIP' | 'OVERWRITE' | 'REGENERATE';
}

export interface ImportResult {
  treeId: string;
  mode: 'APPEND' | 'REPLACE';
  imported: {
    members: number;
    relationships: number;
    events: number;
    media: number;
    albums: number;
  };
  skipped: number;
  warnings: ImportIssue[];
}

export type PaperSize = 'A4' | 'A3' | 'A2' | 'A1';
export type PrintOrientation = 'PORTRAIT' | 'LANDSCAPE';
export type PrintFont = 'HELVETICA' | 'TIMES_ROMAN' | 'COURIER';

export interface PrintDisplayOptions {
  showDates?: boolean;
  showGender?: boolean;
  showLocations?: boolean;
  showMemberIds?: boolean;
}

export interface PrintOptions {
  paperSize?: PaperSize;
  orientation?: PrintOrientation;
  font?: PrintFont;
  colorScheme?: 'CLASSIC' | 'MONOCHROME' | 'EARTH';
  display?: PrintDisplayOptions;
  /** Output raster density. Values below 300 are rejected. */
  dpi?: number;
}

export type PDFOptions = PrintOptions;
export type ImageOptions = PrintOptions;
export type SVGOptions = Omit<PrintOptions, 'dpi'>;

export interface PrintPreview {
  paperSize: PaperSize;
  orientation: PrintOrientation;
  widthMm: number;
  heightMm: number;
  treeWidth: number;
  treeHeight: number;
  pageCount: number;
  columns: number;
  rows: number;
  svg: string;
}

export interface FamilyTreeExportDocument {
  schema: 'family-genealogy-management/export';
  version: '1.0';
  exportedAt: string;
  tree: FamilyTree;
  members: Member[];
  relationships: Relationship[];
  events: Event[];
  mediaMetadata: MediaMetadata[];
  albums: Album[];
}
