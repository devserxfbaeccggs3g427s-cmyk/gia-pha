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

