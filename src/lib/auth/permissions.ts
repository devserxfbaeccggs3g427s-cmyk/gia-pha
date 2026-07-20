export {
  AuthorizationError,
  canAccessTree,
  getUserTreeRole,
  hasPermission,
  requireCompositeAdminPermission,
  requireSourceAdminConsent,
  requireSourceReadPermission,
  requireTreePermission
} from './rbac';
export type { TreePermission } from './rbac';

