import { describe, expect, it } from 'vitest';
import { putEvents, putMembers, putMediaMetadata, putRelationships, putTrees } from '@/lib/blob/writers';
import { ShareLinkService } from '@/lib/services/share-link-service';
import { buildFamilyTree, buildMember } from '../../utils/factories';

describe('ShareLinkService', () => {
  it('creates a cryptographically unique, expiring VIEW-only link and returns sanitized shared data', async () => {
    let now = new Date('2026-07-18T10:30:00.000Z');
    const service = new ShareLinkService(() => new Date(now));
    const tree = buildFamilyTree({ id: 'tree-share', ownerId: 'private-owner' });
    const member = buildMember({ treeId: tree.id });
    await putTrees([tree]);
    await putMembers(tree.id, [member]);
    await putRelationships(tree.id, []);
    await putEvents(tree.id, []);
    await putMediaMetadata(tree.id, []);

    const link = await service.createShareLink(tree.id, { expiresAt: '2026-07-20T10:30:00.000Z' });
    expect(link).toMatchObject({ treeId: tree.id, permission: 'VIEW', expiresAt: '2026-07-20T10:30:00.000Z' });
    expect(link.token).toMatch(/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]{43}$/);
    await expect(service.validateShareToken(link.token)).resolves.toEqual(link);
    const tamperedToken = `${link.token.slice(0, -1)}${link.token.endsWith('a') ? 'b' : 'a'}`;
    await expect(service.validateShareToken(tamperedToken)).rejects.toMatchObject({ code: 'LINK_NOT_FOUND' });
    await expect(service.listShareLinks(tree.id)).resolves.toEqual([link]);

    const shared = await service.getSharedTree(link.token);
    expect(shared.tree).toMatchObject({ id: tree.id, ownerId: '', memberships: [] });
    expect(shared.members).toEqual([member]);
    expect(shared.shareLink).toEqual({ permission: 'VIEW', expiresAt: link.expiresAt });

    now = new Date(link.expiresAt);
    await expect(service.validateShareToken(link.token)).rejects.toMatchObject({ code: 'LINK_EXPIRED' });
  });

  it('revokes token access and prevents unsafe or excessive expiration periods', async () => {
    const now = new Date('2026-07-18T10:30:00.000Z');
    const service = new ShareLinkService(() => new Date(now));
    const tree = buildFamilyTree({ id: 'tree-revoke' });
    await putTrees([tree]);

    await expect(service.createShareLink(tree.id, { expiresAt: now.toISOString() })).rejects.toMatchObject({ code: 'INVALID_INPUT' });
    await expect(service.createShareLink(tree.id, { expiresAt: '2028-01-01T00:00:00.000Z' })).rejects.toMatchObject({ code: 'INVALID_INPUT' });

    const link = await service.createShareLink(tree.id, { expiresAt: '2026-07-19T10:30:00.000Z' });
    await service.revokeShareLink(tree.id, link.id);
    await expect(service.validateShareToken(link.token)).rejects.toMatchObject({ code: 'LINK_NOT_FOUND' });
    await expect(service.listShareLinks(tree.id)).resolves.toEqual([]);
  });
});
