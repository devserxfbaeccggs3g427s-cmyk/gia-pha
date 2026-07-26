import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { serviceRouteError } from '@/lib/api/service-error-handler';
import { mediaService, MediaServiceError } from '@/lib/services/media-service';

export const runtime = 'nodejs';

/**
 * Legacy media upload endpoint. The BFF now returns an upload intent
 * (a signed Blob URL) instead of streaming bytes through Spring. The
 * client uploads bytes directly to the signed URL and then calls
 * {@code /api/trees/{treeId}/media/complete} to activate the record.
 */
export async function POST(request: Request): Promise<NextResponse> {
    try {
        const userId = await requireAuthenticatedUserId();
        const form = await request.formData();
        const treeId = stringField(form, 'treeId') ?? new URL(request.url).searchParams.get('treeId');
        if (!treeId) throw new MediaServiceError('INVALID_INPUT', 'treeId là bắt buộc');
        await requireTreePermission(treeId, userId, 'CREATE');

        const file = form.get('file');
        if (!(file instanceof Blob)) {
            throw new MediaServiceError('INVALID_INPUT', 'file là bắt buộc');
        }

        const intent = await mediaService.requestUpload(treeId, {
            filename: (file as File).name ?? 'upload.bin',
            mimeType: file.type ?? 'application/octet-stream',
            size: file.size
        });

        return NextResponse.json(
            {
                ok: true,
                uploadIntent: intent,
                completeUrl: `/api/trees/${encodeURIComponent(treeId)}/media/complete`,
                memberId: stringField(form, 'memberId'),
                eventId: stringField(form, 'eventId'),
                albumId: stringField(form, 'albumId'),
                isAvatar: stringField(form, 'isAvatar') === 'true',
                caption: stringField(form, 'caption'),
                takenAt: stringField(form, 'takenAt')
            },
            { status: 201 }
        );
    } catch (error) {
        return serviceRouteError(error, 'Không thể upload media');
    }
}

function stringField(form: FormData, name: string): string | undefined {
    const value = form.get(name);
    if (typeof value !== 'string') return undefined;
    const normalized = value.trim();
    return normalized || undefined;
}
