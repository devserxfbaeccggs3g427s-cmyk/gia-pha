import 'server-only';

/**
 * Service-layer error type used across BFF adapters. Carries a stable
 * code so the route handlers can map to the right HTTP status.
 */
export class ServiceError extends Error {
    constructor(
        public readonly code:
            | 'NOT_FOUND'
            | 'INVALID_INPUT'
            | 'UNAUTHORIZED'
            | 'FORBIDDEN'
            | 'CONFLICT'
            | 'INTERNAL',
        message: string
    ) {
        super(message);
        this.name = 'ServiceError';
    }
}
