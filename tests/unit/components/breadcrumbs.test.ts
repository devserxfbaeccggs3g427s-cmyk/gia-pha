import { describe, expect, it } from 'vitest';
import { buildBreadcrumbItems } from '@/components/layout/breadcrumbs';

describe('buildBreadcrumbItems', () => {
  it('keeps stable IDs in every breadcrumb href', () => {
    expect(buildBreadcrumbItems('/trees/tree-id/members/member-id')).toEqual([
      { segment: 'trees', index: 0, href: '/trees' },
      { segment: 'tree-id', index: 1, href: '/trees/tree-id' },
      { segment: 'members', index: 2, href: '/trees/tree-id/members' },
      { segment: 'member-id', index: 3, href: '/trees/tree-id/members/member-id' }
    ]);
  });
});
