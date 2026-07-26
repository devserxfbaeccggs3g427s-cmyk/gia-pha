import fc from 'fast-check';
import { describe, expect, it } from 'vitest';
import { MAX_FAILED_LOGIN_ATTEMPTS } from '@/lib/auth/constants';
import { getLockoutState, recordFailedLogin } from '@/lib/auth/lockout';
import { buildUser } from '../../utils/factories';

describe('Feature: family-genealogy-management, Property 1: Account Lockout Threshold', () => {
  it('locks on the fifth consecutive failure and remains locked during the window', () => {
    fc.assert(
      fc.property(fc.integer({ min: 0, max: MAX_FAILED_LOGIN_ATTEMPTS - 1 }), (initialFailures) => {
        const now = new Date('2026-01-01T00:00:00.000Z');
        let user = buildUser({ failedLoginAttempts: initialFailures, lockedUntil: undefined });

        for (let attempt = initialFailures; attempt < MAX_FAILED_LOGIN_ATTEMPTS; attempt += 1) {
          user = recordFailedLogin(user, now);
        }

        expect(getLockoutState(user, now).locked).toBe(true);
        expect(user.failedLoginAttempts).toBeGreaterThanOrEqual(MAX_FAILED_LOGIN_ATTEMPTS);
        expect(getLockoutState(user, new Date(now.getTime() + 15 * 60 * 1000 - 1)).locked).toBe(true);
        expect(getLockoutState(user, new Date(now.getTime() + 15 * 60 * 1000 + 1)).locked).toBe(false);
      }),
      { numRuns: 100 }
    );
  });
});

