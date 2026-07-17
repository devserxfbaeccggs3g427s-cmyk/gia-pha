import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId, requireTreePermission } from '@/lib/auth/guards';
import { importExportRouteError } from '@/lib/services/import-export-api-errors';
import { importService } from '@/lib/services/import-service';
import type { ImportFormat, ImportOptions, ParsedImportData } from '@/types/import-export';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function POST(request: Request): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const input = await readImportInput(request);
    await requireTreePermission(input.treeId, userId, 'UPDATE');
    const parsed = await parse(input.format, input.content);
    const result = await importService.execute(input.treeId, parsed, input.options);
    return NextResponse.json(result, { status: 200 });
  } catch (error) {
    return importExportRouteError(error);
  }
}

async function readImportInput(request: Request): Promise<{ treeId: string; format: ImportFormat; content: Buffer; options: ImportOptions }> {
  const contentType = request.headers.get('content-type') ?? '';
  if (contentType.includes('multipart/form-data')) {
    const form = await request.formData();
    const file = form.get('file');
    if (!(file instanceof File)) throw new Error('A file field is required');
    return {
      treeId: String(form.get('treeId') ?? ''),
      format: normalizeFormat(String(form.get('format') ?? formatFromFilename(file.name))),
      content: Buffer.from(await file.arrayBuffer()),
      options: parseOptions(form.get('mode'), form.get('conflictStrategy'))
    };
  }
  const body = await request.json() as { treeId?: string; format?: string; content?: string; data?: string; mode?: string; conflictStrategy?: string; options?: ImportOptions };
  return {
    treeId: body.treeId ?? '',
    format: normalizeFormat(body.format ?? 'JSON'),
    content: typeof body.content === 'string' ? Buffer.from(body.content, 'utf8') : Buffer.from(body.data ?? '', 'base64'),
    options: { ...(body.options ?? {}), ...parseOptions(body.mode, body.conflictStrategy) }
  };
}

function parseOptions(mode: FormDataEntryValue | string | null | undefined, strategy: FormDataEntryValue | string | null | undefined): ImportOptions {
  const value = (input: FormDataEntryValue | string | null | undefined) => input === null || input === undefined ? undefined : String(input).toUpperCase();
  const parsedMode = value(mode);
  const parsedStrategy = value(strategy);
  return {
    ...(parsedMode === 'APPEND' || parsedMode === 'REPLACE' ? { mode: parsedMode } : {}),
    ...(parsedStrategy === 'SKIP' || parsedStrategy === 'OVERWRITE' || parsedStrategy === 'REGENERATE' ? { conflictStrategy: parsedStrategy } : {})
  };
}

function normalizeFormat(value: string): ImportFormat {
  const format = value.replace(/^\./, '').toUpperCase();
  if (format === 'GED' || format === 'GEDCOM') return 'GEDCOM';
  if (format === 'CSV') return 'CSV';
  if (format === 'JSON') return 'JSON';
  throw new Error('format must be GEDCOM, JSON, or CSV');
}

function formatFromFilename(filename: string): ImportFormat {
  return normalizeFormat(filename.toLowerCase().split('.').pop() ?? '');
}

async function parse(format: ImportFormat, content: Buffer): Promise<ParsedImportData> {
  if (format === 'GEDCOM') return importService.parseGEDCOM(content);
  if (format === 'CSV') return importService.parseCSV(content);
  return importService.parseJSON(content);
}
