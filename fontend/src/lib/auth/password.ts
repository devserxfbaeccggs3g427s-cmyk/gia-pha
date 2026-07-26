import { compare, hash } from 'bcryptjs';
import { BCRYPT_COST_FACTOR } from './constants';

export function hashPassword(password: string): Promise<string> {
  return hash(password, BCRYPT_COST_FACTOR);
}

export function verifyPassword(password: string, passwordHash: string): Promise<boolean> {
  return compare(password, passwordHash);
}

