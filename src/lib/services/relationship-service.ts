import { nanoid } from 'nanoid';
import { ZodError } from 'zod';
import { createRelationshipSchema, type CreateRelationshipInput } from '@/data/schemas';
import type { MarriageStatus, RelationType, Relationship } from '@/data/types';
import type { ValidationResult } from '@/types/api';
import { getMembers, getRelationships } from '@/lib/blob/readers';
import { putRelationships } from '@/lib/blob/writers';
import { detectCycles as detectRelationshipCycles } from '@/lib/algorithms/cycle-detection';
import { changeLogService } from './changelog-service';

export type RelationshipMutationActor = string | { userId?: string } | undefined;

export class RelationshipServiceError extends Error {
  constructor(
    public readonly code: 'NOT_FOUND' | 'INVALID_INPUT' | 'CONFLICT',
    message: string
  ) {
    super(message);
    this.name = 'RelationshipServiceError';
  }
}

export class RelationshipService {
  async createRelationship(
    treeId: string,
    data: unknown,
    actor: RelationshipMutationActor = undefined
  ): Promise<Relationship> {
    if (!treeId?.trim()) throw new RelationshipServiceError('INVALID_INPUT', 'treeId is required');
    // Parse separately so callers receive the detailed ZodError contract used
    // by the other services.
    const input = createRelationshipSchema.parse(data);
    const [members, relationships] = await Promise.all([getMembers(treeId), getRelationships(treeId)]);
    const validation = validateInput(input, members, relationships);
    if (!validation.valid) {
      const duplicate = validation.errors.some((message) => message === 'This relationship already exists');
      throw new RelationshipServiceError(duplicate ? 'CONFLICT' : 'INVALID_INPUT', validation.errors.join('; '));
    }

    const now = new Date().toISOString();
    const relationship = toRelationship(treeId, input, nanoid(), now);
    const inverse = toRelationship(
      treeId,
      {
        ...input,
        sourceMemberId: input.targetMemberId,
        targetMemberId: input.sourceMemberId
      },
      nanoid(),
      now
    );

    // A pre-existing reciprocal record can occur after a partial retry. Keep
    // it and only write the missing side, making creation idempotent for the
    // same logical relationship.
    const hasSame = relationships.some((candidate) => sameDirected(candidate, relationship));
    const hasInverse = relationships.some((candidate) => sameDirected(candidate, inverse));
    const next = [...relationships];
    if (!hasSame) next.push(relationship);
    if (!hasInverse) next.push(inverse);
    if (next.length !== relationships.length) await putRelationships(treeId, next);

    if (!hasSame || !hasInverse) {
      await changeLogService.recordChange({
        treeId,
        userId: actorId(actor),
        action: 'CREATE',
        entityType: 'RELATIONSHIP',
        newData: relationship as unknown as Record<string, unknown>,
        createdAt: now
      });
    }
    return relationships.find((candidate) => sameDirected(candidate, relationship)) ?? relationship;
  }

  async deleteRelationship(
    treeId: string,
    relationshipId: string,
    actor: RelationshipMutationActor = undefined
  ): Promise<void> {
    if (!treeId?.trim() || !relationshipId?.trim()) {
      throw new RelationshipServiceError('INVALID_INPUT', 'treeId and relationshipId are required');
    }
    const relationships = await getRelationships(treeId);
    const relationship = relationships.find((candidate) => candidate.id === relationshipId);
    if (!relationship) throw new RelationshipServiceError('NOT_FOUND', 'Relationship not found');

    const remaining = relationships.filter(
      (candidate) =>
        candidate.id !== relationship.id &&
        !isInverse(candidate, relationship)
    );
    await putRelationships(treeId, remaining);
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      action: 'DELETE',
      entityType: 'RELATIONSHIP',
      previousData: relationship as unknown as Record<string, unknown>
    });
  }

  async validateRelationship(treeId: string, data: unknown): Promise<ValidationResult> {
    if (!treeId?.trim()) return { valid: false, errors: ['treeId is required'] };
    const errors: string[] = [];
    let input: CreateRelationshipInput;
    try {
      input = createRelationshipSchema.parse(data);
    } catch (error) {
      if (error instanceof ZodError) {
        errors.push(...error.issues.map((issue) => `${issue.path.join('.') || 'relationship'}: ${issue.message}`));
        return { valid: false, errors };
      }
      throw error;
    }
    const [members, relationships] = await Promise.all([getMembers(treeId), getRelationships(treeId)]);
    return validateInput(input, members, relationships);
  }

  async getRelationshipsForMember(treeId: string, memberId: string): Promise<Relationship[]> {
    if (!treeId?.trim() || !memberId?.trim()) return [];
    const relationships = await getRelationships(treeId);
    return relationships.filter(
      (relationship) =>
        relationship.sourceMemberId === memberId || relationship.targetMemberId === memberId
    );
  }

  async detectCycles(treeId: string, proposedRelation: CreateRelationshipInput): Promise<boolean> {
    if (!treeId?.trim()) throw new RelationshipServiceError('INVALID_INPUT', 'treeId is required');
    const relationships = await getRelationships(treeId);
    if (proposedRelation.type !== 'PARENT_CHILD') return proposedRelation.sourceMemberId === proposedRelation.targetMemberId;
    return detectRelationshipCycles(
      relationships,
      proposedRelation.sourceMemberId,
      proposedRelation.targetMemberId
    );
  }

  getInverseRelationType(type: RelationType): RelationType {
    // Every currently supported relation is represented as a directed record
    // and has the same domain type when reversed (the source/target fields
    // carry the direction for PARENT_CHILD).
    switch (type) {
      case 'PARENT_CHILD':
      case 'SPOUSE':
      case 'SIBLING':
      case 'ADOPTED':
      case 'CUSTOM':
        return type;
      default:
        return type;
    }
  }
}

function validateInput(
  input: CreateRelationshipInput,
  members: Array<{ id: string }>,
  relationships: Relationship[]
): ValidationResult {
  const errors: string[] = [];
  const memberIds = new Set(members.map((member) => member.id));
  if (!memberIds.has(input.sourceMemberId)) errors.push(`Source member "${input.sourceMemberId}" was not found`);
  if (!memberIds.has(input.targetMemberId)) errors.push(`Target member "${input.targetMemberId}" was not found`);
  if (input.sourceMemberId === input.targetMemberId) errors.push('A member cannot be related to itself');

  const duplicate = relationships.some((relationship) =>
    relationship.sourceMemberId === input.sourceMemberId &&
    relationship.targetMemberId === input.targetMemberId &&
    relationship.type === input.type &&
    (relationship.customType ?? '') === (input.customType ?? '')
  );
  if (duplicate) errors.push('This relationship already exists');

  if (input.marriageDate && input.divorceDate && new Date(input.divorceDate) < new Date(input.marriageDate)) {
    errors.push('divorceDate cannot be before marriageDate');
  }
  if (input.type === 'PARENT_CHILD' && detectRelationshipCycles(
    relationships,
    input.sourceMemberId,
    input.targetMemberId
  )) {
    errors.push('The relationship would create a parent-child cycle');
  }
  return { valid: errors.length === 0, errors };
}

function toRelationship(
  treeId: string,
  input: CreateRelationshipInput,
  id: string,
  createdAt: string
): Relationship {
  return {
    id,
    treeId,
    sourceMemberId: input.sourceMemberId,
    targetMemberId: input.targetMemberId,
    type: input.type,
    ...(input.customType !== undefined ? { customType: input.customType } : {}),
    ...(input.marriageDate !== undefined ? { marriageDate: input.marriageDate } : {}),
    ...(input.divorceDate !== undefined ? { divorceDate: input.divorceDate } : {}),
    ...(input.marriageStatus !== undefined ? { marriageStatus: input.marriageStatus as MarriageStatus } : {}),
    createdAt
  };
}

function sameDirected(a: Relationship, b: Relationship): boolean {
  return a.sourceMemberId === b.sourceMemberId &&
    a.targetMemberId === b.targetMemberId &&
    a.type === b.type &&
    (a.customType ?? '') === (b.customType ?? '');
}

function isInverse(candidate: Relationship, original: Relationship): boolean {
  return candidate.sourceMemberId === original.targetMemberId &&
    candidate.targetMemberId === original.sourceMemberId &&
    candidate.type === original.type &&
    (candidate.customType ?? '') === (original.customType ?? '');
}

function actorId(actor: RelationshipMutationActor): string {
  return typeof actor === 'string' ? actor : actor?.userId ?? 'system';
}

export const relationshipService = new RelationshipService();
export const relationshipServiceInstance = relationshipService;
export default relationshipService;

export const createRelationship = relationshipService.createRelationship.bind(relationshipService);
export const deleteRelationship = relationshipService.deleteRelationship.bind(relationshipService);
export const validateRelationship = relationshipService.validateRelationship.bind(relationshipService);
export const getRelationshipsForMember = relationshipService.getRelationshipsForMember.bind(relationshipService);
export const detectCycles = relationshipService.detectCycles.bind(relationshipService);
export const getInverseRelationType = relationshipService.getInverseRelationType.bind(relationshipService);
