import { nanoid } from 'nanoid';
import { ZodError } from 'zod';
import { createRelationshipSchema, type CreateRelationshipInput } from '@/data/schemas';
import type { MarriageStatus, RelationType, Relationship, RelationshipRole, RelationshipView } from '@/data/types';
import type { ValidationResult } from '@/types/api';
import { getMembers, getRelationships } from '@/lib/blob/readers';
import { putRelationships } from '@/lib/blob/writers';
import { detectCycles as detectRelationshipCycles } from '@/lib/algorithms/cycle-detection';
import { logicalRelationshipKey, normalizeRelationship } from '@/lib/algorithms/relationship-normalization';
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
    await putRelationships(treeId, [...relationships, relationship]);
    await changeLogService.recordChange({
      treeId,
      userId: actorId(actor),
      action: 'CREATE',
      entityType: 'RELATIONSHIP',
      newData: relationship as unknown as Record<string, unknown>,
      createdAt: now
    });
    return relationship;
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

    const remaining = relationships.filter((candidate) => candidate.id !== relationship.id);
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

  async getRelationshipsForMember(treeId: string, memberId: string): Promise<RelationshipView[]> {
    if (!treeId?.trim() || !memberId?.trim()) return [];
    const relationships = await getRelationships(treeId);
    return relationships
      .filter((relationship) => relationship.sourceMemberId === memberId || relationship.targetMemberId === memberId)
      .map((relationship) => toRelationshipView(relationship, memberId));
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
    // Inverse semantics are materialized as a RelationshipView; the persisted
    // relationship remains one canonical logical record.
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

  const candidate = toRelationship('validation', input, 'validation', '1970-01-01T00:00:00.000Z');
  const candidateKey = logicalRelationshipKey(candidate);
  const duplicate = relationships.some((relationship) => logicalRelationshipKey(relationship) === candidateKey);
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
  return normalizeRelationship({
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
  });
}

function toRelationshipView(relationship: Relationship, memberId: string): RelationshipView {
  const isSource = relationship.sourceMemberId === memberId;
  const relatedMemberId = isSource ? relationship.targetMemberId : relationship.sourceMemberId;
  return {
    ...relationship,
    memberId,
    relatedMemberId,
    role: relationshipRole(relationship.type, isSource)
  };
}

function relationshipRole(type: RelationType, isSource: boolean): RelationshipRole {
  if (type === 'PARENT_CHILD') return isSource ? 'PARENT' : 'CHILD';
  if (type === 'ADOPTED') return isSource ? 'PARENT' : 'ADOPTED';
  if (type === 'SPOUSE') return 'SPOUSE';
  if (type === 'SIBLING') return 'SIBLING';
  return 'CUSTOM';
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
