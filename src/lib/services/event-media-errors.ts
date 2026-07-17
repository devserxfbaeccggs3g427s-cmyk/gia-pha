export class EventServiceError extends Error {
  constructor(
    public readonly code: 'NOT_FOUND' | 'INVALID_INPUT' | 'CONFLICT',
    message: string
  ) {
    super(message);
    this.name = 'EventServiceError';
  }
}

export class MediaServiceError extends Error {
  constructor(
    public readonly code:
      | 'NOT_FOUND'
      | 'INVALID_INPUT'
      | 'INVALID_FILE_TYPE'
      | 'FILE_TOO_LARGE'
      | 'CONFLICT',
    message: string
  ) {
    super(message);
    this.name = 'MediaServiceError';
  }
}
