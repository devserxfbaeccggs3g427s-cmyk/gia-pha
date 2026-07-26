import { describe, expect, it } from 'vitest';
import { getMemberAvatarUrl } from '@/lib/media/avatar';

describe('getMemberAvatarUrl', () => {
  it('prefers the managed media endpoint and keeps legacy URL compatibility', () => {
    expect(getMemberAvatarUrl({ avatarMediaId: 'media/id', avatarUrl: 'https://legacy.test/a.jpg' }, 'tree id'))
      .toBe('/api/media/media%2Fid/content?treeId=tree%20id');
    expect(getMemberAvatarUrl({ avatarUrl: 'https://legacy.test/a.jpg' }, 'tree id'))
      .toBe('https://legacy.test/a.jpg');
  });
});
