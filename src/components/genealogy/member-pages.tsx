'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocale, useTranslations } from 'next-intl';
import { Link, useRouter } from '@/i18n/navigation';
import {
  AlertTriangle,
  ArrowLeft,
  CalendarDays,
  Check,
  ChevronRight,
  Edit3,
  Image as ImageIcon,
  Loader2,
  Mail,
  MapPin,
  Plus,
  Search,
  Trash2,
  UserRound,
  UsersRound,
  X
} from 'lucide-react';
import type { Event, Gender, MediaMetadata, Member, Relationship, RelationType } from '@/data/types';
import type { CreateMemberInput, CreateRelationshipInput } from '@/data/schemas';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { useToast } from '@/components/ui/toast';
import { useMembersQuery } from '@/hooks/useGenealogyQueries';
import { useMemberMutation } from '@/hooks/useMemberMutation';
import { useRelationshipMutation } from '@/hooks/useRelationshipMutation';

type MemberFormValue = Partial<Member> & { firstName: string; lastName: string; fullName: string; gender: Gender; isAlive: boolean };
type RelationshipDraft = { sourceMemberId: string; targetMemberId: string; type: RelationType; customType?: string; marriageDate?: string; divorceDate?: string; marriageStatus?: string; direction?: 'SOURCE' | 'TARGET' };

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, { ...init, headers: { ...(init?.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }), ...init?.headers } });
  const body = await response.json().catch(() => null) as { error?: { message?: string }; message?: string } | null;
  if (!response.ok) throw new Error(body?.error?.message ?? body?.message ?? 'Request failed');
  return body as T;
}

function emptyMember(): MemberFormValue {
  return { firstName: '', lastName: '', fullName: '', gender: 'OTHER', isAlive: true };
}

function toMemberPayload(form: MemberFormValue): CreateMemberInput {
  const fields: Array<keyof MemberFormValue> = ['firstName', 'lastName', 'fullName', 'nickname', 'gender', 'dateOfBirth', 'dateOfDeath', 'placeOfBirth', 'currentAddress', 'phone', 'email', 'occupation', 'education', 'biography', 'achievements', 'notes', 'avatarUrl', 'generation', 'isAlive'];
  const payload: Record<string, unknown> = Object.fromEntries(fields.map((key) => [key, form[key]]));
  for (const key of Object.keys(payload)) if (payload[key] === '' || payload[key] === undefined) delete payload[key];
  payload.fullName = `${String(form.firstName).trim()} ${String(form.lastName).trim()}`.trim();
  payload.isAlive = Boolean(form.isAlive) && !form.dateOfDeath;
  return payload as unknown as CreateMemberInput;
}

function formatDate(value: string | undefined, locale: string, fallback = '—') {
  if (!value) return fallback;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value.slice(0, 10) : new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(date);
}

function years(member: Member): string {
  const birth = member.dateOfBirth ? new Date(member.dateOfBirth) : null;
  const death = member.dateOfDeath ? new Date(member.dateOfDeath) : null;
  if (!birth || Number.isNaN(birth.getTime())) return '';
  return `${birth.getFullYear()}${death && !Number.isNaN(death.getTime()) ? ` – ${death.getFullYear()}` : ''}`;
}

function initials(name: string) { return name.split(/\s+/).filter(Boolean).slice(-2).map((part) => part[0]).join('').toUpperCase() || '?'; }

function Avatar({ member, size = 'md' }: { member?: Member; size?: 'sm' | 'md' | 'lg' }) {
  const cls = size === 'lg' ? 'size-20 text-2xl rounded-2xl' : size === 'sm' ? 'size-9 text-xs rounded-lg' : 'size-12 text-sm rounded-xl';
  return member?.avatarUrl ? <img src={member.avatarUrl} alt="" className={`${cls} object-cover`} /> : <span className={`grid shrink-0 place-items-center bg-primary/10 font-bold text-primary ${cls}`}>{initials(member?.fullName ?? '')}</span>;
}

function Field({ label, children, hint }: { label: string; children: React.ReactNode; hint?: string }) {
  return <label className="grid gap-1.5 text-sm font-medium"><span>{label}</span>{children}{hint && <span className="text-xs font-normal text-muted-foreground">{hint}</span>}</label>;
}

function MemberForm({ initial, onSubmit, onCancel, submitting }: { initial: MemberFormValue; onSubmit: (value: MemberFormValue) => void; onCancel: () => void; submitting: boolean }) {
  const t = useTranslations('membersPage');
  const [form, setForm] = useState<MemberFormValue>(initial);
  const [error, setError] = useState('');
  useEffect(() => setForm(initial), [initial]);
  const change = (key: keyof MemberFormValue, value: unknown) => setForm((current) => ({ ...current, [key]: value }));
  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!form.firstName.trim() || !form.lastName.trim()) { setError(t('form.requiredName')); return; }
    if (form.dateOfDeath && form.dateOfBirth && form.dateOfDeath < form.dateOfBirth) { setError(t('form.invalidDates')); return; }
    setError(''); onSubmit({ ...form, firstName: form.firstName.trim(), lastName: form.lastName.trim(), fullName: `${form.firstName.trim()} ${form.lastName.trim()}` });
  };
  const input = (key: keyof MemberFormValue, type = 'text', props: React.ComponentProps<typeof Input> = {}) => <Input type={type} value={type === 'date' ? String(form[key] ?? '').slice(0, 10) : String(form[key] ?? '')} onChange={(event) => change(key, event.target.value)} {...props} />;
  return <form onSubmit={submit} className="grid gap-5">
    {error && <p role="alert" className="flex items-center gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive"><AlertTriangle className="size-4" />{error}</p>}
    <div className="grid gap-4 sm:grid-cols-2">
      <Field label={t('form.firstName')}>{input('firstName', 'text', { autoFocus: true })}</Field>
      <Field label={t('form.lastName')}>{input('lastName')}</Field>
      <Field label={t('form.nickname')}>{input('nickname')}</Field>
      <Field label={t('form.gender')}><select className="h-10 rounded-lg border border-input bg-background px-3 text-sm" value={form.gender} onChange={(e) => change('gender', e.target.value)}><option value="MALE">{t('gender.male')}</option><option value="FEMALE">{t('gender.female')}</option><option value="OTHER">{t('gender.other')}</option></select></Field>
      <Field label={t('form.dateOfBirth')}>{input('dateOfBirth', 'date')}</Field>
      <Field label={t('form.dateOfDeath')}>{input('dateOfDeath', 'date')}</Field>
      <Field label={t('form.placeOfBirth')}>{input('placeOfBirth')}</Field>
      <Field label={t('form.occupation')}>{input('occupation')}</Field>
      <Field label={t('form.currentAddress')}>{input('currentAddress')}</Field>
      <Field label={t('form.phone')}>{input('phone', 'tel')}</Field>
      <Field label={t('form.email')}>{input('email', 'email')}</Field>
      <Field label={t('form.education')}>{input('education')}</Field>
    </div>
    <Field label={t('form.avatarUrl')} hint={t('form.avatarHint')}>{input('avatarUrl', 'url')}</Field>
    <Field label={t('form.biography')}><textarea className="min-h-24 rounded-lg border border-input bg-background px-3 py-2 text-sm" value={form.biography ?? ''} onChange={(e) => change('biography', e.target.value)} /></Field>
    <div className="grid gap-4 sm:grid-cols-2"><Field label={t('form.achievements')}><textarea className="min-h-20 rounded-lg border border-input bg-background px-3 py-2 text-sm" value={form.achievements ?? ''} onChange={(e) => change('achievements', e.target.value)} /></Field><Field label={t('form.notes')}><textarea className="min-h-20 rounded-lg border border-input bg-background px-3 py-2 text-sm" value={form.notes ?? ''} onChange={(e) => change('notes', e.target.value)} /></Field></div>
    <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={Boolean(form.isAlive)} onChange={(e) => change('isAlive', e.target.checked)} disabled={Boolean(form.dateOfDeath)} className="size-4 accent-primary" />{t('form.isAlive')}</label>
    <div className="flex justify-end gap-2"><Button type="button" variant="ghost" onClick={onCancel}>{t('actions.cancel')}</Button><Button type="submit" disabled={submitting}>{submitting && <Loader2 className="animate-spin" />}{submitting ? t('actions.saving') : t('actions.save')}</Button></div>
  </form>;
}

function RelationshipDialog({ treeId, member, members, onCreated, onClose }: { treeId: string; member: Member; members: Member[]; onCreated: (relationship: Relationship) => void; onClose: () => void }) {
  const t = useTranslations('membersPage'); const locale = useLocale();
  const relationshipMutation = useRelationshipMutation(treeId);
  const [draft, setDraft] = useState<RelationshipDraft>({ sourceMemberId: member.id, targetMemberId: members.find((item) => item.id !== member.id)?.id ?? '', type: 'PARENT_CHILD', direction: 'SOURCE' });
  const [errors, setErrors] = useState<string[]>([]); const [saving, setSaving] = useState(false);
  const update = (key: keyof RelationshipDraft, value: string) => setDraft((current) => ({ ...current, [key]: value }));
  const validate = async () => { setSaving(true); setErrors([]); try { const { direction, ...fields } = draft; const oriented = direction === 'TARGET' ? { ...fields, sourceMemberId: fields.targetMemberId, targetMemberId: fields.sourceMemberId } : fields; const payload = { ...oriented, ...(oriented.marriageDate ? { marriageDate: new Date(`${oriented.marriageDate}T00:00:00.000Z`).toISOString() } : {}), ...(oriented.divorceDate ? { divorceDate: new Date(`${oriented.divorceDate}T00:00:00.000Z`).toISOString() } : {}) } as CreateRelationshipInput; const result = await request<{ valid: boolean; errors: string[] }>('/api/relationships/validate', { method: 'POST', body: JSON.stringify({ treeId, data: payload }) }); if (!result.valid) { setErrors(result.errors); return; } const created = await relationshipMutation.mutateAsync({ operation: 'create', data: payload }); onCreated(created as Relationship); onClose(); } catch (error) { setErrors([error instanceof Error ? error.message : t('errors.generic')]); } finally { setSaving(false); } };
  return <Dialog open onOpenChange={(open) => !open && onClose()}><DialogContent className="max-w-lg"><DialogHeader><DialogTitle>{t('relationship.title')}</DialogTitle><DialogDescription>{t('relationship.description', { name: member.fullName })}</DialogDescription></DialogHeader>
    <div className="grid gap-4"><Field label={t('relationship.type')}><select className="h-10 rounded-lg border border-input bg-background px-3 text-sm" value={draft.type} onChange={(e) => update('type', e.target.value)}><option value="PARENT_CHILD">{t('relationship.parentChild')}</option><option value="SPOUSE">{t('relationship.spouse')}</option><option value="SIBLING">{t('relationship.sibling')}</option><option value="ADOPTED">{t('relationship.adopted')}</option><option value="CUSTOM">{t('relationship.custom')}</option></select></Field><Field label={t('relationship.otherMember')}><select className="h-10 rounded-lg border border-input bg-background px-3 text-sm" value={draft.targetMemberId} onChange={(e) => update('targetMemberId', e.target.value)}>{members.filter((item) => item.id !== member.id).map((item) => <option key={item.id} value={item.id}>{item.fullName}</option>)}</select></Field>{(draft.type === 'PARENT_CHILD' || draft.type === 'ADOPTED') && <Field label={locale === 'vi' ? 'Vai trò của thành viên hiện tại' : "Current member's role"}><select className="h-10 rounded-lg border border-input bg-background px-3 text-sm" value={draft.direction} onChange={(e) => update('direction', e.target.value)}><option value="SOURCE">{locale === 'vi' ? 'Là cha / mẹ' : 'Is the parent'}</option><option value="TARGET">{locale === 'vi' ? 'Là con' : 'Is the child'}</option></select></Field>}{draft.type === 'CUSTOM' && <Field label={t('relationship.customType')}><Input value={draft.customType ?? ''} onChange={(e) => update('customType', e.target.value)} /></Field>}{draft.type === 'SPOUSE' && <div className="grid gap-4 sm:grid-cols-2"><Field label={t('relationship.marriageDate')}><Input type="date" value={draft.marriageDate ?? ''} onChange={(e) => update('marriageDate', e.target.value)} /></Field><Field label={locale === 'vi' ? 'Ngày ly hôn' : 'Divorce date'}><Input type="date" value={draft.divorceDate ?? ''} onChange={(e) => update('divorceDate', e.target.value)} /></Field><Field label={t('relationship.status')}><select className="h-10 rounded-lg border border-input bg-background px-3 text-sm" value={draft.marriageStatus ?? 'MARRIED'} onChange={(e) => update('marriageStatus', e.target.value)}><option value="MARRIED">{t('relationship.married')}</option><option value="DIVORCED">{t('relationship.divorced')}</option><option value="WIDOWED">{t('relationship.widowed')}</option></select></Field></div>}{errors.length > 0 && <div role="alert" className="rounded-xl border border-destructive/20 bg-destructive/5 p-3 text-sm text-destructive"><p className="font-semibold">{t('relationship.validation')}</p><ul className="mt-1 list-disc pl-5">{errors.map((error) => <li key={error}>{error}</li>)}</ul></div>}</div>
    <DialogFooter><Button variant="ghost" onClick={onClose}>{t('actions.cancel')}</Button><Button onClick={() => void validate()} disabled={saving || !draft.targetMemberId}>{saving && <Loader2 className="animate-spin" />}{t('relationship.create')}</Button></DialogFooter>
  </DialogContent></Dialog>;
}

function DeleteMemberDialog({ treeId, member, onDeleted, onClose }: { treeId: string; member: Member; onDeleted: () => void; onClose: () => void }) {
  const t = useTranslations('membersPage'); const mutation = useMemberMutation(treeId); const [preview, setPreview] = useState<{ affectedRelationships?: Relationship[]; affectedEvents?: Event[]; affectedMedia?: unknown[] }>(); const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  useEffect(() => { void request<typeof preview>(`/api/members/${member.id}?treeId=${encodeURIComponent(treeId)}&preview=true`).then(setPreview).catch((e) => setError(e instanceof Error ? e.message : t('errors.generic'))).finally(() => setLoading(false)); }, [member.id, treeId, t]);
  const confirm = async () => { setError(''); try { await mutation.mutateAsync({ operation: 'delete', memberId: member.id }); onDeleted(); onClose(); } catch (e) { setError(e instanceof Error ? e.message : t('errors.generic')); } };
  return <Dialog open onOpenChange={(open) => !open && onClose()}><DialogContent><DialogHeader><DialogTitle className="flex items-center gap-2 text-destructive"><Trash2 className="size-5" />{t('delete.title')}</DialogTitle><DialogDescription>{t('delete.description', { name: member.fullName })}</DialogDescription></DialogHeader>{loading ? <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground"><Loader2 className="animate-spin" />{t('actions.loading')}</div> : <div className="space-y-3 text-sm"><p>{t('delete.impact')}</p><ul className="grid gap-2 rounded-xl bg-muted/50 p-3 text-muted-foreground"><li>{t('delete.relationships', { count: preview?.affectedRelationships?.length ?? 0 })}</li><li>{t('delete.events', { count: preview?.affectedEvents?.length ?? 0 })}</li><li>{t('delete.media', { count: preview?.affectedMedia?.length ?? 0 })}</li></ul>{error && <p role="alert" className="text-destructive">{error}</p>}</div>}<DialogFooter><Button variant="ghost" onClick={onClose}>{t('actions.cancel')}</Button><Button variant="destructive" onClick={() => void confirm()} disabled={loading || mutation.isPending}>{mutation.isPending && <Loader2 className="animate-spin" />}{t('delete.confirm')}</Button></DialogFooter></DialogContent></Dialog>;
}

export function MemberListPage({ treeId }: { treeId: string }) {
  const t = useTranslations('membersPage'); const locale = useLocale(); const router = useRouter(); const { toast } = useToast();
  const membersQuery = useMembersQuery(treeId); const memberMutation = useMemberMutation(treeId); const members = membersQuery.data ?? []; const loading = membersQuery.isLoading; const error = membersQuery.error?.message ?? '';
  const [query, setQuery] = useState(''); const [status, setStatus] = useState<'ALL' | 'ALIVE' | 'DECEASED'>('ALL'); const [gender, setGender] = useState<'ALL' | Gender>('ALL'); const [formOpen, setFormOpen] = useState(false); const saving = memberMutation.isPending;
  const visible = useMemo(() => members.filter((member) => { const needle = query.trim().toLocaleLowerCase(locale); return (!needle || [member.fullName, member.nickname, member.occupation, member.placeOfBirth].filter(Boolean).some((field) => field!.toLocaleLowerCase(locale).includes(needle))) && (status === 'ALL' || (status === 'ALIVE' ? member.isAlive : !member.isAlive)) && (gender === 'ALL' || member.gender === gender); }), [members, query, status, gender, locale]);
  const create = (form: MemberFormValue) => memberMutation.mutate({ operation: 'create', data: toMemberPayload(form) }, { onSuccess: () => { setFormOpen(false); toast({ title: t('toasts.created'), tone: 'success' }); } });
  return <div className="space-y-7"><section className="flex flex-wrap items-end justify-between gap-4"><div><p className="text-sm font-semibold uppercase tracking-[.16em] text-primary">{t('eyebrow')}</p><h1 className="mt-2 font-display text-4xl font-medium tracking-[-.035em]">{t('title')}</h1><p className="mt-2 max-w-2xl text-muted-foreground">{t('description')}</p></div><Button size="lg" onClick={() => setFormOpen(true)}><Plus />{t('actions.add')}</Button></section><Card><CardContent className="grid gap-3 p-4 lg:grid-cols-[1fr_auto_auto]"><label className="relative block"><Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" /><Input className="pl-9" value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t('search')} /></label><select className="h-10 rounded-lg border border-input bg-background px-3 text-sm" value={status} onChange={(e) => setStatus(e.target.value as typeof status)}><option value="ALL">{t('filters.allStatus')}</option><option value="ALIVE">{t('filters.alive')}</option><option value="DECEASED">{t('filters.deceased')}</option></select><select className="h-10 rounded-lg border border-input bg-background px-3 text-sm" value={gender} onChange={(e) => setGender(e.target.value as typeof gender)}><option value="ALL">{t('filters.allGender')}</option><option value="MALE">{t('gender.male')}</option><option value="FEMALE">{t('gender.female')}</option><option value="OTHER">{t('gender.other')}</option></select></CardContent></Card>{error && <Card className="border-destructive/30 bg-destructive/5"><CardContent className="flex items-center gap-3 p-5 text-sm text-destructive"><AlertTriangle className="size-5" />{error}<Button variant="outline" size="sm" className="ml-auto" onClick={() => void membersQuery.refetch()}>{t('actions.retry')}</Button></CardContent></Card>}{loading ? <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{[1, 2, 3].map((item) => <div key={item} className="h-44 animate-pulse rounded-2xl bg-muted" />)}</div> : visible.length === 0 ? <Card><CardContent className="grid min-h-56 place-items-center p-8 text-center"><UsersRound className="size-10 text-primary/35" /><h2 className="mt-3 font-display text-xl">{query ? t('empty.searchTitle') : t('empty.title')}</h2><p className="mt-1 max-w-md text-sm text-muted-foreground">{query ? t('empty.searchDescription') : t('empty.description')}</p></CardContent></Card> : <><div className="flex items-center justify-between text-sm text-muted-foreground"><span>{t('count', { count: visible.length })}</span><span>{t('updatedHint')}</span></div><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">{visible.map((member) => <MemberCard key={member.id} member={member} locale={locale} onOpen={() => router.push(`/trees/${treeId}/members/${member.id}`)} t={t} />)}</div></>}{formOpen && <Dialog open onOpenChange={(open) => !open && setFormOpen(false)}><DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto"><DialogHeader><DialogTitle>{t('createTitle')}</DialogTitle><DialogDescription>{t('description')}</DialogDescription></DialogHeader><MemberForm initial={emptyMember()} onSubmit={create} onCancel={() => setFormOpen(false)} submitting={saving} /></DialogContent></Dialog>}</div>;
}

function MemberCard({ member, locale, onOpen, t }: { member: Member; locale: string; onOpen: () => void; t: ReturnType<typeof useTranslations<'membersPage'>> }) {
  return <Card className="group overflow-hidden transition-[transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:shadow-md"><button className="block w-full text-left" onClick={onOpen}><CardContent className="p-5"><div className="flex items-start gap-3"><Avatar member={member} /><div className="min-w-0 flex-1"><div className="flex items-start justify-between gap-2"><h2 className="truncate font-semibold">{member.fullName}</h2><ChevronRight className="size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5" /></div><p className="mt-1 text-sm text-muted-foreground">{years(member) || t('unknownYear')}</p></div></div><div className="mt-5 flex flex-wrap gap-2 text-xs"><span className={`rounded-full px-2.5 py-1 ${member.isAlive ? 'bg-primary/10 text-primary' : 'bg-muted text-muted-foreground'}`}>{member.isAlive ? t('status.alive') : t('status.deceased')}</span>{member.generation !== undefined && <span className="rounded-full bg-accent px-2.5 py-1 text-accent-foreground">{t('generation', { generation: member.generation })}</span>}</div><div className="mt-4 grid gap-2 text-sm text-muted-foreground">{member.occupation && <span className="truncate">{member.occupation}</span>}{member.placeOfBirth && <span className="flex items-center gap-1.5 truncate"><MapPin className="size-3.5" />{member.placeOfBirth}</span>}{!member.occupation && !member.placeOfBirth && <span>{t('noDetails')}</span>}</div><p className="mt-4 text-xs text-muted-foreground">{t('updated', { date: formatDate(member.updatedAt, locale) })}</p></CardContent></button></Card>;
}

export function MemberDetailPage({ treeId, memberId }: { treeId: string; memberId: string }) {
  const t = useTranslations('membersPage'); const mt = useTranslations('mediaPage'); const locale = useLocale(); const router = useRouter(); const { toast } = useToast();
  const memberMutation = useMemberMutation(treeId);
  const [detail, setDetail] = useState<Member & { relationships: Relationship[]; relatedMembers: Member[]; events: Event[]; media: MediaMetadata[]; lifespan: number | null }>(); const [members, setMembers] = useState<Member[]>([]); const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [editOpen, setEditOpen] = useState(false); const [relationOpen, setRelationOpen] = useState(false); const [deleteOpen, setDeleteOpen] = useState(false); const saving = memberMutation.isPending;
  const load = useCallback(async () => { setLoading(true); try { const [member, all] = await Promise.all([request<typeof detail>(`/api/members/${memberId}?treeId=${encodeURIComponent(treeId)}`), request<Member[]>(`/api/trees/${treeId}/members`)]); setDetail(member); setMembers(all); setError(''); } catch (e) { setError(e instanceof Error ? e.message : t('errors.generic')); } finally { setLoading(false); } }, [memberId, treeId, t]);
  useEffect(() => { void load(); }, [load]);
  if (loading) return <div className="grid min-h-80 place-items-center text-muted-foreground"><Loader2 className="size-6 animate-spin" /></div>;
  if (error || !detail) return <Card><CardContent className="grid min-h-56 place-items-center p-8 text-center"><AlertTriangle className="size-8 text-destructive" /><p className="mt-3 text-sm text-destructive">{error || t('errors.notFound')}</p><Button className="mt-4" variant="outline" onClick={() => router.push(`/trees/${treeId}/members`)}>{t('actions.back')}</Button></CardContent></Card>;
  const update = async (form: MemberFormValue) => { const previous = detail; const next = { ...detail, ...toMemberPayload(form), fullName: `${form.firstName} ${form.lastName}` } as typeof detail; setDetail(next); try { const saved = await memberMutation.mutateAsync({ operation: 'update', memberId, data: toMemberPayload(form) }); setDetail((current) => current ? { ...current, ...(saved as Member) } : current); setEditOpen(false); toast({ title: t('toasts.updated'), tone: 'success' }); } catch { setDetail(previous); } };
  const removeRelationship = async (relationship: Relationship) => { try { await request(`/api/relationships/${relationship.id}?treeId=${encodeURIComponent(treeId)}`, { method: 'DELETE' }); setDetail((current) => current ? { ...current, relationships: current.relationships.filter((item) => item.id !== relationship.id), relatedMembers: current.relatedMembers } : current); toast({ title: t('relationship.deleted'), tone: 'success' }); } catch (e) { toast({ title: t('toasts.failed'), description: e instanceof Error ? e.message : t('errors.generic'), tone: 'destructive' }); } };
  const nameById = new Map(members.map((item) => [item.id, item]));
  const relationships = detail.relationships.filter((relation, index, all) => index === all.findIndex((candidate) => candidate.type === relation.type && (candidate.customType ?? '') === (relation.customType ?? '') && ((candidate.sourceMemberId === relation.sourceMemberId && candidate.targetMemberId === relation.targetMemberId) || (candidate.sourceMemberId === relation.targetMemberId && candidate.targetMemberId === relation.sourceMemberId))));
  return <div className="space-y-6"><div className="flex flex-wrap items-center justify-between gap-3"><Button variant="ghost" onClick={() => router.push(`/trees/${treeId}/members`)}><ArrowLeft />{t('actions.back')}</Button><div className="flex gap-2"><Button variant="outline" onClick={() => setEditOpen(true)}><Edit3 />{t('actions.edit')}</Button><Button variant="outline" className="text-destructive hover:text-destructive" onClick={() => setDeleteOpen(true)}><Trash2 />{t('actions.delete')}</Button></div></div><Card className="overflow-hidden"><div className="h-28 bg-gradient-to-r from-primary/20 via-accent to-primary/5" /><CardContent className="relative p-5 sm:p-7"><div className="-mt-16 flex flex-wrap items-end justify-between gap-4"><div className="flex items-end gap-4"><Avatar member={detail} size="lg" /><div className="pb-1"><div className="flex flex-wrap items-center gap-2"><h1 className="font-display text-3xl font-medium tracking-[-.03em]">{detail.fullName}</h1><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${detail.isAlive ? 'bg-primary/10 text-primary' : 'bg-muted text-muted-foreground'}`}>{detail.isAlive ? t('status.alive') : t('status.deceased')}</span></div><p className="mt-1 text-sm text-muted-foreground">{years(detail) || t('unknownYear')}{detail.generation !== undefined && ` · ${t('generation', { generation: detail.generation })}`}</p></div></div></div><div className="mt-7 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{[[CalendarDays, t('detail.birth'), formatDate(detail.dateOfBirth, locale)], [MapPin, t('detail.birthPlace'), detail.placeOfBirth || '—'], [UserRound, t('detail.occupation'), detail.occupation || '—'], [Check, t('detail.lifespan'), detail.lifespan ? `${detail.lifespan} ${t('detail.years')}` : '—']].map(([Icon, label, value]) => { const Component = Icon as typeof CalendarDays; return <div key={String(label)} className="rounded-xl bg-muted/45 p-3"><Component className="size-4 text-primary" /><p className="mt-2 text-xs text-muted-foreground">{String(label)}</p><p className="mt-1 truncate text-sm font-semibold">{String(value)}</p></div>; })}</div></CardContent></Card><div className="grid gap-5 lg:grid-cols-[1.2fr_.8fr]"><Card><CardHeader><CardTitle>{t('detail.story')}</CardTitle><CardDescription>{t('detail.storyHint')}</CardDescription></CardHeader><CardContent className="space-y-5 text-sm leading-7"><p className="whitespace-pre-wrap">{detail.biography || t('detail.noStory')}</p>{(detail.achievements || detail.education || detail.currentAddress || detail.phone || detail.email) && <div className="grid gap-3 border-t border-border/70 pt-5 sm:grid-cols-2">{detail.education && <Info label={t('form.education')} value={detail.education} />}{detail.currentAddress && <Info label={t('form.currentAddress')} value={detail.currentAddress} />}{detail.phone && <Info label={t('form.phone')} value={detail.phone} />}{detail.email && <Info label={t('form.email')} value={detail.email} />}{detail.achievements && <Info label={t('form.achievements')} value={detail.achievements} />}</div>}</CardContent></Card><Card><CardHeader className="flex-row items-center justify-between"><div><CardTitle>{t('relationship.title')}</CardTitle><CardDescription>{t('relationship.count', { count: relationships.length })}</CardDescription></div><Button size="sm" onClick={() => setRelationOpen(true)}><Plus />{t('relationship.add')}</Button></CardHeader><CardContent>{relationships.length === 0 ? <p className="rounded-xl bg-muted/40 p-4 text-sm text-muted-foreground">{t('relationship.empty')}</p> : <ul className="grid gap-2">{relationships.map((relation) => { const otherId = relation.sourceMemberId === memberId ? relation.targetMemberId : relation.sourceMemberId; const other = nameById.get(otherId); return <li key={relation.id} className="flex items-center gap-2 rounded-xl border border-border/70 p-3"><UsersRound className="size-4 shrink-0 text-primary" /><Link href={`/trees/${treeId}/members/${otherId}`} className="min-w-0 flex-1 truncate text-sm font-semibold hover:text-primary">{other?.fullName ?? otherId}</Link><span className="hidden rounded-full bg-accent px-2 py-1 text-[11px] sm:inline">{relation.type === 'CUSTOM' ? relation.customType : t(`relationship.types.${relation.type}`)}</span><button aria-label={t('relationship.remove')} className="rounded p-1 text-muted-foreground hover:bg-destructive/10 hover:text-destructive" onClick={() => void removeRelationship(relation)}><X className="size-4" /></button></li>; })}</ul>}</CardContent></Card></div><Card><CardHeader><CardTitle>{mt('title')}</CardTitle><CardDescription>{mt('description')}</CardDescription></CardHeader><CardContent>{detail.media.length === 0 ? <p className="text-sm text-muted-foreground">{mt('empty.description')}</p> : <div className="grid grid-cols-3 gap-2 sm:grid-cols-5">{detail.media.map((item) => <a key={item.id} href={item.contentUrl ?? item.blobUrl} target="_blank" rel="noreferrer" className="aspect-square overflow-hidden rounded-xl bg-muted">{item.thumbnailContentUrl ?? item.thumbnailUrl ? <img src={item.thumbnailContentUrl ?? item.thumbnailUrl} alt={item.caption || item.originalName} className="size-full object-cover" /> : <span className="grid size-full place-items-center"><ImageIcon className="size-5 text-primary" /></span>}</a>)}</div>}</CardContent></Card>{editOpen && <Dialog open onOpenChange={(open) => !open && setEditOpen(false)}><DialogContent className="max-h-[90vh] overflow-y-auto"><DialogHeader><DialogTitle>{t('editTitle')}</DialogTitle><DialogDescription>{t('editDescription')}</DialogDescription></DialogHeader><MemberForm initial={detail} onSubmit={(value) => void update(value)} onCancel={() => setEditOpen(false)} submitting={saving} /></DialogContent></Dialog>}{relationOpen && <RelationshipDialog treeId={treeId} member={detail} members={members} onCreated={(relation) => setDetail((current) => current ? { ...current, relationships: [...current.relationships, relation] } : current)} onClose={() => setRelationOpen(false)} />}{deleteOpen && <DeleteMemberDialog treeId={treeId} member={detail} onDeleted={() => router.push(`/trees/${treeId}/members`)} onClose={() => setDeleteOpen(false)} />}</div>;
}

function Info({ label, value }: { label: string; value: string }) { return <div><p className="text-xs text-muted-foreground">{label}</p><p className="mt-1 whitespace-pre-wrap text-sm">{value}</p></div>; }
