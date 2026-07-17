import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { importExportRouteError } from '@/lib/services/import-export-api-errors';
import { importService } from '@/lib/services/import-service';
import type { ImportFormat, ParsedImportData } from '@/types/import-export';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function POST(request: Request): Promise<NextResponse> {
  try {
    await requireAuthenticatedUserId();
    const input = await readImportInput(request);
    const parsed = await parse(input.format, input.content);
    return NextResponse.json(await importService.preview(parsed));
  } catch (error) {
    return importExportRouteError(error);
  }
}

async function readImportInput(request: Request): Promise<{ format: ImportFormat; content: Buffer }> {
  const contentType = request.headers.get('content-type') ?? '';
  if (contentType.includes('multipart/form-data')) {
    const form = await request.formData();
    const file = form.get('file');
    if (!(file instanceof File)) throw new Error('A file field is required');
    const format = normalizeFormat(String(form.get('format') ?? formatFromFilename(file.name)));
    return { format, content: Buffer.from(await file.arrayBuffer()) };
  }
  const body = await request.json() as { format?: string; content?: string; data?: string };
  const format = normalizeFormat(body.format ?? 'JSON');
  if (typeof body.content === 'string') return { format, content: Buffer.from(body.content, 'utf8') };
  if (typeof body.data === 'string') return { format, content: Buffer.from(body.data, 'base64') };
  throw new Error('Request must include content or base64 data');
}

function normalizeFormat(value: string): ImportFormat {
  const format = value.replace(/^\./, '').toUpperCase();
  if (format === 'GED' || format === 'GEDCOM') return 'GEDCOM';
  if (format === 'CSV') return 'CSV';
  if (format === 'JSON') return 'JSON';
  throw new Error('format must be GEDCOM, JSON, or CSV');
}

function formatFromFilename(filename: string): ImportFormat {
  const extension = filename.toLowerCase().split('.').pop() ?? '';
  return normalizeFormat(extension);
}

async function parse(format: ImportFormat, content: Buffer): Promise<ParsedImportData> {
  if (format === 'GEDCOM') return importService.parseGEDCOM(content);
  if (format === 'CSV') return importService.parseCSV(content);
  return importService.parseJSON(content);
}
