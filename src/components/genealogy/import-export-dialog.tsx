'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import {
  AlertCircle,
  Check,
  Download,
  FileArchive,
  FileJson,
  FileSpreadsheet,
  FileText,
  ImageDown,
  Loader2,
  Upload,
  X
} from 'lucide-react';
import type { ImportFormat, ImportIssue, ImportOptions, ImportPreview, ImportResult, PaperSize, PrintOptions, PrintPreview } from '@/types/import-export';
import { queryKeys } from '@/lib/query/keys';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useToast } from '@/components/ui/toast';

type Workspace = 'import' | 'export' | null;
type ExportFormat = 'GEDCOM' | 'JSON' | 'PDF' | 'PNG' | 'SVG';

const importFormats: Array<{ value: ImportFormat; icon: typeof FileText; extensions: string }> = [
  { value: 'GEDCOM', icon: FileArchive, extensions: '.ged, .gedcom' },
  { value: 'JSON', icon: FileJson, extensions: '.json' },
  { value: 'CSV', icon: FileSpreadsheet, extensions: '.csv' }
];

const exportFormats: Array<{ value: ExportFormat; icon: typeof FileText }> = [
  { value: 'GEDCOM', icon: FileArchive },
  { value: 'JSON', icon: FileJson },
  { value: 'PDF', icon: FileText },
  { value: 'PNG', icon: ImageDown },
  { value: 'SVG', icon: ImageDown }
];

const defaultPrintOptions: PrintOptions = {
  paperSize: 'A4', orientation: 'LANDSCAPE', font: 'HELVETICA', colorScheme: 'CLASSIC', dpi: 300,
  display: { showDates: true, showGender: true, showLocations: false, showMemberIds: false }
};

interface ImportExportActionsProps {
  treeId: string;
}

export function ImportExportActions({ treeId }: ImportExportActionsProps) {
  const t = useTranslations('importExport');
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [workspace, setWorkspace] = useState<Workspace>(null);
  const [importFormat, setImportFormat] = useState<ImportFormat>('GEDCOM');
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [importIssues, setImportIssues] = useState<ImportIssue[]>([]);
  const [importError, setImportError] = useState('');
  const [importBusy, setImportBusy] = useState(false);
  const [importMode, setImportMode] = useState<NonNullable<ImportOptions['mode']>>('APPEND');
  const [conflictStrategy, setConflictStrategy] = useState<NonNullable<ImportOptions['conflictStrategy']>>('REGENERATE');
  const [exportFormat, setExportFormat] = useState<ExportFormat>('PDF');
  const [printOptions, setPrintOptions] = useState<PrintOptions>(defaultPrintOptions);
  const [printPreview, setPrintPreview] = useState<PrintPreview | null>(null);
  const [previewBusy, setPreviewBusy] = useState(false);
  const [exportBusy, setExportBusy] = useState(false);
  const [exportError, setExportError] = useState('');
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const importRequestRef = useRef(0);
  const previewRequestRef = useRef(0);

  const openImport = () => {
    setWorkspace('import'); setFile(null); setPreview(null); setImportIssues([]); setImportError(''); setDragging(false); if (inputRef.current) inputRef.current.value = '';
  };
  const openExport = () => {
    setWorkspace('export'); setExportError(''); setPrintPreview(null);
  };
  const closeWorkspace = (open: boolean) => { if (!open && !importBusy && !exportBusy) setWorkspace(null); };

  const parsePreview = useCallback(async (candidate: File) => {
    const requestId = ++importRequestRef.current;
    setImportBusy(true); setImportError(''); setImportIssues([]); setPreview(null);
    const form = new FormData(); form.append('file', candidate); form.append('format', importFormat);
    try {
      const response = await fetch('/api/import/preview', { method: 'POST', body: form, headers: { Accept: 'application/json' } });
      const body = await response.json().catch(() => null) as ImportPreview | { error?: { message?: string; details?: { issues?: ImportIssue[] } } } | null;
      if (requestId !== importRequestRef.current) return;
      if (!response.ok) {
        const error = body && 'error' in body ? body.error : undefined;
        setImportError(error?.message ?? t('errors.preview'));
        setImportIssues(error?.details?.issues ?? []);
        return;
      }
      if (!body || !('format' in body)) { setImportError(t('errors.preview')); return; }
      setPreview(body as ImportPreview);
      setImportIssues((body as ImportPreview).issues ?? []);
    } catch (error) {
      if (requestId !== importRequestRef.current) return;
      setImportError(error instanceof Error ? error.message : t('errors.preview'));
    } finally { if (requestId === importRequestRef.current) setImportBusy(false); }
  }, [importFormat, t]);

  const chooseFile = (candidate: File | undefined) => {
    if (!candidate) return;
    const extension = candidate.name.toLowerCase().split('.').pop();
    if (!['ged', 'gedcom', 'json', 'csv'].includes(extension ?? '')) {
      setFile(null); setPreview(null); setImportIssues([]); setImportError(t('errors.unsupported')); return;
    }
    if (candidate.size > 25 * 1024 * 1024) {
      setFile(null); setPreview(null); setImportIssues([]); setImportError(t('errors.tooLarge')); return;
    }
    const detected: ImportFormat = extension === 'csv' ? 'CSV' : extension === 'json' ? 'JSON' : 'GEDCOM';
    if (['ged', 'gedcom', 'json', 'csv'].includes(extension ?? '')) setImportFormat(detected);
    setFile(candidate); setPreview(null); setImportIssues([]); setImportError('');
  };

  useEffect(() => { if (file) void parsePreview(file); }, [file, parsePreview]);

  const executeImport = async () => {
    if (!file || !preview?.valid) return;
    setImportBusy(true); setImportError('');
    const form = new FormData(); form.append('treeId', treeId); form.append('file', file); form.append('format', importFormat); form.append('mode', importMode); form.append('conflictStrategy', conflictStrategy);
    try {
      const response = await fetch('/api/import/execute', { method: 'POST', body: form, headers: { Accept: 'application/json' } });
      const body = await response.json().catch(() => null) as ImportResult | { error?: { message?: string; details?: { issues?: ImportIssue[] } } } | null;
      if (!response.ok) {
        const error = body && 'error' in body ? body.error : undefined;
        setImportError(error?.message ?? t('errors.execute')); setImportIssues(error?.details?.issues ?? []); return;
      }
      await queryClient.invalidateQueries({ queryKey: queryKeys.tree(treeId) });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.members(treeId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.relationships(treeId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.events(treeId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.media(treeId) })
      ]);
      const result = body as ImportResult;
      toast({ title: t('import.success'), description: t('import.successDescription', { members: result.imported.members, skipped: result.skipped }), tone: 'success' });
      setWorkspace(null);
    } catch (error) { setImportError(error instanceof Error ? error.message : t('errors.execute')); }
    finally { setImportBusy(false); }
  };

  const queryString = useMemo(() => {
    const params = new URLSearchParams();
    const values = printOptions as Record<string, unknown>;
    if (values.paperSize) params.set('paperSize', String(values.paperSize));
    if (values.orientation) params.set('orientation', String(values.orientation));
    if (values.font) params.set('font', String(values.font));
    if (values.colorScheme) params.set('colorScheme', String(values.colorScheme));
    if (values.dpi) params.set('dpi', String(values.dpi));
    Object.entries(printOptions.display ?? {}).forEach(([key, value]) => params.set(key, String(value)));
    return params.toString();
  }, [printOptions]);

  const loadPrintPreview = useCallback(async () => {
    const requestId = ++previewRequestRef.current;
    setPreviewBusy(true); setExportError('');
    try {
      const response = await fetch(`/api/export/${encodeURIComponent(treeId)}/preview?${queryString}`, { headers: { Accept: 'application/json' } });
      const body = await response.json().catch(() => null) as PrintPreview | { error?: { message?: string } } | null;
      if (requestId !== previewRequestRef.current) return;
      if (!response.ok) { setExportError(body && 'error' in body ? body.error?.message ?? t('errors.preview') : t('errors.preview')); return; }
      if (!body || !('svg' in body)) { setExportError(t('errors.preview')); return; }
      setPrintPreview(body as PrintPreview);
    } catch (error) { if (requestId === previewRequestRef.current) setExportError(error instanceof Error ? error.message : t('errors.preview')); }
    finally { if (requestId === previewRequestRef.current) setPreviewBusy(false); }
  }, [queryString, t, treeId]);

  useEffect(() => {
    if (workspace !== 'export') return;
    const timeout = window.setTimeout(() => void loadPrintPreview(), 220);
    return () => window.clearTimeout(timeout);
  }, [loadPrintPreview, workspace]);

  const downloadExport = async () => {
    setExportBusy(true); setExportError('');
    try {
      const response = await fetch(`/api/export/${encodeURIComponent(treeId)}/${exportFormat.toLowerCase()}?${queryString}`, { headers: { Accept: '*/*' } });
      if (!response.ok) {
        const body = await response.json().catch(() => null) as { error?: { message?: string } } | null;
        throw new Error(body?.error?.message ?? t('errors.export'));
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = `${treeId}.${exportFormat === 'GEDCOM' ? 'ged' : exportFormat.toLowerCase()}`; document.body.appendChild(anchor); anchor.click(); anchor.remove(); URL.revokeObjectURL(url);
      toast({ title: t('export.success'), description: t('export.downloaded', { format: exportFormat }), tone: 'success' });
    } catch (error) { setExportError(error instanceof Error ? error.message : t('errors.export')); }
    finally { setExportBusy(false); }
  };

  const updatePrint = (patch: Partial<PrintOptions>) => setPrintOptions((current) => ({ ...current, ...patch }));
  const updateDisplay = (key: keyof NonNullable<PrintOptions['display']>) => setPrintOptions((current) => ({ ...current, display: { ...current.display, [key]: !current.display?.[key] } }));

  return <>
    <div className="flex flex-wrap items-center justify-end gap-2">
      <Button variant="outline" size="sm" onClick={openImport}><Upload />{t('actions.import')}</Button>
      <Button variant="default" size="sm" onClick={openExport}><Download />{t('actions.export')}</Button>
    </div>

    <Dialog open={workspace === 'import'} onOpenChange={closeWorkspace}>
      <DialogContent className="max-h-[min(90vh,800px)] max-w-3xl overflow-y-auto">
        <DialogHeader><DialogTitle className="flex items-center gap-2"><Upload className="size-5 text-primary" />{t('import.title')}</DialogTitle><DialogDescription>{t('import.description')}</DialogDescription></DialogHeader>
        <div className="grid gap-5">
          <div className="grid gap-2 sm:grid-cols-3" role="radiogroup" aria-label={t('import.formatLabel')}>
            {importFormats.map(({ value, icon: Icon, extensions }) => <button key={value} type="button" role="radio" aria-checked={importFormat === value} className={`rounded-xl border p-3 text-left transition-colors ${importFormat === value ? 'border-primary bg-primary/5 ring-1 ring-primary' : 'border-border hover:bg-accent'}`} onClick={() => { setImportFormat(value); setPreview(null); }}>{<Icon className="mb-2 size-5 text-primary" />}<span className="block text-sm font-semibold">{value}</span><span className="text-xs text-muted-foreground">{extensions}</span></button>)}
          </div>
          <input ref={inputRef} className="sr-only" type="file" accept=".ged,.gedcom,.json,.csv,application/json,text/csv,text/plain" onChange={(event) => chooseFile(event.target.files?.[0])} />
          <button type="button" className={`grid min-h-32 place-items-center rounded-2xl border-2 border-dashed p-5 text-center transition-colors ${dragging ? 'border-primary bg-primary/5' : 'border-border hover:border-primary/60 hover:bg-accent/40'}`} onClick={() => inputRef.current?.click()} onDragOver={(event) => { event.preventDefault(); setDragging(true); }} onDragLeave={() => setDragging(false)} onDrop={(event) => { event.preventDefault(); setDragging(false); chooseFile(event.dataTransfer.files?.[0]); }}>
            {file ? <span className="flex items-center gap-3 text-sm"><FileText className="size-7 text-primary" /><span className="text-left"><strong className="block">{file.name}</strong><span className="text-xs text-muted-foreground">{formatBytes(file.size)} · {t('import.replaceFile')}</span></span><X className="size-4 text-muted-foreground" /></span> : <span><Upload className="mx-auto mb-2 size-7 text-primary" /><strong className="block text-sm">{t('import.drop')}</strong><span className="text-xs text-muted-foreground">{t('import.maxSize')}</span></span>}
          </button>
          {importBusy && <ProgressMessage label={preview ? t('import.importing') : t('import.analyzing')} />}
          {importError && <ErrorBox message={importError} />}
          {preview && <ImportPreviewPanel preview={preview} issues={importIssues} mode={importMode} strategy={conflictStrategy} onModeChange={setImportMode} onStrategyChange={setConflictStrategy} t={t} />}
        </div>
        <DialogFooter><Button variant="ghost" onClick={() => setWorkspace(null)} disabled={importBusy}>{t('actions.cancel')}</Button><Button onClick={() => void executeImport()} disabled={!file || !preview?.valid || importBusy}>{importBusy && <Loader2 className="animate-spin" />}{t('import.confirm')}</Button></DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog open={workspace === 'export'} onOpenChange={closeWorkspace}>
      <DialogContent className="max-h-[min(92vh,900px)] max-w-5xl overflow-y-auto">
        <DialogHeader><DialogTitle className="flex items-center gap-2"><Download className="size-5 text-primary" />{t('export.title')}</DialogTitle><DialogDescription>{t('export.description')}</DialogDescription></DialogHeader>
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(280px,360px)]">
          <div className="min-w-0 space-y-4">
            <div className="grid gap-2 sm:grid-cols-2" role="radiogroup" aria-label={t('export.formatLabel')}>
              {exportFormats.map(({ value, icon: Icon }) => <button key={value} type="button" role="radio" aria-checked={exportFormat === value} className={`flex items-start gap-3 rounded-xl border p-3 text-left transition-colors ${exportFormat === value ? 'border-primary bg-primary/5 ring-1 ring-primary' : 'border-border hover:bg-accent'}`} onClick={() => setExportFormat(value)}><Icon className="mt-0.5 size-5 shrink-0 text-primary" /><span><strong className="block text-sm">{value}</strong><span className="text-xs text-muted-foreground">{t(`export.formats.${value}`)}</span></span>{exportFormat === value && <Check className="ml-auto size-4 text-primary" />}</button>)}
            </div>
            <Card className="overflow-hidden border-border/80 bg-muted/20"><CardContent className="p-3 sm:p-4"><div className="mb-3 flex items-center justify-between gap-3"><div><h3 className="text-sm font-semibold">{t('export.previewTitle')}</h3><p className="text-xs text-muted-foreground">{t('export.previewDescription')}</p></div>{previewBusy && <Loader2 className="size-4 animate-spin text-primary" />}</div>{printPreview ? <><div className="overflow-hidden rounded-xl border border-border bg-white shadow-inner"><iframe title={t('export.previewTitle')} srcDoc={printPreview.svg} sandbox="" className="h-[min(48vh,420px)] w-full" /></div><div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground"><span>{t('export.pages', { count: printPreview.pageCount })}</span><span>{printPreview.widthMm} × {printPreview.heightMm} mm</span><span>{printPreview.columns} × {printPreview.rows} {t('export.tiles')}</span></div></> : <div className="grid h-48 place-items-center text-sm text-muted-foreground">{t('export.previewLoading')}</div>}</CardContent></Card>
          </div>
          <PrintOptionsPanel options={printOptions} updatePrint={updatePrint} updateDisplay={updateDisplay} t={t} />
        </div>
        {exportError && <ErrorBox message={exportError} />}
        <DialogFooter><Button variant="ghost" onClick={() => setWorkspace(null)} disabled={exportBusy}>{t('actions.cancel')}</Button><Button variant="outline" onClick={() => void loadPrintPreview()} disabled={previewBusy}>{t('export.refreshPreview')}</Button><Button onClick={() => void downloadExport()} disabled={exportBusy}>{exportBusy && <Loader2 className="animate-spin" />}{exportBusy ? t('export.exporting') : t('export.download')}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  </>;
}

function ImportPreviewPanel({ preview, issues, mode, strategy, onModeChange, onStrategyChange, t }: { preview: ImportPreview; issues: ImportIssue[]; mode: NonNullable<ImportOptions['mode']>; strategy: NonNullable<ImportOptions['conflictStrategy']>; onModeChange: (value: NonNullable<ImportOptions['mode']>) => void; onStrategyChange: (value: NonNullable<ImportOptions['conflictStrategy']>) => void; t: ReturnType<typeof useTranslations<'importExport'>> }) {
  const errors = issues.filter((issue) => issue.severity === 'ERROR');
  return <div className="space-y-4 rounded-2xl border border-border bg-muted/20 p-4"><div className="flex flex-wrap items-start justify-between gap-3"><div><div className="flex items-center gap-2 text-sm font-semibold">{preview.valid ? <Check className="size-4 text-emerald-600" /> : <AlertCircle className="size-4 text-destructive" />}{preview.valid ? t('import.valid') : t('import.invalid')}</div><p className="mt-1 text-xs text-muted-foreground">{t('import.detected', { format: preview.format })}</p></div><div className="flex flex-wrap gap-2 text-xs"><Count label={t('counts.members')} value={preview.counts.members} /><Count label={t('counts.relationships')} value={preview.counts.relationships} /><Count label={t('counts.events')} value={preview.counts.events} /><Count label={t('counts.media')} value={preview.counts.media} /></div></div>{preview.sampleMembers.length > 0 && <div className="overflow-x-auto rounded-xl border border-border bg-background"><table className="w-full min-w-[420px] text-left text-xs"><thead className="border-b border-border bg-muted/40 text-muted-foreground"><tr><th className="px-3 py-2">{t('import.previewName')}</th><th className="px-3 py-2">{t('import.previewGender')}</th><th className="px-3 py-2">{t('import.previewBirth')}</th></tr></thead><tbody>{preview.sampleMembers.map((member) => <tr key={member.id} className="border-b border-border last:border-0"><td className="px-3 py-2 font-medium">{member.fullName}</td><td className="px-3 py-2">{member.gender}</td><td className="px-3 py-2 text-muted-foreground">{member.dateOfBirth?.slice(0, 10) ?? '—'}</td></tr>)}</tbody></table></div>}{issues.length > 0 && <div className="space-y-2"><h4 className="text-sm font-semibold">{t('import.issues', { count: issues.length })}</h4><div className="max-h-44 overflow-y-auto rounded-xl border border-border bg-destructive/5 p-2">{issues.map((issue, index) => <div key={`${issue.line}-${issue.path}-${index}`} className={`flex gap-2 border-b px-2 py-2 text-xs last:border-0 ${issue.severity === 'WARNING' ? 'border-amber-500/10' : 'border-destructive/10'}`}><span className={`shrink-0 font-mono font-semibold ${issue.severity === 'WARNING' ? 'text-amber-700 dark:text-amber-400' : 'text-destructive'}`}>{t('import.line', { line: issue.line })}</span><span className="min-w-0 break-words text-foreground">{issue.message}{issue.path && <span className="ml-1 text-muted-foreground">({issue.path})</span>}</span></div>)}</div></div>}{errors.length === 0 && <div className="grid gap-3 border-t border-border pt-4 sm:grid-cols-2"><label className="grid gap-1.5 text-xs font-semibold"><span>{t('import.mode')}</span><Select value={mode} onValueChange={(value) => onModeChange(value as NonNullable<ImportOptions['mode']>)}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="APPEND">{t('import.modeAppend')}</SelectItem><SelectItem value="REPLACE">{t('import.modeReplace')}</SelectItem></SelectContent></Select></label><label className="grid gap-1.5 text-xs font-semibold"><span>{t('import.conflict')}</span><Select value={strategy} onValueChange={(value) => onStrategyChange(value as NonNullable<ImportOptions['conflictStrategy']>)}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="REGENERATE">{t('import.conflictRegenerate')}</SelectItem><SelectItem value="SKIP">{t('import.conflictSkip')}</SelectItem><SelectItem value="OVERWRITE">{t('import.conflictOverwrite')}</SelectItem></SelectContent></Select></label></div>}</div>;
}

function PrintOptionsPanel({ options, updatePrint, updateDisplay, t }: { options: PrintOptions; updatePrint: (patch: Partial<PrintOptions>) => void; updateDisplay: (key: keyof NonNullable<PrintOptions['display']>) => void; t: ReturnType<typeof useTranslations<'importExport'>> }) {
  return <aside className="space-y-4 rounded-2xl border border-border bg-card p-4"><h3 className="text-sm font-semibold">{t('export.customize')}</h3><div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1"><Option label={t('export.paperSize')}><Select value={options.paperSize} onValueChange={(value) => updatePrint({ paperSize: value as PaperSize })}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent>{(['A4', 'A3', 'A2', 'A1'] as const).map((value) => <SelectItem key={value} value={value}>{value}</SelectItem>)}</SelectContent></Select></Option><Option label={t('export.orientation')}><Select value={options.orientation} onValueChange={(value) => updatePrint({ orientation: value as PrintOptions['orientation'] })}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="LANDSCAPE">{t('export.landscape')}</SelectItem><SelectItem value="PORTRAIT">{t('export.portrait')}</SelectItem></SelectContent></Select></Option><Option label={t('export.font')}><Select value={options.font} onValueChange={(value) => updatePrint({ font: value as PrintOptions['font'] })}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="HELVETICA">Helvetica</SelectItem><SelectItem value="TIMES_ROMAN">Times Roman</SelectItem><SelectItem value="COURIER">Courier</SelectItem></SelectContent></Select></Option><Option label={t('export.colorScheme')}><Select value={options.colorScheme} onValueChange={(value) => updatePrint({ colorScheme: value as PrintOptions['colorScheme'] })}><SelectTrigger><SelectValue /></SelectTrigger><SelectContent><SelectItem value="CLASSIC">{t('export.classic')}</SelectItem><SelectItem value="MONOCHROME">{t('export.monochrome')}</SelectItem><SelectItem value="EARTH">{t('export.earth')}</SelectItem></SelectContent></Select></Option></div><fieldset className="grid gap-2 border-t border-border pt-4"><legend className="mb-1 text-xs font-semibold">{t('export.display')}</legend>{(['showDates', 'showGender', 'showLocations', 'showMemberIds'] as const).map((key) => <label key={key} className="flex cursor-pointer items-center gap-2 text-sm"><input type="checkbox" checked={Boolean(options.display?.[key])} onChange={() => updateDisplay(key)} className="size-4 rounded border-input accent-primary" />{t(`export.${key}`)}</label>)}</fieldset>{(options.dpi ?? 300) >= 300 && <p className="border-t border-border pt-3 text-xs text-muted-foreground">{t('export.dpiHint', { dpi: options.dpi ?? 300 })}</p>}</aside>;
}

function Option({ label, children }: { label: string; children: React.ReactNode }) { return <label className="grid gap-1.5 text-xs font-semibold"><span>{label}</span>{children}</label>; }
function Count({ label, value }: { label: string; value: number }) { return <span className="rounded-full bg-background px-2.5 py-1 text-muted-foreground"><strong className="mr-1 text-foreground">{value}</strong>{label}</span>; }
function ProgressMessage({ label }: { label: string }) { return <div role="status" className="flex items-center gap-2 rounded-lg bg-primary/5 px-3 py-2 text-sm text-primary"><Loader2 className="size-4 animate-spin" />{label}</div>; }
function ErrorBox({ message }: { message: string }) { return <div role="alert" className="flex items-start gap-2 rounded-xl border border-destructive/25 bg-destructive/5 px-3 py-2 text-sm text-destructive"><AlertCircle className="mt-0.5 size-4 shrink-0" />{message}</div>; }
function formatBytes(bytes: number): string { if (bytes < 1024) return `${bytes} B`; if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`; return `${(bytes / (1024 * 1024)).toFixed(1)} MB`; }
