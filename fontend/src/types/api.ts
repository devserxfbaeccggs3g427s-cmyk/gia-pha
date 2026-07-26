export interface ApiSuccess<T> {
  ok: true;
  data: T;
}

export interface ApiFailure {
  ok: false;
  error: {
    code: string;
    message: string;
    details?: unknown;
  };
}

export type ApiResponse<T> = ApiSuccess<T> | ApiFailure;

export interface PaginationParams {
  page?: number;
  pageSize?: number;
}

export interface PaginatedResponse<T> {
  items: T[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

export type SortDirection = 'asc' | 'desc';

export interface SortParams<TField extends string = string> {
  field: TField;
  direction: SortDirection;
}

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}

export interface SearchResult {
  member: {
    id: string;
    fullName: string;
    nickname?: string;
    avatarMediaId?: string;
    avatarUrl?: string;
    gender?: 'MALE' | 'FEMALE' | 'OTHER';
    generation?: number;
    dateOfBirth?: string;
    isAlive?: boolean;
    occupation?: string;
    placeOfBirth?: string;
    currentAddress?: string;
  };
  matchedFields: string[];
}
