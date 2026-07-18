import { describe, expect, it } from 'vitest';
import {
  compositeTreeConfigSchema,
  crossTreeRelationshipInputSchema,
  familyTreeSchema,
  identityGroupInputSchema,
  sourceScopeInputSchema
} from '@/data/schemas';
import type {
  CompositeRelationship,
  CompositeSource,
  CompositeTreeConfig,
  SourceReference
} from '@/data/types';

const NOW = '2026-01-01T00:00:00.000Z';

function buildSource(sourceTreeId: string, overrides: Partial<CompositeSource> = {}): CompositeSource {
  return {
    id: `source-${sourceTreeId}`,
    sourceTreeId,
    scope: 'FULL_TREE',
    anchorMemberIds: [],
    selectedMemberIds: [],
    includeSpouses: false,
    includeEvents: true,
    includeMedia: true,
    allowCompositeSharing: false,
    shareLivingDetails: false,
    createdAt: NOW,
    updatedAt: NOW,
    ...overrides
  };
}

function reference(treeId: string, memberId: string): SourceReference {
  return { treeId, memberId };
}

function buildRelationship(
  id: string,
  source: SourceReference,
  target: SourceReference,
  overrides: Partial<CompositeRelationship> = {}
): CompositeRelationship {
  return {
    id,
    source,
    target,
    type: 'SPOUSE',
    createdBy: 'admin-1',
    createdAt: NOW,
    ...overrides
  };
}

function buildConfig(overrides: Partial<CompositeTreeConfig> = {}): CompositeTreeConfig {
  return {
    treeId: 'composite-1',
    schemaVersion: 1,
    revision: 0,
    sources: [buildSource('tree-a'), buildSource('tree-b')],
    identityGroups: [],
    crossTreeRelationships: [],
    createdAt: NOW,
    updatedAt: NOW,
    ...overrides
  };
}

describe('composite domain schemas', () => {
  it('normalizes legacy tree metadata and preserves explicit tree kinds', () => {
    const legacyTree = {
      id: 'legacy-tree',
      name: 'Legacy tree',
      ownerId: 'owner-1',
      memberships: [{ userId: 'owner-1', role: 'ADMIN', createdAt: NOW }],
      createdAt: NOW,
      updatedAt: NOW
    };

    expect(familyTreeSchema.parse(legacyTree)).toEqual({
      ...legacyTree,
      kind: 'STANDALONE'
    });
    expect(familyTreeSchema.parse({ ...legacyTree, kind: 'COMPOSITE' }).kind).toBe('COMPOSITE');
    expect(familyTreeSchema.safeParse({ ...legacyTree, kind: 'NESTED' }).success).toBe(false);
  });

  it('applies safe source input defaults', () => {
    expect(sourceScopeInputSchema.parse({
      sourceTreeId: 'tree-a',
      scope: 'FULL_TREE'
    })).toEqual({
      sourceTreeId: 'tree-a',
      scope: 'FULL_TREE',
      anchorMemberIds: [],
      selectedMemberIds: [],
      includeSpouses: false,
      includeEvents: true,
      includeMedia: true,
      allowCompositeSharing: false,
      shareLivingDetails: false
    });
  });

  it.each([
    {
      name: 'DESCENDANTS without an anchor',
      input: { sourceTreeId: 'tree-a', scope: 'DESCENDANTS' }
    },
    {
      name: 'SELECTED_MEMBERS without a selection',
      input: { sourceTreeId: 'tree-a', scope: 'SELECTED_MEMBERS' }
    },
    {
      name: 'FULL_TREE with scope-specific member ids',
      input: { sourceTreeId: 'tree-a', scope: 'FULL_TREE', anchorMemberIds: ['member-1'] }
    },
    {
      name: 'duplicate anchors',
      input: {
        sourceTreeId: 'tree-a',
        scope: 'DESCENDANTS',
        anchorMemberIds: ['member-1', 'member-1']
      }
    },
    {
      name: 'living-detail sharing without composite sharing',
      input: {
        sourceTreeId: 'tree-a',
        scope: 'FULL_TREE',
        shareLivingDetails: true
      }
    }
  ])('rejects invalid source scope input: $name', ({ input }) => {
    expect(sourceScopeInputSchema.safeParse(input).success).toBe(false);
  });

  it('validates identity references and explicit confirmation review metadata', () => {
    const confirmed = {
      references: [reference('tree-a', 'member-a'), reference('tree-b', 'member-b')],
      status: 'CONFIRMED' as const,
      preferredReference: reference('tree-a', 'member-a'),
      reviewedBy: 'admin-1',
      reviewedAt: NOW
    };

    expect(identityGroupInputSchema.safeParse(confirmed).success).toBe(true);
    expect(identityGroupInputSchema.safeParse({
      ...confirmed,
      reviewedBy: undefined,
      reviewedAt: undefined
    }).success).toBe(false);
    expect(identityGroupInputSchema.safeParse({
      ...confirmed,
      references: [reference('tree-a', 'member-a'), reference('tree-a', 'member-a')]
    }).success).toBe(false);
    expect(identityGroupInputSchema.safeParse({
      ...confirmed,
      preferredReference: reference('tree-b', 'not-in-group')
    }).success).toBe(false);
  });

  it('enforces the 20-source MVP limit and unique source trees', () => {
    const twentySources = Array.from({ length: 20 }, (_, index) => buildSource(`tree-${index}`));
    expect(compositeTreeConfigSchema.safeParse(buildConfig({ sources: twentySources })).success).toBe(true);

    const twentyOneSources = Array.from({ length: 21 }, (_, index) => buildSource(`tree-${index}`));
    expect(compositeTreeConfigSchema.safeParse(buildConfig({ sources: twentyOneSources })).success).toBe(false);

    expect(compositeTreeConfigSchema.safeParse(buildConfig({
      sources: [buildSource('tree-a'), buildSource('tree-a', { id: 'another-source-id' })]
    })).success).toBe(false);
  });

  it('allows a SourceReference in at most one confirmed identity group', () => {
    const sharedReference = reference('tree-a', 'member-a');
    const identityGroups = [
      {
        id: 'group-1',
        references: [sharedReference, reference('tree-b', 'member-b')],
        status: 'CONFIRMED' as const,
        reviewedBy: 'admin-1',
        reviewedAt: NOW,
        createdAt: NOW,
        updatedAt: NOW
      },
      {
        id: 'group-2',
        references: [sharedReference, reference('tree-b', 'member-c')],
        status: 'CONFIRMED' as const,
        reviewedBy: 'admin-1',
        reviewedAt: NOW,
        createdAt: NOW,
        updatedAt: NOW
      }
    ];

    expect(compositeTreeConfigSchema.safeParse(buildConfig({ identityGroups })).success).toBe(false);
  });

  it('requires every persisted reference to belong to a configured source', () => {
    const identityGroups = [{
      id: 'group-1',
      references: [reference('tree-a', 'member-a'), reference('missing-tree', 'member-x')],
      status: 'PROPOSED' as const,
      createdAt: NOW,
      updatedAt: NOW
    }];
    const crossTreeRelationships = [
      buildRelationship(
        'relationship-1',
        reference('tree-a', 'member-a'),
        reference('missing-tree', 'member-x')
      )
    ];

    expect(compositeTreeConfigSchema.safeParse(buildConfig({ identityGroups })).success).toBe(false);
    expect(compositeTreeConfigSchema.safeParse(buildConfig({ crossTreeRelationships })).success).toBe(false);
  });

  it('validates cross-tree endpoints, CUSTOM metadata and canonical duplicates', () => {
    const validInput = {
      source: reference('tree-a', 'member-a'),
      target: reference('tree-b', 'member-b'),
      type: 'CUSTOM' as const,
      customType: 'Guardian'
    };

    expect(crossTreeRelationshipInputSchema.safeParse(validInput).success).toBe(true);
    expect(crossTreeRelationshipInputSchema.safeParse({
      ...validInput,
      target: reference('tree-a', 'member-b')
    }).success).toBe(false);
    expect(crossTreeRelationshipInputSchema.safeParse({
      ...validInput,
      customType: undefined
    }).success).toBe(false);

    const first = buildRelationship(
      'relationship-1',
      reference('tree-a', 'member-a'),
      reference('tree-b', 'member-b')
    );
    const reversedDuplicate = buildRelationship(
      'relationship-2',
      first.target,
      first.source
    );

    expect(compositeTreeConfigSchema.safeParse(buildConfig({
      crossTreeRelationships: [first, reversedDuplicate]
    }))).toMatchObject({ success: false });
  });

  it.each(['PARENT_CHILD', 'ADOPTED'] as const)(
    'keeps %s direction when checking canonical relationship duplicates',
    (type) => {
      const forward = buildRelationship(
        'relationship-1',
        reference('tree-a', 'member-a'),
        reference('tree-b', 'member-b'),
        { type }
      );
      const reverseDirection = buildRelationship(
        'relationship-2',
        forward.target,
        forward.source,
        { type }
      );

      expect(compositeTreeConfigSchema.safeParse(buildConfig({
        crossTreeRelationships: [forward, reverseDirection]
      })).success).toBe(true);
    }
  );
});