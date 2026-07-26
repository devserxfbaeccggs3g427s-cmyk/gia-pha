export type AuthErrorCode =
  | 'ACCOUNT_LOCKED'
  | 'EMAIL_ALREADY_EXISTS'
  | 'EMAIL_NOT_VERIFIED'
  | 'INVALID_TOKEN'
  | 'EMAIL_DELIVERY_FAILED';

export class AuthServiceError extends Error {
  constructor(
    public readonly code: AuthErrorCode,
    message: string,
    public readonly details?: unknown
  ) {
    super(message);
    this.name = 'AuthServiceError';
  }
}

