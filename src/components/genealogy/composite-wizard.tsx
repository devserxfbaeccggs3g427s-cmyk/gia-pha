'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { AlertTriangle, CheckCircle2, ExternalLink, Loader2, RefreshCw, Trash2 } from 'lucide-react';
import type { CompositeTreeConfig, FamilyTree, Member, SourcePreview, SourceReference } from '@/data/types';
import type { IdentitySuggestion } from '@/lib/composite/identity-matching';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { queryKeys } from '@/lib/query/keys';

type Scope = 'FULL_TREE' | 'DESCENDANTS' | 'SELECTED_MEMBERS';
type Validation = { valid: boolean; errors?: Array<{ message?: string } | string>; warnings?: Array<{ message?: string } | string> };
const STEPS = ['details', 'sources', 'preview', 'identities', 'relationships', 'validate', 'publish'] as const;

export function CompositeWizard({ tree, availableTrees }: { tree: FamilyTree; availableTrees: FamilyTree[] }) {
  const t = useTranslations('composite');
  const queryClient = useQueryClient();
  const storageKey = `composite-wizard:${tree.id}`;
  const [step, setStep] = useState(0);
  const [config, setConfig] = useState<CompositeTreeConfig>();
  const [sourceTreeId, setSourceTreeId] = useState('');
  const [scope, setScope] = useState<Scope>('FULL_TREE');
  const [memberIds, setMemberIds] = useState('');
  const [includeSpouses, setIncludeSpouses] = useState(false);
  const [includeEvents, setIncludeEvents] = useState(true);
  const [includeMedia, setIncludeMedia] = useState(true);
  const [preview, setPreview] = useState<SourcePreview>();
  const [suggestions, setSuggestions] = useState<IdentitySuggestion[]>([]);
  const [validation, setValidation] = useState<Validation>();
  const [linkSource, setLinkSource] = useState<SourceReference>({ treeId: '', memberId: '' });
  const [linkTarget, setLinkTarget] = useState<SourceReference>({ treeId: '', memberId: '' });
  const [sourceMembers, setSourceMembers] = useState<Record<string, Member[]>>({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const response = await fetch(`/api/trees/${tree.id}/composition`);
    if (!response.ok) return;
    setConfig(await response.json());
  }, [tree.id]);
  useEffect(() => { void load(); try { const saved = JSON.parse(localStorage.getItem(storageKey) ?? '{}'); if (Number.isInteger(saved.step)) setStep(Math.min(6, Math.max(0, saved.step))); if (typeof saved.sourceTreeId === 'string') setSourceTreeId(saved.sourceTreeId); if (['FULL_TREE', 'DESCENDANTS', 'SELECTED_MEMBERS'].includes(saved.scope)) setScope(saved.scope); if (typeof saved.memberIds === 'string') setMemberIds(saved.memberIds); setIncludeSpouses(Boolean(saved.includeSpouses)); if (typeof saved.includeEvents === 'boolean') setIncludeEvents(saved.includeEvents); if (typeof saved.includeMedia === 'boolean') setIncludeMedia(saved.includeMedia); if (saved.linkSource) setLinkSource(saved.linkSource); if (saved.linkTarget) setLinkTarget(saved.linkTarget); } catch { localStorage.removeItem(storageKey); } }, [load, storageKey]);
  useEffect(() => { localStorage.setItem(storageKey, JSON.stringify({ step, sourceTreeId, scope, memberIds, includeSpouses, includeEvents, includeMedia, linkSource, linkTarget })); }, [step, sourceTreeId, scope, memberIds, includeSpouses, includeEvents, includeMedia, linkSource, linkTarget, storageKey]);

  const request = async <T,>(path: string, method: 'POST' | 'PUT' | 'DELETE' = 'POST', body?: unknown): Promise<T | undefined> => {
    if (!config || !navigator.onLine) { setError(t('offline')); return; }
    setBusy(true); setError('');
    try {
      const response = await fetch(`/api/trees/${tree.id}/composition/${path}`, { method, headers: { 'Content-Type': 'application/json', 'If-Match': String(config.revision) }, body: body === undefined ? undefined : JSON.stringify(body) });
      const value = response.status === 204 ? undefined : await response.json();
      if (!response.ok) throw new Error(value?.error?.message ?? value?.message ?? t('error'));
      if (value && 'revision' in value) setConfig(value);
      return value as T;
    } catch (reason) { setError(reason instanceof Error ? reason.message : t('error')); }
    finally { setBusy(false); }
  };
  const ids = memberIds.split(',').map((id) => id.trim()).filter(Boolean);
  const sourceInput = { sourceTreeId, scope, anchorMemberIds: scope === 'DESCENDANTS' ? ids : [], selectedMemberIds: scope === 'SELECTED_MEMBERS' ? ids : [], includeSpouses, includeEvents, includeMedia, allowCompositeSharing: false, shareLivingDetails: false };
  const sourceName = (id: string) => availableTrees.find((item) => item.id === id)?.name ?? id;
  const warningText = (value: { message?: string } | string) => typeof value === 'string' ? value : value.message ?? t('error');
  const status = useMemo(() => ({ conflicts: validation?.warnings?.filter((item) => warningText(item).toLowerCase().includes('conflict')) ?? [], stale: validation?.warnings?.filter((item) => warningText(item).toLowerCase().includes('stale')) ?? [] }), [validation]);

  const previewSource = async () => {
    setBusy(true); setError('');
    try { const response = await fetch(`/api/trees/${tree.id}/composition/preview-source`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(sourceInput) }); const value = await response.json(); if (!response.ok) throw new Error(value?.error?.message ?? t('error')); setPreview(value); setStep(2); }
    catch (reason) { setError(reason instanceof Error ? reason.message : t('error')); } finally { setBusy(false); }
  };
  const loadSuggestions = async () => { setBusy(true); setNotice(''); try { const response = await fetch(`/api/trees/${tree.id}/composition/identity-suggestions`); if (!response.ok) throw new Error(t('error')); setSuggestions(await response.json()); setStep(3); } catch (reason) { setError(reason instanceof Error ? reason.message : t('error')); } finally { setBusy(false); } };
  const reviewSuggestion = async (suggestion: IdentitySuggestion, decision: 'CONFIRMED' | 'REJECTED') => {
    const saved = await request<CompositeTreeConfig>('identity-groups', 'POST', { references: suggestion.references, status: decision, ...(decision === 'CONFIRMED' ? { preferredReference: suggestion.references[0] } : {}) });
    if (!saved) return;
    setSuggestions((current) => current.filter((item) => item !== suggestion));
    setNotice(decision === 'CONFIRMED' ? t('identityConfirmed') : t('identityRejected'));
  };
  const loadSourceMembers = useCallback(async () => {
    const sourceIds = config?.sources.map((source) => source.sourceTreeId) ?? [];
    const entries = await Promise.all(sourceIds.map(async (sourceId) => {
      const response = await fetch(`/api/trees/${sourceId}/members`);
      return [sourceId, response.ok ? await response.json() as Member[] : []] as const;
    }));
    setSourceMembers(Object.fromEntries(entries));
  }, [config?.sources]);
  useEffect(() => { if (step === 4) void loadSourceMembers(); }, [step, loadSourceMembers]);
  const memberName = (reference: SourceReference) => sourceMembers[reference.treeId]?.find((member) => member.id === reference.memberId)?.fullName ?? reference.memberId;
  const validate = async () => { const value = await request<Validation>('validate'); if (value) { setValidation(value); setStep(5); } };

  return <Card className="border-primary/20"><CardHeader><CardTitle>{t('title')}</CardTitle></CardHeader><CardContent className="space-y-5">
    <ol className="grid gap-2 text-xs sm:grid-cols-3 lg:grid-cols-7">{STEPS.map((item, index) => <li key={item}><button type="button" onClick={() => setStep(index)} className={`w-full rounded-lg p-2 text-left ${step === index ? 'bg-primary text-primary-foreground' : 'bg-muted'}`}>{index + 1}. {t(item)}</button></li>)}</ol>
    {step === 0 && <div className="rounded-xl bg-muted/40 p-4"><h3 className="font-semibold">{tree.name}</h3><p className="mt-1 text-sm text-muted-foreground">{tree.description || t('noDescription')}</p><Button className="mt-4" onClick={() => setStep(1)}>{t('continue')}</Button></div>}
    {(step === 1 || step === 2) && <div className="space-y-4"><div className="grid gap-3 md:grid-cols-2"><label className="grid gap-1 text-sm"><span>{t('source')}</span><select value={sourceTreeId} onChange={(event) => setSourceTreeId(event.target.value)} className="h-10 rounded-md border bg-background px-3"><option value="">{t('chooseSource')}</option>{availableTrees.filter((item) => item.id !== tree.id && (item.kind ?? 'STANDALONE') === 'STANDALONE').map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label><label className="grid gap-1 text-sm"><span>{t('scope')}</span><select value={scope} onChange={(event) => setScope(event.target.value as Scope)} className="h-10 rounded-md border bg-background px-3"><option value="FULL_TREE">{t('fullTree')}</option><option value="DESCENDANTS">{t('descendants')}</option><option value="SELECTED_MEMBERS">{t('selectedMembers')}</option></select></label></div>{scope !== 'FULL_TREE' && <label className="grid gap-1 text-sm"><span>{scope === 'DESCENDANTS' ? t('anchorIds') : t('memberIds')}</span><Input value={memberIds} onChange={(event) => setMemberIds(event.target.value)} placeholder={t('idsHint')} /></label>}<div className="flex flex-wrap gap-4 text-sm"><label><input type="checkbox" checked={includeSpouses} onChange={(event) => setIncludeSpouses(event.target.checked)} /> {t('includeSpouses')}</label><label><input type="checkbox" checked={includeEvents} onChange={(event) => setIncludeEvents(event.target.checked)} /> {t('includeEvents')}</label><label><input type="checkbox" checked={includeMedia} onChange={(event) => setIncludeMedia(event.target.checked)} /> {t('includeMedia')}</label></div><div className="flex gap-2"><Button variant="outline" disabled={!sourceTreeId || busy} onClick={() => void previewSource()}>{t('preview')}</Button><Button disabled={!sourceTreeId || busy} onClick={async () => { if (await request('sources', 'POST', sourceInput)) setStep(1); }}>{t('addSource')}</Button></div>{preview && <div className="rounded-xl border p-4 text-sm"><p>{t('previewCounts', { members: preview.memberCount, relationships: preview.relationshipCount, events: preview.eventCount, media: preview.mediaCount })}</p>{preview.warnings.map((warning) => <p key={`${warning.code}-${warning.entityId}`} className="mt-2 text-amber-700">{warning.message}</p>)}</div>}<div className="space-y-2">{config?.sources.map((source) => <div key={source.id} className="flex items-center gap-3 rounded-lg border p-3"><span className="min-w-0 flex-1"><strong>{sourceName(source.sourceTreeId)}</strong><small className="block text-muted-foreground">{t(`scopeNames.${source.scope}`)}</small></span><a href={`/trees/${source.sourceTreeId}`} className="rounded p-2 hover:bg-muted" aria-label={t('openSource')}><ExternalLink className="size-4" /></a><Button size="icon" variant="ghost" disabled={busy} onClick={() => void request(`sources/${source.id}`, 'DELETE').then(load)}><Trash2 /></Button></div>)}</div><Button variant="outline" onClick={() => void loadSuggestions()}>{t('continueIdentity')}</Button></div>}
    {step === 3 && <div className="space-y-3">{suggestions.length === 0 ? <p className="text-sm text-muted-foreground">{t('noSuggestions')}</p> : suggestions.map((suggestion) => <div key={suggestion.references.map((ref) => `${ref.treeId}:${ref.memberId}`).join('|')} className="flex flex-wrap items-center gap-3 rounded-xl border p-3 text-sm"><span className="flex-1">{suggestion.references.map((ref, index) => `${sourceName(ref.treeId)} · ${suggestion.memberNames[index] ?? ref.memberId}`).join(' ↔ ')}<small className="block text-muted-foreground">{t('matchScore', { score: suggestion.score })} · {suggestion.matchedFields.join(', ')}</small></span><Button size="sm" disabled={busy} onClick={() => void reviewSuggestion(suggestion, 'CONFIRMED')}>{t('confirm')}</Button><Button size="sm" variant="outline" disabled={busy} onClick={() => void reviewSuggestion(suggestion, 'REJECTED')}>{t('reject')}</Button></div>)}<Button onClick={() => setStep(4)}>{t('continue')}</Button></div>}
    {step === 4 && <div className="space-y-3"><div className="grid gap-3 md:grid-cols-2">{[ { value: linkSource, set: setLinkSource, label: t('linkSource') }, { value: linkTarget, set: setLinkTarget, label: t('linkTarget') } ].map(({ value, set, label }) => <div key={label} className="grid gap-2"><label className="text-sm">{label}<select className="mt-1 h-10 w-full rounded-md border bg-background px-3" value={value.treeId} onChange={(event) => set({ ...value, treeId: event.target.value })}><option value="">{t('chooseSource')}</option>{config?.sources.map((source) => <option key={source.id} value={source.sourceTreeId}>{sourceName(source.sourceTreeId)}</option>)}</select></label><select className="h-10 w-full rounded-md border bg-background px-3" value={value.memberId} onChange={(event) => set({ ...value, memberId: event.target.value })} disabled={!value.treeId}><option value="">{t('memberId')}</option>{(sourceMembers[value.treeId] ?? []).map((member) => <option key={member.id} value={member.id}>{member.fullName}</option>)}</select></div>)}</div><Button disabled={!linkSource.treeId || !linkSource.memberId || !linkTarget.treeId || !linkTarget.memberId || busy} onClick={() => void request('relationships', 'POST', { source: linkSource, target: linkTarget, type: 'PARENT_CHILD' })}>{t('addLink')}</Button><div className="space-y-2">{config?.crossTreeRelationships.map((relationship) => <div key={relationship.id} className="flex items-center gap-2 rounded-lg border p-3 text-sm"><span className="flex-1">{sourceName(relationship.source.treeId)} · {memberName(relationship.source)} → {sourceName(relationship.target.treeId)} · {memberName(relationship.target)}</span><Button size="icon" variant="ghost" onClick={() => void request(`relationships/${relationship.id}`, 'DELETE').then(load)}><Trash2 /></Button></div>)}</div><Button variant="outline" onClick={() => void validate()}>{t('validate')}</Button></div>}
    {step === 5 && <div className="space-y-3">{validation?.valid ? <p className="flex items-center gap-2 text-sm text-primary"><CheckCircle2 />{t('valid')}</p> : <p className="flex items-center gap-2 text-sm text-destructive"><AlertTriangle />{t('invalid')}</p>}{validation?.errors?.map((item, index) => <p key={index} className="text-sm text-destructive">{warningText(item)}</p>)}{status.conflicts.length > 0 && <StatusPanel title={t('conflicts')} items={status.conflicts.map(warningText)} />}{status.stale.length > 0 && <StatusPanel title={t('staleSources')} items={status.stale.map(warningText)} />}{config?.sources.filter((source) => !availableTrees.some((candidate) => candidate.id === source.sourceTreeId)).length ? <StatusPanel title={t('unavailableSources')} items={config.sources.filter((source) => !availableTrees.some((candidate) => candidate.id === source.sourceTreeId)).map((source) => source.preferredLabel ?? source.sourceTreeId)} /> : null}<div className="flex gap-2"><Button variant="outline" onClick={() => void validate()} disabled={busy}><RefreshCw />{t('validateAgain')}</Button><Button disabled={!validation?.valid} onClick={() => setStep(6)}>{t('continue')}</Button></div></div>}
    {step === 6 && <div className="rounded-xl border p-5"><p className="text-sm text-muted-foreground">{config?.publishedAt ? t('publishedAt', { date: new Date(config.publishedAt).toLocaleString() }) : t('publishHint')}</p><Button className="mt-4" disabled={busy || !validation?.valid} onClick={async () => { if (await request('publish')) { localStorage.removeItem(storageKey); await queryClient.invalidateQueries({ queryKey: queryKeys.tree(tree.id) }); await queryClient.invalidateQueries({ queryKey: queryKeys.members(tree.id) }); await queryClient.invalidateQueries({ queryKey: queryKeys.relationships(tree.id) }); } }}>{busy && <Loader2 className="animate-spin" />}{t('publish')}</Button></div>}
    {notice && <p role="status" className="flex items-center gap-2 rounded-lg bg-primary/10 p-3 text-sm text-primary"><CheckCircle2 className="size-4" />{notice}</p>}
    {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
  </CardContent></Card>;
}

function StatusPanel({ title, items }: { title: string; items: string[] }) { return <div className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-amber-950 dark:bg-amber-950/20 dark:text-amber-100"><h3 className="font-semibold">{title}</h3><ul className="mt-2 list-disc pl-5 text-sm">{items.map((item, index) => <li key={`${item}-${index}`}>{item}</li>)}</ul></div>; }
