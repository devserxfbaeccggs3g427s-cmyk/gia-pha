'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Image from 'next/image';
import ReactFlow, {
  Background,
  BackgroundVariant,
  MarkerType,
  MiniMap,
  Panel,
  type Edge,
  type Node,
  type ReactFlowInstance
} from 'reactflow';
import {
  AlertCircle,
  ArrowDown,
  ArrowRight,
  BadgeInfo,
  CalendarDays,
  CircleDotDashed,
  Crosshair,
  Focus,
  LayoutGrid,
  Leaf,
  MapPin,
  Maximize2,
  Minus,
  Network,
  Plus,
  RefreshCw,
  Route,
  Rows3,
  UserRound,
  UsersRound,
  X
} from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import { useRouter } from '@/i18n/navigation';
import type { FamilyTree, Member, Relationship } from '@/data/types';
import { getAncestrySubgraph, getCanonicalParentChildEdges, type AncestrySubgraph } from '@/lib/algorithms/ancestry';
import { calculateGenerations } from '@/lib/algorithms/generation';
import { MemberNode, getMemberColorScheme, formatLifeYears, type MemberNodeData, type MemberSummary } from './MemberCard';
import { buildTreeLayout, type TreeDisplayMode, type TreeLayoutMode } from './tree-layout';
import { useTreeUiStore } from '@/store/tree-ui-store';
import { apiRequest } from '@/lib/api/mutations';
import { queryKeys } from '@/lib/query/keys';
import { ImportExportActions } from '@/components/genealogy/import-export-dialog';
import styles from './tree-viewer.module.css';
import 'reactflow/dist/style.css';

export interface TreeViewerProps {
  treeId: string;
  mode?: TreeLayoutMode;
  selectedMemberId?: string;
  onMemberSelect?: (memberId: string) => void;
  onMemberDoubleClick?: (memberId: string) => void;
  highlightPath?: string[];
}

const EMPTY_ANCESTRY_SUBGRAPH: AncestrySubgraph = {
  targetMemberId: '',
  memberIds: [],
  parentChildEdges: [],
  spouseEdges: []
};

interface TreeViewerData extends FamilyTree {
  members: Member[];
  relationships: Relationship[];
}

interface RelationshipCounts {
  parents: number;
  children: number;
  spouses: number;
}

const nodeTypes = { member: MemberNode };

export function TreeViewer({
  treeId,
  mode = 'vertical',
  selectedMemberId,
  onMemberSelect,
  onMemberDoubleClick,
  highlightPath
}: TreeViewerProps) {
  const t = useTranslations('treeViewer');
  const locale = useLocale();
  const router = useRouter();
  const flowRef = useRef<ReactFlowInstance<MemberNodeData> | null>(null);
  const [layoutMode, setLayoutMode] = useState<TreeLayoutMode>(mode);
  const [displayMode, setDisplayMode] = useState<TreeDisplayMode>('detailed');
  const storedSelectedId = useTreeUiStore((state) => state.activeTreeId === treeId ? state.selectedNodeId : undefined);
  const setActiveTree = useTreeUiStore((state) => state.setActiveTree);
  const setInternalSelectedId = useTreeUiStore((state) => state.selectNode);
  const storedViewport = useTreeUiStore((state) => state.viewport);
  const setStoredViewport = useTreeUiStore((state) => state.setViewport);
  const [lineageEnabled, setLineageEnabled] = useState(false);
  const zoom = storedViewport.zoom;

  const selectedId = selectedMemberId ?? storedSelectedId;

  useEffect(() => setActiveTree(treeId), [setActiveTree, treeId]);

  const treeQuery = useQuery({
    queryKey: queryKeys.tree(treeId),
    queryFn: () => apiRequest<TreeViewerData>(`/api/trees/${encodeURIComponent(treeId)}`)
  });
  const data = treeQuery.data;

  useEffect(() => setLayoutMode(mode), [mode]);

  const members = data?.members ?? [];
  const relationships = data?.relationships ?? [];
  const generationMap = useMemo(
    () => calculateGenerations(members, relationships),
    [members, relationships]
  );
  const positions = useMemo(
    () => buildTreeLayout(members, relationships, layoutMode, displayMode),
    [displayMode, layoutMode, members, relationships]
  );
  const positionById = useMemo(() => new Map(positions.map((item) => [item.id, item])), [positions]);

  const lineage = useMemo<AncestrySubgraph>(() => {
    if (highlightPath) return ancestrySubgraphFromPath(members, relationships, highlightPath);
    if (!lineageEnabled || !selectedId) return EMPTY_ANCESTRY_SUBGRAPH;
    return getAncestrySubgraph(members, relationships, selectedId, { includeSpouses: true });
  }, [highlightPath, lineageEnabled, members, relationships, selectedId]);
  const lineageSet = useMemo(() => new Set(lineage.memberIds), [lineage.memberIds]);

  const selectMember = useCallback((memberId: string) => {
    setInternalSelectedId(memberId);
    onMemberSelect?.(memberId);
  }, [onMemberSelect]);

  const openMember = useCallback((memberId: string) => {
    if (onMemberDoubleClick) {
      onMemberDoubleClick(memberId);
      return;
    }
    router.push(`/trees/${treeId}/members/${memberId}` as never);
  }, [onMemberDoubleClick, router, treeId]);

  const nodes = useMemo<Array<Node<MemberNodeData>>>(() => members.map((member) => {
    const layout = positionById.get(member.id);
    const generation = layout?.generation ?? generationMap.get(member.id) ?? member.generation ?? 0;
    const memberSummary: MemberSummary = { ...member, generation };
    return {
      id: member.id,
      type: 'member',
      position: layout?.position ?? { x: 0, y: 0 },
      data: {
        member: memberSummary,
        isSelected: member.id === selectedId,
        isHighlighted: lineageSet.has(member.id),
        displayMode,
        colorScheme: getMemberColorScheme(member, generation),
        layoutMode,
        onSelect: selectMember,
        onDoubleClick: openMember
      },
      selected: member.id === selectedId,
      draggable: false,
      selectable: true,
      focusable: true,
      zIndex: member.id === selectedId ? 12 : lineageSet.has(member.id) ? 8 : 1,
      ariaLabel: t('memberNodeLabel', {
        name: member.fullName,
        generation: generation + 1,
        status: member.isAlive ? t('alive') : t('deceased')
      })
    };
  }), [displayMode, generationMap, layoutMode, lineageSet, members, openMember, positionById, selectMember, selectedId, t]);

  const edges = useMemo(
    () => buildRelationshipEdges(members, relationships, lineage, positions, layoutMode),
    [layoutMode, lineage, members, positions, relationships]
  );

  useEffect(() => {
    if (!flowRef.current || nodes.length === 0) return;
    const frame = window.requestAnimationFrame(() => {
      void flowRef.current?.fitView({ padding: 0.16, duration: 360, maxZoom: 1.12 });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [displayMode, layoutMode, nodes.length]);

  useEffect(() => {
    if (!selectedId || !flowRef.current || !positionById.has(selectedId)) return;
    const selected = positionById.get(selectedId)!;
    const frame = window.requestAnimationFrame(() => {
      void flowRef.current?.setCenter(selected.position.x, selected.position.y, {
        zoom: Math.max(flowRef.current?.getZoom() ?? 1, 0.8),
        duration: 320
      });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [positionById, selectedId]);

  const selectedMember = members.find((member) => member.id === selectedId);
  const selectedGeneration = selectedMember
    ? generationMap.get(selectedMember.id) ?? selectedMember.generation ?? 0
    : 0;
  const relationshipCounts = useMemo(
    () => selectedId ? getRelationshipCounts(selectedId, members, relationships) : undefined,
    [members, relationships, selectedId]
  );
  const generationCount = generationMap.size > 0 ? Math.max(...generationMap.values()) + 1 : 0;
  const virtualized = members.length > 100;

  if (treeQuery.isLoading) return <TreeViewerSkeleton label={t('loading')} />;
  if (treeQuery.error) return <TreeViewerError message={treeQuery.error.message || t('loadError')} retry={() => void treeQuery.refetch()} />;

  return (
    <section className={styles.viewer} aria-labelledby="tree-viewer-title">
      <header className={styles.header}>
        <div className={styles.heading}>
          <span className={styles.eyebrow}><Leaf aria-hidden="true" />{t('eyebrow')}</span>
          <h1 id="tree-viewer-title">{data?.name || t('title')}</h1>
          <p>{data?.description || t('description')}</p>
        </div>
        <div className="flex flex-wrap items-center justify-end gap-4">
          <div className={styles.stats} aria-label={t('overview')}>
            <span><UsersRound aria-hidden="true" /><strong>{members.length}</strong>{t('members')}</span>
            <span><Rows3 aria-hidden="true" /><strong>{generationCount}</strong>{t('generations')}</span>
            {virtualized && <span className={styles.optimized}><CircleDotDashed aria-hidden="true" />{t('optimized')}</span>}
          </div>
          <ImportExportActions treeId={treeId} />
        </div>
      </header>

      <div className={styles.workspace}>
        <div className={styles.toolbar} role="toolbar" aria-label={t('viewControls')}>
          <div className={styles.segmented} aria-label={t('layout')}>
            <ModeButton active={layoutMode === 'vertical'} label={t('vertical')} onClick={() => setLayoutMode('vertical')} icon={<ArrowDown />} />
            <ModeButton active={layoutMode === 'horizontal'} label={t('horizontal')} onClick={() => setLayoutMode('horizontal')} icon={<ArrowRight />} />
            <ModeButton active={layoutMode === 'fan'} label={t('fan')} onClick={() => setLayoutMode('fan')} icon={<CircleDotDashed />} />
          </div>
          <span className={styles.toolbarDivider} />
          <button
            type="button"
            className={styles.toolbarButton}
            data-active={lineageEnabled}
            disabled={!selectedMember}
            onClick={() => setLineageEnabled((current) => !current)}
            aria-pressed={lineageEnabled}
            title={selectedMember ? t('lineageHint') : t('selectForLineage')}
          >
            <Route aria-hidden="true" />
            <span>{t('lineage')}</span>
          </button>
          <button
            type="button"
            className={styles.toolbarButton}
            onClick={() => setDisplayMode((current) => current === 'compact' ? 'detailed' : 'compact')}
            aria-pressed={displayMode === 'compact'}
            title={t('densityHint')}
          >
            <LayoutGrid aria-hidden="true" />
            <span>{displayMode === 'compact' ? t('detailed') : t('compact')}</span>
          </button>
          <button
            type="button"
            className={styles.toolbarButton}
            onClick={() => void flowRef.current?.fitView({ padding: 0.16, duration: 360, maxZoom: 1.12 })}
            title={t('fitView')}
          >
            <Maximize2 aria-hidden="true" />
            <span>{t('fitView')}</span>
          </button>
        </div>

        <div className={styles.canvas} data-panel-open={Boolean(selectedMember)}>
          {members.length === 0 ? (
            <div className={styles.empty}>
              <span><Network aria-hidden="true" /></span>
              <h2>{t('emptyTitle')}</h2>
              <p>{t('emptyDescription')}</p>
            </div>
          ) : (
            <ReactFlow
              nodes={nodes}
              edges={edges}
              nodeTypes={nodeTypes}
              onInit={(instance) => { flowRef.current = instance; setStoredViewport(instance.getViewport()); }}
              onNodeClick={(_event, node) => selectMember(node.id)}
              onNodeDoubleClick={(_event, node) => openMember(node.id)}
              onMoveEnd={(_event, viewport) => setStoredViewport(viewport)}
              minZoom={0.12}
              maxZoom={2.4}
              fitView
              fitViewOptions={{ padding: 0.16, maxZoom: 1.12 }}
              onlyRenderVisibleElements={virtualized}
              nodesDraggable={false}
              nodesConnectable={false}
              elementsSelectable
              panOnDrag
              panOnScroll={false}
              zoomOnPinch
              zoomOnScroll
              zoomOnDoubleClick={false}
              preventScrolling
              selectionOnDrag={false}
              elevateNodesOnSelect
              className={styles.flow}
            >
              <Background variant={BackgroundVariant.Dots} gap={24} size={1.2} color="hsl(var(--muted-foreground) / 0.22)" />
              <MiniMap
                className={styles.minimap}
                pannable
                zoomable
                position="bottom-right"
                nodeStrokeWidth={3}
                nodeColor={(node) => `hsl(${node.data?.colorScheme?.accent ?? '150 20% 50%'})`}
                maskColor="hsl(var(--background) / 0.74)"
                ariaLabel={t('minimap')}
              />
              <Panel position="bottom-left" className={styles.flowControls}>
                <button type="button" onClick={() => flowRef.current?.zoomIn({ duration: 180 })} aria-label={t('zoomIn')} title={t('zoomIn')}><Plus /></button>
                <span aria-live="polite">{Math.round(zoom * 100)}%</span>
                <button type="button" onClick={() => flowRef.current?.zoomOut({ duration: 180 })} aria-label={t('zoomOut')} title={t('zoomOut')}><Minus /></button>
                <button type="button" onClick={() => void flowRef.current?.fitView({ padding: .16, duration: 300 })} aria-label={t('fitView')} title={t('fitView')}><Focus /></button>
              </Panel>
              <Panel position="top-left" className={styles.legend}>
                <span><i data-color="male" />{t('male')}</span>
                <span><i data-color="female" />{t('female')}</span>
                <span><i data-color="other" />{t('other')}</span>
                <span><i data-color="deceased" />{t('deceased')}</span>
              </Panel>
            </ReactFlow>
          )}

          {selectedMember && relationshipCounts && (
            <MemberSummaryPanel
              member={{ ...selectedMember, generation: selectedGeneration }}
              counts={relationshipCounts}
              lineageEnabled={lineageEnabled}
              locale={locale}
              onClose={() => setInternalSelectedId(undefined)}
              onLineage={() => setLineageEnabled((current) => !current)}
              onOpen={() => openMember(selectedMember.id)}
            />
          )}
        </div>
      </div>
      <p className={styles.help}><BadgeInfo aria-hidden="true" />{t('interactionHint')}</p>
    </section>
  );
}

function ModeButton({ active, icon, label, onClick }: { active: boolean; icon: React.ReactNode; label: string; onClick: () => void }) {
  return <button type="button" data-active={active} aria-pressed={active} onClick={onClick}>{icon}<span>{label}</span></button>;
}

function MemberSummaryPanel({
  member,
  counts,
  lineageEnabled,
  locale,
  onClose,
  onLineage,
  onOpen
}: {
  member: MemberSummary;
  counts: RelationshipCounts;
  lineageEnabled: boolean;
  locale: string;
  onClose: () => void;
  onLineage: () => void;
  onOpen: () => void;
}) {
  const t = useTranslations('treeViewer');
  const color = getMemberColorScheme(member, member.generation);
  const dateFormatter = useMemo(() => new Intl.DateTimeFormat(locale, {
    day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC'
  }), [locale]);
  const birthDate = formatDate(member.dateOfBirth, dateFormatter);

  return (
    <aside className={styles.summary} aria-label={t('memberSummary')}>
      <button type="button" className={styles.closeSummary} onClick={onClose} aria-label={t('closeSummary')}><X /></button>
      <div className={styles.summaryIdentity}>
        <span className={styles.summaryAvatar} style={{ background: `hsl(${color.accent} / .12)`, color: `hsl(${color.accent})` }}>
          {member.avatarUrl
            ? <Image src={member.avatarUrl} alt="" width={55} height={55} sizes="55px" />
            : <UserRound aria-hidden="true" />}
        </span>
        <span>
          <small>{t('generationFull', { generation: member.generation + 1 })}</small>
          <strong>{member.fullName}</strong>
          {member.nickname && <em>“{member.nickname}”</em>}
        </span>
      </div>
      <div className={styles.lifeStatus} data-alive={member.isAlive}>
        <i />
        <span>{member.isAlive ? t('alive') : t('deceased')}</span>
        <b>{formatLifeYears(member, t('unknownYear'))}</b>
      </div>
      <dl className={styles.summaryDetails}>
        {birthDate && <div><dt><CalendarDays />{t('born')}</dt><dd>{birthDate}</dd></div>}
        {member.placeOfBirth && <div><dt><MapPin />{t('birthPlace')}</dt><dd>{member.placeOfBirth}</dd></div>}
        {member.occupation && <div><dt><Leaf />{t('occupation')}</dt><dd>{member.occupation}</dd></div>}
      </dl>
      <div className={styles.relationStats}>
        <span><strong>{counts.parents}</strong>{t('parents')}</span>
        <span><strong>{counts.children}</strong>{t('children')}</span>
        <span><strong>{counts.spouses}</strong>{t('spouses')}</span>
      </div>
      <div className={styles.summaryActions}>
        <button type="button" data-active={lineageEnabled} onClick={onLineage}><Route />{lineageEnabled ? t('hideLineage') : t('showLineage')}</button>
        <button type="button" onClick={onOpen}>{t('viewProfile')}<ArrowRight /></button>
      </div>
    </aside>
  );
}

function TreeViewerSkeleton({ label }: { label: string }) {
  return (
    <div className={styles.skeleton} aria-busy="true" aria-label={label}>
      <div className={styles.skeletonHeader}><i /><span><b /><b /></span></div>
      <div className={styles.skeletonToolbar} />
      <div className={styles.skeletonCanvas}>{[0, 1, 2, 3, 4].map((item) => <i key={item} />)}</div>
    </div>
  );
}

function TreeViewerError({ message, retry }: { message: string; retry: () => void }) {
  const t = useTranslations('treeViewer');
  return (
    <div className={styles.error} role="alert">
      <span><AlertCircle /></span>
      <h1>{t('errorTitle')}</h1>
      <p>{message}</p>
      <button type="button" onClick={retry}><RefreshCw />{t('retry')}</button>
    </div>
  );
}

function buildRelationshipEdges(
  members: readonly Member[],
  relationships: readonly Relationship[],
  lineage: AncestrySubgraph,
  positions: readonly { id: string; position: { x: number; y: number } }[],
  layoutMode: TreeLayoutMode
): Edge[] {
  const memberIds = new Set(members.map((member) => member.id));
  const positionById = new Map(positions.map((position) => [position.id, position.position]));
  const parentChildLineageEdges = new Set(
    lineage.parentChildEdges.map((edge) => edgeKey(edge.parentId, edge.childId))
  );
  const spouseLineageEdges = new Set(
    lineage.spouseEdges.map((edge) => edgeKey(edge.sourceMemberId, edge.targetMemberId))
  );
  const edges: Edge[] = [];
  const accepted = new Set<string>();

  for (const { parentId, childId } of getCanonicalParentChildEdges(members, relationships)) {
    const key = `parent:${edgeKey(parentId, childId)}`;
    if (accepted.has(key)) continue;
    accepted.add(key);
    const highlighted = parentChildLineageEdges.has(edgeKey(parentId, childId));
    edges.push({
      id: key,
      source: parentId,
      target: childId,
      sourceHandle: 'source-child',
      targetHandle: 'target-parent',
      type: 'smoothstep',
      animated: highlighted,
      zIndex: highlighted ? 9 : 0,
      style: {
        stroke: highlighted ? 'hsl(38 65% 48%)' : 'hsl(146 21% 56%)',
        strokeWidth: highlighted ? 3.2 : 1.8
      },
      markerEnd: {
        type: MarkerType.ArrowClosed,
        width: 14,
        height: 14,
        color: highlighted ? 'hsl(38 65% 48%)' : 'hsl(146 21% 56%)'
      }
    });
  }

  for (const relationship of relationships) {
    if (relationship.type === 'PARENT_CHILD') continue;
    if (!memberIds.has(relationship.sourceMemberId) || !memberIds.has(relationship.targetMemberId)) continue;
    const pair = [relationship.sourceMemberId, relationship.targetMemberId].sort();
    const key = `${relationship.type}:${edgeKey(pair[0], pair[1])}`;
    if (accepted.has(key)) continue;
    accepted.add(key);
    const spouse = relationship.type === 'SPOUSE';
    const highlighted = spouse && spouseLineageEdges.has(edgeKey(relationship.sourceMemberId, relationship.targetMemberId));
    const spouseHandles = spouse
      ? getSpouseHandles(
        relationship.sourceMemberId,
        relationship.targetMemberId,
        positionById,
        layoutMode
      )
      : undefined;
    edges.push({
      id: key,
      source: relationship.sourceMemberId,
      target: relationship.targetMemberId,
      ...(spouseHandles ? {
        sourceHandle: spouseHandles.source,
        targetHandle: spouseHandles.target
      } : {}),
      type: spouse ? 'straight' : 'smoothstep',
      style: {
        stroke: highlighted ? 'hsl(38 65% 48%)' : spouse ? 'hsl(38 58% 52%)' : 'hsl(267 28% 59%)',
        strokeWidth: highlighted ? 3.2 : spouse ? 2.2 : 1.5,
        strokeDasharray: spouse ? '7 4' : '3 4'
      },
      animated: highlighted,
      zIndex: highlighted ? 9 : 0
    });
  }

  return edges;
}

function getSpouseHandles(
  sourceMemberId: string,
  targetMemberId: string,
  positionById: ReadonlyMap<string, { x: number; y: number }>,
  layoutMode: TreeLayoutMode
): { source: string; target: string } {
  const sourcePosition = positionById.get(sourceMemberId);
  const targetPosition = positionById.get(targetMemberId);
  const horizontal = layoutMode === 'horizontal';
  const sourceComesFirst = sourcePosition && targetPosition
    ? horizontal
      ? sourcePosition.y <= targetPosition.y
      : sourcePosition.x <= targetPosition.x
    : sourceMemberId <= targetMemberId;
  const sourceSide = sourceComesFirst ? 'end' : 'start';
  const targetSide = sourceComesFirst ? 'start' : 'end';
  return {
    source: `source-spouse-${sourceSide}`,
    target: `target-spouse-${targetSide}`
  };
}

function ancestrySubgraphFromPath(
  members: readonly Member[],
  relationships: readonly Relationship[],
  path: readonly string[]
): AncestrySubgraph {
  const memberIds = new Set(path);
  if (path.length === 0) return EMPTY_ANCESTRY_SUBGRAPH;
  const pathPairs = new Set(path.slice(1).map((id, index) => edgeKey(path[index], id)));
  const parentChildEdges = getCanonicalParentChildEdges(members, relationships)
    .filter((edge) => pathPairs.has(edgeKey(edge.parentId, edge.childId)));
  const spouseEdges = relationships
    .filter((relationship) => relationship.type === 'SPOUSE')
    .filter((relationship) => memberIds.has(relationship.sourceMemberId) && memberIds.has(relationship.targetMemberId))
    .map((relationship) => ({
      sourceMemberId: relationship.sourceMemberId,
      targetMemberId: relationship.targetMemberId
    }));
  return {
    targetMemberId: path[path.length - 1],
    memberIds: [...memberIds],
    parentChildEdges,
    spouseEdges
  };
}

function getRelationshipCounts(
  memberId: string,
  members: readonly Member[],
  relationships: readonly Relationship[]
): RelationshipCounts {
  const parentChild = getCanonicalParentChildEdges(members, relationships);
  const spouses = new Set<string>();
  for (const relationship of relationships) {
    if (relationship.type !== 'SPOUSE') continue;
    if (relationship.sourceMemberId === memberId) spouses.add(relationship.targetMemberId);
    if (relationship.targetMemberId === memberId) spouses.add(relationship.sourceMemberId);
  }
  return {
    parents: new Set(parentChild.filter((edge) => edge.childId === memberId).map((edge) => edge.parentId)).size,
    children: new Set(parentChild.filter((edge) => edge.parentId === memberId).map((edge) => edge.childId)).size,
    spouses: spouses.size
  };
}

function formatDate(value: string | undefined, formatter: Intl.DateTimeFormat): string | undefined {
  if (!value) return undefined;
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return undefined;
  return formatter.format(date);
}

function edgeKey(sourceId: string, targetId: string): string {
  return `${sourceId}>${targetId}`;
}

export default TreeViewer;
