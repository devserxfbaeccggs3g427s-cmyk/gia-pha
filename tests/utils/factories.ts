import type { ChangeLog, Event, FamilyTree, MediaMetadata, Member, Relationship, User } from '@/data/types';

let sequence = 0;

export function testId(prefix: string): string {
  sequence += 1;
  return `${prefix}_${sequence}`;
}

export function resetFactorySequence(): void {
  sequence = 0;
}

export function buildUser(overrides: Partial<User> = {}): User {
  const now = new Date().toISOString();
  const id = overrides.id ?? testId('user');

  return {
    id,
    email: `${id}@example.com`,
    name: 'Test User',
    passwordHash: '$2b$12$test-hash',
    provider: 'credentials',
    failedLoginAttempts: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides
  };
}

export function buildFamilyTree(overrides: Partial<FamilyTree> = {}): FamilyTree {
  const now = new Date().toISOString();
  const ownerId = overrides.ownerId ?? testId('user');

  return {
    id: testId('tree'),
    // Writers always set kind; the read boundary normalises absent kind to
    // STANDALONE.  Tests that write-then-read must include kind to match.
    kind: 'STANDALONE',
    name: 'Test Family Tree',
    ownerId,
    memberships: [
      {
        userId: ownerId,
        role: 'ADMIN',
        createdAt: now
      }
    ],
    createdAt: now,
    updatedAt: now,
    ...overrides
  };
}

export function buildMember(overrides: Partial<Member> = {}): Member {
  const now = new Date().toISOString();

  return {
    id: testId('member'),
    treeId: overrides.treeId ?? 'tree_1',
    firstName: 'Van',
    lastName: 'Nguyen',
    fullName: 'Nguyen Van A',
    gender: 'MALE',
    isAlive: true,
    createdAt: now,
    updatedAt: now,
    ...overrides
  };
}

export function buildRelationship(overrides: Partial<Relationship> = {}): Relationship {
  return {
    id: testId('relationship'),
    treeId: overrides.treeId ?? 'tree_1',
    sourceMemberId: overrides.sourceMemberId ?? 'member_parent',
    targetMemberId: overrides.targetMemberId ?? 'member_child',
    type: 'PARENT_CHILD',
    createdAt: new Date().toISOString(),
    ...overrides
  };
}

export function buildEvent(overrides: Partial<Event> = {}): Event {
  const now = new Date().toISOString();

  return {
    id: testId('event'),
    treeId: overrides.treeId ?? 'tree_1',
    type: 'REUNION',
    title: 'Family Reunion',
    eventDate: now,
    memberIds: [],
    mediaIds: [],
    createdAt: now,
    updatedAt: now,
    ...overrides
  };
}

export function buildMediaMetadata(overrides: Partial<MediaMetadata> = {}): MediaMetadata {
  return {
    id: testId('media'),
    treeId: overrides.treeId ?? 'tree_1',
    filename: 'family-photo.webp',
    originalName: 'family-photo.webp',
    mimeType: 'image/webp',
    fileSize: 1024,
    blobUrl: 'https://blob.test/media/family-photo.webp',
    uploadedAt: new Date().toISOString(),
    ...overrides
  };
}

export function buildChangeLog(overrides: Partial<ChangeLog> = {}): ChangeLog {
  return {
    id: testId('change'),
    treeId: overrides.treeId ?? 'tree_1',
    userId: overrides.userId ?? 'user_1',
    action: 'CREATE',
    entityType: 'MEMBER',
    newData: {},
    createdAt: new Date().toISOString(),
    ...overrides
  };
}
