'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import {
  CheckCircle2,
  ChevronDown,
  Filter,
  Loader2,
  MapPin,
  RotateCcw,
  Search,
  SlidersHorizontal,
  UserRound,
  X
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useRouter } from '@/i18n/navigation';
import type { Gender, Member } from '@/data/types';
import type { AutocompleteItem, SearchResult, SearchableMemberField } from '@/lib/services/search-service';
import { Button } from '@/components/ui/button';
import { DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';

type SearchStatus = 'idle' | 'loading' | 'success' | 'error';

interface SearchFilters {
  gender: '' | Gender;
  generation: string;
  birthYearFrom: string;
  birthYearTo: string;
  status: '' | 'ALIVE' | 'DECEASED';
  location: string;
}

interface DisplayResult {
  member: Pick<Member, 'id' | 'fullName'> & Partial<Pick<Member, 'nickname' | 'avatarUrl' | 'gender' | 'generation' | 'dateOfBirth' | 'isAlive' | 'occupation' | 'placeOfBirth' | 'currentAddress'>>;
  matchedFields: SearchableMemberField[];
}

const EMPTY_FILTERS: SearchFilters = {
  gender: '',
  generation: '',
  birthYearFrom: '',
  birthYearTo: '',
  status: '',
  location: ''
};

export function MemberSearch({ treeId, onClose }: { treeId: string; onClose: () => void }) {
  const t = useTranslations('memberSearch');
  const router = useRouter();
  const resultRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState<SearchFilters>(EMPTY_FILTERS);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [status, setStatus] = useState<SearchStatus>('idle');
  const [results, setResults] = useState<DisplayResult[]>([]);
  const [error, setError] = useState('');

  const activeFilterCount = useMemo(
    () => Object.values(filters).filter((value) => value.trim() !== '').length,
    [filters]
  );
  const canSearch = query.trim().length >= 2 || activeFilterCount > 0;
  const invalidYearRange = Boolean(
    filters.birthYearFrom && filters.birthYearTo && Number(filters.birthYearFrom) > Number(filters.birthYearTo)
  );

  useEffect(() => {
    if (!canSearch) {
      setStatus('idle');
      setResults([]);
      setError('');
      return;
    }
    if (invalidYearRange) {
      setStatus('error');
      setResults([]);
      setError(t('filters.invalidYears'));
      return;
    }

    const controller = new AbortController();
    const timeout = window.setTimeout(async () => {
      setStatus('loading');
      setError('');
      try {
        const params = new URLSearchParams({ treeId, limit: '24' });
        const normalizedQuery = query.trim();
        if (normalizedQuery.length >= 2) {
          params.set('q', normalizedQuery);
          // Live search is the autocomplete experience here, while the full
          // search mode also keeps occupation and birthplace discoverable.
          params.set('mode', 'search');
        } else {
          params.set('mode', 'filter');
        }
        appendFilters(params, filters);
        const response = await fetch(`/api/search?${params.toString()}`, {
          signal: controller.signal,
          headers: { Accept: 'application/json' }
        });
        const body = await response.json().catch(() => null) as unknown;
        if (!response.ok) throw new Error(readApiError(body) || t('error'));
        setResults(normalizeResults(body).slice(0, 24));
        setStatus('success');
      } catch (reason) {
        if (reason instanceof DOMException && reason.name === 'AbortError') return;
        setResults([]);
        setStatus('error');
        setError(reason instanceof Error ? reason.message : t('error'));
      }
    }, 220);

    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [activeFilterCount, canSearch, filters, invalidYearRange, query, t, treeId]);

  const selectResult = (memberId: string) => {
    onClose();
    router.push(`/trees/${treeId}?member=${encodeURIComponent(memberId)}` as never);
  };

  const onInputKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown' && results.length > 0) {
      event.preventDefault();
      resultRefs.current[0]?.focus();
    }
  };

  const resetFilters = () => {
    setFilters(EMPTY_FILTERS);
    setError('');
  };

  return (
    <div className="overflow-hidden">
      <div className="border-b border-border px-5 pb-4 pt-5 sm:px-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-[.14em] text-primary">{t('eyebrow')}</p>
            <DialogTitle className="mt-1 font-display text-xl font-semibold tracking-tight">{t('title')}</DialogTitle>
            <DialogDescription className="mt-1 text-sm text-muted-foreground">{t('description')}</DialogDescription>
          </div>
          <Button type="button" variant="ghost" size="icon" className="-mr-2 -mt-2 shrink-0" onClick={onClose} aria-label={t('close')}>
            <X aria-hidden="true" />
          </Button>
        </div>

        <div className="mt-4 flex gap-2">
          <label className="relative min-w-0 flex-1">
            <span className="sr-only">{t('inputLabel')}</span>
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
            <Input
              autoFocus
              className="h-11 pl-10 pr-10"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={onInputKeyDown}
              placeholder={t('placeholder')}
              role="combobox"
              aria-expanded={status === 'success' && results.length > 0}
              aria-controls="member-search-results"
              aria-autocomplete="list"
            />
            {status === 'loading' ? (
              <Loader2 className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 animate-spin text-primary" aria-hidden="true" />
            ) : query ? (
              <button type="button" className="absolute right-2 top-1/2 grid size-7 -translate-y-1/2 place-items-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground" onClick={() => setQuery('')} aria-label={t('clearQuery')}>
                <X className="size-3.5" aria-hidden="true" />
              </button>
            ) : null}
          </label>
          <Button type="button" variant={filtersOpen ? 'secondary' : 'outline'} className="h-11 shrink-0" onClick={() => setFiltersOpen((value) => !value)} aria-expanded={filtersOpen} aria-controls="member-search-filters">
            <SlidersHorizontal aria-hidden="true" />
            <span className="hidden sm:inline">{t('filters.title')}</span>
            {activeFilterCount > 0 && <span className="grid size-5 place-items-center rounded-full bg-primary text-[10px] font-bold text-primary-foreground" aria-label={t('filters.active', { count: activeFilterCount })}>{activeFilterCount}</span>}
            <ChevronDown className={`transition-transform ${filtersOpen ? 'rotate-180' : ''}`} aria-hidden="true" />
          </Button>
        </div>

        {filtersOpen && (
          <div id="member-search-filters" className="mt-3 rounded-xl border border-border bg-muted/30 p-3 sm:p-4">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <SearchSelect label={t('filters.gender')} value={filters.gender} onChange={(value) => setFilters((current) => ({ ...current, gender: value as SearchFilters['gender'] }))} options={[
                ['', t('filters.anyGender')], ['MALE', t('gender.MALE')], ['FEMALE', t('gender.FEMALE')], ['OTHER', t('gender.OTHER')]
              ]} />
              <SearchField label={t('filters.generation')}>
                <Input inputMode="numeric" min={0} max={99} type="number" value={filters.generation} onChange={(event) => setFilters((current) => ({ ...current, generation: event.target.value }))} placeholder={t('filters.anyGeneration')} />
              </SearchField>
              <SearchSelect label={t('filters.status')} value={filters.status} onChange={(value) => setFilters((current) => ({ ...current, status: value as SearchFilters['status'] }))} options={[
                ['', t('filters.anyStatus')], ['ALIVE', t('status.ALIVE')], ['DECEASED', t('status.DECEASED')]
              ]} />
              <SearchField label={t('filters.birthYearFrom')}>
                <Input inputMode="numeric" min={1} max={9999} type="number" value={filters.birthYearFrom} onChange={(event) => setFilters((current) => ({ ...current, birthYearFrom: event.target.value }))} placeholder={t('filters.yearPlaceholder')} />
              </SearchField>
              <SearchField label={t('filters.birthYearTo')}>
                <Input inputMode="numeric" min={1} max={9999} type="number" value={filters.birthYearTo} onChange={(event) => setFilters((current) => ({ ...current, birthYearTo: event.target.value }))} placeholder={t('filters.yearPlaceholder')} aria-invalid={invalidYearRange} />
              </SearchField>
              <SearchField label={t('filters.location')}>
                <Input value={filters.location} onChange={(event) => setFilters((current) => ({ ...current, location: event.target.value }))} placeholder={t('filters.locationPlaceholder')} />
              </SearchField>
            </div>
            <div className="mt-3 flex items-center justify-between gap-3 border-t border-border/70 pt-3">
              <p className="text-xs text-muted-foreground">{t('filters.hint')}</p>
              <Button type="button" variant="ghost" size="sm" onClick={resetFilters} disabled={activeFilterCount === 0}><RotateCcw aria-hidden="true" />{t('filters.reset')}</Button>
            </div>
          </div>
        )}
      </div>

      <div className="max-h-[min(52vh,430px)] min-h-48 overflow-y-auto p-3 sm:p-4">
        <div className="sr-only" role="status" aria-live="polite">
          {status === 'loading' ? t('loading') : status === 'success' ? t('resultCount', { count: results.length }) : error}
        </div>
        {!canSearch && <SearchState icon={<Search />} title={t('startTitle')} description={t('startDescription')} />}
        {status === 'loading' && <SearchSkeleton />}
        {status === 'error' && <SearchState tone="error" icon={<Filter />} title={t('errorTitle')} description={error} />}
        {status === 'success' && results.length === 0 && <SearchState icon={<UserRound />} title={t('emptyTitle')} description={t('emptyDescription')} />}
        {status === 'success' && results.length > 0 && (
          <div>
            <div className="mb-2 flex items-center justify-between px-1 text-xs text-muted-foreground">
              <span>{t('resultCount', { count: results.length })}</span>
              <span className="hidden sm:inline">{t('selectHint')}</span>
            </div>
            <div id="member-search-results" role="listbox" aria-label={t('resultsLabel')} className="grid gap-1.5">
              {results.map(({ member, matchedFields }, index) => (
                <button
                  key={member.id}
                  ref={(node) => { resultRefs.current[index] = node; }}
                  type="button"
                  role="option"
                  aria-selected="false"
                  className="group flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-2.5 text-left transition-colors hover:border-border hover:bg-accent/65 focus-visible:border-ring focus-visible:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  onClick={() => selectResult(member.id)}
                  onKeyDown={(event) => {
                    if (event.key === 'ArrowDown') { event.preventDefault(); resultRefs.current[(index + 1) % results.length]?.focus(); }
                    if (event.key === 'ArrowUp') { event.preventDefault(); index === 0 ? document.querySelector<HTMLInputElement>('[role="combobox"]')?.focus() : resultRefs.current[index - 1]?.focus(); }
                  }}
                >
                  <SearchAvatar member={member} />
                  <span className="min-w-0 flex-1">
                    <span className="flex min-w-0 items-center gap-2">
                      <strong className="truncate text-sm font-semibold">{member.fullName}</strong>
                      {member.nickname && <span className="truncate text-xs text-muted-foreground">“{member.nickname}”</span>}
                    </span>
                    <span className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                      {member.generation !== undefined && <span>{t('generation', { value: member.generation + 1 })}</span>}
                      {member.dateOfBirth && <span>{member.dateOfBirth.slice(0, 4)}</span>}
                      {(member.placeOfBirth || member.currentAddress) && <span className="inline-flex min-w-0 items-center gap-1"><MapPin className="size-3 shrink-0" aria-hidden="true" /><span className="max-w-52 truncate">{member.placeOfBirth || member.currentAddress}</span></span>}
                      {member.occupation && <span className="max-w-44 truncate">{member.occupation}</span>}
                    </span>
                    {matchedFields.length > 0 && <span className="mt-1.5 flex flex-wrap gap-1">{matchedFields.map((field) => <span key={field} className="rounded-full bg-primary/8 px-2 py-0.5 text-[10px] font-semibold text-primary">{t(`matched.${field}`)}</span>)}</span>}
                  </span>
                  <span className="grid size-8 shrink-0 place-items-center rounded-lg text-muted-foreground transition-colors group-hover:bg-background group-hover:text-primary"><CheckCircle2 className="size-4" aria-hidden="true" /></span>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function appendFilters(params: URLSearchParams, filters: SearchFilters): void {
  if (filters.gender) params.set('gender', filters.gender);
  if (filters.generation) params.set('generation', filters.generation);
  if (filters.birthYearFrom) params.set('birthYearFrom', filters.birthYearFrom);
  if (filters.birthYearTo) params.set('birthYearTo', filters.birthYearTo);
  if (filters.status) params.set('status', filters.status);
  if (filters.location.trim()) params.set('location', filters.location.trim());
}

function normalizeResults(body: unknown): DisplayResult[] {
  if (!Array.isArray(body)) return [];
  return body.flatMap((item): DisplayResult[] => {
    if (!item || typeof item !== 'object') return [];
    if ('member' in item && item.member && typeof item.member === 'object') {
      const result = item as SearchResult;
      return [{ member: result.member, matchedFields: result.matchedFields }];
    }
    if ('memberId' in item && 'fullName' in item) {
      const suggestion = item as AutocompleteItem;
      return [{
        member: { id: suggestion.memberId, fullName: suggestion.fullName, nickname: suggestion.nickname, avatarUrl: suggestion.avatarUrl },
        matchedFields: []
      } as DisplayResult];
    }
    if ('id' in item && 'fullName' in item) return [{ member: item as Member, matchedFields: [] }];
    return [];
  });
}

function readApiError(body: unknown): string | undefined {
  if (!body || typeof body !== 'object' || !('error' in body)) return undefined;
  const error = (body as { error?: unknown }).error;
  if (typeof error === 'string') return error;
  if (error && typeof error === 'object' && 'message' in error && typeof error.message === 'string') return error.message;
  return undefined;
}

function SearchField({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="grid gap-1.5 text-xs font-semibold text-foreground"><span>{label}</span>{children}</label>;
}

function SearchSelect({ label, value, onChange, options }: { label: string; value: string; onChange: (value: string) => void; options: Array<[string, string]> }) {
  return <SearchField label={label}><select className="h-10 rounded-lg border border-input bg-background px-3 text-sm shadow-sm outline-none focus:ring-2 focus:ring-ring" value={value} onChange={(event) => onChange(event.target.value)}>{options.map(([optionValue, optionLabel]) => <option key={optionValue || 'all'} value={optionValue}>{optionLabel}</option>)}</select></SearchField>;
}

function SearchAvatar({ member }: { member: DisplayResult['member'] }) {
  if (member.avatarUrl) return <img className="size-11 shrink-0 rounded-xl object-cover" src={member.avatarUrl} alt="" />;
  const initials = member.fullName.split(/\s+/).filter(Boolean).slice(-2).map((part) => part[0]).join('').toUpperCase();
  const tone = member.gender === 'FEMALE' ? 'bg-rose-100 text-rose-700 dark:bg-rose-950 dark:text-rose-200' : member.gender === 'MALE' ? 'bg-sky-100 text-sky-700 dark:bg-sky-950 dark:text-sky-200' : 'bg-primary/10 text-primary';
  return <span className={`grid size-11 shrink-0 place-items-center rounded-xl text-xs font-bold ${tone}`} aria-hidden="true">{initials || '?'}</span>;
}

function SearchState({ icon, title, description, tone = 'default' }: { icon: React.ReactNode; title: string; description: string; tone?: 'default' | 'error' }) {
  return <div className="grid min-h-44 place-items-center px-5 py-8 text-center"><div><span className={`mx-auto grid size-11 place-items-center rounded-xl [&>svg]:size-5 ${tone === 'error' ? 'bg-destructive/10 text-destructive' : 'bg-accent text-primary'}`}>{icon}</span><h3 className="mt-3 font-display text-base font-semibold">{title}</h3><p className="mx-auto mt-1 max-w-sm text-sm leading-relaxed text-muted-foreground">{description}</p></div></div>;
}

function SearchSkeleton() {
  return <div className="grid gap-2" aria-hidden="true">{[0, 1, 2, 3].map((item) => <div key={item} className="flex animate-pulse items-center gap-3 rounded-xl px-3 py-2.5"><span className="size-11 rounded-xl bg-muted" /><span className="grid flex-1 gap-2"><i className="h-3 w-2/5 rounded bg-muted" /><i className="h-2.5 w-3/5 rounded bg-muted" /></span></div>)}</div>;
}
