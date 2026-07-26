# Frontend `/api` Call Map (Task 1.5)

Every network call issued by the Next.js frontend, mapped to a frozen OpenAPI
operation in `openapi-legacy-baseline.yaml`. During the strangler migration the
routing layer (middleware/rewrites) directs each operation to Next.js or Spring
according to the per-tree cutover state; the frontend code itself is unchanged.

| # | Caller (source) | Method + Path | operationId |
|---|-----------------|---------------|-------------|
| 1 | `components/auth/register-form.tsx` | `POST /api/auth/register` | `registerUser` |
| 2 | NextAuth client (`signIn`, session) | `GET/POST /api/auth/[...nextauth]` | `nextAuthGet`/`nextAuthPost` |
| 3 | Email link (server-generated) | `GET /api/auth/verify-email?token=` | `verifyEmail` |
| 4 | `components/genealogy/trees-page.tsx` | `GET /api/trees` | `listTrees` |
| 5 | `components/genealogy/trees-page.tsx` | `POST /api/trees` | `createTree` |
| 6 | `components/tree/TreeViewer.tsx`, `components/layout/breadcrumbs.tsx` | `GET /api/trees/{treeId}` | `getTree` |
| 7 | `hooks/useGenealogyQueries.ts`, `trees-page.tsx`, `member-pages.tsx`, `media-pages.tsx` | `GET /api/trees/{treeId}/members` | `listMembers` |
| 8 | `lib/api/mutations.ts#createMember` | `POST /api/trees/{treeId}/members` | `createMember` |
| 9 | `useGenealogyQueries.ts#useMember`, `member-pages.tsx`, `breadcrumbs.tsx` | `GET /api/members/{memberId}?treeId=` | `getMember` |
| 10 | `lib/api/mutations.ts#updateMember` | `PUT /api/members/{memberId}?treeId=` | `updateMember` |
| 11 | `lib/api/mutations.ts#deleteMember` | `DELETE /api/members/{memberId}?treeId=` | `deleteMember` |
| 12 | `useGenealogyQueries.ts#useRelationships` | `GET /api/trees/{treeId}/relationships` | `listRelationships` |
| 13 | `lib/api/mutations.ts#createRelationship` | `POST /api/trees/{treeId}/relationships` | `createRelationship` |
| 14 | `lib/api/mutations.ts#deleteRelationship`, `member-pages.tsx` | `DELETE /api/relationships/{relationshipId}?treeId=` | `deleteRelationship` |
| 15 | `useGenealogyQueries.ts#useEvents`, `media-pages.tsx` | `GET /api/trees/{treeId}/events` | `listEvents` |
| 16 | `useGenealogyQueries.ts#useUpcomingEvents`, `dashboard-upcoming-events.tsx` | `GET /api/trees/{treeId}/events?upcoming=true&days=` | `listEvents` |
| 17 | `lib/api/mutations.ts#createEvent` | `POST /api/trees/{treeId}/events` | `createEvent` |
| 18 | `lib/api/mutations.ts#updateEvent` | `PUT /api/events/{eventId}?treeId=` | `updateEvent` |
| 19 | `lib/api/mutations.ts#deleteEvent` | `DELETE /api/events/{eventId}?treeId=` | `deleteEvent` |
| 20 | `useGenealogyQueries.ts#useMedia`, `media-pages.tsx` | `GET /api/trees/{treeId}/media` | `listMedia` |
| 21 | `member-pages.tsx` (avatar/media upload form) | `POST /api/media/upload` (multipart) | `uploadMedia` |
| 22 | `media-pages.tsx#remove` | `DELETE /api/media/{mediaId}?treeId=` | `deleteMedia` |
| 23 | `lib/media/avatar.ts`, `lib/images/media-loader.ts`, `<img>` tags | `GET /api/media/{mediaId}/content?treeId=[&thumbnail=true][&format=webp&width=&quality=]` | `getMediaContent` |
| 24 | `media-pages.tsx` | `GET /api/trees/{treeId}/albums` | `listAlbums` |
| 25 | `media-pages.tsx#createAlbum` | `POST /api/trees/{treeId}/albums` | `createAlbum` |
| 26 | `components/genealogy/member-search.tsx` | `GET /api/search?treeId=&mode=&q=…` | `searchMembers` |
| 27 | `components/genealogy/import-export-dialog.tsx` | `POST /api/import/preview` (multipart) | `previewImport` |
| 28 | `import-export-dialog.tsx` | `POST /api/import/execute` (multipart) | `executeImport` |
| 29 | `import-export-dialog.tsx` | `GET /api/export/{treeId}/preview?…` | `exportTree` |
| 30 | `import-export-dialog.tsx` | `GET /api/export/{treeId}/{format}?…` | `exportTree` |
| 31 | `components/genealogy/reports-page.tsx` | `GET /api/reports/{treeId}/statistics[?format=pdf|timeline][&branchRootMemberId=]` | `getStatistics` |
| 32 | Share page `app/share/[token]/page.tsx` (server fetch) | `GET /api/share/{token}` | `getSharedTree` |
| 33 | Vercel cron (`vercel.json`) | `GET /api/cron/backups` (Bearer CRON_SECRET) | `runScheduledBackups` |

Operations with **no current frontend caller** (still part of the frozen
contract because they are reachable and documented): `updateTree`,
`deleteTree`, `assignMembershipRole`, `getRelationship`, `updateRelationship`,
`validateRelationship`, `getEvent`, `getMedia`, `updateAlbum`, `deleteAlbum`,
`listBackups`, `createBackup`, `restoreBackup`, `listShareLinks`,
`createShareLink`, `revokeShareLink`.

Coverage check: 100% of frontend calls above resolve to an operation in
`openapi-legacy-baseline.yaml`; the OpenAPI file contains no operation that is
not implemented by a legacy handler.
