import { z } from 'zod';

export const providerSchema = z.enum(['credentials', 'google', 'facebook']);
export const treeRoleSchema = z.enum(['ADMIN', 'EDITOR', 'VIEWER']);
export const genderSchema = z.enum(['MALE', 'FEMALE', 'OTHER']);
export const relationTypeSchema = z.enum(['PARENT_CHILD', 'SPOUSE', 'SIBLING', 'ADOPTED', 'CUSTOM']);
export const marriageStatusSchema = z.enum(['MARRIED', 'DIVORCED', 'WIDOWED']);
export const eventTypeSchema = z.enum(['BIRTHDAY', 'WEDDING', 'FUNERAL', 'REUNION', 'ANNIVERSARY', 'CUSTOM']);
export const changeActionSchema = z.enum(['CREATE', 'UPDATE', 'DELETE']);
export const changeEntityTypeSchema = z.enum(['MEMBER', 'RELATIONSHIP', 'EVENT', 'MEDIA']);

const optionalIsoDateTime = z.string().datetime().optional();

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
  avatarUrl: z.string().url().optional(),
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

export type CreateMemberInput = z.infer<typeof createMemberSchema>;
export type UpdateMemberInput = z.infer<typeof updateMemberSchema>;
export type CreateRelationshipInput = z.infer<typeof createRelationshipSchema>;
export type CreateEventInput = z.infer<typeof createEventSchema>;
export type UpdateEventInput = z.infer<typeof updateEventSchema>;
export type MediaUploadInput = z.infer<typeof mediaUploadSchema>;
export type CreateAlbumInput = z.infer<typeof createAlbumSchema>;
export type UpdateAlbumInput = z.infer<typeof updateAlbumSchema>;
export type CreateTreeInput = z.infer<typeof createTreeSchema>;
export type UpdateTreeInput = z.infer<typeof updateTreeSchema>;
export type RegisterInput = z.infer<typeof registerSchema>;
export type RoleAssignmentInput = z.infer<typeof roleAssignmentSchema>;
