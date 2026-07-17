export const BCRYPT_COST_FACTOR = 12;
export const MAX_FAILED_LOGIN_ATTEMPTS = 5;
export const ACCOUNT_LOCK_DURATION_MS = 15 * 60 * 1000;
export const SESSION_IDLE_TIMEOUT_SECONDS = 30 * 60;
export const EMAIL_VERIFICATION_TTL_MS = 24 * 60 * 60 * 1000;
export const AUTH_SECRET =
  process.env.NEXTAUTH_SECRET ??
  (process.env.NODE_ENV === 'production' ? undefined : 'local-development-secret-change-me');
