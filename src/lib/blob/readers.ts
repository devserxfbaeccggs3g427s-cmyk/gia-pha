import type { ChangeLog, Event, FamilyTree, MediaMetadata, Member, Relationship, User } from '@/data/types';
import { BLOB_PATHS, readBlob } from './client';

export async function getUsers(): Promise<User[]> {
  return (await readBlob<User[]>(BLOB_PATHS.users())) ?? [];
}

export async function getTrees(): Promise<FamilyTree[]> {
  return (await readBlob<FamilyTree[]>(BLOB_PATHS.trees())) ?? [];
}

export async function getMembers(treeId: string): Promise<Member[]> {
  return (await readBlob<Member[]>(BLOB_PATHS.members(treeId))) ?? [];
}

export async function getRelationships(treeId: string): Promise<Relationship[]> {
  return (await readBlob<Relationship[]>(BLOB_PATHS.relationships(treeId))) ?? [];
}

export async function getEvents(treeId: string): Promise<Event[]> {
  return (await readBlob<Event[]>(BLOB_PATHS.events(treeId))) ?? [];
}

export async function getMediaMetadata(treeId: string): Promise<MediaMetadata[]> {
  return (await readBlob<MediaMetadata[]>(BLOB_PATHS.mediaMetadata(treeId))) ?? [];
}

export async function getChangeLogs(treeId: string): Promise<ChangeLog[]> {
  return (await readBlob<ChangeLog[]>(BLOB_PATHS.changeLogs(treeId))) ?? [];
}
