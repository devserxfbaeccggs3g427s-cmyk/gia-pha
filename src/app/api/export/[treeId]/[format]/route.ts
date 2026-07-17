import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { importExportRouteError } from '@/lib/services/import-export-api-errors';
import { exportService } from '@/lib/services/export-service';
import type { PrintOptions } from '@/types/import-export';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(request: Request, { params }: { params: { treeId: string; format: string } }): Promise<Response> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'READ');
    const options = readPrintOptions(new URL(request.url).searchParams);
    if (params.format.toLowerCase() === 'preview') {
      return NextResponse.json(await exportService.createPrintPreview(params.treeId, options));
    }
    const format = params.format.toLowerCase();
    let body: string | Buffer;
    let contentType: string;
    let filename: string;
    if (format === 'gedcom' || format === 'ged') {
      body = await exportService.exportGEDCOM(params.treeId); contentType = 'text/plain; charset=utf-8'; filename = `${params.treeId}.ged`;
    } else if (format === 'json') {
      body = await exportService.exportJSON(params.treeId); contentType = 'application/json; charset=utf-8'; filename = `${params.treeId}.json`;
    } else if (format === 'pdf') {
      body = await exportService.exportPDF(params.treeId, options); contentType = 'application/pdf'; filename = `${params.treeId}.pdf`;
    } else if (format === 'png' || format === 'image') {
      body = await exportService.exportImage(params.treeId, options); contentType = 'image/png'; filename = `${params.treeId}.png`;
    } else if (format === 'svg') {
      body = await exportService.exportSVG(params.treeId, options); contentType = 'image/svg+xml; charset=utf-8'; filename = `${params.treeId}.svg`;
    } else {
      return NextResponse.json({ ok: false, error: { code: 'INVALID_INPUT', message: 'format must be GEDCOM, JSON, PDF, PNG, SVG, or preview' } }, { status: 400 });
    }
    return new NextResponse(typeof body === 'string' ? body : new Uint8Array(body), {
      status: 200,
      headers: {
        'Content-Type': contentType,
        'Content-Disposition': `attachment; filename="${filename}"`,
        'Cache-Control': 'private, no-store'
      }
    });
  } catch (error) {
    return importExportRouteError(error);
  }
}

function readPrintOptions(params: URLSearchParams): PrintOptions {
  const display: PrintOptions['display'] = {
    ...(params.get('showDates') !== null ? { showDates: params.get('showDates') === 'true' } : {}),
    ...(params.get('showGender') !== null ? { showGender: params.get('showGender') === 'true' } : {}),
    ...(params.get('showLocations') !== null ? { showLocations: params.get('showLocations') === 'true' } : {}),
    ...(params.get('showMemberIds') !== null ? { showMemberIds: params.get('showMemberIds') === 'true' } : {})
  };
  const dpi = params.get('dpi');
  return {
    ...(params.get('paperSize') ? { paperSize: params.get('paperSize')!.toUpperCase() as PrintOptions['paperSize'] } : {}),
    ...(params.get('orientation') ? { orientation: params.get('orientation')!.toUpperCase() as PrintOptions['orientation'] } : {}),
    ...(params.get('font') ? { font: params.get('font')!.toUpperCase() as PrintOptions['font'] } : {}),
    ...(params.get('colorScheme') ? { colorScheme: params.get('colorScheme')!.toUpperCase() as PrintOptions['colorScheme'] } : {}),
    ...(dpi ? { dpi: Number(dpi) } : {}),
    ...(Object.keys(display).length ? { display } : {})
  };
}
