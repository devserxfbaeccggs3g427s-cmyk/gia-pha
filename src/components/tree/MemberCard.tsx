'use client';

import type { CSSProperties, KeyboardEvent, MouseEvent } from 'react';
import { Handle, Position, type NodeProps } from 'reactflow';
import { CalendarDays, Leaf, Sparkles } from 'lucide-react';
import { useTranslations } from 'next-intl';
import type { Member } from '@/data/types';
import type { TreeDisplayMode, TreeLayoutMode } from './tree-layout';
import styles from './member-card.module.css';

export interface MemberSummary extends Member {
  generation: number;
}

export interface ColorScheme {
  background: string;
  border: string;
  accent: string;
  status: string;
}

export interface MemberCardProps {
  member: MemberSummary;
  isSelected: boolean;
  isHighlighted: boolean;
  displayMode: TreeDisplayMode;
  colorScheme: ColorScheme;
  layoutMode?: TreeLayoutMode;
  onSelect?: (memberId: string) => void;
  onDoubleClick?: (memberId: string) => void;
}

export interface MemberNodeData extends MemberCardProps {}

export function MemberCard({
  member,
  isSelected,
  isHighlighted,
  displayMode,
  colorScheme,
  layoutMode = 'vertical',
  onSelect,
  onDoubleClick
}: MemberCardProps) {
  const t = useTranslations('treeViewer');
  const horizontal = layoutMode === 'horizontal';
  const style = {
    '--member-bg': colorScheme.background,
    '--member-border': colorScheme.border,
    '--member-accent': colorScheme.accent,
    '--member-status': colorScheme.status
  } as CSSProperties;
  const years = formatLifeYears(member, t('unknownYear'));
  const initials = getInitials(member.fullName);

  const select = (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    onSelect?.(member.id);
  };
  const open = (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    onDoubleClick?.(member.id);
  };
  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onSelect?.(member.id);
    }
  };

  return (
    <div
      className={styles.node}
      data-display={displayMode}
      data-selected={isSelected}
      data-highlighted={isHighlighted}
      data-deceased={!member.isAlive}
      style={style}
    >
      <Handle
        className={styles.handle}
        type="target"
        position={horizontal ? Position.Left : Position.Top}
        isConnectable={false}
      />
      <button
        type="button"
        className={`${styles.card} nodrag nopan`}
        aria-label={t('selectMember', { name: member.fullName })}
        aria-pressed={isSelected}
        onClick={select}
        onDoubleClick={open}
        onKeyDown={onKeyDown}
      >
        <span className={styles.generationBar} aria-hidden="true" />
        <span className={styles.avatarWrap}>
          {member.avatarUrl ? (
            // Avatar URLs may point to authenticated application endpoints.
            // eslint-disable-next-line @next/next/no-img-element
            <img className={styles.avatar} src={member.avatarUrl} alt="" loading="lazy" draggable={false} />
          ) : (
            <span className={styles.initials} aria-hidden="true">{initials}</span>
          )}
          <span className={styles.statusDot} title={member.isAlive ? t('alive') : t('deceased')} />
        </span>

        <span className={styles.content}>
          <span className={styles.name} title={member.fullName}>{member.fullName}</span>
          <span className={styles.meta}>
            <CalendarDays aria-hidden="true" />
            <span>{years}</span>
          </span>
          {displayMode === 'detailed' && (
            <span className={styles.detail} title={member.occupation || member.placeOfBirth || undefined}>
              {member.occupation || member.placeOfBirth || t('storyPending')}
            </span>
          )}
        </span>

        <span className={styles.generationBadge} title={t('generationFull', { generation: member.generation + 1 })}>
          {isHighlighted ? <Sparkles aria-hidden="true" /> : <Leaf aria-hidden="true" />}
          <span>{t('generationShort', { generation: member.generation + 1 })}</span>
        </span>
      </button>
      <Handle
        className={styles.handle}
        type="source"
        position={horizontal ? Position.Right : Position.Bottom}
        isConnectable={false}
      />
    </div>
  );
}

export function MemberNode({ data }: NodeProps<MemberNodeData>) {
  return <MemberCard {...data} />;
}

export function getMemberColorScheme(member: Pick<Member, 'gender' | 'isAlive'>, generation: number): ColorScheme {
  const hue = (generation * 47 + 139) % 360;
  const gender = member.gender === 'MALE'
    ? { background: '214 62% 97%', border: '211 44% 74%' }
    : member.gender === 'FEMALE'
      ? { background: '342 70% 97%', border: '342 43% 76%' }
      : { background: '276 34% 97%', border: '274 25% 75%' };

  return {
    background: gender.background,
    border: gender.border,
    accent: `${hue} 48% ${member.isAlive ? '48%' : '55%'}`,
    status: member.isAlive ? '145 52% 39%' : '32 11% 53%'
  };
}

export function formatLifeYears(member: Pick<Member, 'dateOfBirth' | 'dateOfDeath' | 'isAlive'>, unknown: string): string {
  const birth = getYear(member.dateOfBirth);
  const death = getYear(member.dateOfDeath);
  if (!birth && !death) return unknown;
  if (birth && death) return `${birth}–${death}`;
  if (birth) return member.isAlive ? `${birth}–` : `${birth}–?`;
  return `?–${death}`;
}

function getYear(value?: string): number | undefined {
  if (!value) return undefined;
  const match = /^(\d{4})/.exec(value);
  return match ? Number(match[1]) : undefined;
}

function getInitials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map((part) => part[0])
    .join('')
    .toLocaleUpperCase()
    .slice(0, 2) || '?';
}

export default MemberCard;
