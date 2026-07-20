export type Provider = 'credentials' | 'google' | 'facebook';
export type TreeRole = 'ADMIN' | 'EDITOR' | 'VIEWER';
export type FamilyTreeKind = 'STANDALONE' | 'COMPOSITE';
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
  /**
   * Records created before composite trees do not contain this field.
   * Read boundaries normalize an omitted value to STANDALONE.
   */
  kind?: FamilyTreeKind;
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
  /** ID of the image managed by MediaService and used as this member's avatar. */
  avatarMediaId?: string;
  /** @deprecated Kept for imports and records created before avatar uploads. */
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

export type RelationshipRole = 'PARENT' | 'CHILD' | 'SPOUSE' | 'SIBLING' | 'ADOPTED' | 'CUSTOM';

/** A member-perspective view of one canonical relationship record. */
export interface RelationshipView extends Relationship {
  memberId: string;
  relatedMemberId: string;
  role: RelationshipRole;
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

export type CompositeSourceScope = 'FULL_TREE' | 'DESCENDANTS' | 'SELECTED_MEMBERS';
export type CompositeSourceStatus = 'ACTIVE' | 'UNAVAILABLE';
export type IdentityLinkStatus = 'PROPOSED' | 'CONFIRMED' | 'REJECTED';

export interface SourceReference {
  treeId: string;
  memberId: string;
}

export interface CompositeSource {
  id: string;
  sourceTreeId: string;
  scope: CompositeSourceScope;
  anchorMemberIds: string[];
  selectedMemberIds: string[];
  includeSpouses: boolean;
  includeEvents: boolean;
  includeMedia: boolean;
  allowCompositeSharing: boolean;
  shareLivingDetails: boolean;
  preferredLabel?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CompositeIdentityGroup {
  id: string;
  references: SourceReference[];
  status: IdentityLinkStatus;
  preferredReference?: SourceReference;
  reviewedBy?: string;
  reviewedAt?: string;
  reason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CompositeRelationship {
  id: string;
  source: SourceReference;
  target: SourceReference;
  type: RelationType;
  customType?: string;
  marriageDate?: string;
  divorceDate?: string;
  marriageStatus?: MarriageStatus;
  createdBy: string;
  createdAt: string;
}

export interface CompositeTreeConfig {
  treeId: string;
  schemaVersion: 1;
  revision: number;
  sources: CompositeSource[];
  identityGroups: CompositeIdentityGroup[];
  crossTreeRelationships: CompositeRelationship[];
  publishedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export type SourceEntityType = 'MEMBER' | 'RELATIONSHIP' | 'EVENT' | 'MEDIA';

export interface SourceProvenance {
  treeId: string;
  entityId: string;
  entityType: SourceEntityType;
  sourceUpdatedAt?: string;
}

export interface VirtualMember extends Omit<Member, 'id' | 'treeId'> {
  id: string;
  treeId: string;
  sourceReferences: SourceReference[];
  preferredReference: SourceReference;
  provenance: SourceProvenance[];
  hasConflictingFields: boolean;
  isPlaceholder?: boolean;
}

export interface VirtualRelationship
  extends Omit<Relationship, 'id' | 'treeId' | 'sourceMemberId' | 'targetMemberId'> {
  id: string;
  treeId: string;
  sourceMemberId: string;
  targetMemberId: string;
  provenance: SourceProvenance[];
  isCrossTree: boolean;
}

/**
 * Read-only event DTO emitted by a composite resolver.
 *
 * The explicit type prevents source Event values from being cast after their
 * identity, tree, member and media references have been rewritten.
 */
export interface ResolvedEvent extends Omit<Event, 'id' | 'treeId' | 'memberIds' | 'mediaIds'> {
  id: string;
  treeId: string;
  memberIds: string[];
  mediaIds: string[];
  provenance: SourceProvenance[];
}

/**
 * Read-only media DTO emitted by a composite resolver.
 *
 * Legacy singular links are intentionally removed. Composite consumers always
 * receive normalized arrays containing virtual member and event identifiers.
 */
export interface ResolvedMediaMetadata
  extends Omit<MediaMetadata, 'id' | 'treeId' | 'memberId' | 'eventId' | 'memberIds' | 'eventIds'> {
  id: string;
  treeId: string;
  memberIds: string[];
  eventIds: string[];
  provenance: SourceProvenance[];
}

export interface ResolvedSourceManifest {
  sourceTreeId: string;
  status: CompositeSourceStatus;
  version: string;
  resolvedMemberCount: number;
  warningCode?: string;
}

export type CompositeWarningCode =
  | 'SOURCE_FORBIDDEN'
  | 'SOURCE_UNAVAILABLE'
  | 'STALE_SOURCE'
  | 'INVALID_REFERENCE'
  | 'IDENTITY_CONFLICT'
  | 'UNRESOLVED_IDENTITY';

export interface CompositeWarning {
  code: CompositeWarningCode;
  message: string;
  sourceTreeId?: string;
  sourceReference?: SourceReference;
  entityId?: string;
}

export interface SourcePreview {
  sourceTreeId: string;
  memberCount: number;
  relationshipCount: number;
  eventCount: number;
  mediaCount: number;
  warnings: Array<{
    code: 'INVALID_SCOPE' | 'INVALID_REFERENCE' | 'REFERENCE_OUT_OF_SCOPE';
    message: string;
    entityId?: string;
  }>;
}

export interface ResolvedTreeData {
  tree: FamilyTree;
  members: VirtualMember[];
  relationships: VirtualRelationship[];
  events: ResolvedEvent[];
  mediaMetadata: ResolvedMediaMetadata[];
  sourceManifest: ResolvedSourceManifest[];
  warnings: CompositeWarning[];
  resolvedAt: string;
  configRevision: number;
  stale: boolean;
}

/**
 * Discriminated action codes for the composite config audit log
 * (composite-change-logs.json).
 *
 * Each code corresponds to one class of CompositeTreeConfig mutation.
 * No living-person sensitive fields (name, phone, email, etc.) are stored in
 * audit entries; only structural config identifiers are captured.
 */
export type CompositeAuditAction =
  | 'CONFIG_CREATED'
  | 'SOURCE_ADDED'
  | 'SOURCE_UPDATED'
  | 'SOURCE_REMOVED'
  | 'IDENTITY_GROUP_UPSERTED'
  | 'IDENTITY_GROUP_REMOVED'
  | 'CROSS_TREE_RELATIONSHIP_CREATED'
  | 'CROSS_TREE_RELATIONSHIP_DELETED'
  | 'CONFIG_PUBLISHED';

/**
 * One entry in the composite audit log (composite-change-logs.json).
 *
 * The log is append-only from the store's perspective. Each successful config
 * mutation appends one entry. The log can be used to reconstruct the history
 * of a composite configuration and to support undo operations.
 *
 * Fields must NOT contain living-person sensitive data such as member names,
 * dates of birth, phone numbers or email addresses.
 */
export interface CompositeAuditEntry {
  id: string;
  compositeTreeId: string;
  actorId: string;
  action: CompositeAuditAction;
  /** Revision of the CompositeTreeConfig after this mutation. */
  revision: number;
  /** Structural summary of the config state before the mutation. */
  previousData?: Record<string, unknown>;
  /** Structural summary of the config state after the mutation. */
  newData?: Record<string, unknown>;
  /** The SourceReference most directly affected by this action, if applicable. */
  sourceReference?: SourceReference;
  timestamp: string;
}
