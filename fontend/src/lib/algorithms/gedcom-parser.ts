import type { Gender, Member, Relationship } from '@/data/types';
import type { ImportIssue, ParsedGEDCOM } from '@/types/import-export';

export interface GEDCOMRecord {
  level: number;
  tag: string;
  value?: string;
  xref?: string;
  line: number;
  children: GEDCOMRecord[];
}

const GEDCOM_LINE = /^(\d+)\s+(?:(@[^@\s]+@)\s+)?([A-Za-z0-9_]+)(?:\s+(.*))?$/;
const MONTHS: Record<string, number> = {
  JAN: 1, FEB: 2, MAR: 3, APR: 4, MAY: 5, JUN: 6,
  JUL: 7, AUG: 8, SEP: 9, OCT: 10, NOV: 11, DEC: 12
};

/** Parse the GEDCOM 5.5 line-oriented grammar and convert INDI/FAM records. */
export function parseGEDCOMContent(content: string): ParsedGEDCOM {
  const issues: ImportIssue[] = [];
  const records = parseRecords(stripBom(content), issues);
  const now = new Date().toISOString();
  const individuals = records.filter((record) => record.level === 0 && record.tag === 'INDI');
  const families = records.filter((record) => record.level === 0 && record.tag === 'FAM');
  const memberByXref = new Map<string, Member>();

  for (const [index, record] of individuals.entries()) {
    const xref = record.xref;
    if (!xref) {
      issues.push(issue('MISSING_FIELD', 'GEDCOM INDI record requires an xref identifier', record.line));
      continue;
    }
    if (memberByXref.has(xref)) {
      issues.push(issue('DUPLICATE_ID', `Duplicate GEDCOM individual identifier ${xref}`, record.line));
      continue;
    }
    const nameRecord = child(record, 'NAME');
    const parsedName = parseName(nameRecord?.value ?? '');
    if (!parsedName.fullName) {
      issues.push(issue('MISSING_FIELD', `Individual ${xref} is missing NAME`, nameRecord?.line ?? record.line));
      continue;
    }
    const birth = parseEventDate(child(record, 'BIRT'), 'birth', issues);
    const death = parseEventDate(child(record, 'DEAT'), 'death', issues);
    const sexRecord = child(record, 'SEX');
    const gender = parseGender(sexRecord?.value, sexRecord?.line ?? record.line, issues);
    const id = gedcomId('member', xref, index);
    const member: Member = {
      id,
      treeId: 'imported',
      firstName: parsedName.firstName,
      lastName: parsedName.lastName,
      fullName: parsedName.fullName,
      gender,
      ...(birth.date ? { dateOfBirth: birth.date } : {}),
      ...(death.date ? { dateOfDeath: death.date } : {}),
      ...(birth.place ? { placeOfBirth: birth.place } : {}),
      ...(child(record, 'OCCU')?.value ? { occupation: child(record, 'OCCU')!.value } : {}),
      ...(child(record, 'NOTE')?.value ? { notes: collectText(child(record, 'NOTE')!) } : {}),
      isAlive: !death.present,
      createdAt: now,
      updatedAt: now
    };
    memberByXref.set(xref, member);
  }

  const relationships: Relationship[] = [];
  let relationshipSequence = 0;
  const addPair = (
    source: Member,
    target: Member,
    type: Relationship['type'],
    metadata: Partial<Relationship> = {}
  ) => {
    const base = { treeId: 'imported', type, createdAt: now, ...metadata };
    relationships.push({
      ...base,
      id: `gedcom-relationship-${++relationshipSequence}`,
      sourceMemberId: source.id,
      targetMemberId: target.id
    });
    relationships.push({
      ...base,
      id: `gedcom-relationship-${++relationshipSequence}`,
      sourceMemberId: target.id,
      targetMemberId: source.id
    });
  };

  for (const family of families) {
    const husband = resolvePointer(family, 'HUSB', memberByXref, issues);
    const wife = resolvePointer(family, 'WIFE', memberByXref, issues);
    const marriage = parseEventDate(child(family, 'MARR'), 'marriage', issues);
    const divorce = parseEventDate(child(family, 'DIV'), 'divorce', issues);
    if (husband && wife) {
      addPair(husband, wife, 'SPOUSE', {
        ...(marriage.date ? { marriageDate: `${marriage.date}T00:00:00.000Z` } : {}),
        ...(divorce.date ? { divorceDate: `${divorce.date}T00:00:00.000Z`, marriageStatus: 'DIVORCED' } : {}),
        ...(!divorce.date && marriage.present ? { marriageStatus: 'MARRIED' } : {})
      });
    }
    const parents = [husband, wife].filter((member): member is Member => Boolean(member));
    for (const childRecord of children(family, 'CHIL')) {
      const childMember = pointerMember(childRecord.value, memberByXref);
      if (!childMember) {
        issues.push(issue('BROKEN_REFERENCE', `Unknown CHIL reference ${childRecord.value ?? '(empty)'}`, childRecord.line));
        continue;
      }
      for (const parent of parents) addPair(parent, childMember, 'PARENT_CHILD');
    }
  }

  if (!records.some((record) => record.tag === 'HEAD')) {
    issues.push(issue('MISSING_FIELD', 'GEDCOM file is missing the HEAD record', 1));
  }
  if (!records.some((record) => record.tag === 'TRLR')) {
    issues.push(issue('MISSING_FIELD', 'GEDCOM file is missing the TRLR record', Math.max(1, content.split(/\r?\n/).length)));
  }

  return {
    format: 'GEDCOM',
    version: '1.0',
    members: [...memberByXref.values()],
    relationships,
    events: [],
    mediaMetadata: [],
    albums: [],
    issues
  };
}

function parseRecords(content: string, issues: ImportIssue[]): GEDCOMRecord[] {
  const records: GEDCOMRecord[] = [];
  const stack: GEDCOMRecord[] = [];
  const lines = content.split(/\r?\n/);
  lines.forEach((rawLine, index) => {
    const lineNumber = index + 1;
    if (!rawLine.trim()) return;
    const match = GEDCOM_LINE.exec(rawLine.trimEnd());
    if (!match) {
      issues.push(issue('SYNTAX_ERROR', 'Invalid GEDCOM line syntax', lineNumber));
      return;
    }
    const level = Number(match[1]);
    if (!Number.isSafeInteger(level) || level < 0) {
      issues.push(issue('INVALID_VALUE', `Invalid GEDCOM level ${match[1]}`, lineNumber));
      return;
    }
    if (level > stack.length) {
      issues.push(issue('SYNTAX_ERROR', `GEDCOM level jumps from ${stack.length - 1} to ${level}`, lineNumber));
      return;
    }
    const record: GEDCOMRecord = {
      level,
      ...(match[2] ? { xref: match[2] } : {}),
      tag: match[3].toUpperCase(),
      ...(match[4]?.trim() ? { value: match[4].trim() } : {}),
      line: lineNumber,
      children: []
    };
    while (stack.length > level) stack.pop();
    if (level === 0) records.push(record);
    else if (stack[level - 1]) stack[level - 1].children.push(record);
    else {
      issues.push(issue('SYNTAX_ERROR', 'GEDCOM record has no valid parent', lineNumber));
      return;
    }
    stack[level] = record;
    stack.length = level + 1;
  });
  return records;
}

function parseName(value: string): { firstName: string; lastName: string; fullName: string } {
  const surnameMatch = /\/([^/]*)\//.exec(value);
  const lastName = (surnameMatch?.[1] ?? '').trim();
  const firstName = value.replace(/\/[^/]*\//, ' ').replace(/\s+/g, ' ').trim();
  const fullName = [lastName, firstName].filter(Boolean).join(' ').trim();
  return { firstName: firstName || fullName, lastName: lastName || '-', fullName };
}

function parseGender(value: string | undefined, line: number, issues: ImportIssue[]): Gender {
  if (!value || value.toUpperCase() === 'U') return 'OTHER';
  if (value.toUpperCase() === 'M') return 'MALE';
  if (value.toUpperCase() === 'F') return 'FEMALE';
  issues.push(issue('UNSUPPORTED_VALUE', `Unsupported GEDCOM SEX value "${value}"; imported as OTHER`, line, 'WARNING'));
  return 'OTHER';
}

function parseEventDate(
  event: GEDCOMRecord | undefined,
  label: string,
  issues: ImportIssue[]
): { present: boolean; date?: string; place?: string } {
  if (!event) return { present: false };
  const dateRecord = child(event, 'DATE');
  const place = child(event, 'PLAC')?.value;
  if (!dateRecord?.value) return { present: true, ...(place ? { place } : {}) };
  const date = gedcomDateToIso(dateRecord.value);
  if (!date) {
    issues.push(issue(
      'UNSUPPORTED_VALUE',
      `Unsupported or incomplete ${label} date "${dateRecord.value}"; date was omitted`,
      dateRecord.line,
      'WARNING'
    ));
  }
  return { present: true, ...(date ? { date } : {}), ...(place ? { place } : {}) };
}

function gedcomDateToIso(value: string): string | undefined {
  const normalized = value.trim().toUpperCase().replace(/^(ABT|CAL|EST|BEF|AFT)\s+/, '');
  const match = /^(\d{1,2})\s+([A-Z]{3})\s+(\d{4})$/.exec(normalized);
  if (!match || !MONTHS[match[2]]) return undefined;
  const day = Number(match[1]);
  const month = MONTHS[match[2]];
  const candidate = new Date(Date.UTC(Number(match[3]), month - 1, day));
  if (candidate.getUTCMonth() !== month - 1 || candidate.getUTCDate() !== day) return undefined;
  return `${match[3]}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function resolvePointer(
  family: GEDCOMRecord,
  tag: string,
  members: Map<string, Member>,
  issues: ImportIssue[]
): Member | undefined {
  const record = child(family, tag);
  if (!record) return undefined;
  const member = pointerMember(record.value, members);
  if (!member) issues.push(issue('BROKEN_REFERENCE', `Unknown ${tag} reference ${record.value ?? '(empty)'}`, record.line));
  return member;
}

function pointerMember(value: string | undefined, members: Map<string, Member>): Member | undefined {
  return value ? members.get(value.trim()) : undefined;
}

function child(record: GEDCOMRecord, tag: string): GEDCOMRecord | undefined {
  return record.children.find((candidate) => candidate.tag === tag);
}

function children(record: GEDCOMRecord, tag: string): GEDCOMRecord[] {
  return record.children.filter((candidate) => candidate.tag === tag);
}

function collectText(record: GEDCOMRecord): string {
  return [record.value, ...record.children.filter((item) => item.tag === 'CONT' || item.tag === 'CONC').map((item) => item.value)]
    .filter((value): value is string => Boolean(value))
    .join('\n');
}

function gedcomId(prefix: string, xref: string, fallback: number): string {
  const normalized = xref.replace(/^@|@$/g, '').replace(/[^A-Za-z0-9_-]/g, '-');
  return `gedcom-${prefix}-${normalized || fallback + 1}`;
}

function issue(
  code: ImportIssue['code'],
  message: string,
  line: number,
  severity: ImportIssue['severity'] = 'ERROR'
): ImportIssue {
  return { severity, code, message, line };
}

function stripBom(value: string): string {
  return value.charCodeAt(0) === 0xfeff ? value.slice(1) : value;
}
