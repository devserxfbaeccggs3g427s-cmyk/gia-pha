import { serviceRouteError } from '@/lib/api/service-error-handler';

/**
 * Backwards-compatible alias for tree routes. New code should import
 * {@link serviceRouteError} from `@/lib/api/service-error-handler`.
 */
export function treeRouteError(error: unknown): import('next/server').NextResponse {
  return serviceRouteError(error, 'Không thể xử lý cây gia phả');
}
