import 'server-only';

import { requireBridgeCookie } from '@/lib/api/spring-guard';
import { springFetch } from '@/lib/api/spring-client';
import type { Member } from '@/data/types';
import type { SearchResult } from '@/types/api';

export type { SearchResult };

export type SearchableMemberField =
    | 'fullName'
    | 'firstName'
    | 'lastName'
    | 'nickname'
    | 'placeOfBirth'
    | 'currentAddress'
    | 'occupation'
    | 'notes';

export interface AutocompleteItem {
    memberId: string;
    fullName: string;
    nickname?: string;
    avatarMediaId?: string;
    avatarUrl?: string;
}

export class SearchService {
    /**
     * Vietnamese-accent-tolerant member search (Task 27). The microservice
     * implementation in {@code reporting-service} applies
     * {@code VietnameseSearchNormalizer} before scoring.
     */
    async searchMembers(
        treeId: string,
        term: string,
        options: { limit?: number } = {}
    ): Promise<SearchResult[]> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<{ results: SearchResult[] } | SearchResult[]>(
            `/api/trees/${encodeURIComponent(treeId)}/member-search`,
            {
                method: 'GET',
                public: true,
                query: {
                    q: term,
                    limit: options.limit ?? 50
                }
            },
            cookie
        );
        if (Array.isArray(res.body)) return res.body;
        return res.body?.results ?? [];
    }

    async searchAcrossTrees(term: string, options: { limit?: number } = {}): Promise<Member[]> {
        const cookie = await requireBridgeCookie();
        const res = await springFetch<{ members: Member[] } | Member[]>(
            `/api/search/global`,
            {
                method: 'GET',
                public: true,
                query: {
                    q: term,
                    limit: options.limit ?? 50
                }
            },
            cookie
        );
        if (Array.isArray(res.body)) return res.body;
        return res.body?.members ?? [];
    }
}

export const searchService = new SearchService();
export default searchService;
