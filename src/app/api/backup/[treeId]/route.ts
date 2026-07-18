import { NextResponse } from 'next/server';
import { restoreBackupSchema } from '@/data/schemas';
import { requireAuthenticatedUserId } from '@/lib/auth/guards';
import { requireTreePermission } from '@/lib/auth/rbac';
import { backupShareRouteError } from '@/lib/services/backup-share-api-errors';
import { backupService } from '@/lib/services/backup-service';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

interface RouteContext {
  params: { treeId: string };
}

export async function GET(_request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'READ');
    return NextResponse.json(
      { backups: await backupService.listBackups(params.treeId), retentionDays: 30 },
      { headers: { 'Cache-Control': 'private, no-store' } }
    );
  } catch (error) {
    return backupShareRouteError(error);
  }
}

export async function POST(_request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'UPDATE');
    return NextResponse.json(await backupService.createBackup(params.treeId), {
      status: 201,
      headers: { 'Cache-Control': 'private, no-store' }
    });
  } catch (error) {
    return backupShareRouteError(error);
  }
}

export async function PUT(request: Request, { params }: RouteContext): Promise<NextResponse> {
  try {
    const userId = await requireAuthenticatedUserId();
    await requireTreePermission(params.treeId, userId, 'UPDATE');
    const { timestamp } = restoreBackupSchema.parse(await request.json());
    return NextResponse.json(await backupService.restoreFromBackup(params.treeId, timestamp), {
      headers: { 'Cache-Control': 'private, no-store' }
    });
  } catch (error) {
    return backupShareRouteError(error);
  }
}
