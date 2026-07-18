import { NextResponse } from 'next/server';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { eventMediaRouteError } from '@/lib/services/event-media-api-errors';
import { MediaServiceError } from '@/lib/services/event-media-errors';
import { mediaService } from '@/lib/services/media-service';

export const runtime = 'nodejs';

export async function POST(request: Request): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    const form = await request.formData();
    const treeId = stringField(form, 'treeId') ?? new URL(request.url).searchParams.get('treeId');
    if (!treeId) throw new MediaServiceError('INVALID_INPUT', 'treeId là bắt buộc');
    await requireTreePermission(treeId, userId, 'CREATE');

    const file = form.get('file');
    const item = await mediaService.uploadMedia(treeId, file as File, {
      memberId: stringField(form, 'memberId'),
      eventId: stringField(form, 'eventId'),
      memberIds: arrayField(form, 'memberIds'),
      eventIds: arrayField(form, 'eventIds'),
      albumId: stringField(form, 'albumId'),
      isAvatar: stringField(form, 'isAvatar') === 'true',
      caption: stringField(form, 'caption'),
      takenAt: stringField(form, 'takenAt')
    }, userId);
    return NextResponse.json(item, { status: 201 });
  } catch (error) {
    return eventMediaRouteError(error, 'media');
  }
}

function stringField(form: FormData, name: string): string | undefined {
  const value = form.get(name);
  if (typeof value !== 'string') return undefined;
  const normalized = value.trim();
  return normalized || undefined;
}

function arrayField(form: FormData, name: string): string[] | undefined {
  const values = form.getAll(name).filter((value): value is string => typeof value === 'string');
  if (values.length === 0) return undefined;
  const result: string[] = [];
  for (const value of values) {
    const normalized = value.trim();
    if (!normalized) continue;
    if (normalized.startsWith('[')) {
      const parsed: unknown = JSON.parse(normalized);
      if (!Array.isArray(parsed) || parsed.some((item) => typeof item !== 'string')) {
        throw new MediaServiceError('INVALID_INPUT', `${name} phải là một mảng chuỗi`);
      }
      result.push(...parsed);
    } else {
      result.push(normalized);
    }
  }
  return [...new Set(result)];
}
