export type Provider = 'credentials' | 'google' | 'facebook';
export type TreeRole = 'ADMIN' | 'EDITOR' | 'VIEWER';
export type Gender = 'MALE' | 'FEMALE' | 'OTHER';
export type RelationType = 'PARENT_CHILD' | 'SPOUSE' | 'SIBLING' | 'ADOPTED' | 'CUSTOM';
export type MarriageStatus = 'MARRIED' | 'DIVORCED' | 'WIDOWED';
export type EventType = 'BIRTHDAY' | 'WEDDING' | 'FUNERAL' | 'REUNION' | 'ANNIVERSARY' | 'CUSTOM';
export type ChangeAction = 'CREATE' | 'UPDATE' | 'DELETE';
export type ChangeEntityType = 'MEMBER' | 'RELATIONSHIP' | 'EVENT' | 'MEDIA';
export type SharePermission = 'VIEW';

export interface OAuthAccount {
  provider: 'google' | 'facebook';
  providerAccountId: string;
  type: 'oauth';
}

export interface User {
  id: string;
  email: string;
  name: string;
  passwordHash: string;
  image?: string;
  provider: Provider;
  emailVerified?: string | null;
  emailVerificationTokenHash?: string;
  emailVerificationExpiresAt?: string;
  oauthAccounts?: OAuthAccount[];
  failedLoginAttempts: number;
  lockedUntil?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Session {
  userId: string;
  token: string;
  expiresAt: string;
}

export interface FamilyTree {
  id: string;
  name: string;
  description?: string;
  ownerId: string;
  memberships: TreeMembership[];
  createdAt: string;
  updatedAt: string;
}

export interface TreeMembership {
  userId: string;
  role: TreeRole;
  createdAt: string;
}

export interface Member {
  id: string;
  treeId: string;
  firstName: string;
  lastName: string;
  fullName: string;
  nickname?: string;
  gender: Gender;
  dateOfBirth?: string;
  dateOfDeath?: string;
  placeOfBirth?: string;
  currentAddress?: string;
  phone?: string;
  email?: string;
  occupation?: string;
  education?: string;
  biography?: string;
  achievements?: string;
  notes?: string;
  avatarUrl?: string;
  generation?: number;
  isAlive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Relationship {
  id: string;
  treeId: string;
  sourceMemberId: string;
  targetMemberId: string;
  type: RelationType;
  customType?: string;
  marriageDate?: string;
  divorceDate?: string;
  marriageStatus?: MarriageStatus;
  createdAt: string;
}

export interface Event {
  id: string;
  treeId: string;
  type: EventType;
  customType?: string;
  title: string;
  eventDate: string;
  location?: string;
  description?: string;
  memberIds: string[];
  mediaIds: string[];
  createdAt: string;
  updatedAt: string;
}

export interface MediaMetadata {
  id: string;
  treeId: string;
  /** @deprecated Read compatibility for metadata written before multi-link support. */
  memberId?: string;
  /** @deprecated Read compatibility for metadata written before multi-link support. */
  eventId?: string;
  memberIds?: string[];
  eventIds?: string[];
  albumId?: string;
  filename: string;
  originalName: string;
  mimeType: string;
  fileSize: number;
  blobUrl: string;
  thumbnailUrl?: string;
  /** Authenticated application URLs for private Vercel Blob content. */
  contentUrl?: string;
  thumbnailContentUrl?: string;
  caption?: string;
  takenAt?: string;
  uploadedAt: string;
}

export interface Album {
  id: string;
  treeId: string;
  title: string;
  description?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface ChangeLog {
  id: string;
  treeId: string;
  memberId?: string;
  userId: string;
  action: ChangeAction;
  entityType: ChangeEntityType;
  previousData?: Record<string, unknown>;
  newData?: Record<string, unknown>;
  fieldChanged?: string;
  createdAt: string;
}

export interface ShareLink {
  id: string;
  treeId: string;
  token: string;
  permission: SharePermission;
  expiresAt: string;
  createdAt: string;
}

export interface BackupSnapshot {
  treeId: string;
  timestamp: string;
  data: {
    members: Member[];
    relationships: Relationship[];
    events: Event[];
    mediaMetadata: MediaMetadata[];
  };
}
