import type { Member } from '@/data/types';

/**
 * Resolves the managed avatar content endpoint while preserving compatibility
 * with members that still have a public avatarUrl from an older import.
 */
export function getMemberAvatarUrl(
  member: Pick<Member, 'avatarMediaId' | 'avatarUrl' | 'treeId'> | { avatarMediaId?: string; avatarUrl?: string },
  treeId?: string
): string | undefined {
  const resolvedTreeId = treeId ?? ('treeId' in member ? member.treeId : undefined);
  if (member.avatarMediaId && resolvedTreeId) {
    return `/api/media/${encodeURIComponent(member.avatarMediaId)}/content?treeId=${encodeURIComponent(resolvedTreeId)}`;
  }
  return member.avatarUrl;
}
