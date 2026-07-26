import type { Album, ChangeLog, Event, FamilyTree, MediaMetadata, Member, Relationship, User } from '@/data/types';
import { normalizeRelationships } from '@/lib/algorithms/relationship-normalization';
import { BLOB_PATHS, writeBlob } from './client';

export async function putUsers(users: User[]): Promise<void> {
  await writeBlob(BLOB_PATHS.users(), users);
}

export async function putTrees(trees: FamilyTree[]): Promise<void> {
  await writeBlob(BLOB_PATHS.trees(), trees);
}

export async function putMembers(treeId: string, members: Member[]): Promise<void> {
  await writeBlob(BLOB_PATHS.members(treeId), members);
}

export async function putRelationships(treeId: string, relationships: Relationship[]): Promise<void> {
  await writeBlob(BLOB_PATHS.relationships(treeId), normalizeRelationships(relationships));
}

export async function putEvents(treeId: string, events: Event[]): Promise<void> {
  await writeBlob(BLOB_PATHS.events(treeId), events);
}

export async function putMediaMetadata(treeId: string, metadata: MediaMetadata[]): Promise<void> {
  await writeBlob(BLOB_PATHS.mediaMetadata(treeId), metadata);
}

export async function putAlbums(treeId: string, albums: Album[]): Promise<void> {
  await writeBlob(BLOB_PATHS.albums(treeId), albums);
}

export async function putChangeLogs(treeId: string, changeLogs: ChangeLog[]): Promise<void> {
  await writeBlob(BLOB_PATHS.changeLogs(treeId), changeLogs);
}
