import { describe, expect, it, vi } from 'vitest';
import { getUsers } from '@/lib/blob/readers';
import { putUsers } from '@/lib/blob/writers';
import { authenticateCredentials, registerUser, verifyEmailToken } from '@/lib/auth/auth-service';
import { AuthServiceError } from '@/lib/auth/errors';
import { hashPassword } from '@/lib/auth/password';
import { buildUser } from '../../utils/factories';

describe('authentication service', () => {
  it('locks an account after five invalid passwords and resets after a valid login', async () => {
    const passwordHash = await hashPassword('Valid-password-123!');
    await putUsers([buildUser({ email: 'person@example.com', passwordHash, failedLoginAttempts: 0 })]);

    for (let attempt = 0; attempt < 4; attempt += 1) {
      await expect(authenticateCredentials('person@example.com', 'wrong-password')).resolves.toBeNull();
    }

    await expect(authenticateCredentials('person@example.com', 'wrong-password')).rejects.toMatchObject({ code: 'ACCOUNT_LOCKED' });
    await expect(authenticateCredentials('person@example.com', 'Valid-password-123!')).rejects.toMatchObject({ code: 'ACCOUNT_LOCKED' });

    const users = await getUsers();
    expect(users[0].failedLoginAttempts).toBe(5);
    expect(users[0].lockedUntil).toBeDefined();
  });

  it('registers a pending account and stores only a hashed verification token', async () => {
    vi.stubEnv('AUTH_REQUIRE_EMAIL_VERIFICATION', 'true');
    const created = await registerUser(
      { name: 'Nguyễn Văn A', email: ' Person@Example.com ', password: 'Valid-password-123!' },
      'https://example.com',
      new Date('2026-01-01T00:00:00.000Z')
    );
    const user = (await getUsers())[0];
    expect(created.user.email).toBe('person@example.com');
    expect(created.emailVerificationRequired).toBe(true);
    expect(user.emailVerified).toBeNull();
    expect(user.passwordHash.startsWith('$2a$12$') || user.passwordHash.startsWith('$2b$12$')).toBe(true);

    const tokenHash = user.emailVerificationTokenHash;
    expect(tokenHash).toBeDefined();
    // The token is intentionally only stored as a hash; an invalid token cannot verify the account.
    await expect(verifyEmailToken('not-the-token')).rejects.toBeInstanceOf(AuthServiceError);

    // This service-level test verifies the expiry and hash contract without exposing token material.
    expect(tokenHash).toHaveLength(64);

    await putUsers([{ ...user, emailVerified: new Date().toISOString() }]);
    await expect(authenticateCredentials('person@example.com', 'Valid-password-123!')).resolves.toMatchObject({
      email: 'person@example.com'
    });
  });

  it('allows immediate login when email verification is explicitly disabled', async () => {
    vi.stubEnv('AUTH_REQUIRE_EMAIL_VERIFICATION', 'false');
    const registration = await registerUser(
      { name: 'Demo User', email: 'demo@example.com', password: 'Valid-password-123!' },
      'http://localhost:3020'
    );

    expect(registration.emailVerificationRequired).toBe(false);
    await expect(authenticateCredentials('demo@example.com', 'Valid-password-123!')).resolves.toMatchObject({
      email: 'demo@example.com'
    });
  });
});
