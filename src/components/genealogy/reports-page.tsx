'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  BarChart3,
  BookOpenCheck,
  CalendarClock,
  Download,
  GraduationCap,
  Loader2,
  MapPinned,
  RefreshCw,
  TrendingUp,
  UserRoundCheck,
  UsersRound,
  Workflow
} from 'lucide-react';
import { useLocale, useTranslations } from 'next-intl';
import type { LucideIcon } from 'lucide-react';
import type { Member } from '@/data/types';
import type { BranchStatistics, GrowthTimelinePoint, ReportStatistics } from '@/types/report';
import { Button } from '@/components/ui/button';
import styles from './reports-page.module.css';

type Statistics = ReportStatistics | BranchStatistics;

export function ReportsPage({ treeId }: { treeId: string }) {
  const t = useTranslations('reportsPage');
  const locale = useLocale();
  const [members, setMembers] = useState<Member[]>([]);
  const [statistics, setStatistics] = useState<Statistics | null>(null);
  const [timeline, setTimeline] = useState<GrowthTimelinePoint[]>([]);
  const [branchRootId, setBranchRootId] = useState('');
  const [loadedBranchRootId, setLoadedBranchRootId] = useState('');
  const [loading, setLoading] = useState(true);
  const [membersLoading, setMembersLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setMembersLoading(true);
    void request<Member[]>(`/api/trees/${encodeURIComponent(treeId)}/members`, controller.signal)
      .then((data) => setMembers([...data].sort((left, right) => left.fullName.localeCompare(right.fullName, locale))))
      .catch((reason) => {
        if (!(reason instanceof DOMException && reason.name === 'AbortError')) setError(reason instanceof Error ? reason.message : t('errors.load'));
      })
      .finally(() => { if (!controller.signal.aborted) setMembersLoading(false); });
    return () => controller.abort();
  }, [locale, t, treeId]);

  const loadReport = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError('');
    const branch = branchRootId ? `&branchRootMemberId=${encodeURIComponent(branchRootId)}` : '';
    try {
      const [summary, growth] = await Promise.all([
        request<Statistics>(`/api/reports/${encodeURIComponent(treeId)}/statistics?view=summary${branch}`, signal),
        request<GrowthTimelinePoint[]>(`/api/reports/${encodeURIComponent(treeId)}/statistics?view=timeline${branch}`, signal)
      ]);
      setStatistics(summary);
      setTimeline(growth);
      setLoadedBranchRootId(branchRootId);
    } catch (reason) {
      if (reason instanceof DOMException && reason.name === 'AbortError') return;
      setError(reason instanceof Error ? reason.message : t('errors.load'));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [branchRootId, t, treeId]);

  useEffect(() => {
    const controller = new AbortController();
    void loadReport(controller.signal);
    return () => controller.abort();
  }, [loadReport]);

  const branchName = loadedBranchRootId ? members.find((member) => member.id === loadedBranchRootId)?.fullName : undefined;
  const scopeIsCurrent = branchRootId === loadedBranchRootId;
  const number = useMemo(() => new Intl.NumberFormat(locale), [locale]);
  const generatedAt = statistics
    ? new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(statistics.generatedAt))
    : '';

  const exportPDF = async () => {
    setExporting(true);
    setError('');
    try {
      const branch = branchRootId ? `&branchRootMemberId=${encodeURIComponent(branchRootId)}` : '';
      const response = await fetch(`/api/reports/${encodeURIComponent(treeId)}/statistics?format=pdf${branch}`);
      if (!response.ok) {
        const body = await response.json().catch(() => null) as unknown;
        throw new Error(readApiError(body) || t('errors.export'));
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `${safeFilename(branchName || treeId)}-${t('pdfFilename')}.pdf`;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : t('errors.export'));
    } finally {
      setExporting(false);
    }
  };

  return (
    <section className={styles.page} aria-labelledby="reports-title">
      <header className={styles.hero}>
        <div className={styles.heroCopy}>
          <span className={styles.eyebrow}><BarChart3 aria-hidden="true" />{t('eyebrow')}</span>
          <h1 id="reports-title">{t('title')}</h1>
          <p>{t('description')}</p>
        </div>
        <Button size="lg" onClick={() => void exportPDF()} disabled={loading || !statistics || exporting || !scopeIsCurrent}>
          {exporting ? <Loader2 className="animate-spin" aria-hidden="true" /> : <Download aria-hidden="true" />}
          {exporting ? t('actions.exporting') : t('actions.exportPdf')}
        </Button>
      </header>

      <div className={styles.scopeBar}>
        <div className={styles.scopeIdentity}>
          <span><Workflow aria-hidden="true" /></span>
          <div><strong>{branchName ? t('scope.branchTitle') : t('scope.allTitle')}</strong><small>{branchName ? t('scope.branchDescription', { name: branchName }) : t('scope.allDescription')}</small></div>
        </div>
        <label className={styles.branchSelect}>
          <span>{t('scope.label')}</span>
          <select value={branchRootId} onChange={(event) => setBranchRootId(event.target.value)} disabled={membersLoading || members.length === 0}>
            <option value="">{t('scope.allOption')}</option>
            {members.map((member) => <option key={member.id} value={member.id}>{member.fullName}</option>)}
          </select>
        </label>
        {statistics && <span className={styles.generated}><CalendarClock aria-hidden="true" />{t('generatedAt', { value: generatedAt })}</span>}
      </div>

      {error && (
        <div className={styles.error} role="alert">
          <AlertTriangle aria-hidden="true" />
          <span><strong>{t('errors.title')}</strong><small>{error}</small></span>
          <Button type="button" variant="outline" size="sm" onClick={() => void loadReport()}><RefreshCw aria-hidden="true" />{t('actions.retry')}</Button>
        </div>
      )}

      {loading && !statistics ? <ReportsSkeleton /> : statistics ? (
        <div className={styles.dashboard} aria-busy={loading}>
          {loading && <div className={styles.refreshing} role="status"><Loader2 className="animate-spin" aria-hidden="true" />{t('refreshing')}</div>}

          <div className={styles.metrics}>
            <MetricCard icon={UsersRound} label={t('metrics.members')} value={number.format(statistics.totalMembers)} detail={t('metrics.membersDetail', { living: number.format(statistics.livingMembers), deceased: number.format(statistics.deceasedMembers) })} tone="green" />
            <MetricCard icon={Workflow} label={t('metrics.generations')} value={number.format(statistics.generationsCount)} detail={t('metrics.generationsDetail')} tone="gold" />
            <MetricCard icon={UserRoundCheck} label={t('metrics.living')} value={statistics.totalMembers ? `${Math.round(statistics.livingMembers / statistics.totalMembers * 100)}%` : '0%'} detail={t('metrics.livingDetail', { count: number.format(statistics.livingMembers) })} tone="blue" />
            <MetricCard icon={Activity} label={t('metrics.averageAge')} value={statistics.averageAge === null ? '—' : number.format(statistics.averageAge)} detail={t('metrics.averageAgeDetail', { count: number.format(statistics.membersWithKnownAge) })} tone="violet" />
          </div>

          <div className={styles.primaryGrid}>
            <ChartCard eyebrow={t('charts.demographics')} title={t('charts.gender.title')} description={t('charts.gender.description')}>
              <GenderDonut values={statistics.genderDistribution} />
            </ChartCard>
            <ChartCard eyebrow={t('charts.demographics')} title={t('charts.age.title')} description={t('charts.age.description')}>
              <AgeBars values={statistics.ageDistribution} />
            </ChartCard>
          </div>

          <ChartCard className={styles.timelineCard} eyebrow={t('charts.timeline.eyebrow')} title={t('charts.timeline.title')} description={t('charts.timeline.description')} action={<span className={styles.timelineTotal}><TrendingUp aria-hidden="true" />{t('charts.timeline.total', { count: number.format(timeline.at(-1)?.totalMembers ?? 0) })}</span>}>
            <GrowthTimeline data={timeline} locale={locale} />
          </ChartCard>

          <div className={styles.distributionGrid}>
            <ChartCard icon={MapPinned} eyebrow={t('charts.distribution')} title={t('charts.geography.title')} description={t('charts.geography.description')}>
              <HorizontalBars values={statistics.geographicDistribution} empty={t('charts.empty')} otherLabel={t('charts.other')} />
            </ChartCard>
            <ChartCard icon={BookOpenCheck} eyebrow={t('charts.distribution')} title={t('charts.occupation.title')} description={t('charts.occupation.description')}>
              <HorizontalBars values={statistics.occupationDistribution} empty={t('charts.empty')} otherLabel={t('charts.other')} color="gold" />
            </ChartCard>
            <ChartCard icon={GraduationCap} eyebrow={t('charts.distribution')} title={t('charts.education.title')} description={t('charts.education.description')}>
              <HorizontalBars values={statistics.educationDistribution} empty={t('charts.empty')} otherLabel={t('charts.other')} color="blue" />
            </ChartCard>
          </div>
        </div>
      ) : null}
    </section>
  );
}

async function request<T>(url: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(url, { signal, headers: { Accept: 'application/json' } });
  const body = await response.json().catch(() => null) as unknown;
  if (!response.ok) throw new Error(readApiError(body) || 'Request failed');
  return body as T;
}

function readApiError(body: unknown): string | undefined {
  if (!body || typeof body !== 'object' || !('error' in body)) return undefined;
  const error = (body as { error?: unknown }).error;
  if (typeof error === 'string') return error;
  if (error && typeof error === 'object' && 'message' in error && typeof error.message === 'string') return error.message;
  return undefined;
}

function safeFilename(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[đĐ]/g, 'd').replace(/[^a-zA-Z0-9_-]+/g, '-').replace(/^-|-$/g, '') || 'family';
}

function MetricCard({ icon: Icon, label, value, detail, tone }: { icon: LucideIcon; label: string; value: string; detail: string; tone: 'green' | 'gold' | 'blue' | 'violet' }) {
  return <article className={styles.metric} data-tone={tone}><div className={styles.metricTop}><span><Icon aria-hidden="true" /></span><small>{label}</small></div><strong>{value}</strong><p>{detail}</p></article>;
}

function ChartCard({ icon: Icon, eyebrow, title, description, action, className, children }: { icon?: LucideIcon; eyebrow: string; title: string; description: string; action?: React.ReactNode; className?: string; children: React.ReactNode }) {
  return <article className={`${styles.chartCard} ${className ?? ''}`}><header className={styles.chartHeader}><div>{Icon && <span className={styles.chartIcon}><Icon aria-hidden="true" /></span>}<span><small>{eyebrow}</small><h2>{title}</h2><p>{description}</p></span></div>{action}</header><div className={styles.chartBody}>{children}</div></article>;
}

function GenderDonut({ values }: { values: ReportStatistics['genderDistribution'] }) {
  const t = useTranslations('reportsPage');
  const total = values.MALE + values.FEMALE + values.OTHER;
  const maleEnd = total ? values.MALE / total * 100 : 0;
  const femaleEnd = total ? maleEnd + values.FEMALE / total * 100 : 0;
  const background = total
    ? `conic-gradient(hsl(207 62% 55%) 0 ${maleEnd}%, hsl(342 62% 61%) ${maleEnd}% ${femaleEnd}%, hsl(42 66% 55%) ${femaleEnd}% 100%)`
    : 'conic-gradient(hsl(var(--muted)) 0 100%)';
  const entries: Array<[keyof typeof values, string]> = [['MALE', 'hsl(207 62% 55%)'], ['FEMALE', 'hsl(342 62% 61%)'], ['OTHER', 'hsl(42 66% 55%)']];
  return <div className={styles.donutLayout}><div className={styles.donut} style={{ background }} role="img" aria-label={t('charts.gender.aria', { total })}><span><strong>{total}</strong><small>{t('charts.gender.people')}</small></span></div><ul className={styles.donutLegend}>{entries.map(([key, color]) => { const count = values[key]; const percentage = total ? Math.round(count / total * 100) : 0; return <li key={key}><i style={{ backgroundColor: color }} /><span>{t(`gender.${key}`)}</span><strong>{count}</strong><small>{percentage}%</small></li>; })}</ul></div>;
}

function AgeBars({ values }: { values: ReportStatistics['ageDistribution'] }) {
  const t = useTranslations('reportsPage');
  const entries = Object.entries(values);
  const max = Math.max(1, ...entries.map(([, value]) => value));
  return <div className={styles.ageChart} role="img" aria-label={t('charts.age.aria')}><div className={styles.ageGrid}>{entries.map(([label, count]) => <div key={label} className={styles.ageColumn}><div className={styles.ageValue}>{count}</div><div className={styles.ageTrack}><i style={{ height: `${count / max * 100}%` }} /></div><span>{label === 'UNKNOWN' ? t('unknown') : label}</span></div>)}</div></div>;
}

function HorizontalBars({ values, empty, otherLabel, color = 'green' }: { values: Record<string, number>; empty: string; otherLabel: string; color?: 'green' | 'gold' | 'blue' }) {
  const allEntries = Object.entries(values);
  if (allEntries.length === 0) return <div className={styles.emptyChart}><BarChart3 aria-hidden="true" /><span>{empty}</span></div>;
  const entries = allEntries.length > 6
    ? [...allEntries.slice(0, 5), [otherLabel, allEntries.slice(5).reduce((sum, [, count]) => sum + count, 0)] as [string, number]]
    : allEntries;
  const total = allEntries.reduce((sum, [, count]) => sum + count, 0);
  const max = Math.max(1, ...entries.map(([, count]) => count));
  return <ol className={styles.horizontalBars} data-color={color}>{entries.map(([label, count]) => <li key={label}><div><span title={label}>{label === 'UNKNOWN' ? '—' : label}</span><strong>{count}<small>{total ? Math.round(count / total * 100) : 0}%</small></strong></div><span className={styles.barTrack}><i style={{ width: `${count / max * 100}%` }} /></span></li>)}</ol>;
}

function GrowthTimeline({ data, locale }: { data: GrowthTimelinePoint[]; locale: string }) {
  const t = useTranslations('reportsPage');
  if (data.length === 0) return <div className={styles.emptyChart}><TrendingUp aria-hidden="true" /><span>{t('charts.timeline.empty')}</span></div>;
  const width = 760;
  const height = 230;
  const padding = { left: 42, right: 18, top: 22, bottom: 38 };
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;
  const max = Math.max(1, ...data.map((point) => point.totalMembers));
  const points = data.map((point, index) => ({
    ...point,
    x: padding.left + (data.length === 1 ? chartWidth / 2 : chartWidth * index / (data.length - 1)),
    y: padding.top + chartHeight - point.totalMembers / max * chartHeight
  }));
  const line = points.map((point, index) => `${index ? 'L' : 'M'} ${point.x} ${point.y}`).join(' ');
  const area = `${line} L ${points.at(-1)!.x} ${padding.top + chartHeight} L ${points[0].x} ${padding.top + chartHeight} Z`;
  const labelIndexes = [...new Set([0, Math.floor((data.length - 1) / 2), data.length - 1])];
  const formatPeriod = (period: string) => new Intl.DateTimeFormat(locale, { month: 'short', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${period}-01T00:00:00Z`));
  return <div className={styles.timelineWrap}><svg className={styles.timeline} viewBox={`0 0 ${width} ${height}`} role="img" aria-label={t('charts.timeline.aria')} preserveAspectRatio="none"><defs><linearGradient id="timeline-area" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="hsl(var(--primary))" stopOpacity=".24" /><stop offset="1" stopColor="hsl(var(--primary))" stopOpacity=".015" /></linearGradient></defs>{[0, .25, .5, .75, 1].map((ratio) => <line key={ratio} x1={padding.left} x2={width - padding.right} y1={padding.top + chartHeight * ratio} y2={padding.top + chartHeight * ratio} className={styles.gridLine} />)}<path d={area} fill="url(#timeline-area)" /><path d={line} className={styles.timelineLine} />{points.map((point) => <g key={point.period}><circle cx={point.x} cy={point.y} r="4" className={styles.timelinePoint}><title>{`${formatPeriod(point.period)}: ${point.totalMembers} (${point.newMembers > 0 ? '+' : ''}${point.newMembers})`}</title></circle></g>)}{labelIndexes.map((index) => <text key={index} x={points[index].x} y={height - 9} textAnchor={index === 0 ? 'start' : index === data.length - 1 ? 'end' : 'middle'} className={styles.axisLabel}>{formatPeriod(points[index].period)}</text>)}<text x={padding.left - 8} y={padding.top + 4} textAnchor="end" className={styles.axisLabel}>{max}</text><text x={padding.left - 8} y={padding.top + chartHeight + 3} textAnchor="end" className={styles.axisLabel}>0</text></svg></div>;
}

function ReportsSkeleton() {
  return <div className={styles.skeleton} aria-label="Loading"><div className={styles.metrics}>{[0, 1, 2, 3].map((item) => <i key={item} />)}</div><div className={styles.primaryGrid}><i /><i /></div><i className={styles.skeletonTimeline} /><div className={styles.distributionGrid}><i /><i /><i /></div></div>;
}
