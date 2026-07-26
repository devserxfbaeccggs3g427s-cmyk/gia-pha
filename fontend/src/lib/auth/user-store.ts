import type { User } from '@/data/types';
import { getUsers } from '@/lib/blob/readers';
import { putUsers } from '@/lib/blob/writers';

export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

export async function findUserById(id: string): Promise<User | null> {
  const users = await getUsers();
  return users.find((user) => user.id === id) ?? null;
}

export async function findUserByEmail(email: string): Promise<User | null> {
  const normalizedEmail = normalizeEmail(email);
  const users = await getUsers();
  return users.find((user) => normalizeEmail(user.email) === normalizedEmail) ?? null;
}

export async function updateUserRecord(user: User): Promise<User> {
  const users = await getUsers();
  const index = users.findIndex((candidate) => candidate.id === user.id);

  if (index === -1) {
    throw new Error(`User "${user.id}" does not exist`);
  }

  users[index] = user;
  await putUsers(users);
  return user;
}

export async function createUserRecord(user: User): Promise<User> {
  const users = await getUsers();

  if (users.some((candidate) => normalizeEmail(candidate.email) === normalizeEmail(user.email))) {
    throw new Error('EMAIL_ALREADY_EXISTS');
  }

  users.push(user);
  await putUsers(users);
  return user;
}

