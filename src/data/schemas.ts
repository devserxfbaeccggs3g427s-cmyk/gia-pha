import { z } from 'zod';

export const providerSchema = z.enum(['credentials', 'google', 'facebook']);
export const treeRoleSchema = z.enum(['ADMIN', 'EDITOR', 'VIEWER']);
export const familyTreeKindSchema = z.enum(['STANDALONE', 'COMPOSITE']);
export const genderSchema = z.enum(['MALE', 'FEMALE', 'OTHER']);
export const relationTypeSchema = z.enum(['PARENT_CHILD', 'SPOUSE', 'SIBLING', 'ADOPTED', 'CUSTOM']);
export const marriageStatusSchema = z.enum(['MARRIED', 'DIVORCED', 'WIDOWED']);
export const eventTypeSchema = z.enum(['BIRTHDAY', 'WEDDING', 'FUNERAL', 'REUNION', 'ANNIVERSARY', 'CUSTOM']);
export const changeActionSchema = z.enum(['CREATE', 'UPDATE', 'DELETE']);
export const changeEntityTypeSchema = z.enum(['MEMBER', 'RELATIONSHIP', 'EVENT', 'MEDIA']);

const identifierSchema = z.string().trim().min(1).max(200);
const optionalIsoDateTime = z.string().datetime().optional();

export const treeMembershipSchema = z.object({
  userId: identifierSchema,
  role: treeRoleSchema,
  createdAt: z.string().datetime()
});

/**
 * Persisted tree metadata reader. The default is deliberately applied only at
 * the read boundary so legacy blobs remain valid without an eager migration.
 */
export const familyTreeSchema = z.object({
  id: identifierSchema,
  kind: familyTreeKindSchema.default('STANDALONE'),
  name: z.string().min(1).max(200),
  description: z.string().max(2000).optional(),
  ownerId: identifierSchema,
  memberships: z.array(treeMembershipSchema),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime()
});

const isoDate = z.string().refine(
  (value) => {
    const match = /^(\d{4})-(\d{2})-(\d{2})(?:T.*)?$/.exec(value);
    if (!match || Number.isNaN(new Date(value).getTime())) return false;
    const calendar = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
    return calendar.getUTCFullYear() === Number(match[1]) &&
      calendar.getUTCMonth() === Number(match[2]) - 1 &&
      calendar.getUTCDate() === Number(match[3]);
  },
  'Must be a valid ISO date or date-time'
);

// Birth/death are calendar dates in the domain model.  Accepting a date-only
// ISO value avoids timezone shifts in forms, while still accepting the full
// ISO timestamp produced by existing clients.
const optionalIsoDate = z
  .string()
  .refine(
    (value) => {
      if (!/^\d{4}-\d{2}-\d{2}(?:T.*)?$/.test(value)) return false;
      const parsed = new Date(value);
      return !Number.isNaN(parsed.getTime());
    },
    'Must be a valid ISO date or date-time'
  )
  .optional();

export const createMemberSchema = z.object({
  firstName: z.string().min(1).max(100),
  lastName: z.string().min(1).max(100),
  fullName: z.string().min(1).max(200),
  nickname: z.string().max(100).optional(),
  gender: genderSchema,
  dateOfBirth: optionalIsoDate,
  dateOfDeath: optionalIsoDate,
  placeOfBirth: z.string().max(200).optional(),
  currentAddress: z.string().max(500).optional(),
  phone: z.string().max(20).optional(),
  email: z.string().email().optional(),
  occupation: z.string().max(200).optional(),
  education: z.string().max(200).optional(),
  biography: z.string().max(5000).optional(),
  achievements: z.string().max(2000).optional(),
  notes: z.string().max(2000).optional(),
  avatarMediaId: z.string().min(1).optional(),
  generation: z.number().int().min(0).optional(),
  isAlive: z.boolean().default(true)
});

export const updateMemberSchema = createMemberSchema.partial();

export const createRelationshipSchema = z
  .object({
    sourceMemberId: z.string().min(1),
    targetMemberId: z.string().min(1),
    type: relationTypeSchema,
    customType: z.string().max(100).optional(),
    marriageDate: optionalIsoDateTime,
    divorceDate: optionalIsoDateTime,
    marriageStatus: marriageStatusSchema.optional()
  })
  .refine((value) => value.sourceMemberId !== value.targetMemberId, {
    message: 'sourceMemberId and targetMemberId must be different',
    path: ['targetMemberId']
  })
  .refine((value) => value.type !== 'CUSTOM' || Boolean(value.customType?.trim()), {
    message: 'customType is required for CUSTOM relationships',
    path: ['customType']
  });

export const createEventSchema = z
  .object({
    type: eventTypeSchema,
    customType: z.string().max(100).optional(),
    title: z.string().min(1).max(200),
    eventDate: isoDate,
    location: z.string().max(300).optional(),
    description: z.string().max(2000).optional(),
    memberIds: z.array(z.string().min(1)).max(1000).default([]).transform((ids) => [...new Set(ids)]),
    mediaIds: z.array(z.string().min(1)).max(1000).default([]).transform((ids) => [...new Set(ids)])
  })
  .refine((value) => value.type !== 'CUSTOM' || Boolean(value.customType?.trim()), {
    message: 'customType is required for CUSTOM events',
    path: ['customType']
  });

export const updateEventSchema = z
  .object({
    type: eventTypeSchema.optional(),
    customType: z.string().max(100).optional(),
    title: z.string().min(1).max(200).optional(),
    eventDate: isoDate.optional(),
    location: z.string().max(300).optional(),
    description: z.string().max(2000).optional(),
    memberIds: z.array(z.string().min(1)).max(1000).transform((ids) => [...new Set(ids)]).optional(),
    mediaIds: z.array(z.string().min(1)).max(1000).transform((ids) => [...new Set(ids)]).optional()
  })
  .refine((value) => Object.keys(value).length > 0, 'At least one event field must be provided');

export const mediaUploadSchema = z.object({
  memberId: z.string().min(1).optional(),
  eventId: z.string().min(1).optional(),
  memberIds: z.array(z.string().min(1)).max(1000).optional(),
  eventIds: z.array(z.string().min(1)).max(1000).optional(),
  albumId: z.string().min(1).optional(),
  filename: z.string().min(1).max(255),
  originalName: z.string().min(1).max(255),
  mimeType: z.enum(['image/jpeg', 'image/png', 'image/webp', 'application/pdf']),
  fileSize: z.number().int().positive().max(10 * 1024 * 1024),
  isAvatar: z.boolean().default(false),
  caption: z.string().max(500).optional(),
  takenAt: optionalIsoDate
}).transform((value) => ({
  ...value,
  memberIds: [...new Set([...(value.memberIds ?? []), ...(value.memberId ? [value.memberId] : [])])],
  eventIds: [...new Set([...(value.eventIds ?? []), ...(value.eventId ? [value.eventId] : [])])]
}));

export const createAlbumSchema = z.object({
  title: z.string().trim().min(1).max(200),
  description: z.string().trim().max(2000).optional()
});

export const updateAlbumSchema = createAlbumSchema.partial().refine(
  (value) => Object.keys(value).length > 0,
  'At least one album field must be provided'
);

export const createTreeSchema = z.object({
  name: z.string().min(1).max(200),
  description: z.string().max(2000).optional()
});

export const createCompositeTreeInputSchema = createTreeSchema.extend({
  kind: z.literal('COMPOSITE')
});

export const compositeSourceScopeSchema = z.enum(['FULL_TREE', 'DESCENDANTS', 'SELECTED_MEMBERS']);
export const compositeSourceStatusSchema = z.enum(['ACTIVE', 'UNAVAILABLE']);
export const identityLinkStatusSchema = z.enum(['PROPOSED', 'CONFIRMED', 'REJECTED']);

export const sourceReferenceSchema = z.object({
  treeId: identifierSchema,
  memberId: identifierSchema
}).strict();

const sourceScopeFields = {
  scope: compositeSourceScopeSchema,
  anchorMemberIds: z.array(identifierSchema).max(1000),
  selectedMemberIds: z.array(identifierSchema).max(1000),
  includeSpouses: z.boolean(),
  includeEvents: z.boolean(),
  includeMedia: z.boolean(),
  allowCompositeSharing: z.boolean(),
  shareLivingDetails: z.boolean(),
  preferredLabel: z.string().trim().min(1).max(200).optional()
};

function addSourceScopeIssues(
  value: {
    scope: z.infer<typeof compositeSourceScopeSchema>;
    anchorMemberIds: string[];
    selectedMemberIds: string[];
    allowCompositeSharing: boolean;
    shareLivingDetails: boolean;
  },
  context: z.RefinementCtx
): void {
  const duplicateAnchor = findDuplicate(value.anchorMemberIds);
  if (duplicateAnchor) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: `Duplicate anchor member reference: ${duplicateAnchor}`,
      path: ['anchorMemberIds']
    });
  }

  const duplicateSelected = findDuplicate(value.selectedMemberIds);
  if (duplicateSelected) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: `Duplicate selected member reference: ${duplicateSelected}`,
      path: ['selectedMemberIds']
    });
  }

  if (value.scope === 'FULL_TREE' && (value.anchorMemberIds.length > 0 || value.selectedMemberIds.length > 0)) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'FULL_TREE scope must not contain anchorMemberIds or selectedMemberIds',
      path: ['scope']
    });
  }

  if (value.scope === 'DESCENDANTS') {
    if (value.anchorMemberIds.length === 0) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'DESCENDANTS scope requires at least one anchorMemberId',
        path: ['anchorMemberIds']
      });
    }
    if (value.selectedMemberIds.length > 0) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'DESCENDANTS scope must not contain selectedMemberIds',
        path: ['selectedMemberIds']
      });
    }
  }

  if (value.scope === 'SELECTED_MEMBERS') {
    if (value.selectedMemberIds.length === 0) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'SELECTED_MEMBERS scope requires at least one selectedMemberId',
        path: ['selectedMemberIds']
      });
    }
    if (value.anchorMemberIds.length > 0) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'SELECTED_MEMBERS scope must not contain anchorMemberIds',
        path: ['anchorMemberIds']
      });
    }
  }

  if (value.shareLivingDetails && !value.allowCompositeSharing) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'shareLivingDetails requires allowCompositeSharing',
      path: ['shareLivingDetails']
    });
  }
}

const persistedSourceScopeSchema = z.object(sourceScopeFields).strict();

const sourceScopeInputObjectSchema = z.object({
  sourceTreeId: identifierSchema,
  scope: compositeSourceScopeSchema,
  anchorMemberIds: z.array(identifierSchema).max(1000).default([]),
  selectedMemberIds: z.array(identifierSchema).max(1000).default([]),
  includeSpouses: z.boolean().default(false),
  includeEvents: z.boolean().default(true),
  includeMedia: z.boolean().default(true),
  allowCompositeSharing: z.boolean().default(false),
  shareLivingDetails: z.boolean().default(false),
  preferredLabel: z.string().trim().min(1).max(200).optional()
}).strict();

export const sourceScopeInputSchema = sourceScopeInputObjectSchema.superRefine(addSourceScopeIssues);
export const addSourceInputSchema = sourceScopeInputSchema;
export const updateSourceInputSchema = sourceScopeInputObjectSchema
  .omit({ sourceTreeId: true })
  .superRefine(addSourceScopeIssues);
export const compositeSourceSchema = persistedSourceScopeSchema.extend({
  id: identifierSchema,
  sourceTreeId: identifierSchema,
  sharingConsentedBy: identifierSchema.optional(),
  sharingConsentedAt: z.string().datetime().optional(),
  sharingConsentSourceVersion: z.string().optional(),
  sourceVersion: z.string().optional(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime()
}).strict().superRefine(addSourceScopeIssues);

const identityGroupFields = {
  references: z.array(sourceReferenceSchema).min(2).max(100),
  status: identityLinkStatusSchema,
  preferredReference: sourceReferenceSchema.optional(),
  reviewedBy: identifierSchema.optional(),
  reviewedAt: z.string().datetime().optional(),
  reason: z.string().trim().min(1).max(2000).optional()
};

function addIdentityGroupIssues(
  value: {
    references: Array<z.infer<typeof sourceReferenceSchema>>;
    status: z.infer<typeof identityLinkStatusSchema>;
    preferredReference?: z.infer<typeof sourceReferenceSchema>;
    reviewedBy?: string;
    reviewedAt?: string;
  },
  context: z.RefinementCtx
): void {
  const referenceKeys = value.references.map(sourceReferenceKey);
  const duplicateReference = findDuplicate(referenceKeys);
  if (duplicateReference) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'An identity group cannot contain the same SourceReference more than once',
      path: ['references']
    });
  }

  if (new Set(value.references.map((reference) => reference.treeId)).size < 2) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'An identity group must link references from at least two source trees',
      path: ['references']
    });
  }

  if (
    value.preferredReference &&
    !referenceKeys.includes(sourceReferenceKey(value.preferredReference))
  ) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'preferredReference must belong to references',
      path: ['preferredReference']
    });
  }

  if (value.status === 'CONFIRMED' && (!value.reviewedBy || !value.reviewedAt)) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Confirmed identity groups require reviewedBy and reviewedAt',
      path: ['status']
    });
  }
}

export const identityGroupInputSchema = z.object(identityGroupFields).strict().superRefine(addIdentityGroupIssues);
export const compositeIdentityGroupSchema = z.object({
  id: identifierSchema,
  ...identityGroupFields,
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime()
}).strict().superRefine(addIdentityGroupIssues);

const compositeRelationshipFields = {
  source: sourceReferenceSchema,
  target: sourceReferenceSchema,
  type: relationTypeSchema,
  customType: z.string().trim().min(1).max(100).optional(),
  marriageDate: optionalIsoDateTime,
  divorceDate: optionalIsoDateTime,
  marriageStatus: marriageStatusSchema.optional()
};

function addCompositeRelationshipIssues(
  value: {
    source: z.infer<typeof sourceReferenceSchema>;
    target: z.infer<typeof sourceReferenceSchema>;
    type: z.infer<typeof relationTypeSchema>;
    customType?: string;
  },
  context: z.RefinementCtx
): void {
  if (sourceReferenceKey(value.source) === sourceReferenceKey(value.target)) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'source and target must reference different members',
      path: ['target']
    });
  }

  if (value.source.treeId === value.target.treeId) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'A cross-tree relationship must connect two different source trees',
      path: ['target', 'treeId']
    });
  }

  if (value.type === 'CUSTOM' && !value.customType) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'customType is required for CUSTOM relationships',
      path: ['customType']
    });
  }
}

export const crossTreeRelationshipInputSchema = z.object(compositeRelationshipFields)
  .strict()
  .superRefine(addCompositeRelationshipIssues);

export const compositeRelationshipSchema = z.object({
  id: identifierSchema,
  ...compositeRelationshipFields,
  createdBy: identifierSchema,
  createdAt: z.string().datetime()
}).strict().superRefine(addCompositeRelationshipIssues);

export const compositeTreeConfigSchema = z.object({
  treeId: identifierSchema,
  schemaVersion: z.literal(1),
  revision: z.number().int().nonnegative(),
  sources: z.array(compositeSourceSchema).max(20),
  identityGroups: z.array(compositeIdentityGroupSchema),
  crossTreeRelationships: z.array(compositeRelationshipSchema),
  publishedAt: z.string().datetime().optional(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime()
}).strict().superRefine((config, context) => {
  const sourceIds = config.sources.map((source) => source.id);
  if (findDuplicate(sourceIds)) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Composite source ids must be unique',
      path: ['sources']
    });
  }

  const sourceTreeIds = config.sources.map((source) => source.sourceTreeId);
  if (findDuplicate(sourceTreeIds)) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'A source tree can be included only once',
      path: ['sources']
    });
  }

  if (sourceTreeIds.includes(config.treeId)) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'A composite tree cannot reference itself as a source',
      path: ['sources']
    });
  }

  const configuredSourceTreeIds = new Set(sourceTreeIds);
  const identityGroupIds = config.identityGroups.map((group) => group.id);
  if (findDuplicate(identityGroupIds)) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Identity group ids must be unique',
      path: ['identityGroups']
    });
  }

  const confirmedReferences = new Set<string>();
  config.identityGroups.forEach((group, groupIndex) => {
    group.references.forEach((reference, referenceIndex) => {
      if (!configuredSourceTreeIds.has(reference.treeId)) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'Identity reference must belong to a configured source tree',
          path: ['identityGroups', groupIndex, 'references', referenceIndex, 'treeId']
        });
      }

      if (group.status !== 'CONFIRMED') return;
      const key = sourceReferenceKey(reference);
      if (confirmedReferences.has(key)) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'A SourceReference can belong to at most one confirmed identity group',
          path: ['identityGroups', groupIndex, 'references', referenceIndex]
        });
      }
      confirmedReferences.add(key);
    });
  });

  const relationshipIds = config.crossTreeRelationships.map((relationship) => relationship.id);
  if (findDuplicate(relationshipIds)) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Cross-tree relationship ids must be unique',
      path: ['crossTreeRelationships']
    });
  }

  const relationshipKeys = new Set<string>();
  config.crossTreeRelationships.forEach((relationship, relationshipIndex) => {
    for (const [endpoint, reference] of [
      ['source', relationship.source],
      ['target', relationship.target]
    ] as const) {
      if (!configuredSourceTreeIds.has(reference.treeId)) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          message: 'Relationship endpoint must belong to a configured source tree',
          path: ['crossTreeRelationships', relationshipIndex, endpoint, 'treeId']
        });
      }
    }

    const key = compositeRelationshipKey(relationship);
    if (relationshipKeys.has(key)) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Duplicate canonical cross-tree relationship',
        path: ['crossTreeRelationships', relationshipIndex]
      });
    }
    relationshipKeys.add(key);
  });
});

export function sourceReferenceKey(reference: z.infer<typeof sourceReferenceSchema>): string {
  return `${reference.treeId}\u0000${reference.memberId}`;
}

export function compositeRelationshipKey(relationship: {
  source: z.infer<typeof sourceReferenceSchema>;
  target: z.infer<typeof sourceReferenceSchema>;
  type: z.infer<typeof relationTypeSchema>;
  customType?: string;
}): string {
  const source = sourceReferenceKey(relationship.source);
  const target = sourceReferenceKey(relationship.target);
  const isDirected = relationship.type === 'PARENT_CHILD' || relationship.type === 'ADOPTED';
  const endpoints = isDirected
    ? [source, target]
    : [source, target].sort();
  return [
    relationship.type,
    relationship.customType ?? '',
    ...endpoints
  ].join('\u0000');
}

function findDuplicate(values: readonly string[]): string | undefined {
  const seen = new Set<string>();
  return values.find((value) => {
    if (seen.has(value)) return true;
    seen.add(value);
    return false;
  });
}

export const updateTreeSchema = createTreeSchema.partial().refine(
  (value) => Object.keys(value).length > 0,
  'At least one tree field must be provided'
);

export const registerSchema = z.object({
  name: z.string().trim().min(2).max(100),
  email: z.string().trim().email().max(254).transform((value) => value.toLowerCase()),
  password: z
    .string()
    .min(12)
    .max(72)
    .regex(/[a-z]/, 'Password must contain a lowercase letter')
    .regex(/[A-Z]/, 'Password must contain an uppercase letter')
    .regex(/[0-9]/, 'Password must contain a number')
    .regex(/[^A-Za-z0-9]/, 'Password must contain a special character')
});

export const roleAssignmentSchema = z.object({
  role: treeRoleSchema
});

export const createShareLinkSchema = z.object({
  expiresAt: z.string().datetime()
});

export const restoreBackupSchema = z.object({
  timestamp: z.string().datetime()
});

export type CreateMemberInput = z.infer<typeof createMemberSchema>;
export type UpdateMemberInput = z.infer<typeof updateMemberSchema>;
export type CreateRelationshipInput = z.infer<typeof createRelationshipSchema>;
export type CreateEventInput = z.infer<typeof createEventSchema>;
export type UpdateEventInput = z.infer<typeof updateEventSchema>;
export type MediaUploadInput = z.infer<typeof mediaUploadSchema>;
export type CreateAlbumInput = z.infer<typeof createAlbumSchema>;
export type UpdateAlbumInput = z.infer<typeof updateAlbumSchema>;
export type CreateTreeInput = z.infer<typeof createTreeSchema>;
export type CreateCompositeTreeInput = z.infer<typeof createCompositeTreeInputSchema>;
export type UpdateTreeInput = z.infer<typeof updateTreeSchema>;
export type SourceScopeInput = z.infer<typeof sourceScopeInputSchema>;
export type AddSourceInput = z.infer<typeof addSourceInputSchema>;
export type UpdateSourceInput = z.infer<typeof updateSourceInputSchema>;
export type IdentityGroupInput = z.infer<typeof identityGroupInputSchema>;
export type CrossTreeRelationshipInput = z.infer<typeof crossTreeRelationshipInputSchema>;
export type RegisterInput = z.infer<typeof registerSchema>;
export type RoleAssignmentInput = z.infer<typeof roleAssignmentSchema>;
export type CreateShareLinkInput = z.infer<typeof createShareLinkSchema>;
export type RestoreBackupInput = z.infer<typeof restoreBackupSchema>;
