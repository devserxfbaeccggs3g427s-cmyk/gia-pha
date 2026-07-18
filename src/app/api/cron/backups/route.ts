import { NextResponse } from 'next/server';
import { getTrees } from '@/lib/blob/readers';
import { backupShareRouteError } from '@/lib/services/backup-share-api-errors';
import { backupService } from '@/lib/services/backup-service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(request: Request): Promise<NextResponse> {
  const configuredSecret = process.env.CRON_SECRET;
  const authorization = request.headers.get('authorization');
  if (!configuredSecret || authorization !== `Bearer ${configuredSecret}`) {
    return NextResponse.json({ ok: false, error: { code: 'UNAUTHORIZED', message: 'Invalid cron credentials' } }, { status: 401 });
  }

  try {
    const trees = await getTrees();
    const results = [];
    for (const tree of trees) {
      const daily = await backupService.ensureDailyBackup(tree.id);
      const deletedExpired = await backupService.deleteExpiredBackups(tree.id);
      results.push({ treeId: tree.id, created: daily.created, timestamp: daily.snapshot.timestamp, deletedExpired });
    }
    return NextResponse.json({ ok: true, processed: results.length, results }, {
      headers: { 'Cache-Control': 'private, no-store' }
    });
  } catch (error) {
    return backupShareRouteError(error);
  }
}
