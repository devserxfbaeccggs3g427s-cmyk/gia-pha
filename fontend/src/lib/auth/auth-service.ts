import { createHash, randomBytes } from 'crypto';
import { nanoid } from 'nanoid';
import type { User } from '@/data/types';
import type { RegisterInput } from '@/data/schemas';
import { EMAIL_VERIFICATION_TTL_MS } from './constants';
import { sendVerificationEmail } from './email';
import { isEmailVerificationRequired } from './email-verification';
import { AuthServiceError } from './errors';
import { getLockoutState, recordFailedLogin, recordSuccessfulLogin } from './lockout';
import { hashPassword, verifyPassword } from './password';
import {
  createUserRecord,
  findUserByEmail,
  normalizeEmail,
  updateUserRecord
} from './user-store';

const DUMMY_PASSWORD_HASH = '$2a$12$di2fvsHit2Jc5VufildE9e2gS2kh2KggSh7HYK8qOzRd.fb6hNb4.';

export interface AuthenticatedUser {
  id: string;
  email: string;
  name: string;
  image?: string;
}

export async function authenticateCredentials(
  email: string,
  password: string,
  now = new Date()
): Promise<AuthenticatedUser | null> {
  const user = await findUserByEmail(email);

  if (!user || !user.passwordHash) {
    await verifyPassword(password, DUMMY_PASSWORD_HASH);
    return null;
  }

  const lockout = getLockoutState(user, now);
  if (lockout.locked) {
    throw new AuthServiceError('ACCOUNT_LOCKED', 'ACCOUNT_LOCKED', {
      lockedUntil: lockout.lockedUntil
    });
  }

  let passwordMatches = false;
  try {
    passwordMatches = await verifyPassword(password, user.passwordHash);
  } catch {
    // A malformed legacy hash is treated as a failed attempt, never as a server error.
    passwordMatches = false;
  }
  if (!passwordMatches) {
    const updatedUser = recordFailedLogin(user, now);
    await updateUserRecord(updatedUser);

    if (getLockoutState(updatedUser, now).locked) {
      throw new AuthServiceError('ACCOUNT_LOCKED', 'ACCOUNT_LOCKED', {
        lockedUntil: updatedUser.lockedUntil
      });
    }

    return null;
  }

  const emailVerificationRequired = isEmailVerificationRequired();
  if (user.emailVerified === null && emailVerificationRequired) {
    throw new AuthServiceError('EMAIL_NOT_VERIFIED', 'EMAIL_NOT_VERIFIED');
  }

  const loginUser =
    user.emailVerified === null && !emailVerificationRequired
      ? { ...user, emailVerified: now.toISOString() }
      : user;
  const updatedUser = recordSuccessfulLogin(loginUser, now);
  await updateUserRecord(updatedUser);

  return {
    id: updatedUser.id,
    email: updatedUser.email,
    name: updatedUser.name,
    image: updatedUser.image
  };
}

export interface RegistrationResult {
  user: Pick<User, 'id' | 'email' | 'name' | 'createdAt'>;
  emailVerificationRequired: boolean;
}

export async function registerUser(
  input: RegisterInput,
  baseUrl: string,
  now = new Date()
): Promise<RegistrationResult> {
  const email = normalizeEmail(input.email);
  if (await findUserByEmail(email)) {
    throw new AuthServiceError('EMAIL_ALREADY_EXISTS', 'An account with this email already exists');
  }

  const emailVerificationRequired = isEmailVerificationRequired();
  const verificationToken = emailVerificationRequired ? randomBytes(32).toString('hex') : null;
  const timestamp = now.toISOString();
  const user: User = {
    id: nanoid(),
    email,
    name: input.name.trim(),
    passwordHash: await hashPassword(input.password),
    provider: 'credentials',
    emailVerified: emailVerificationRequired ? null : timestamp,
    emailVerificationTokenHash: verificationToken
      ? hashVerificationToken(verificationToken)
      : undefined,
    emailVerificationExpiresAt: verificationToken
      ? new Date(now.getTime() + EMAIL_VERIFICATION_TTL_MS).toISOString()
      : undefined,
    failedLoginAttempts: 0,
    createdAt: timestamp,
    updatedAt: timestamp
  };

  try {
    await createUserRecord(user);
  } catch (error) {
    if (error instanceof Error && error.message === 'EMAIL_ALREADY_EXISTS') {
      throw new AuthServiceError('EMAIL_ALREADY_EXISTS', 'An account with this email already exists');
    }
    throw error;
  }

  if (verificationToken) {
    const verificationUrl = new URL('/api/auth/verify-email', baseUrl);
    verificationUrl.searchParams.set('token', verificationToken);

    try {
      await sendVerificationEmail({ email, name: user.name, verificationUrl: verificationUrl.toString() });
    } catch (error) {
      throw new AuthServiceError('EMAIL_DELIVERY_FAILED', 'Unable to send the verification email', error);
    }
  }

  return {
    user: { id: user.id, email: user.email, name: user.name, createdAt: user.createdAt },
    emailVerificationRequired
  };
}

export async function verifyEmailToken(token: string, now = new Date()): Promise<User> {
  const tokenHash = hashVerificationToken(token);
  const { getUsers } = await import('@/lib/blob/readers');
  const users = await getUsers();
  const user = users.find((candidate) => candidate.emailVerificationTokenHash === tokenHash);

  if (
    !user ||
    !user.emailVerificationExpiresAt ||
    new Date(user.emailVerificationExpiresAt).getTime() <= now.getTime()
  ) {
    throw new AuthServiceError('INVALID_TOKEN', 'The verification token is invalid or expired');
  }

  const updatedUser: User = {
    ...user,
    emailVerified: now.toISOString(),
    emailVerificationTokenHash: undefined,
    emailVerificationExpiresAt: undefined,
    updatedAt: now.toISOString()
  };

  return updateUserRecord(updatedUser);
}

function hashVerificationToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}
