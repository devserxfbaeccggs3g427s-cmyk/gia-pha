import { getServerSession } from 'next-auth';
import { authOptions } from './options';

export class AuthenticationError extends Error {
  constructor() {
    super('Authentication is required');
    this.name = 'AuthenticationError';
  }
}

export async function requireAuthenticatedUserId(): Promise<string> {
  const session = await getServerSession(authOptions);
  if (!session?.user?.id) throw new AuthenticationError();
  return session.user.id;
}

// Keep authentication and authorization guards discoverable from one module
// for API handlers.  The implementations remain in rbac.ts to avoid a
// circular dependency with the tree data readers.
export {
  AuthorizationError,
  canAccessTree,
  getUserTreeRole,
  hasPermission,
  requireTreePermission
} from './rbac';
export type { TreePermission } from './rbac';
