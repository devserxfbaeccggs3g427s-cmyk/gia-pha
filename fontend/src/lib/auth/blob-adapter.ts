import type { Adapter, AdapterAccount, AdapterUser } from 'next-auth/adapters';
import { nanoid } from 'nanoid';
import type { OAuthAccount, Provider, User } from '@/data/types';
import { getUsers } from '@/lib/blob/readers';
import { putUsers } from '@/lib/blob/writers';
import { normalizeEmail } from './user-store';

function toAdapterUser(user: User): AdapterUser {
  return {
    id: user.id,
    email: user.email,
    emailVerified: user.emailVerified ? new Date(user.emailVerified) : null,
    name: user.name,
    image: user.image ?? null
  };
}

export function VercelBlobAdapter(): Adapter {
  return {
    async createUser(adapterUser: Omit<AdapterUser, 'id'>) {
      const users = await getUsers();
      const now = new Date().toISOString();
      const user: User = {
        id: nanoid(),
        email: normalizeEmail(adapterUser.email),
        name: adapterUser.name?.trim() || adapterUser.email,
        passwordHash: '',
        image: adapterUser.image ?? undefined,
        provider: 'google',
        emailVerified: adapterUser.emailVerified?.toISOString() ?? now,
        failedLoginAttempts: 0,
        createdAt: now,
        updatedAt: now
      };

      users.push(user);
      await putUsers(users);
      return toAdapterUser(user);
    },

    async getUser(id) {
      const users = await getUsers();
      const user = users.find((candidate) => candidate.id === id);
      return user ? toAdapterUser(user) : null;
    },

    async getUserByEmail(email) {
      const users = await getUsers();
      const normalizedEmail = normalizeEmail(email);
      const user = users.find((candidate) => normalizeEmail(candidate.email) === normalizedEmail);
      return user ? toAdapterUser(user) : null;
    },

    async getUserByAccount(account) {
      const users = await getUsers();
      const user = users.find((candidate) =>
        candidate.oauthAccounts?.some(
          (storedAccount) =>
            storedAccount.provider === account.provider &&
            storedAccount.providerAccountId === account.providerAccountId
        )
      );
      return user ? toAdapterUser(user) : null;
    },

    async updateUser(adapterUser) {
      const users = await getUsers();
      const index = users.findIndex((candidate) => candidate.id === adapterUser.id);
      if (index === -1) throw new Error(`User "${adapterUser.id}" does not exist`);

      const current = users[index];
      const updated: User = {
        ...current,
        email: adapterUser.email ? normalizeEmail(adapterUser.email) : current.email,
        name: adapterUser.name ?? current.name,
        image: adapterUser.image ?? current.image,
        emailVerified:
          adapterUser.emailVerified === undefined
            ? current.emailVerified
            : adapterUser.emailVerified?.toISOString() ?? null,
        updatedAt: new Date().toISOString()
      };
      users[index] = updated;
      await putUsers(users);
      return toAdapterUser(updated);
    },

    async deleteUser(userId) {
      const users = await getUsers();
      const index = users.findIndex((candidate) => candidate.id === userId);
      if (index === -1) return null;
      const [deleted] = users.splice(index, 1);
      await putUsers(users);
      return toAdapterUser(deleted);
    },

    async linkAccount(account: AdapterAccount) {
      const users = await getUsers();
      const index = users.findIndex((candidate) => candidate.id === account.userId);
      if (index === -1) throw new Error(`User "${account.userId}" does not exist`);

      const provider = account.provider as OAuthAccount['provider'];
      if (provider !== 'google' && provider !== 'facebook') {
        throw new Error(`Unsupported OAuth provider "${account.provider}"`);
      }

      const storedAccount: OAuthAccount = {
        provider,
        providerAccountId: account.providerAccountId,
        type: 'oauth'
      };
      const user = users[index];
      const accounts = user.oauthAccounts ?? [];
      if (!accounts.some((candidate) => candidate.provider === provider && candidate.providerAccountId === account.providerAccountId)) {
        accounts.push(storedAccount);
      }
      users[index] = {
        ...user,
        provider: provider as Provider,
        oauthAccounts: accounts,
        updatedAt: new Date().toISOString()
      };
      await putUsers(users);
      return account;
    },

    async unlinkAccount(account: Pick<AdapterAccount, 'provider' | 'providerAccountId'>) {
      const users = await getUsers();
      const index = users.findIndex((candidate) =>
        candidate.oauthAccounts?.some(
          (storedAccount) =>
            storedAccount.provider === account.provider &&
            storedAccount.providerAccountId === account.providerAccountId
        )
      );
      if (index === -1) return undefined;

      users[index] = {
        ...users[index],
        oauthAccounts: users[index].oauthAccounts?.filter(
          (candidate) =>
            candidate.provider !== account.provider ||
            candidate.providerAccountId !== account.providerAccountId
        ),
        updatedAt: new Date().toISOString()
      };
      await putUsers(users);
      return account as AdapterAccount;
    }
  };
}

export const createVercelBlobAdapter = VercelBlobAdapter;
