import type { User } from '@/data/types';
import { ACCOUNT_LOCK_DURATION_MS, MAX_FAILED_LOGIN_ATTEMPTS } from './constants';

export interface LockoutState {
  locked: boolean;
  lockedUntil?: string;
  remainingMs: number;
}

export function getLockoutState(user: User, now = new Date()): LockoutState {
  if (!user.lockedUntil) {
    return { locked: false, remainingMs: 0 };
  }

  const remainingMs = new Date(user.lockedUntil).getTime() - now.getTime();

  return remainingMs > 0
    ? { locked: true, lockedUntil: user.lockedUntil, remainingMs }
    : { locked: false, remainingMs: 0 };
}

export function recordFailedLogin(user: User, now = new Date()): User {
  const currentState = getLockoutState(user, now);

  if (currentState.locked) {
    return user;
  }

  const previousAttempts = user.lockedUntil ? 0 : user.failedLoginAttempts;
  const failedLoginAttempts = previousAttempts + 1;
  const shouldLock = failedLoginAttempts >= MAX_FAILED_LOGIN_ATTEMPTS;

  return {
    ...user,
    failedLoginAttempts,
    lockedUntil: shouldLock
      ? new Date(now.getTime() + ACCOUNT_LOCK_DURATION_MS).toISOString()
      : undefined,
    updatedAt: now.toISOString()
  };
}

export function recordSuccessfulLogin(user: User, now = new Date()): User {
  return {
    ...user,
    failedLoginAttempts: 0,
    lockedUntil: undefined,
    updatedAt: now.toISOString()
  };
}

