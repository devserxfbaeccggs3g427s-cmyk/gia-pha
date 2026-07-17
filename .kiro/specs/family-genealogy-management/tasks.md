# Implementation Plan: Family Genealogy Management

## Overview

Triển khai ứng dụng quản lý gia phả sử dụng Next.js 14+ App Router, Vercel Blob cho lưu trữ dữ liệu (JSON files), NextAuth.js cho authentication, React Flow cho tree visualization, và các công cụ hỗ trợ (Zustand, React Query, Zod, next-intl, shadcn/ui). Tasks được sắp xếp theo dependency: foundational layers trước, UI/features sau.

## Tasks

- [x] 1. Set up project structure, core types, and Vercel Blob data layer
  - [x] 1.1 Initialize Next.js 14+ project with TypeScript, configure folder structure
    - Create `src/app`, `src/lib`, `src/components`, `src/data`, `src/hooks`, `src/store`, `src/types`, `src/messages`, `tests/` directories as defined in design
    - Install dependencies: `@vercel/blob`, `zod`, `nanoid`, `zustand`, `@tanstack/react-query`, `next-intl`, `next-auth`, `reactflow`, `fast-check`, `vitest`
    - Configure `tsconfig.json` path aliases (`@/`)
    - _Requirements: 16.1, 15.2_

  - [x] 1.2 Define core TypeScript interfaces and data types
    - Create `src/data/types.ts` with all interfaces: User, FamilyTree, Member, Relationship, Event, MediaMetadata, Album, ChangeLog, ShareLink, BackupSnapshot
    - Create `src/types/` for general app types (API responses, pagination, etc.)
    - _Requirements: 2.1, 2.4, 3.1, 3.4, 7.7, 8.1, 8.2, 16.2_

  - [x] 1.3 Create Zod validation schemas
    - Create `src/data/schemas.ts` with: createMemberSchema, updateMemberSchema, createRelationshipSchema, createEventSchema, mediaUploadSchema, createTreeSchema
    - Include all field constraints as defined in design (min/max lengths, enums, optionals)
    - _Requirements: 2.1, 3.1, 7.1, 8.2_

  - [x] 1.4 Implement Vercel Blob access layer (client, readers, writers)
    - Create `src/lib/blob/client.ts` with `readBlob<T>`, `writeBlob<T>`, BLOB_PATHS constants, and `withBlobErrorHandling` wrapper
    - Create `src/lib/blob/readers.ts` with typed read helpers: getMembers, getRelationships, getEvents, getMediaMetadata, getUsers, getTrees, getChangeLogs
    - Create `src/lib/blob/writers.ts` with typed write helpers: putMembers, putRelationships, putEvents, putMediaMetadata, putUsers, putTrees, putChangeLogs
    - Implement BlobStorageError class with error codes (NETWORK, RATE_LIMIT, STORAGE_FULL, NOT_FOUND)
    - _Requirements: 16.1, 16.2, 16.3, 16.6_

  - [x] 1.5 Set up Vitest testing framework and test utilities
    - Configure `vitest.config.ts` with path aliases, environment setup
    - Create test utilities: mock blob storage helpers, test data factories
    - Create `tests/` directory structure: unit/, property/, integration/
    - _Requirements: 16.1_

- [x] 2. Implement Authentication and Authorization system
  - [x] 2.1 Configure NextAuth.js with credentials and OAuth providers
    - Create `src/app/api/auth/[...nextauth]/route.ts` with NextAuth.js config
    - Implement credentials provider with bcrypt password verification (cost factor 12)
    - Configure Google and Facebook OAuth providers
    - Implement custom adapter for Vercel Blob user storage (users.json)
    - _Requirements: 1.1, 1.2, 1.3, 14.2_

  - [x] 2.2 Implement account lockout mechanism
    - Track `failedLoginAttempts` and `lockedUntil` fields in User record
    - Lock account after 5 consecutive failed attempts for 15 minutes
    - Reset counter on successful login
    - _Requirements: 1.4_

  - [x]* 2.3 Write property test for account lockout threshold
    - **Property 1: Account Lockout Threshold**
    - **Validates: Requirements 1.4**

  - [x] 2.4 Implement role-based access control (RBAC)
    - Create middleware for route protection in `src/middleware.ts`
    - Implement TreeMembership roles: ADMIN (all ops), EDITOR (read+write), VIEWER (read only)
    - Create permission checking utility functions
    - Implement role assignment API (Admin assigns roles, instant update without re-login)
    - _Requirements: 1.5, 1.6_

  - [x]* 2.5 Write property test for role-based permission enforcement
    - **Property 2: Role-Based Permission Enforcement**
    - **Validates: Requirements 1.5**

  - [x] 2.6 Implement session management and security features
    - Configure session expiry (auto-logout after 30 minutes inactivity)
    - Ensure HTTPS/TLS enforcement
    - Create login/register pages at `src/app/[locale]/(auth)/login/` and `register/`
    - _Requirements: 14.1, 14.5_

- [x] 3. Checkpoint - Ensure auth and data layer tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement Member CRUD and Change Log services
  - [x] 4.1 Create MemberService with CRUD operations
    - Implement `src/lib/services/member-service.ts` with: createMember, updateMember, deleteMember, getMemberWithRelations
    - Create API routes: `src/app/api/trees/[treeId]/members/route.ts` (GET/POST), `src/app/api/members/[memberId]/route.ts` (GET/PUT/DELETE)
    - Validate input with Zod schemas, generate IDs with nanoid
    - On delete: show affected relationships, cascade-remove related data
    - _Requirements: 2.1, 2.3, 2.4, 2.5, 16.3_

  - [ ]* 4.2 Write property test for member data round-trip
    - **Property 3: Member Data Preservation Round-Trip**
    - **Validates: Requirements 2.1**

  - [x] 4.3 Implement Change Log service
    - Create `src/lib/services/changelog-service.ts`
    - Record all CREATE/UPDATE/DELETE operations on members with: userId, timestamp, previousData, newData, fieldChanged
    - Store change logs in `change-logs.json` blob per tree
    - _Requirements: 2.2, 14.6_

  - [ ]* 4.4 Write property test for change log completeness
    - **Property 4: Change Log Completeness**
    - **Validates: Requirements 2.2**

  - [x] 4.5 Implement lifespan calculation and member status
    - Calculate age/lifespan from dateOfBirth and dateOfDeath (accounting for birthday in death year)
    - Display deceased status and computed lifespan
    - _Requirements: 2.5_

  - [ ]* 4.6 Write property test for lifespan calculation correctness
    - **Property 5: Lifespan Calculation Correctness**
    - **Validates: Requirements 2.5**

  - [x] 4.7 Implement duplicate member detection and merge
    - Create `findDuplicates` method comparing name, birth date, place of birth
    - Implement `mergeMember` with configurable merge strategy
    - _Requirements: 2.6_

- [x] 5. Implement Relationship service with validation algorithms
  - [x] 5.1 Create RelationshipService with CRUD and inverse relationship logic
    - Implement `src/lib/services/relationship-service.ts` with: createRelationship, deleteRelationship, getRelationshipsForMember
    - Auto-create inverse relationship (parent→child creates child→parent)
    - On delete: remove both sides of the relationship
    - Create API routes: `src/app/api/trees/[treeId]/relationships/route.ts`, `src/app/api/relationships/[relationshipId]/route.ts`
    - Support all types: PARENT_CHILD, SPOUSE, SIBLING, ADOPTED, CUSTOM
    - Store marriage metadata: marriageDate, divorceDate, marriageStatus
    - _Requirements: 3.1, 3.2, 3.4, 3.6_

  - [ ]* 5.2 Write property test for inverse relationship symmetry
    - **Property 6: Inverse Relationship Symmetry**
    - **Validates: Requirements 3.2**

  - [x] 5.3 Implement cycle detection algorithm
    - Create `src/lib/algorithms/cycle-detection.ts` with `detectCycles` function
    - Prevent self-references and parent-child loops
    - Create validation API route: `src/app/api/relationships/validate/route.ts`
    - Return specific error messages explaining why relationship is invalid
    - _Requirements: 3.3_

  - [ ]* 5.4 Write property test for cycle detection correctness
    - **Property 7: Cycle Detection Correctness**
    - **Validates: Requirements 3.3**

  - [x] 5.5 Implement generation calculation algorithm
    - Create `src/lib/algorithms/generation.ts` with `calculateGenerations` function
    - BFS from root members (no parents = generation 0)
    - Children = parent.generation + 1, spouses = same generation
    - _Requirements: 3.5_

  - [ ]* 5.6 Write property test for generation calculation invariants
    - **Property 8: Generation Calculation Invariants**
    - **Validates: Requirements 3.5**

- [x] 6. Checkpoint - Ensure member and relationship tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement Tree service and ancestry path
  - [x] 7.1 Create TreeService with CRUD operations
    - Implement `src/lib/services/tree-service.ts` with: createTree, getTree, deleteTree, getTreeWithMembers
    - Create API routes: `src/app/api/trees/route.ts` (GET/POST), `src/app/api/trees/[treeId]/route.ts` (GET/PUT/DELETE)
    - Manage TreeMembership within tree data
    - _Requirements: 16.1, 16.2, 16.3_

  - [x] 7.2 Implement ancestry path algorithm
    - Create `src/lib/algorithms/ancestry.ts` with `getAncestryPath` function
    - Trace valid path from root ancestor (generation 0) to target member via parent-child relationships
    - _Requirements: 4.6_

  - [x]* 7.3 Write property test for ancestry path validity
    - **Property 9: Ancestry Path Validity**
    - **Validates: Requirements 4.6**

- [x] 8. Implement Search and Filter service
  - [x] 8.1 Create SearchService with Vietnamese fuzzy matching
    - Implement `src/lib/services/search-service.ts` with: search, autocomplete, filterMembers
    - Create `src/lib/utils/vietnamese.ts` with `normalizeVietnamese` function (NFD normalization, diacritics removal, đ→d)
    - In-memory search on parsed members.json: search by fullName, nickname, occupation, placeOfBirth
    - Create API route: `src/app/api/search/route.ts`
    - Return results within 500ms for typical datasets
    - _Requirements: 6.1, 6.2, 6.4_

  - [x]* 8.2 Write property test for search correctness with Vietnamese normalization
    - **Property 10: Search Correctness with Vietnamese Normalization**
    - **Validates: Requirements 6.1, 6.2**

  - [x] 8.3 Implement member filtering by criteria
    - Filter by: gender, generation, birth year range, alive status, location
    - Combine multiple filter conditions with AND logic
    - _Requirements: 6.3_

  - [x]* 8.4 Write property test for filter returns only matching members
    - **Property 11: Filter Returns Only Matching Members**
    - **Validates: Requirements 6.3**

- [ ] 9. Implement Event and Media services
  - [ ] 9.1 Create EventService with CRUD operations
    - Implement `src/lib/services/event-service.ts` with: createEvent, updateEvent, deleteEvent, getEventsForTree
    - Create API routes: `src/app/api/trees/[treeId]/events/route.ts` (GET/POST), `src/app/api/events/[eventId]/route.ts` (GET/PUT/DELETE)
    - Support event types: BIRTHDAY, WEDDING, FUNERAL, REUNION, ANNIVERSARY, CUSTOM
    - Link events to members (memberIds) and media (mediaIds)
    - Implement upcoming event detection (within 7 days) for dashboard reminders
    - _Requirements: 8.1, 8.2, 8.4, 8.5, 8.6_

  - [ ] 9.2 Create MediaService with upload/download operations
    - Implement `src/lib/services/media-service.ts` with: uploadMedia, deleteMedia, getMediaForMember, getMediaForEvent
    - Create API routes: `src/app/api/media/upload/route.ts` (POST), `src/app/api/trees/[treeId]/media/route.ts` (GET)
    - Upload files directly to Vercel Blob (binary), store metadata in media-metadata.json
    - Validate file type (JPEG, PNG, WebP, PDF) and size (max 10MB)
    - Generate thumbnail URLs for web display
    - Support album creation and management
    - _Requirements: 7.1, 7.2, 7.3, 7.5, 7.6, 7.7_

- [ ] 10. Implement Import and Export services
  - [ ] 10.1 Create ImportService with GEDCOM, JSON, and CSV parsers
    - Implement `src/lib/services/import-service.ts` with: parseGEDCOM, parseJSON, parseCSV, preview, execute
    - Create GEDCOM parser in `src/lib/algorithms/gedcom-parser.ts`
    - Create API routes: `src/app/api/import/preview/route.ts`, `src/app/api/import/execute/route.ts`
    - Show preview before confirming import
    - Validate imported data and report errors per-line for invalid files
    - _Requirements: 9.1, 9.2, 9.6_

  - [ ]* 10.2 Write property test for invalid import error reporting
    - **Property 13: Invalid Import Error Reporting**
    - **Validates: Requirements 9.6**

  - [ ] 10.3 Create ExportService with GEDCOM, JSON, PDF, image export
    - Implement `src/lib/services/export-service.ts` with: exportGEDCOM, exportJSON, exportPDF, exportImage, exportSVG
    - Create API route: `src/app/api/export/[treeId]/[format]/route.ts`
    - JSON export: full tree data (members, relationships, events, media metadata)
    - PDF export: formatted document with tree visualization, member list, statistics
    - Image export: PNG (300 DPI min) and SVG formats
    - Support print preview with paper size options (A4, A3, A2, A1)
    - Auto-paginate large trees across multiple pages with join guides
    - _Requirements: 9.3, 9.4, 9.5, 12.1, 12.2, 12.3, 12.4, 12.5_

  - [ ]* 10.4 Write property test for JSON export/import round-trip
    - **Property 12: JSON Export/Import Round-Trip**
    - **Validates: Requirements 9.5**

- [ ] 11. Checkpoint - Ensure service layer tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 12. Implement Backup/Restore and Share Link features
  - [ ] 12.1 Create backup and restore service
    - Implement `src/lib/services/backup-service.ts` with: createBackup, restoreFromBackup, listBackups
    - Create API route: `src/app/api/backup/[treeId]/route.ts`
    - Backup creates timestamped JSON snapshot of all tree data (members, relationships, events, media metadata)
    - Store backups at `backups/{treeId}/{timestamp}.json` on Vercel Blob
    - Support restore from any backup within 30 days
    - _Requirements: 14.4, 16.7_

  - [ ]* 12.2 Write property test for backup/restore round-trip
    - **Property 14: Backup/Restore Round-Trip**
    - **Validates: Requirements 16.7**

  - [ ] 12.3 Implement share link functionality
    - Create share link with expiration time and VIEW-only permission
    - Generate unique tokens for share links
    - Validate share link access in middleware
    - _Requirements: 14.3_

- [ ] 13. Implement Report and Statistics service
  - [ ] 13.1 Create ReportService with statistics and charts
    - Implement `src/lib/services/report-service.ts` with: getStatistics, getBranchStatistics, getGrowthTimeline
    - Create API route: `src/app/api/reports/[treeId]/statistics/route.ts`
    - Calculate: total members, generations count, gender distribution, age distribution, geographic distribution
    - Calculate: occupation and education distribution
    - Support branch-specific statistics
    - Generate timeline of member growth over time
    - Export report to PDF with charts and tables
    - Response time under 3 seconds for < 1000 members
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_

- [ ] 14. Set up Internationalization (i18n)
  - [ ] 14.1 Configure next-intl with locale routing
    - Set up `src/app/[locale]/layout.tsx` with next-intl provider
    - Configure middleware for locale detection and routing
    - Create translation files: `src/messages/vi.json`, `src/messages/en.json`
    - Default locale: Vietnamese (vi)
    - Support language switch without page reload (< 500ms)
    - _Requirements: 10.1, 10.2_

  - [ ] 14.2 Implement locale-aware formatting and RTL support
    - Format dates, numbers, and currency according to selected locale
    - Ensure member data (names, biographies) remain in original language regardless of UI locale
    - Add RTL text direction support for future language expansion
    - _Requirements: 10.3, 10.4, 10.5_

- [ ] 15. Implement UI shell, layout, and navigation
  - [ ] 15.1 Set up shadcn/ui components and design system
    - Install and configure shadcn/ui with custom theme (colors, typography, spacing)
    - Implement dark mode and light mode with system preference auto-detection
    - Create base UI components: Button, Input, Select, Dialog, Toast, Card, Skeleton
    - _Requirements: 13.1, 13.2_

  - [ ] 15.2 Create responsive layout system with navigation
    - Implement `src/app/[locale]/(dashboard)/layout.tsx` with sidebar navigation
    - Create responsive breakpoints: mobile (< 768px), tablet (768-1024px), desktop (> 1024px)
    - Implement breadcrumb navigation component
    - Handle orientation change within 300ms without losing state
    - Support keyboard navigation and WCAG 2.1 Level AA
    - _Requirements: 5.1, 5.4, 13.3, 13.5, 13.6_

  - [ ] 15.3 Implement loading states and animations
    - Create loading indicators and skeleton screens for operations > 1 second
    - Implement smooth animations and transitions at 60fps
    - _Requirements: 13.4, 13.5_

- [ ] 16. Implement Tree Viewer with React Flow
  - [ ] 16.1 Create TreeViewer component with multiple layout modes
    - Implement `src/components/tree/TreeViewer.tsx` using React Flow
    - Support 3 display modes: vertical tree, horizontal tree, fan chart
    - Implement zoom in/out, pan (drag), and minimap navigation
    - Apply color coding by gender, generation, and alive/deceased status
    - _Requirements: 4.1, 4.3, 4.5_

  - [ ] 16.2 Implement member selection and ancestry path highlighting
    - On member select: highlight node, show summary info panel
    - On double-click: navigate to member detail page
    - Implement "lineage view" mode: highlight path from root ancestor to selected member
    - _Requirements: 4.2, 4.6_

  - [ ] 16.3 Implement lazy loading for large trees
    - Render only nodes within viewport when tree has > 100 members
    - Implement virtual scrolling for node rendering
    - Support mobile gesture navigation (pinch-to-zoom, swipe)
    - _Requirements: 4.4, 5.2_

  - [ ] 16.4 Create MemberCard component for tree nodes
    - Implement `src/components/tree/MemberCard.tsx` with compact and detailed modes
    - Display: name, avatar, birth/death years, generation
    - Apply color scheme based on gender/generation/status
    - _Requirements: 4.2, 4.5_

- [ ] 17. Checkpoint - Ensure tree viewer and UI tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 18. Implement Member, Event, and Media UI pages
  - [ ] 18.1 Create Member management pages
    - Implement member list page: `src/app/[locale]/(dashboard)/trees/[treeId]/members/page.tsx`
    - Implement member detail page with edit form
    - Implement member creation form with Zod validation and optimistic updates
    - Show delete confirmation with affected relationships list
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [ ] 18.2 Create Relationship management UI
    - Implement relationship creation form with type selection
    - Show cycle detection errors inline
    - Display member's relationships list with navigation
    - _Requirements: 3.1, 3.2, 3.3, 3.4_

  - [ ] 18.3 Create Event management pages
    - Implement event list with interactive timeline view
    - Implement event creation/edit form
    - Display related members and attached media per event
    - Show upcoming events (within 7 days) on dashboard
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

  - [ ] 18.4 Create Media gallery and upload UI
    - Implement media gallery with grid layout and lightbox viewer
    - Implement drag-and-drop upload with progress indicator
    - Show file type/size validation errors
    - Support album creation and time-based sorting
    - Link media to members and events
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_

- [ ] 19. Implement Search UI and Report/Statistics pages
  - [ ] 19.1 Create Search UI with autocomplete and filters
    - Implement search bar with autocomplete (triggers at 2+ characters)
    - Implement filter panel with: gender, generation, birth year range, alive status, location
    - On result selection: navigate to member on TreeViewer and highlight position
    - _Requirements: 6.1, 6.3, 6.4, 6.5_

  - [ ] 19.2 Create Reports and Statistics dashboard
    - Implement statistics dashboard page at `src/app/[locale]/(dashboard)/trees/[treeId]/reports/page.tsx`
    - Display charts: gender distribution, age distribution, geographic distribution, occupation/education
    - Implement growth timeline chart
    - Support branch-specific statistics
    - Add PDF export button for reports
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_

- [ ] 20. Implement state management and optimistic updates
  - [ ] 20.1 Set up Zustand stores and React Query configuration
    - Create `src/store/ui-store.ts`: theme, sidebar state, locale
    - Create `src/store/tree-ui-store.ts`: zoom, pan, selected node
    - Create `src/store/offline-store.ts`: pending mutations queue
    - Configure React Query provider with cache settings, refetch policies
    - _Requirements: 16.5, 16.8_

  - [ ] 20.2 Implement optimistic update hooks with error rollback
    - Create custom hooks: `useMemberMutation`, `useRelationshipMutation`, `useEventMutation`
    - Implement optimistic update → API call → confirm or rollback pattern
    - On network failure: queue mutation in offline store for retry
    - On Blob error (rate limit, storage full): show specific error, keep pending mutation
    - _Requirements: 16.5, 16.6_

- [ ] 21. Implement PWA and offline support
  - [ ] 21.1 Configure Progressive Web App
    - Create `public/manifest.json` with app metadata, icons, theme colors
    - Implement service worker (`public/sw.js`) for asset caching and offline support
    - Cache critical app shell and previously viewed data
    - Display cached data when offline
    - Sync pending changes when connection restored
    - _Requirements: 5.5, 5.6_

- [ ] 22. Implement performance optimizations
  - [ ] 22.1 Apply Next.js performance best practices
    - Configure SSR for initial page load, CSR for subsequent interactions
    - Implement code splitting and lazy loading for non-critical modules
    - Configure prefetching for navigation (< 200ms perceived load)
    - Optimize images with next/image (WebP format, responsive srcset)
    - Set up appropriate caching headers for static assets and API responses
    - Target FCP < 1.5s on 4G, Lighthouse Performance >= 90
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 5.3_

- [ ] 23. Implement Import/Export UI and integration
  - [ ] 23.1 Create Import UI with preview
    - Implement file upload for GEDCOM, JSON, CSV formats
    - Show parsed data preview before confirming import
    - Display per-line error report for invalid files
    - _Requirements: 9.1, 9.2, 9.6_

  - [ ] 23.2 Create Export UI with format selection and print preview
    - Implement export dialog with format options (GEDCOM, JSON, PDF, PNG, SVG)
    - Implement print preview with paper size selection (A4, A3, A2, A1)
    - Support customization: display info, colors, fonts, orientation
    - Handle auto-pagination for large trees
    - _Requirements: 9.3, 9.4, 12.1, 12.2, 12.3, 12.4, 12.5_

- [ ] 24. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The Vercel Blob data layer (task 1.4) is foundational — all services depend on it
- Authentication (task 2) must be complete before any protected feature
- Service layer tasks (4-13) can be parallelized after the data layer is ready
- UI tasks (15-23) depend on their corresponding service layer tasks

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.5"] },
    { "id": 2, "tasks": ["1.3", "1.4"] },
    { "id": 3, "tasks": ["2.1", "14.1"] },
    { "id": 4, "tasks": ["2.2", "2.4", "2.6", "14.2"] },
    { "id": 5, "tasks": ["2.3", "2.5"] },
    { "id": 6, "tasks": ["4.1", "5.1", "7.1"] },
    { "id": 7, "tasks": ["4.2", "4.3", "4.5", "4.7", "5.2", "5.3", "5.5", "7.2"] },
    { "id": 8, "tasks": ["4.4", "4.6", "5.4", "5.6", "7.3", "8.1"] },
    { "id": 9, "tasks": ["8.2", "8.3", "9.1", "9.2"] },
    { "id": 10, "tasks": ["8.4", "10.1", "10.3", "12.1", "13.1"] },
    { "id": 11, "tasks": ["10.2", "10.4", "12.2", "12.3"] },
    { "id": 12, "tasks": ["15.1", "20.1"] },
    { "id": 13, "tasks": ["15.2", "15.3", "20.2"] },
    { "id": 14, "tasks": ["16.1", "16.4"] },
    { "id": 15, "tasks": ["16.2", "16.3"] },
    { "id": 16, "tasks": ["18.1", "18.2", "18.3", "18.4"] },
    { "id": 17, "tasks": ["19.1", "19.2", "23.1", "23.2"] },
    { "id": 18, "tasks": ["21.1", "22.1"] }
  ]
}
```
