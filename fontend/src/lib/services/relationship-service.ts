import 'server-only';

import { ZodError } from 'zod';
import { createRelationshipSchema, type CreateRelationshipInput } from '@/data/schemas';
import type { RelationType, Relationship, RelationshipView } from '@/data/types';
import type { ValidationResult } from '@/types/api';
import { ServiceError } from '@/lib/api/service-error';
import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';
import {
    fromSpringRelationship,
    toSpringRelationship,
    type SpringRelationshipDto
} from '@/lib/api/dto-mapper';
import { detectCycles as detectRelationshipCycles } from '@/lib/algorithms/cycle-detection';
import { logicalRelationshipKey, normalizeRelationship } from '@/lib/algorithms/relationship-normalization';

export type RelationshipMutationActor = string | { userId?: string } | undefined;

export class RelationshipServiceError extends ServiceError {
    constructor(code: 'NOT_FOUND' | 'INVALID_INPUT' | 'CONFLICT', message: string) {
        super(code, message);
        this.name = 'RelationshipServiceError';
    }
}

async function fetchRelationships(treeId: string, cookieHeader: string): Promise<Relationship[]> {
    const res = await springFetch<SpringRelationshipDto[] | { data?: SpringRelationshipDto[] }>(
        `/api/trees/${encodeURIComponent(treeId)}/relationships`,
        { method: 'GET', public: true },
        cookieHeader
    );
    const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
    return list.map(fromSpringRelationship);
}

async function fetchMembersIds(treeId: string, cookieHeader: string): Promise<string[]> {
    const res = await springFetch<{ id: string }[] | { data?: { id: string }[] }>(
        `/api/trees/${encodeURIComponent(treeId)}/members`,
        { method: 'GET', public: true },
        cookieHeader
    );
    const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
    return list.map((m) => m.id);
}

export class RelationshipService {
    async createRelationship(
        treeId: string,
        data: unknown,
        actor: RelationshipMutationActor = undefined
    ): Promise<Relationship> {
        if (!treeId?.trim()) throw new RelationshipServiceError('INVALID_INPUT', 'treeId is required');
        const input = createRelationshipSchema.parse(data);
        const cookieHeader = await requireBridgeCookie();

        // BFF-side validation (cycle detection + duplicate key) runs on the
        // full data so we can return the same ValidationResult shape used by
        // the legacy implementation.
        const [memberIds, relationships] = await Promise.all([
            fetchMembersIds(treeId, cookieHeader),
            fetchRelationships(treeId, cookieHeader),
        ]);
        const validation = validateInput(input, memberIds, relationships);
        if (!validation.valid) {
            const duplicate = validation.errors.some((message) => message === 'This relationship already exists');
            throw new RelationshipServiceError(duplicate ? 'CONFLICT' : 'INVALID_INPUT', validation.errors.join('; '));
        }

        const now = new Date().toISOString();
        const relationship = toRelationship(treeId, input, nanoidLocal(), now);
        const res = await springFetch<SpringRelationshipDto>(
            `/api/trees/${encodeURIComponent(treeId)}/relationships`,
            { method: 'POST', body: toSpringRelationship(relationship) },
            cookieHeader
        );
        return fromSpringRelationship(res.body);
    }

    async deleteRelationship(
        treeId: string,
        relationshipId: string,
        actor: RelationshipMutationActor = undefined
    ): Promise<void> {
        if (!treeId?.trim() || !relationshipId?.trim()) {
            throw new RelationshipServiceError('INVALID_INPUT', 'treeId and relationshipId are required');
        }
        const cookieHeader = await requireBridgeCookie();
        await springFetch<unknown>(
            `/api/trees/${encodeURIComponent(treeId)}/relationships/${encodeURIComponent(relationshipId)}`,
            { method: 'DELETE' },
            cookieHeader
        );
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
        const cookieHeader = await requireBridgeCookie();
        const [memberIds, relationships] = await Promise.all([
            fetchMembersIds(treeId, cookieHeader),
            fetchRelationships(treeId, cookieHeader),
        ]);
        return validateInput(input, memberIds, relationships);
    }

    async getRelationshipsForMember(treeId: string, memberId: string): Promise<RelationshipView[]> {
        if (!treeId?.trim() || !memberId?.trim()) return [];
        const cookieHeader = await requireBridgeCookie();
        const relationships = await fetchRelationships(treeId, cookieHeader);
        return relationships
            .filter((relationship) => relationship.sourceMemberId === memberId || relationship.targetMemberId === memberId)
            .map((relationship) => toRelationshipView(relationship, memberId));
    }

    async detectCycles(treeId: string, proposedRelation: CreateRelationshipInput): Promise<boolean> {
        if (!treeId?.trim()) throw new RelationshipServiceError('INVALID_INPUT', 'treeId is required');
        const cookieHeader = await requireBridgeCookie();
        const relationships = await fetchRelationships(treeId, cookieHeader);
        if (proposedRelation.type !== 'PARENT_CHILD') {
            return proposedRelation.sourceMemberId === proposedRelation.targetMemberId;
        }
        return detectRelationshipCycles(
            relationships,
            proposedRelation.sourceMemberId,
            proposedRelation.targetMemberId
        );
    }

    getInverseRelationType(type: RelationType): RelationType {
        return type;
    }
}

function nanoidLocal(): string {
    // Tiny ID generator used for optimistic local IDs before the gateway
    // assigns the canonical externalId.
    return (
        'tmp_' +
        Math.random().toString(36).slice(2, 10) +
        Date.now().toString(36)
    );
}

function validateInput(
    input: CreateRelationshipInput,
    memberIds: string[],
    relationships: Relationship[]
): ValidationResult {
    const errors: string[] = [];
    const ids = new Set(memberIds);
    if (!ids.has(input.sourceMemberId)) errors.push(`Source member "${input.sourceMemberId}" was not found`);
    if (!ids.has(input.targetMemberId)) errors.push(`Target member "${input.targetMemberId}" was not found`);
    if (input.sourceMemberId === input.targetMemberId) errors.push('A member cannot be related to itself');

    const candidate = toRelationship('validation', input, 'validation', '1970-01-01T00:00:00.000Z');
    const candidateKey = logicalRelationshipKey(candidate);
    const duplicate = relationships.some((relationship) => logicalRelationshipKey(relationship) === candidateKey);
    if (duplicate) errors.push('This relationship already exists');

    if (input.marriageDate && input.divorceDate && new Date(input.divorceDate) < new Date(input.marriageDate)) {
        errors.push('divorceDate cannot be before marriageDate');
    }
    if (
        input.type === 'PARENT_CHILD' &&
        detectRelationshipCycles(relationships, input.sourceMemberId, input.targetMemberId)
    ) {
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
        ...(input.marriageStatus !== undefined ? { marriageStatus: input.marriageStatus } : {}),
        createdAt,
    });
}

function toRelationshipView(relationship: Relationship, memberId: string): RelationshipView {
    const isSource = relationship.sourceMemberId === memberId;
    const relatedMemberId = isSource ? relationship.targetMemberId : relationship.sourceMemberId;
    return {
        ...relationship,
        memberId,
        relatedMemberId,
        role: relationshipRole(relationship.type, isSource),
    };
}

function relationshipRole(type: RelationType, isSource: boolean) {
    if (type === 'PARENT_CHILD') return isSource ? 'PARENT' : 'CHILD';
    if (type === 'ADOPTED') return isSource ? 'PARENT' : 'ADOPTED';
    if (type === 'SPOUSE') return 'SPOUSE';
    if (type === 'SIBLING') return 'SIBLING';
    return 'CUSTOM';
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
