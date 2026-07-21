import { nanoid } from 'nanoid';
import { createShareLinkSchema } from '@/data/schemas';
import type { Event, FamilyTree, MediaMetadata, Member, Relationship, ShareLink } from '@/data/types';
import { compositeResolver } from './composite-resolver';
import { redactLivingDetails } from '@/lib/composite/composite-cache';
import { AUTH_SECRET } from '@/lib/auth/constants';
import { createSignedShareToken, ShareTokenError, verifySignedShareToken } from '@/lib/auth/share-token';
import { BLOB_PATHS, deleteBlob, readBlob, writeBlob } from '@/lib/blob/client';
import { getCompositeConfig, getEvents, getMembers, getMediaMetadata, getRelationships, getTrees } from '@/lib/blob/readers';
import { getUserTreeRole } from '@/lib/auth/rbac';
import { requireCompositeFeature } from '@/lib/composite/feature-flags';

const MAX_SHARE_LINK_LIFETIME_MS = 365 * 24 * 60 * 60 * 1000;

export interface SharedTreeData {
  tree: FamilyTree;
  members: Member[];
  relationships: Relationship[];
  events: Event[];
  mediaMetadata: MediaMetadata[];
  shareLink: Pick<ShareLink, 'permission' | 'expiresAt'>;
}

export class ShareLinkServiceError extends Error {
  constructor(
    public readonly code: 'INVALID_INPUT' | 'TREE_NOT_FOUND' | 'LINK_NOT_FOUND' | 'LINK_EXPIRED',
    message: string
  ) {
    super(message);
    this.name = 'ShareLinkServiceError';
  }
}

export class ShareLinkService {
  constructor(private readonly clock: () => Date = () => new Date()) {}

  async createShareLink(treeId: string, data: unknown): Promise<ShareLink> {
    assertIdentifier(treeId, 'treeId');
    await this.assertTreeExists(treeId);
    const input = createShareLinkSchema.parse(data);
    const now = this.clock();
    const expiresAt = new Date(input.expiresAt);
    if (expiresAt.getTime() <= now.getTime()) {
      throw new ShareLinkServiceError('INVALID_INPUT', 'Share link expiration must be in the future');
    }
    if (expiresAt.getTime() - now.getTime() > MAX_SHARE_LINK_LIFETIME_MS) {
      throw new ShareLinkServiceError('INVALID_INPUT', 'Share links may be valid for at most 365 days');
    }

    const id = nanoid();
    const token = await createSignedShareToken({
      version: 1,
      treeId,
      expiresAt: expiresAt.getTime(),
      nonce: id,
      permission: 'VIEW'
    }, AUTH_SECRET).catch((error: unknown) => {
      if (error instanceof ShareTokenError && error.code === 'CONFIGURATION') {
        throw new ShareLinkServiceError('INVALID_INPUT', 'Share link signing is not configured');
      }
      throw error;
    });
    const link: ShareLink = {
      id,
      treeId,
      token,
      permission: 'VIEW',
      expiresAt: expiresAt.toISOString(),
      createdAt: now.toISOString()
    };
    const links = await this.readTreeLinks(treeId);

    // The token record enables constant-time public validation. Roll it back if indexing fails.
    await writeBlob(BLOB_PATHS.shareLink(link.token), link);
    try {
      await writeBlob(BLOB_PATHS.shareLinks(treeId), [...links, link]);
    } catch (error) {
      await deleteBlob(BLOB_PATHS.shareLink(link.token)).catch(() => undefined);
      throw error;
    }
    return link;
  }

  async listShareLinks(treeId: string): Promise<ShareLink[]> {
    assertIdentifier(treeId, 'treeId');
    await this.assertTreeExists(treeId);
    return (await this.readTreeLinks(treeId)).sort((left, right) => right.createdAt.localeCompare(left.createdAt));
  }

  async revokeShareLink(treeId: string, linkId: string): Promise<void> {
    assertIdentifier(treeId, 'treeId');
    assertIdentifier(linkId, 'linkId');
    await this.assertTreeExists(treeId);
    const links = await this.readTreeLinks(treeId);
    const link = links.find((candidate) => candidate.id === linkId);
    if (!link) throw new ShareLinkServiceError('LINK_NOT_FOUND', 'Share link not found');

    await writeBlob(BLOB_PATHS.shareLinks(treeId), links.filter((candidate) => candidate.id !== linkId));
    try {
      await deleteBlob(BLOB_PATHS.shareLink(link.token));
    } catch (error) {
      await writeBlob(BLOB_PATHS.shareLinks(treeId), links).catch(() => undefined);
      throw error;
    }
  }

  async validateShareToken(token: string): Promise<ShareLink> {
    assertToken(token);
    let payload;
    try {
      payload = await verifySignedShareToken(token, AUTH_SECRET, this.clock().getTime());
    } catch (error) {
      if (error instanceof ShareTokenError && error.code === 'EXPIRED') {
        throw new ShareLinkServiceError('LINK_EXPIRED', error.message);
      }
      throw new ShareLinkServiceError('LINK_NOT_FOUND', 'Share link not found');
    }
    const link = await readBlob<ShareLink>(BLOB_PATHS.shareLink(token));
    if (!link || link.token !== token || link.permission !== 'VIEW' || link.treeId !== payload.treeId || link.id !== payload.nonce) {
      throw new ShareLinkServiceError('LINK_NOT_FOUND', 'Share link not found');
    }
    if (Date.parse(link.expiresAt) <= this.clock().getTime()) {
      throw new ShareLinkServiceError('LINK_EXPIRED', 'Share link has expired');
    }
    return link;
  }

  async getSharedTree(token: string): Promise<SharedTreeData> {
    const link = await this.validateShareToken(token);
    const [trees, members, relationships, events, mediaMetadata] = await Promise.all([
      getTrees(),
      getMembers(link.treeId),
      getRelationships(link.treeId),
      getEvents(link.treeId),
      getMediaMetadata(link.treeId)
    ]);
    const tree = trees.find((candidate) => candidate.id === link.treeId);
    if (!tree) throw new ShareLinkServiceError('TREE_NOT_FOUND', 'Family tree not found');

    if ((tree.kind ?? 'STANDALONE') === 'COMPOSITE') {
      requireCompositeFeature('sharing');
      const config = await getCompositeConfig(tree.id);
      if (!config) throw new ShareLinkServiceError('TREE_NOT_FOUND', 'Composite configuration not found');
      const treeIndex = new Map(trees.map((candidate) => [candidate.id, candidate]));
      const consented = config.sources.filter((source) => { const sourceTree = treeIndex.get(source.sourceTreeId); return Boolean(sourceTree && source.allowCompositeSharing && getUserTreeRole(sourceTree, tree.ownerId) === 'ADMIN'); });
      if (consented.length !== config.sources.length) throw new ShareLinkServiceError('INVALID_INPUT', 'Every source Admin must consent before composite sharing');
      const livingAllowed = new Set(consented.filter((source) => source.shareLivingDetails).map((source) => source.sourceTreeId));
      const resolved = redactLivingDetails(await compositeResolver.resolveForUser(tree.id, tree.ownerId), livingAllowed);
      return { tree: { ...resolved.tree, ownerId: '', memberships: [] }, members: resolved.members, relationships: resolved.relationships, events: resolved.events, mediaMetadata: resolved.mediaMetadata, shareLink: { permission: 'VIEW', expiresAt: link.expiresAt } };
    }

    // Never expose ownership or membership details through a public link.
    const publicTree: FamilyTree = { ...tree, ownerId: '', memberships: [] };
    return {
      tree: publicTree,
      members,
      relationships,
      events,
      mediaMetadata,
      shareLink: { permission: 'VIEW', expiresAt: link.expiresAt }
    };
  }

  private async readTreeLinks(treeId: string): Promise<ShareLink[]> {
    return (await readBlob<ShareLink[]>(BLOB_PATHS.shareLinks(treeId))) ?? [];
  }

  private async assertTreeExists(treeId: string): Promise<void> {
    if (!(await getTrees()).some((tree) => tree.id === treeId)) {
      throw new ShareLinkServiceError('TREE_NOT_FOUND', 'Family tree not found');
    }
  }
}

function assertIdentifier(value: string, field: string): void {
  if (!/^[A-Za-z0-9_-]{1,128}$/.test(value)) {
    throw new ShareLinkServiceError('INVALID_INPUT', `${field} is invalid`);
  }
}

function assertToken(token: string): void {
  if (!/^[A-Za-z0-9_-]{20,400}\.[A-Za-z0-9_-]{43}$/.test(token)) {
    throw new ShareLinkServiceError('LINK_NOT_FOUND', 'Share link not found');
  }
}

export const shareLinkService = new ShareLinkService();
export default shareLinkService;

export const createShareLink = shareLinkService.createShareLink.bind(shareLinkService);
export const listShareLinks = shareLinkService.listShareLinks.bind(shareLinkService);
export const revokeShareLink = shareLinkService.revokeShareLink.bind(shareLinkService);
export const validateShareToken = shareLinkService.validateShareToken.bind(shareLinkService);
