import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { importService } from '@/lib/services/import-service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/**
 * Import preview endpoint. The BFF forwards the file to the
 * transfer-service preview API; the service runs the parse-only phase
 * (Task 29.4) outside any DB transaction and returns counts +
 * diagnostics.
 */
export async function POST(request: Request): Promise<NextResponse> {
    try {
        await requireAuthenticatedUserId();
        const contentType = request.headers.get('content-type') ?? '';
        let filename = 'import.json';
        let mimeType = 'application/json';
        let size = 0;
        let bytes: ArrayBuffer | undefined;

        if (contentType.includes('multipart/form-data')) {
            const form = await request.formData();
            const file = form.get('file');
            if (!(file instanceof File)) {
                return NextResponse.json(
                    { ok: false, error: { code: 'INVALID_INPUT', message: 'file là bắt buộc' } },
                    { status: 400 }
                );
            }
            filename = file.name ?? filename;
            mimeType = file.type || mimeType;
            size = file.size;
            bytes = await file.arrayBuffer();
        } else {
            const body = await request.json().catch(() => ({}));
            if (typeof body.content === 'string') {
                const enc = new TextEncoder();
                bytes = enc.encode(body.content).buffer as ArrayBuffer;
                size = bytes.byteLength;
                filename = body.filename ?? filename;
                mimeType = body.mimeType ?? mimeType;
            }
        }

        if (!bytes) {
            return NextResponse.json(
                { ok: false, error: { code: 'INVALID_INPUT', message: 'content hoặc file là bắt buộc' } },
                { status: 400 }
            );
        }

        const treeId = new URL(request.url).searchParams.get('treeId') ?? '';
        if (!treeId) {
            return NextResponse.json(
                { ok: false, error: { code: 'INVALID_INPUT', message: 'treeId là bắt buộc' } },
                { status: 400 }
            );
        }
        const format = detectFormat(filename, mimeType);
        const preview = await importService.preview(treeId, {
            format,
            filename,
            mimeType,
            size,
            bytes
        });
        return NextResponse.json(preview);
    } catch (error) {
        return serviceRouteError(error, 'Không thể truy xuất preview import');
    }
}

function detectFormat(filename: string, mimeType: string): 'gedcom' | 'json' | 'csv' {
    const ext = filename.toLowerCase().split('.').pop() ?? '';
    if (ext === 'ged' || ext === 'gedcom') return 'gedcom';
    if (ext === 'csv') return 'csv';
    if (ext === 'json') return 'json';
    if (mimeType.includes('json')) return 'json';
    if (mimeType.includes('csv')) return 'csv';
    return 'gedcom';
}
