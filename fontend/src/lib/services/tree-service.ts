import 'server-only';

import { nanoid } from 'nanoid';
import { createTreeSchema, updateTreeSchema } from '@/data/schemas';
import { createInitialTree } from '@/data/seed';
import type { FamilyTree, Member, Relationship } from '@/data/types';
import {
    getAncestryPath as findAncestryPath,
    getAncestrySubgraph as findAncestrySubgraph,
    type AncestrySubgraph
} from '@/lib/algorithms/ancestry';
import { calculateGenerations as calculateGenerationMap, type GenerationMap } from '@/lib/algorithms/generation';
import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { ServiceError } from '@/lib/api/service-error';
import {
    springFetch
} from '@/lib/api/spring-client';
import {
    fromSpringMember,
    fromSpringMembership,
    fromSpringRelationship,
    fromSpringTree,
    fromSpringTreeMembership,
    toSpringTree,
    type SpringMemberDto,
    type SpringRelationshipDto,
    type SpringTreeDto,
    type SpringTreeMembershipDto
} from '@/lib/api/dto-mapper';

export interface FamilyTreeFull extends FamilyTree {
    members: Member[];
    relationships: Relationship[];
}

export class TreeServiceError extends ServiceError {
    constructor(
        code: 'NOT_FOUND' | 'INVALID_INPUT' | 'UNAUTHORIZED' | 'FORBIDDEN' | 'CONFLICT',
        message: string
    ) {
        super(code, message);
        this.name = 'TreeServiceError';
    }
}

function assertIdentifier(value: string, name: string): void {
    if (!value || typeof value !== 'string') {
        throw new TreeServiceError('INVALID_INPUT', `${name} is required`);
    }
}

async function fetchTrees(cookieHeader: string): Promise<FamilyTree[]> {
    const res = await springFetch<SpringTreeDto[] | { data?: SpringTreeDto[] }>(
        '/api/trees',
        { method: 'GET', public: true },
        cookieHeader
    );
    const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
    return list.map(fromSpringTree);
}

async function fetchMembers(treeId: string, cookieHeader: string): Promise<Member[]> {
    const res = await springFetch<SpringMemberDto[] | { data?: SpringMemberDto[] }>(
        `/api/trees/${encodeURIComponent(treeId)}/members`,
        { method: 'GET', public: true },
        cookieHeader
    );
    const list = Array.isArray(res.body) ? res.body : res.body?.data ?? [];
    return list.map(fromSpringMember);
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

async function fetchMemberships(treeId: string, cookieHeader: string): Promise<FamilyTree['memberships']> {
    const res = await springFetch<SpringTreeMembershipDto[]>(
            `/api/trees/${encodeURIComponent(treeId)}/tree-memberships`,
        { method: 'GET', public: true },
        cookieHeader
    );
    return res.body.map(fromSpringMembership);
}

export class TreeService {

    async createTree(userId: string, data: unknown): Promise<FamilyTree> {
        assertIdentifier(userId, 'userId');
        const cookieHeader = await requireBridgeCookie();
        const input = createTreeSchema.parse(data);
        const now = new Date().toISOString();
        const seed = createInitialTree({
            id: nanoid(),
            ownerId: userId,
            name: input.name,
            ...(input.description !== undefined ? { description: input.description } : {}),
            now
        });

        const res = await springFetch<SpringTreeDto>(
            '/api/trees',
            {
                method: 'POST',
                body: toSpringTree({
                    id: seed.id,
                    name: seed.name,
                    description: seed.description,
                }),
            },
            cookieHeader
        );
        return fromSpringTree(res.body);
    }

    async listTreesForUser(userId: string): Promise<FamilyTree[]> {
        assertIdentifier(userId, 'userId');
        const cookieHeader = await requireBridgeCookie();
        const trees = await fetchTrees(cookieHeader);
        // Hydrate memberships so the rbac check works against membership-based access.
        const hydrated: FamilyTree[] = [];
        for (const tree of trees) {
            try {
                const memberships = await fetchMemberships(tree.id, cookieHeader);
                hydrated.push({ ...tree, memberships });
            } catch {
                hydrated.push(tree);
            }
        }
        return hydrated.filter(
            (tree) => tree.ownerId === userId || tree.memberships.some((m) => m.userId === userId)
        );
    }

    async getTree(treeId: string): Promise<FamilyTree> {
        assertIdentifier(treeId, 'treeId');
        const cookieHeader = await requireBridgeCookie();
        const res = await springFetch<SpringTreeDto>(
            `/api/trees/${encodeURIComponent(treeId)}`,
            { method: 'GET', public: true },
            cookieHeader
        );
        const tree = fromSpringTree(res.body);
        tree.memberships = await fetchMemberships(treeId, cookieHeader);
        return tree;
    }

    async updateTree(treeId: string, data: unknown): Promise<FamilyTree> {
        assertIdentifier(treeId, 'treeId');
        const cookieHeader = await requireBridgeCookie();
        const input = updateTreeSchema.parse(data);
        const res = await springFetch<SpringTreeDto>(
            `/api/trees/${encodeURIComponent(treeId)}`,
            { method: 'PUT', body: toSpringTree(input) },
            cookieHeader
        );
        const tree = fromSpringTree(res.body);
        tree.memberships = await fetchMemberships(treeId, cookieHeader);
        return tree;
    }

    async deleteTree(treeId: string): Promise<void> {
        assertIdentifier(treeId, 'treeId');
        const cookieHeader = await requireBridgeCookie();
        await springFetch<unknown>(
            `/api/trees/${encodeURIComponent(treeId)}`,
            { method: 'DELETE' },
            cookieHeader
        );
    }

    async getTreeWithMembers(treeId: string): Promise<FamilyTreeFull> {
        const cookieHeader = await requireBridgeCookie();
        const [tree, members, relationships] = await Promise.all([
            this.getTree(treeId),
            fetchMembers(treeId, cookieHeader),
            fetchRelationships(treeId, cookieHeader),
        ]);
        return { ...tree, members, relationships };
    }

    async calculateGenerations(treeId: string): Promise<GenerationMap> {
        await this.getTree(treeId);
        const cookieHeader = await requireBridgeCookie();
        const [members, relationships] = await Promise.all([
            fetchMembers(treeId, cookieHeader),
            fetchRelationships(treeId, cookieHeader),
        ]);
        return calculateGenerationMap(members, relationships);
    }

    async getAncestryPath(memberId: string, treeId: string): Promise<Member[]> {
        assertIdentifier(memberId, 'memberId');
        await this.getTree(treeId);
        const cookieHeader = await requireBridgeCookie();
        const [members, relationships] = await Promise.all([
            fetchMembers(treeId, cookieHeader),
            fetchRelationships(treeId, cookieHeader),
        ]);
        if (!members.some((member) => member.id === memberId)) {
            throw new TreeServiceError('NOT_FOUND', 'Member not found');
        }
        return findAncestryPath(members, relationships, memberId);
    }

    async getAncestrySubgraph(
        memberId: string,
        treeId: string,
        options: { includeSpouses?: boolean } = {}
    ): Promise<AncestrySubgraph> {
        assertIdentifier(memberId, 'memberId');
        await this.getTree(treeId);
        const cookieHeader = await requireBridgeCookie();
        const [members, relationships] = await Promise.all([
            fetchMembers(treeId, cookieHeader),
            fetchRelationships(treeId, cookieHeader),
        ]);
        if (!members.some((member) => member.id === memberId)) {
            throw new TreeServiceError('NOT_FOUND', 'Member not found');
        }
        return findAncestrySubgraph(members, relationships, memberId, options);
    }
}

export const treeService = new TreeService();
