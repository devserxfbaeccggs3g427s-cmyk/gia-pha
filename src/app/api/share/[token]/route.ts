import { NextResponse } from 'next/server';
import { backupShareRouteError } from '@/lib/services/backup-share-api-errors';
import { shareLinkService } from '@/lib/services/share-link-service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(_request: Request, { params }: { params: { token: string } }): Promise<NextResponse> {
  try {
    return NextResponse.json(await shareLinkService.getSharedTree(params.token), {
      headers: {
        'Cache-Control': 'private, no-store',
        'X-Robots-Tag': 'noindex, nofollow, noarchive'
      }
    });
  } catch (error) {
    return backupShareRouteError(error);
  }
}
