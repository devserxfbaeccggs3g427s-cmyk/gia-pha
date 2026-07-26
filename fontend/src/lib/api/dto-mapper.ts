import 'server-only';

import type {
    Album,
    Event,
    FamilyTree,
    MediaMetadata,
    Member,
    Relationship,
    ShareLink,
    TreeMembership,
    User,
} from '@/data/types';

/**
 * DTO mappers between the frontend's Vercel-Blob-JSON shapes and the
 * Spring microservice response shapes.
 *
 * <p>Mapping rules (per {@code services/service-common/src/main/java/
 * vn/giapha/research/service/clients/ClientDtos.java} and the per-service
 * controllers):
 *
 * <ul>
 *   <li>IDs are external strings (nanoid) on both sides — frontend keeps
 *       using {@code string} ids; the microservice stores BIGINT keys
 *       internally but exposes them as {@code externalId}.</li>
 *   <li>Date formats are ISO 8601 strings on both sides.</li>
 *   <li>Gender / RelationType / EventType use string enums on both sides.</li>
 *   <li>Booleans: {@code isAlive} (frontend) ↔ {@code alive} (backend).</li>
 *   <li>Avatar: {@code avatarUrl} (frontend) ↔ {@code legacyAvatarUrl} (backend).</li>
 *   <li>Member avatar: {@code avatarMediaId} (frontend) ↔ {@code avatarMediaId} (backend).</li>
 *   <li>Tree: {@code memberships} is a sub-array on the frontend; the
 *       microservice returns it via a separate endpoint.</li>
 * </ul>
 */

/** Shape returned by the gateway for tree content endpoints. */
export interface SpringTreeDto {
    id: string;
    externalId?: string;
    treeKey?: number;
    ownerId?: string;
    name: string;
    description?: string | null;
    revision?: number;
    version?: number;
    createdAt: string;
    updatedAt: string;
}

export interface SpringMemberDto {
    id: string;
    externalId?: string;
    treeId?: string;
    firstName: string;
    lastName?: string;
    fullName: string;
    nickname?: string;
    gender?: 'MALE' | 'FEMALE' | 'OTHER';
    dateOfBirth?: string | null;
    dateOfDeath?: string | null;
    placeOfBirth?: string;
    currentAddress?: string;
    phone?: string;
    email?: string;
    occupation?: string;
    education?: string;
    biography?: string;
    achievements?: string;
    notes?: string;
    avatarMediaId?: string;
    legacyAvatarUrl?: string;
    generation?: number;
    alive?: boolean;
    isAlive?: boolean;
    version?: number;
    createdAt?: string;
    updatedAt?: string;
}

export interface SpringRelationshipDto {
    id: string;
    treeId: string;
    type: 'PARENT_CHILD' | 'SPOUSE' | 'SIBLING' | 'ADOPTED' | 'CUSTOM';
    relationType?: 'PARENT_CHILD' | 'SPOUSE' | 'SIBLING' | 'ADOPTED' | 'CUSTOM';
    fromMemberId: string;
    toMemberId: string;
    /** Optional ordering fields from canonicalisation. */
    fromCanonical?: string;
    toCanonical?: string;
    createdAt?: string;
    updatedAt?: string;
    version?: number;
}

export interface SpringEventDto {
    id: string;
    treeId: string;
    title: string;
    type: 'BIRTHDAY' | 'WEDDING' | 'FUNERAL' | 'REUNION' | 'ANNIVERSARY' | 'CUSTOM';
    eventType?: string;
    date?: string;
    startDate?: string;
    endDate?: string;
    recurrence?: 'NONE' | 'YEARLY';
    description?: string;
    location?: string;
    memberIds?: string[];
    mediaIds?: string[];
    createdAt?: string;
    updatedAt?: string;
    version?: number;
}

export interface SpringMediaDto {
    id: string;
    treeId: string;
    memberId?: string | null;
    filename: string;
    mimeType: string;
    size: number;
    url: string;
    /** Image variant URLs. */
    thumbnailUrl?: string;
    contentUrl?: string;
    /** One of PENDING_UPLOAD / PENDING_SCAN / ACTIVE / FAILED / ORPHANED / DELETING. */
    status?: string;
    uploadedAt: string;
    width?: number;
    height?: number;
    sha256?: string;
}

export interface SpringAlbumDto {
    id: string;
    treeId: string;
    title: string;
    description?: string;
    mediaIds?: string[];
    createdAt: string;
    updatedAt: string;
    version?: number;
}

export interface SpringShareLinkDto {
    id: string;
    treeId: string;
    token: string;
    permission: 'VIEW';
    expiresAt: string;
    revokedAt?: string | null;
    createdAt: string;
    createdBy: string;
}

export interface SpringTreeMembershipDto {
    userId: string;
    role: 'ADMIN' | 'EDITOR' | 'VIEWER';
    createdAt: string;
}

// ----- Tree -----

export function fromSpringTree(dto: SpringTreeDto): FamilyTree {
    const id = dto.id ?? dto.externalId ?? '';
    return {
        id,
        name: dto.name,
        description: dto.description ?? undefined,
        ownerId: dto.ownerId ?? '',
        memberships: [],
        createdAt: dto.createdAt,
        updatedAt: dto.updatedAt,
    };
}

export function toSpringTree(input: Partial<FamilyTree>): Partial<SpringTreeDto> {
    const out: Partial<SpringTreeDto> = {};
    if (input.name !== undefined) out.name = input.name;
    if (input.description !== undefined) out.description = input.description;
    return out;
}

// ----- Membership -----

export function fromSpringTreeMembership(dto: SpringTreeMembershipDto): TreeMembership {
    return {
        userId: dto.userId,
        role: dto.role,
        createdAt: dto.createdAt,
    };
}

// Backwards-compatible alias.
export const fromSpringMembership = fromSpringTreeMembership;

// ----- Member -----

export function fromSpringMember(dto: SpringMemberDto): Member {
    const id = dto.id ?? dto.externalId ?? '';
    return {
        id,
        treeId: dto.treeId ?? '',
        firstName: dto.firstName,
        lastName: dto.lastName ?? '',
        fullName: dto.fullName ?? `${dto.firstName} ${dto.lastName ?? ''}`.trim(),
        nickname: dto.nickname,
        gender: dto.gender ?? 'OTHER',
        dateOfBirth: dto.dateOfBirth ?? undefined,
        dateOfDeath: dto.dateOfDeath ?? undefined,
        placeOfBirth: dto.placeOfBirth,
        currentAddress: dto.currentAddress,
        phone: dto.phone,
        email: dto.email,
        occupation: dto.occupation,
        education: dto.education,
        biography: dto.biography,
        achievements: dto.achievements,
        notes: dto.notes,
        avatarMediaId: dto.avatarMediaId,
        avatarUrl: dto.legacyAvatarUrl,
        generation: dto.generation,
        isAlive: dto.isAlive ?? dto.alive ?? true,
        createdAt: dto.createdAt ?? new Date().toISOString(),
        updatedAt: dto.updatedAt ?? new Date().toISOString(),
    };
}

export function toSpringMember(input: Partial<Member>): Partial<SpringMemberDto> {
    const out: Partial<SpringMemberDto> = {};
    if (input.firstName !== undefined) out.firstName = input.firstName;
    if (input.lastName !== undefined) out.lastName = input.lastName;
    if (input.fullName !== undefined) out.fullName = input.fullName;
    if (input.nickname !== undefined) out.nickname = input.nickname;
    if (input.gender !== undefined) out.gender = input.gender;
    if (input.dateOfBirth !== undefined) out.dateOfBirth = input.dateOfBirth;
    if (input.dateOfDeath !== undefined) out.dateOfDeath = input.dateOfDeath;
    if (input.placeOfBirth !== undefined) out.placeOfBirth = input.placeOfBirth;
    if (input.currentAddress !== undefined) out.currentAddress = input.currentAddress;
    if (input.phone !== undefined) out.phone = input.phone;
    if (input.email !== undefined) out.email = input.email;
    if (input.occupation !== undefined) out.occupation = input.occupation;
    if (input.education !== undefined) out.education = input.education;
    if (input.biography !== undefined) out.biography = input.biography;
    if (input.achievements !== undefined) out.achievements = input.achievements;
    if (input.notes !== undefined) out.notes = input.notes;
    if (input.avatarMediaId !== undefined) out.avatarMediaId = input.avatarMediaId;
    if (input.avatarUrl !== undefined) out.legacyAvatarUrl = input.avatarUrl;
    if (input.generation !== undefined) out.generation = input.generation;
    if (input.isAlive !== undefined) out.alive = input.isAlive;
    return out;
}

// ----- Relationship -----

export function fromSpringRelationship(dto: SpringRelationshipDto): Relationship {
    const type = (dto.type ?? dto.relationType ?? 'CUSTOM') as Relationship['type'];
    return {
        id: dto.id,
        treeId: dto.treeId,
        sourceMemberId: dto.fromMemberId,
        targetMemberId: dto.toMemberId,
        type,
        createdAt: dto.createdAt ?? new Date().toISOString(),
    };
}

export function toSpringRelationship(
    input: Partial<Relationship>
): Partial<SpringRelationshipDto> {
    const out: Partial<SpringRelationshipDto> = {};
    if (input.type !== undefined) out.type = input.type;
    if (input.sourceMemberId !== undefined) out.fromMemberId = input.sourceMemberId;
    if (input.targetMemberId !== undefined) out.toMemberId = input.targetMemberId;
    return out;
}

// ----- Event -----

export function fromSpringEvent(dto: SpringEventDto): Event {
    const type = (dto.type ?? dto.eventType ?? 'CUSTOM') as Event['type'];
    const eventDate = dto.date ?? dto.startDate ?? '';
    return {
        id: dto.id,
        treeId: dto.treeId,
        title: dto.title,
        type,
        eventDate,
        location: dto.location,
        description: dto.description,
        memberIds: dto.memberIds ?? [],
        mediaIds: dto.mediaIds ?? [],
        createdAt: dto.createdAt ?? new Date().toISOString(),
        updatedAt: dto.updatedAt ?? new Date().toISOString(),
    };
}

export function toSpringEvent(input: Partial<Event>): Partial<SpringEventDto> {
    const out: Partial<SpringEventDto> = {};
    if (input.title !== undefined) out.title = input.title;
    if (input.type !== undefined) out.type = input.type;
    if (input.eventDate !== undefined) {
        out.date = input.eventDate;
        out.startDate = input.eventDate;
    }
    if (input.description !== undefined) out.description = input.description;
    if (input.location !== undefined) out.location = input.location;
    if (input.memberIds !== undefined) out.memberIds = input.memberIds;
    if (input.mediaIds !== undefined) out.mediaIds = input.mediaIds;
    return out;
}

// ----- Media -----

export function fromSpringMedia(dto: SpringMediaDto): MediaMetadata {
    return {
        id: dto.id,
        treeId: dto.treeId,
        memberId: dto.memberId ?? undefined,
        filename: dto.filename,
        originalName: dto.filename,
        mimeType: dto.mimeType,
        fileSize: dto.size,
        blobUrl: dto.url,
        thumbnailUrl: dto.thumbnailUrl ?? dto.url,
        contentUrl: dto.contentUrl ?? dto.url,
        thumbnailContentUrl: dto.thumbnailUrl ?? dto.url,
        uploadedAt: dto.uploadedAt,
        width: dto.width,
        height: dto.height,
        sha256: dto.sha256,
    };
}

// ----- Album -----

export function fromSpringAlbum(dto: SpringAlbumDto): Album {
    return {
        id: dto.id,
        treeId: dto.treeId,
        title: dto.title,
        description: dto.description,
        mediaIds: dto.mediaIds ?? [],
        createdAt: dto.createdAt,
        updatedAt: dto.updatedAt ?? dto.createdAt,
    };
}

export function toSpringAlbum(input: Partial<Album>): Partial<SpringAlbumDto> {
    const out: Partial<SpringAlbumDto> = {};
    if (input.title !== undefined) out.title = input.title;
    if (input.description !== undefined) out.description = input.description;
    if (input.mediaIds !== undefined) out.mediaIds = input.mediaIds;
    return out;
}

// ----- Share link -----

export function fromSpringShareLink(dto: SpringShareLinkDto): ShareLink {
    return {
        id: dto.id,
        treeId: dto.treeId,
        token: dto.token,
        permission: dto.permission,
        expiresAt: dto.expiresAt,
        revokedAt: dto.revokedAt ?? null,
        createdAt: dto.createdAt,
        createdBy: dto.createdBy,
    };
}

export function toSpringShareLink(input: Partial<ShareLink>): Partial<SpringShareLinkDto> {
    const out: Partial<SpringShareLinkDto> = {};
    if (input.permission !== undefined) out.permission = input.permission;
    if (input.expiresAt !== undefined) out.expiresAt = input.expiresAt;
    return out;
}

// ----- User (identity) -----

export interface SpringUserDto {
    id: string;
    email: string;
    name: string;
    emailVerified?: string | null;
    image?: string | null;
    lockedUntil?: string | null;
    createdAt: string;
}

export function fromSpringUser(dto: SpringUserDto): User {
    return {
        id: dto.id,
        email: dto.email,
        name: dto.name,
        passwordHash: '',
        image: dto.image ?? undefined,
        provider: 'credentials',
        emailVerified: dto.emailVerified ?? null,
        failedLoginAttempts: 0,
        lockedUntil: dto.lockedUntil ?? undefined,
        createdAt: dto.createdAt,
        updatedAt: dto.createdAt,
    };
}
