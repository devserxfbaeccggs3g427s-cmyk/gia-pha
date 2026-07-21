import { NextResponse } from 'next/server';
import { readBinaryBlob } from '@/lib/blob/client';
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
    const blob = await readBinaryBlob(url);
    if (!blob) {
      return NextResponse.json(
        { ok: false, error: { code: 'NOT_FOUND', message: 'Media content not found' } },
        { status: 404 }
      );
    }
    const download = query.get('download') === 'true';
    const requestedWidth = boundedInteger(query.get('width'), 16, 1920);
    const requestedQuality = boundedInteger(query.get('quality'), 40, 90) ?? 78;
    const renderWebp = query.get('format') === 'webp'
      && item.mimeType.startsWith('image/')
      && requestedWidth !== undefined;
    const optimizedBody = renderWebp
      ? await renderResponsiveWebp(blob.stream, requestedWidth, requestedQuality)
      : undefined;
    const body = optimizedBody ? Uint8Array.from(optimizedBody).buffer : blob.stream;
    const filename = renderWebp || isThumbnail
      ? `${stripExtension(item.originalName)}${isThumbnail ? '-thumbnail' : ''}.webp`
      : item.originalName;
    return new Response(body, {
      status: 200,
      headers: {
        'Content-Type': renderWebp ? 'image/webp' : blob.contentType,
        'Content-Length': String(optimizedBody?.length ?? blob.size),
        'Content-Disposition': `${download ? 'attachment' : 'inline'}; filename*=UTF-8''${encodeURIComponent(filename)}`,
        'Cache-Control': renderWebp
          ? 'private, max-age=86400, stale-while-revalidate=604800'
          : 'private, max-age=3600',
        ETag: renderWebp
          ? variantEtag(blob.etag, requestedWidth, requestedQuality)
          : blob.etag,
        'X-Content-Type-Options': 'nosniff'
      }
    });
  } catch (error) {
    return eventMediaRouteError(error, 'media');
  }
}

async function renderResponsiveWebp(
  stream: ReadableStream<Uint8Array>,
  width: number,
  quality: number
): Promise<Buffer> {
  const source = Buffer.from(await new Response(stream).arrayBuffer());
  const { default: sharp } = await import('sharp');
  return sharp(source, { failOn: 'error', limitInputPixels: 40_000_000 })
    .rotate()
    .resize({ width, withoutEnlargement: true })
    .webp({ quality, effort: 4 })
    .toBuffer();
}

function boundedInteger(value: string | null, minimum: number, maximum: number): number | undefined {
  if (!value || !/^\d+$/.test(value)) return undefined;
  const parsed = Number(value);
  return parsed >= minimum && parsed <= maximum ? parsed : undefined;
}

function variantEtag(etag: string, width: number, quality: number): string {
  const value = etag.replace(/^W\//, '').replace(/^"|"$/g, '');
  return `"${value}-webp-w${width}-q${quality}"`;
}

function stripExtension(filename: string): string {
  return filename.replace(/\.[^.]+$/, '');
}
