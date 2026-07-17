import { get } from '@vercel/blob';
import { NextResponse } from 'next/server';
import { withBlobErrorHandling } from '@/lib/blob/client';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { eventMediaRouteError } from '@/lib/services/event-media-api-errors';
import { mediaService } from '@/lib/services/media-service';
import { findMediaTree } from '@/lib/services/media-route-utils';

export const runtime = 'nodejs';

export async function GET(
  request: Request,
  { params }: { params: { mediaId: string } }
): Promise<Response> {
  try {
    const userId = await requireAuthenticatedUserId();
    const treeId = await findMediaTree(request, params.mediaId);
    if (!treeId) {
      return NextResponse.json(
        { ok: false, error: { code: 'NOT_FOUND', message: 'Media not found' } },
        { status: 404 }
      );
    }
    await requireTreePermission(treeId, userId, 'READ');
    const item = await mediaService.getMedia(treeId, params.mediaId);
    const query = new URL(request.url).searchParams;
    const isThumbnail = query.get('thumbnail') === 'true';
    const url = isThumbnail ? item.thumbnailUrl : item.blobUrl;
    if (!url) {
      return NextResponse.json(
        { ok: false, error: { code: 'NOT_FOUND', message: 'Thumbnail not found' } },
        { status: 404 }
      );
    }
    const blob = await withBlobErrorHandling(
      () => get(url, { access: 'private' }),
      `Read media "${params.mediaId}"`
    );
    if (!blob || blob.statusCode !== 200 || !blob.stream) {
      return NextResponse.json(
        { ok: false, error: { code: 'NOT_FOUND', message: 'Media content not found' } },
        { status: 404 }
      );
    }
    const download = query.get('download') === 'true';
    const filename = isThumbnail ? `${stripExtension(item.originalName)}-thumbnail.webp` : item.originalName;
    return new Response(blob.stream, {
      status: 200,
      headers: {
        'Content-Type': blob.blob.contentType,
        'Content-Length': String(blob.blob.size),
        'Content-Disposition': `${download ? 'attachment' : 'inline'}; filename*=UTF-8''${encodeURIComponent(filename)}`,
        'Cache-Control': 'private, max-age=3600',
        ETag: blob.blob.etag,
        'X-Content-Type-Options': 'nosniff'
      }
    });
  } catch (error) {
    return eventMediaRouteError(error, 'media');
  }
}

function stripExtension(filename: string): string {
  return filename.replace(/\.[^.]+$/, '');
}
